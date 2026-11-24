package io.github.peol13.unicum.widget

import io.github.peol13.unicum.i18n.tr

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import io.github.peol13.unicum.R
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.Readiness
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
                setTextViewText(R.id.wd_label, if (today.deficit >= 0) tr("Deficit") else tr("Surplus"))
                setTextViewText(R.id.wd_value, "${UnicumWidget.fmt(kotlin.math.abs(today.deficit))} kcal")
                setTextViewText(R.id.wd_goal, tr("obiettivo %s", UnicumWidget.fmt(goal)))
                setDeficitLine(context, R.id.wd_line, R.id.wd_marker, today.deficit, goal, innerWidthDp(manager, id, paddingDp = 14))
                setOnClickPendingIntent(R.id.wd_root, UnicumWidget.openAppIntent(context, 410, null))
            }
            manager.updateAppWidget(id, views)
        }
    }
}

/**
 * Widget 2x1 del recupero, in due colonne come la card in cima ad Allenamenti: a sinistra l'anello con la
 * percentuale già recuperata e il tempo che manca, a destra la prontezza all'allenamento dalle ultime notti
 * ([io.github.peol13.unicum.data.Readiness]). Tocco = apre Allenamenti. Se il widget è basso restano solo anelli e
 * valori; se è alto compare anche l'ora in cui sarai pronto.
 *
 * La stima rilegge gli allenamenti dal database: se ci mette troppo (o fallisce) si usa l'ultima salvata, il cui
 * tempo rimasto si ricalcola dall'ora "pronto alle" — così il widget resta giusto anche tra un aggiornamento e l'altro.
 * La prontezza salvata vale solo se è della notte di oggi o di ieri.
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

        // Prontezza: quella appena calcolata, altrimenti l'ultima salvata se è ancora di oggi o di ieri
        val readiness: Int? = if (loaded != null) {
            loaded.readiness?.also { prefs.edit().putInt("readiness", it.score).putString("readiness_date", it.date.toString()).apply() }?.score
        } else {
            val date = prefs.getString("readiness_date", null)
            prefs.getInt("readiness", -1).takeIf { it >= 0 && date != null && date >= DateUtils.today().minusDays(1).toString() }
        }

        // Senza allenamenti nel database (o mai calcolato): niente valori inventati
        val remaining = saved?.let { ((it.readyAt - now) / 3_600_000.0).coerceAtLeast(0.0) }
        val progress = when {
            saved == null -> null
            remaining == 0.0 || saved.totalHours <= 0 -> 1f
            else -> (1 - remaining!! / saved.totalHours).toFloat().coerceIn(0f, 1f)
        }

        for (id in ids) {
            val height = manager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
            // Altezza in dp: sotto 80 solo anelli e valori, da 110 anche la riga dell'ora
            val labels = height == 0 || height >= 80
            val sub = height >= 110
            val views = RemoteViews(context.packageName, R.layout.widget_recovery).apply {
                setRing(context, R.id.wr_ring_track, R.id.wr_ring, progress ?: 0f, sizeDp = 40, strokeDp = 4.5f)
                setTextViewText(R.id.wr_percent, progress?.let { "${(it * 100).roundToInt()}%" } ?: "–")
                when {
                    saved == null || remaining == null -> {
                        setTextViewText(R.id.wr_label, tr("Recupero"))
                        setTextViewText(R.id.wr_title, "–")
                        setTextViewText(R.id.wr_sub, tr("nessun allenamento"))
                    }
                    remaining <= 0.0 -> {
                        setTextViewText(R.id.wr_label, tr("Recupero"))
                        setTextViewText(R.id.wr_title, tr("Recuperato"))
                        setTextViewText(R.id.wr_sub, tr("sei pronto"))
                    }
                    else -> {
                        // Testi corti: mezza colonna di un 2x1 ha posto per circa 10 caratteri
                        setTextViewText(R.id.wr_label, tr("Pronto tra"))
                        setTextViewText(R.id.wr_title, hoursText(remaining))
                        setTextViewText(R.id.wr_sub, readyText(saved.readyAt))
                    }
                }
                setRing(context, R.id.wp_ring_track, R.id.wp_ring, (readiness ?: 0) / 100f, sizeDp = 40, strokeDp = 4.5f)
                setTextViewText(R.id.wp_percent, readiness?.let { "$it%" } ?: "–")
                setTextViewText(R.id.wp_label, tr("Prontezza"))
                setTextViewText(R.id.wp_title, readiness?.let { Readiness.levelLabel(it) } ?: "–")

                val labelVisibility = if (labels) android.view.View.VISIBLE else android.view.View.GONE
                setViewVisibility(R.id.wr_label, labelVisibility)
                setViewVisibility(R.id.wp_label, labelVisibility)
                setViewVisibility(R.id.wr_sub, if (sub) android.view.View.VISIBLE else android.view.View.GONE)
                setOnClickPendingIntent(R.id.wr_root, UnicumWidget.openAppIntent(context, 420, null, openTraining = true))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
