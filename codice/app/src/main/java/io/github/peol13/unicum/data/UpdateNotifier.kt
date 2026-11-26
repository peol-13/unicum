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

/** Notifica "è uscita una nuova versione", mandata da [UpdateCheck] quando la trova il lavoro in background. */
object UpdateNotifier {
    private const val CHANNEL = "app_updates"
    private const val NOTIFICATION_ID = 400

    fun notify(context: Context, version: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Aggiornamenti dell'app"), NotificationManager.IMPORTANCE_DEFAULT))
        // Apre l'app: nella Home c'è il banner che scarica e installa l'aggiornamento
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = tr("Tocca per aprire l'app e installarla: i tuoi dati restano.")
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(tr("È uscita UNICUM %s", version))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }
}
