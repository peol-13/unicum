package io.github.peol13.unicum.widget

import io.github.peol13.unicum.i18n.tr

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.github.peol13.unicum.MainActivity
import io.github.peol13.unicum.R
import io.github.peol13.unicum.data.DayEnergy
import io.github.peol13.unicum.data.MetricType
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.data.UserSettings
import kotlinx.coroutines.runBlocking
import java.text.NumberFormat
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

/** Widget per la schermata del telefono: si aggiorna da solo ogni 30 minuti e dopo ogni pasto o allenamento. */
class UnicumWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        thread {
            try {
                UnicumWidget.render(context.applicationContext, manager, ids)
            } catch (e: Exception) {
                Log.e("UnicumWidget", "Widget non aggiornato", e)
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * Contenuto del widget: anello delle calorie mangiate rispetto all'obiettivo, calorie rimaste, linea del
 * deficit (verde/giallo/rosso come nell'app) e il bottone "+ Pasto" che apre la registrazione.
 */
object UnicumWidget {
    /** Extra dell'Intent che apre l'app sulla registrazione di un pasto (valore: tipo di pasto, "" = in base all'ora). */
    const val EXTRA_ADD_MEAL = "io.github.peol13.unicum.ADD_MEAL"

    private val DeficitGreen = 0xFF27C978.toInt()
    private val DeficitYellow = 0xFFF2C94C.toInt()
    private val DeficitRed = 0xFFE85D5D.toInt()

    /** Extra dell'Intent che apre l'app sulla sezione Allenamenti (widget del recupero). */
    const val EXTRA_OPEN_TRAINING = "io.github.peol13.unicum.OPEN_TRAINING"

    /** Chiede l'aggiornamento di tutti i widget presenti, grandi e piccoli (nessuno = non fa nulla). */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        listOf(UnicumWidgetProvider::class.java, DeficitWidgetProvider::class.java, RecoveryWidgetProvider::class.java).forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) return@forEach
            context.sendBroadcast(
                Intent(context, cls)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }

    /** Intent che apre l'app, facoltativamente sulla registrazione di un pasto o sulla sezione Allenamenti. */
    fun openAppIntent(context: Context, requestCode: Int, addMealType: String?, openTraining: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (addMealType != null) intent.putExtra(EXTRA_ADD_MEAL, addMealType)
        if (openTraining) intent.putExtra(EXTRA_OPEN_TRAINING, true)
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Calorie di oggi per i widget: mangiate, obiettivo, deficit (bruciate − mangiate) e calorie rimaste. */
    data class Today(val eaten: Int, val goal: Int, val deficit: Int, val remaining: Int)

    /** Legge i dati di oggi dal database (fuori dal thread principale). */
    fun today(context: Context): Today {
        UserSettings.init(context)
        val today = UnicumRepository.today()
        val (meals, activities, health) = runBlocking {
            Triple(
                UnicumRepository.loadMeals(context, today),
                UnicumRepository.loadActivities(context, today),
                UnicumRepository.loadHealth(context, 1, today)
            )
        }
        val eaten = meals.sumOf { it.calories }
        val goal = UserSettings.intakeGoalKcal
        val extra = DayEnergy.extraBurn(
            activeKcalHealthConnect = health.metric(MetricType.ACTIVE_KCAL, today),
            importedWorkoutKcal = activities.filter { !it.isManual }.sumOf { it.calories },
            manualWorkoutKcal = activities.filter { it.isManual }.sumOf { it.calories }
        )
        val deficit = DayEnergy.deficit(eaten, UserSettings.restingBurnSoFar(), extra)
        return Today(eaten, goal, deficit, DayEnergy.remaining(goal, eaten))
    }

    /** Legge i dati di oggi e ridisegna i widget. Va chiamato fuori dal thread principale (legge il database). */
    fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val (eaten, goal, deficit, remaining) = today(context)

        val views = RemoteViews(context.packageName, R.layout.widget_unicum).apply {
            setTextViewText(R.id.widget_label, if (remaining >= 0) tr("Rimangono") else tr("Oltre l'obiettivo di"))
            setTextViewText(R.id.widget_remaining, "${fmt(abs(remaining))} kcal")
            setTextViewText(R.id.widget_eaten, tr("%s di %s kcal mangiate", fmt(eaten), fmt(goal)))
            setTextViewText(
                R.id.widget_deficit,
                (if (deficit >= 0) tr("Deficit %s kcal", fmt(deficit)) else tr("Surplus %s kcal", fmt(-deficit))) +
                    " · " + tr("obiettivo %s", fmt(UserSettings.targetDeficitKcal))
            )
            setRing(context, R.id.widget_ring_track, R.id.widget_ring, eaten / goal.coerceAtLeast(1).toFloat())
            setDeficitLine(context, R.id.widget_deficit_line, R.id.widget_deficit_marker, deficit, UserSettings.targetDeficitKcal)
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, 400, null))
            setOnClickPendingIntent(R.id.widget_add, openAppIntent(context, 401, ""))
        }
        manager.updateAppWidget(ids, views)
    }

