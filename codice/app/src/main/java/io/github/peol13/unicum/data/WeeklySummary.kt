package io.github.peol13.unicum.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.peol13.unicum.MainActivity
import io.github.peol13.unicum.R
import io.github.peol13.unicum.i18n.lnum
import io.github.peol13.unicum.i18n.tr
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Il lunedì (o il martedì, se lunedì non è passato) una notifica con il riepilogo della settimana appena finita:
 * deficit medio, minuti di attività e sonno medio. Toccandola si apre il racconto della settimana.
 * Una volta a settimana, solo se c'è qualcosa da raccontare e se non è spenta (Profilo → Notifiche).
 */
object WeeklySummary {
    private const val PREFS = "unicum_reminders"
    private const val CHANNEL = "weekly_summary"
    private const val KEY_ENABLED = "weekly_summary_enabled"
    private const val KEY_LAST = "weekly_summary_last"
    private const val NOTIFICATION_ID = 400
    /** Intent dalla notifica: apre il racconto della settimana. */
    const val EXTRA_OPEN_WEEK = "io.github.peol13.unicum.OPEN_WEEK"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** È il momento: lunedì o martedì dalle 9, settimana non ancora notificata. Logica pura, con test. */
    fun isDue(today: LocalDate, now: LocalTime, lastNotifiedMonday: String?): Boolean {
        val dow = today.dayOfWeek
        if (dow != DayOfWeek.MONDAY && dow != DayOfWeek.TUESDAY) return false
        if (dow == DayOfWeek.MONDAY && now.hour < 9) return false
        return lastNotifiedMonday != DateUtils.weekStart(today).minusWeeks(1).toString()
    }

    /** Il testo della notifica dai giorni della settimana; null se non c'è niente da dire. Logica pura, con test. */
    fun text(days: List<WeekDay>): String? {
        val deficits = days.filter { it.countsForAverages }.mapNotNull { it.deficitKcal }
        val minutes = days.sumOf { it.activityMinutes }
        val nights = days.mapNotNull { it.sleepMinutes }
        val parts = buildList {
            if (deficits.isNotEmpty()) {
                val avg = deficits.average().roundToInt()
                add(if (avg >= 0) tr("deficit medio %s kcal", lnum(avg)) else tr("surplus medio %s kcal", lnum(-avg)))
            }
            if (minutes > 0) add(tr("%d min di attività", minutes))
            if (nights.isNotEmpty()) {
                val m = nights.average().roundToInt()
                add(tr("sonno medio %s", "${m / 60} h ${"%02d".format(m % 60)}"))
            }
        }
        if (parts.isEmpty()) return null
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() } + ". " + tr("Tocca per il racconto della settimana.")
    }

    suspend fun notifyIfDue(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        val today = DateUtils.today()
        if (!isEnabled(app) || !isDue(today, LocalTime.now(), p.getString(KEY_LAST, null))) return
        val monday = DateUtils.weekStart(today).minusWeeks(1)
        val text = text(UnicumRepository.loadWeekStats(app, monday)) ?: return
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Riepilogo della settimana"), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            app, NOTIFICATION_ID,
            Intent(app, MainActivity::class.java).putExtra(EXTRA_OPEN_WEEK, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(tr("La tua settimana è pronta"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
        p.edit().putString(KEY_LAST, monday.toString()).apply()
    }
}
