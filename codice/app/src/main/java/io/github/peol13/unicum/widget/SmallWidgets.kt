package io.github.peol13.unicum.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import io.github.peol13.unicum.R
import io.github.peol13.unicum.data.RecoveryData
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.screens.hoursText
import io.github.peol13.unicum.widget.UnicumWidget.setDeficitLine
import io.github.peol13.unicum.widget.UnicumWidget.setRing
import io.github.peol13.unicum.ui.screens.readyText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Base dei widget piccoli (2x1): disegna ogni widget con la sua larghezza, fuori dal thread principale,
 * dopo ogni aggiornamento e quando l'utente lo ridimensiona.
 */
abstract class SmallWidgetProvider : AppWidgetProvider() {
    protected abstract fun render(context: Context, manager: AppWidgetManager, ids: IntArray)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = renderAsync(context, manager, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        renderAsync(context, manager, intArrayOf(id))

    private fun renderAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        thread {
            try {
                render(context.applicationContext, manager, ids)
            } catch (e: Exception) {
                Log.e("UnicumWidget", "Widget piccolo non aggiornato", e)
            } finally {
                result.finish()
            }
        }
    }
}

/** Larghezza utile (dp) del widget [id], tolto il margine interno; [fallback] se il launcher non la dice. */
private fun innerWidthDp(manager: AppWidgetManager, id: Int, paddingDp: Int, fallback: Int = 150): Int {
    val w = manager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
    return ((if (w > 0) w else fallback) - 2 * paddingDp).coerceAtLeast(60)
}

/** Widget 2x1 del deficit di oggi: valore, obiettivo e la linea verde/gialla/rossa dell'app. Tocco = apre la Home. */
class DeficitWidgetProvider : SmallWidgetProvider() {
    override fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val today = UnicumWidget.today(context)
        val goal = UserSettings.targetDeficitKcal
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_deficit).apply {
                setTextViewText(R.id.wd_label, if (today.deficit >= 0) "Deficit" else "Surplus")
                setTextViewText(R.id.wd_value, "${UnicumWidget.fmt(kotlin.math.abs(today.deficit))} kcal")
                setTextViewText(R.id.wd_goal, "obiettivo ${UnicumWidget.fmt(goal)}")
                setDeficitLine(context, R.id.wd_line, R.id.wd_marker, today.deficit, goal, innerWidthDp(manager, id, paddingDp = 14))
                setOnClickPendingIntent(R.id.wd_root, UnicumWidget.openAppIntent(context, 410, null))
            }
            manager.updateAppWidget(id, views)
        }
    }
}

/**
 * Widget 2x1 del recupero: anello con la percentuale già recuperata, tempo che manca e ora in cui sarai pronto
 * (stessa stima della card in cima ad Allenamenti). Tocco = apre Allenamenti.
 *
 * La stima rilegge gli allenamenti dal database: se ci mette troppo (o fallisce) si usa l'ultima salvata, il cui
 * tempo rimasto si ricalcola dall'ora "pronto alle" — così il widget resta giusto anche tra un aggiornamento e l'altro.
 */
class RecoveryWidgetProvider : SmallWidgetProvider() {
    private class Saved(val totalHours: Double, val readyAt: Long)

    override fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        UserSettings.init(context)
        val prefs = context.getSharedPreferences("widget_recovery", Context.MODE_PRIVATE)
        val loaded = runBlocking { withTimeoutOrNull(8_000) { runCatching { RecoveryData.load(context) }.getOrNull() } }
        val now = System.currentTimeMillis()
        val saved: Saved? = if (loaded != null) {
            val r = loaded.result
            Saved(r.totalHours, r.readyAtMillis ?: now).also {
                prefs.edit().putFloat("total", it.totalHours.toFloat()).putLong("ready", it.readyAt).putBoolean("has", true).apply()
            }
        } else if (prefs.getBoolean("has", false)) {
            Saved(prefs.getFloat("total", 0f).toDouble(), prefs.getLong("ready", 0L))
        } else null

        // Senza allenamenti nel database (o mai calcolato): niente valori inventati
        val remaining = saved?.let { ((it.readyAt - now) / 3_600_000.0).coerceAtLeast(0.0) }
        val progress = when {
            saved == null -> null
            remaining == 0.0 || saved.totalHours <= 0 -> 1f
            else -> (1 - remaining!! / saved.totalHours).toFloat().coerceIn(0f, 1f)
        }

        val views = RemoteViews(context.packageName, R.layout.widget_recovery).apply {
            setRing(context, R.id.wr_ring_track, R.id.wr_ring, progress ?: 0f, sizeDp = 44, strokeDp = 5f)
            setTextViewText(R.id.wr_percent, progress?.let { "${(it * 100).roundToInt()}%" } ?: "–")
            when {
                saved == null || remaining == null -> {
                    setTextViewText(R.id.wr_label, "Recupero")
                    setTextViewText(R.id.wr_title, "–")
                    setTextViewText(R.id.wr_sub, "nessun allenamento")
                }
                remaining <= 0.0 -> {
                    setTextViewText(R.id.wr_label, "Recupero")
                    setTextViewText(R.id.wr_title, "Recuperato")
                    setTextViewText(R.id.wr_sub, "sei pronto")
                }
                else -> {
                    // Testi corti: in 2x1 c'è posto per circa 15 caratteri ("domani 9:00")
                    setTextViewText(R.id.wr_label, "Pronto tra")
                    setTextViewText(R.id.wr_title, hoursText(remaining))
                    setTextViewText(R.id.wr_sub, readyText(saved.readyAt).replace(" alle ", " "))
                }
            }
            setOnClickPendingIntent(R.id.wr_root, UnicumWidget.openAppIntent(context, 420, null, openTraining = true))
        }
        manager.updateAppWidget(ids, views)
    }
}