    internal fun fmt(v: Int): String = NumberFormat.getIntegerInstance(io.github.peol13.unicum.i18n.I18n.locale).format(v)

    /**
     * Colora un'immagine disegnata in bianco con un colore delle risorse. Da Android 12 il colore si risolve quando
     * il widget viene mostrato, quindi segue il tema chiaro/scuro anche se cambia dopo l'ultimo aggiornamento.
     */
    private fun RemoteViews.tint(context: Context, id: Int, colorRes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setColorStateList(id, "setImageTintList", colorRes)
        else setInt(id, "setColorFilter", ContextCompat.getColor(context, colorRes))
    }

    private fun strokePaint(stroke: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    /** Anello di avanzamento su due immagini sovrapposte: traccia tenue ([trackId]) e arco nel colore primario ([arcId]). */
    internal fun RemoteViews.setRing(
        context: Context, trackId: Int, arcId: Int, progress: Float, sizeDp: Int = 56, strokeDp: Float = 7f
    ) {
        val d = context.resources.displayMetrics.density
        val size = (sizeDp * d).toInt()
        val stroke = strokeDp * d
        val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val track = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(track).drawArc(rect, 0f, 360f, false, strokePaint(stroke))
        val arc = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) Canvas(arc).drawArc(rect, -90f, 360f * p, false, strokePaint(stroke))
        setImageViewBitmap(trackId, track)
        setImageViewBitmap(arcId, arc)
        tint(context, trackId, R.color.widget_track)
        tint(context, arcId, R.color.widget_primary)
    }

    /**
     * Linea del deficit come nell'app: verde, giallo, rosso ([barId], colori fissi) e il triangolo che indica a che
     * punto sei ([markerId], nel colore del testo). Le due immagini hanno la stessa misura e stanno sovrapposte.
     */
    internal fun RemoteViews.setDeficitLine(
        context: Context, barId: Int, markerId: Int, deficit: Int, goalDeficit: Int, widthDp: Int = 300
    ) {
        val d = context.resources.displayMetrics.density
        val w = (widthDp * d).toInt()
        val h = (18 * d).toInt()
        val goal = if (goalDeficit > 0) goalDeficit.toFloat() else 500f
        val pos = ((-deficit - (-2 * goal)) / (3 * goal)).coerceIn(0f, 1f)
        val barTop = 10 * d
        val barH = 7 * d
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // tre zone con estremi arrotondati
        val bar = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bar)
        val third = w / 3f
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, barTop, w.toFloat(), barTop + barH), barH / 2, barH / 2, Path.Direction.CW) })
        listOf(DeficitGreen, DeficitYellow, DeficitRed).forEachIndexed { i, c ->
            paint.color = c
            canvas.drawRect(third * i, barTop, third * (i + 1), barTop + barH, paint)
        }

        val marker = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val x = (pos * w).coerceIn(5 * d, w - 5 * d)
        paint.color = Color.WHITE
        Canvas(marker).drawPath(Path().apply {
            moveTo(x, barTop - 1.5f * d)
            lineTo(x - 5 * d, 0f)
            lineTo(x + 5 * d, 0f)
            close()
        }, paint)
        setImageViewBitmap(barId, bar)
        setImageViewBitmap(markerId, marker)
        tint(context, markerId, R.color.widget_text)
    }
}
