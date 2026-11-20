package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.peol13.unicum.MainActivity
import io.github.peol13.unicum.R

/**
 * Notifica degli avvisi sul recupero ([RecoveryAlerts]): al massimo una al giorno, solo se l'utente
 * non l'ha spenta (Profilo → Promemoria e avvisi). Le impostazioni stanno con quelle dei promemoria.
 */
object RecoveryAlertNotifier {
    private const val PREFS = "unicum_reminders"
    private const val CHANNEL = "recovery_alerts"
    private const val KEY_ENABLED = "recovery_alerts_enabled"
    private const val KEY_LAST = "recovery_alert_last_date"
    private const val NOTIFICATION_ID = 300

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Mostra l'avviso se è acceso e se per quel giorno non è già stato mostrato. */
    fun notifyOnce(context: Context, alert: RecoveryAlert) {
        val p = prefs(context)
        if (!isEnabled(context) || p.getString(KEY_LAST, null) == alert.date.toString()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Avvisi sul recupero"), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        p.edit().putString(KEY_LAST, alert.date.toString()).apply()
    }
}
