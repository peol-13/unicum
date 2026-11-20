package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.peol13.unicum.widget.UnicumWidget
import io.github.peol13.unicum.R
import io.github.peol13.unicum.data.db.UnicumDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.concurrent.thread

/**
 * Promemoria per registrare i pasti: una notifica all'orario scelto per ogni tipo di pasto.
 * Se a quell'ora il pasto è già stato registrato oggi, la notifica non compare.
 * Le impostazioni stanno in file proprio perché il ricevitore parte anche ad app chiusa.
 */
object MealReminders {
    private const val PREFS = "unicum_reminders"
    private const val CHANNEL = "meal_reminders"
    const val EXTRA_TYPE = "meal_type"

    val types = MealTime.TYPES

    /** Nome mostrato per ogni tipo ("Altro" = spuntino). */
    fun label(type: String) = if (type == "Altro") tr("Spuntino") else MealTime.label(type)

    private fun default(type: String): Int = when (type) {
        "Colazione" -> 8 * 60 + 30
        "Pranzo" -> 13 * 60
        "Cena" -> 20 * 60
        else -> -1
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Minuto del giorno in cui suona il promemoria, oppure null se è spento. */
    fun minuteOf(context: Context, type: String): Int? =
        prefs(context).getInt("min_$type", default(type)).takeIf { it >= 0 }

    fun set(context: Context, type: String, minute: Int?) {
        prefs(context).edit().putInt("min_$type", minute ?: -1).apply()
        schedule(context, type)
    }

    fun scheduleAll(context: Context) = types.forEach { schedule(context, it) }

    private fun pending(context: Context, type: String): PendingIntent {
        val intent = Intent(context, MealReminderReceiver::class.java).putExtra(EXTRA_TYPE, type)
        return PendingIntent.getBroadcast(
            context, types.indexOf(type), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Programma (o annulla) la prossima notifica di quel pasto. */
    fun schedule(context: Context, type: String) {
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val minute = minuteOf(app, type)
        if (minute == null) {
            alarms.cancel(pending(app, type))
            return
        }
        var at = LocalDateTime.of(LocalDate.now(), LocalTime.of(minute / 60, minute % 60))
        if (!at.isAfter(LocalDateTime.now().plusSeconds(30))) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inesatto di proposito: non richiede il permesso per le sveglie esatte
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(app, type))
    }

    fun notify(context: Context, type: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Promemoria pasti"), NotificationManager.IMPORTANCE_DEFAULT)
        )
        // Toccandola si apre la registrazione di quel pasto
        val open = UnicumWidget.openAppIntent(context, 100 + types.indexOf(type), type)
        val text = when (type) {
            "Colazione" -> tr("Hai fatto colazione? Registrala in UNICUM.")
            "Pranzo" -> tr("È ora di pranzo: ricordati di registrare cosa mangi.")
            "Cena" -> tr("Hai già cenato? Registra la cena in UNICUM.")
            else -> tr("Hai fatto uno spuntino? Registralo in UNICUM.")
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(label(type))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(200 + types.indexOf(type), n)
    }
}

/** Suona all'orario del promemoria (e riprogramma quello del giorno dopo); dopo il riavvio riprogramma tutto. */
class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MealReminders.scheduleAll(app)
            return
        }
        val type = intent.getStringExtra(MealReminders.EXTRA_TYPE) ?: return
        val result = goAsync()
        thread {
            try {
                // Già registrato oggi? Allora niente notifica
                val dao = UnicumDatabase.get(app).dao()
                val today = UnicumRepository.today()
                // Lo spuntino conta anche se ha un nome proprio (es. "Spuntino pomeriggio")
                val done = (if (type == MealTime.OTHER) dao.countOtherMeals(today) else dao.countMealsOfType(today, type)) > 0
                if (!done && MealReminders.minuteOf(app, type) != null) MealReminders.notify(app, type)
            } catch (e: Exception) {
                Log.e("MealReminders", "Promemoria non mostrato", e)
            } finally {
                // Anche se qualcosa va storto, il promemoria di domani resta programmato
                MealReminders.schedule(app, type)
                result.finish()
            }
        }
    }
}
