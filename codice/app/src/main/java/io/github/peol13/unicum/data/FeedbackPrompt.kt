package io.github.peol13.unicum.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.peol13.unicum.BuildConfig
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Il modulo "Come ti trovi?" (pareri, voto sulla prossima funzione, prova sul Play Store) e la card della Home che lo propone.
 *
 * Quando compare la card ([shouldShow], logica pura con test):
 * - almeno [MIN_DAYS_INSTALLED] giorni dall'installazione e almeno [MIN_DAYS_USED] giorni diversi in cui l'app è stata aperta,
 *   così la domanda arriva a chi l'app l'ha usata davvero;
 * - "Rispondi" apre il modulo e la card non torna più;
 * - "Non ora" la rimanda di [SNOOZE_DAYS] giorni; dopo [MAX_DISMISSALS] "Non ora" non torna più.
 * Il modulo resta sempre raggiungibile da Profilo → Invia un parere.
 *
 * Nessun dato parte dall'app: il modulo si apre nel browser e l'utente decide cosa inviare. L'unico campo
 * precompilato è versione dell'app e telefono ([deviceLine]), che l'utente vede e può cancellare.
 */
object FeedbackPrompt {
    /** Modulo Google "UNICUM - come ti trovi (pareri e prova Play Store)": senza account, arriva per email all'autore. */
    const val FORM_URL = "https://docs.google.com/forms/d/e/1FAIpQLSfcQpbejWIYUOtbkkv-rpGvtRq7Qx9fv9LEwcMOA1jQMAu49g/viewform"
    /** Campo "Versione dell'app e telefono" del modulo. */
    private const val DEVICE_ENTRY = "entry.781528088"

    const val MIN_DAYS_INSTALLED = 5
    const val MIN_DAYS_USED = 3
    const val SNOOZE_DAYS = 7
    const val MAX_DISMISSALS = 2

    private const val PREFS = "feedback_prompt"
    private const val K_DAYS_USED = "days_used"
    private const val K_LAST_DAY = "last_day"
    private const val K_ANSWERED = "answered"
    private const val K_DISMISSALS = "dismissals"
    private const val K_SNOOZED_UNTIL = "snoozed_until"

    private lateinit var prefs: SharedPreferences
    private var installDay: LocalDate? = null

    /** La card va mostrata oggi. */
    var visible by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        installDay = runCatching {
            val time = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()
        recordOpen()
    }

    /** Conta il giorno di oggi tra quelli d'uso (una volta al giorno) e ricalcola se mostrare la card. */
    fun recordOpen(today: LocalDate = LocalDate.now()) {
        if (!::prefs.isInitialized) return
        if (prefs.getString(K_LAST_DAY, null) != today.toString()) {
            prefs.edit()
                .putString(K_LAST_DAY, today.toString())
                .putInt(K_DAYS_USED, prefs.getInt(K_DAYS_USED, 0) + 1)
                .apply()
        }
        refresh(today)
    }

    /** "Rispondi": il modulo si apre e la card non torna più. */
    fun answered() {
        prefs.edit().putBoolean(K_ANSWERED, true).apply()
        visible = false
    }

    /** "Non ora": la card torna tra [SNOOZE_DAYS] giorni, al massimo [MAX_DISMISSALS] volte in tutto. */
    fun notNow(today: LocalDate = LocalDate.now()) {
        prefs.edit()
            .putInt(K_DISMISSALS, prefs.getInt(K_DISMISSALS, 0) + 1)
            .putString(K_SNOOZED_UNTIL, today.plusDays(SNOOZE_DAYS.toLong()).toString())
            .apply()
        visible = false
    }

    private fun refresh(today: LocalDate) {
        visible = shouldShow(
            today = today,
            installDay = installDay ?: today,
            daysUsed = prefs.getInt(K_DAYS_USED, 0),
            answered = prefs.getBoolean(K_ANSWERED, false),
            dismissals = prefs.getInt(K_DISMISSALS, 0),
            snoozedUntil = prefs.getString(K_SNOOZED_UNTIL, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        )
    }

    /** Regole della card, senza Android (vedi il commento in testa). */
    fun shouldShow(
        today: LocalDate,
        installDay: LocalDate,
        daysUsed: Int,
        answered: Boolean,
        dismissals: Int,
        snoozedUntil: LocalDate?,
    ): Boolean {
        if (answered || dismissals >= MAX_DISMISSALS) return false
        if (snoozedUntil != null && today.isBefore(snoozedUntil)) return false
        if (installDay.plusDays(MIN_DAYS_INSTALLED.toLong()).isAfter(today)) return false
        return daysUsed >= MIN_DAYS_USED
    }

    /** Versione dell'app e telefono, da incollare nelle segnalazioni. */
    fun deviceLine(): String =
        "UNICUM ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    /** Indirizzo del modulo con versione e telefono già scritti. */
    fun formUrl(): String = "$FORM_URL?usp=pp_url&$DEVICE_ENTRY=" + URLEncoder.encode(deviceLine(), "UTF-8")
}
