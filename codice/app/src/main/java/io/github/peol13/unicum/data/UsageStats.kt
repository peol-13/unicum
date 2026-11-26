package io.github.peol13.unicum.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.peol13.unicum.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.UUID

/**
 * Statistiche d'uso **anonime e facoltative**: quante persone usano l'app e quali schermate aprono.
 *
 * - Si attivano solo se l'utente dice sì ([enabled] = true); null = non ancora chiesto, false = no.
 * - Si conta sul telefono: giorni di uso e aperture di ogni schermata (solo il nome, mai i dati mostrati).
 * - Al massimo una volta al giorno l'app invia il riepilogo dei giorni passati a un modulo Google
 *   dello sviluppatore ([FORM_URL]), con un codice casuale che identifica l'installazione, non la persona.
 *   Nessun dato di salute, nessun nome, nessun dato del profilo.
 * - Dicendo no (o spegnendo l'interruttore nel Profilo) i conteggi non inviati e il codice si cancellano.
 */
object UsageStats {
    /**
     * Indirizzo `formResponse` del modulo Google che riceve i riepiloghi e campi `entry.*` delle sue domande.
     * Vuoto = statistiche spente (chi compila il codice senza un proprio modulo non invia niente).
     */
    const val FORM_URL =
        "https://docs.google.com/forms/d/e/1FAIpQLSc_6o_ppeBROOtJgsFP3ePMgTHq05JZQ8dboVOBUUwxz_eMiw/formResponse"
    private const val F_ID = "entry.1668795694"
    /** Versione dell'app e di Android nello stesso campo: "1.0.0 · Android 35". */
    private const val F_VERSION = "entry.1004856377"
    private const val F_DAYS = "entry.1255031741"
    private const val F_SCREENS = "entry.62096466"
    private const val F_FEATURES = "entry.680873532"

    /** Il modulo è configurato: senza, l'app non chiede nemmeno il consenso. */
    val available: Boolean get() = FORM_URL.isNotBlank() && F_ID.isNotBlank()

    private const val PREFS = "usage_stats"
    private const val K_ENABLED = "enabled"
    private const val K_ID = "install_id"
    private const val K_DAYS = "days"
    private const val K_SCREENS = "screens"
    private const val K_LAST_SENT = "last_sent"
    private const val K_ASK_AFTER = "ask_after"
    private const val K_ASKS = "asks"
    /** Dopo tre "Non ora" la domanda non compare più: la scelta resta nel Profilo. */
    private const val MAX_POSTPONES = 3
    private const val POSTPONE_DAYS = 7L

    private lateinit var prefs: SharedPreferences

    /** true = sì, false = no, null = non ancora chiesto. */
    var enabled by mutableStateOf<Boolean?>(null)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = if (prefs.contains(K_ENABLED)) prefs.getBoolean(K_ENABLED, false) else null
        asking = askDue(LocalDate.now())
    }

    /** La domanda delle statistiche dettagliate va mostrata adesso (vedi [askDue] e [postpone]). */
    var asking by mutableStateOf(false)
        private set

    /** Nessuna risposta ancora, meno di [MAX_POSTPONES] rinvii, e il tempo del rinvio è passato. */
    private fun askDue(today: LocalDate): Boolean {
        if (!available || enabled != null || !::prefs.isInitialized) return false
        val after = prefs.getString(K_ASK_AFTER, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return prefs.getInt(K_ASKS, 0) < MAX_POSTPONES && (after == null || !today.isBefore(after))
    }

    /** "Non ora": non è un no, la domanda torna tra [POSTPONE_DAYS] giorni. */
    fun postpone(today: LocalDate = LocalDate.now()) {
        prefs.edit()
            .putString(K_ASK_AFTER, today.plusDays(POSTPONE_DAYS).toString())
            .putInt(K_ASKS, prefs.getInt(K_ASKS, 0) + 1)
            .apply()
        asking = false
    }

    fun choose(on: Boolean) {
        enabled = on
        asking = false
        prefs.edit().apply {
            putBoolean(K_ENABLED, on)
            if (on) {
                if (!prefs.contains(K_ID)) putString(K_ID, UUID.randomUUID().toString())
            } else {
                remove(K_ID); remove(K_DAYS); remove(K_SCREENS); remove(K_LAST_SENT)
            }
        }.apply()
    }

    /** Il codice casuale dell'installazione (solo con statistiche attive): serve per chiedere di cancellare i dati inviati. */
    val installId: String? get() = if (enabled == true && ::prefs.isInitialized) prefs.getString(K_ID, null) else null

    private val active: Boolean get() = available && enabled == true && ::prefs.isInitialized

    /** Una schermata aperta ([name] = nome della schermata, senza id: "Allenamento", non "Allenamento/123"). */
    fun screen(name: String, today: LocalDate = LocalDate.now()) {
        if (!active) return
        val counts = UsageCounts.decode(prefs.getString(K_SCREENS, null)).add(today, name)
        val days = UsageCounts.addDay(prefs.getString(K_DAYS, null), today)
        prefs.edit().putString(K_SCREENS, counts.encode()).putString(K_DAYS, days).apply()
    }

    /**
     * Invia il riepilogo dei giorni prima di oggi (oggi è ancora in corso), al massimo una volta al giorno.
     * Se l'invio non riesce i conteggi restano e si riprova al prossimo avvio. [features] = funzioni in uso
     * (es. "health_connect", "notion"), senza valori.
     */
    suspend fun sendIfDue(features: List<String>, today: LocalDate = LocalDate.now()) {
        if (!active) return
        // Telefono di chi sviluppa (debug) ed emulatori: mai nei numeri (vedi DevDevice)
        if (DevDevice.notCounted) return
        if (prefs.getString(K_LAST_SENT, null) == today.toString()) return
        val all = UsageCounts.decode(prefs.getString(K_SCREENS, null))
        val (past, current) = all.split(today)
        val pastDays = UsageCounts.daysBefore(prefs.getString(K_DAYS, null), today)
        if (pastDays.isEmpty()) return
        val id = prefs.getString(K_ID, null) ?: return
        val fields = mapOf(
            F_ID to id,
            F_VERSION to BuildConfig.VERSION_NAME + (if (BuildConfig.DEBUG) "-debug" else "") +
                " · Android " + Build.VERSION.SDK_INT,
            F_DAYS to pastDays.joinToString(","),
            F_SCREENS to past.summary(),
            F_FEATURES to features.joinToString(",")
        )
        if (post(fields)) {
            prefs.edit()
                .putString(K_SCREENS, current.encode())
                .putString(K_DAYS, UsageCounts.daysFrom(prefs.getString(K_DAYS, null), today))
                .putString(K_LAST_SENT, today.toString())
                .apply()
        }
    }

    private suspend fun post(fields: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = fields.entries.joinToString("&") { (k, v) ->
                URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
            }.toByteArray(Charsets.UTF_8)
            val conn = (URL(FORM_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = false
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", AppInfo.USER_AGENT)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(body) }
                conn.responseCode in 200..399
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Conteggi delle schermate per giorno, salvati come testo "2026-11-16|Home|12;2026-11-16|Allenamenti|3".
 * Logica pura, con test.
 */
data class UsageCounts(val counts: Map<Pair<String, String>, Int> = emptyMap()) {

    fun add(day: LocalDate, screen: String): UsageCounts {
        val key = day.toString() to clean(screen)
        return UsageCounts(counts + (key to (counts[key] ?: 0) + 1))
    }

    /** (giorni prima di [today], da oggi in poi). */
    fun split(today: LocalDate): Pair<UsageCounts, UsageCounts> {
        val t = today.toString()
        val (past, current) = counts.entries.partition { it.key.first < t }
        return UsageCounts(past.associate { it.key to it.value }) to UsageCounts(current.associate { it.key to it.value })
    }

    /** Totale per schermata, dalla più aperta: "Home=12;Allenamenti=3". */
    fun summary(): String = counts.entries
        .groupBy({ it.key.second }, { it.value })
        .mapValues { it.value.sum() }
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .joinToString(";") { "${it.key}=${it.value}" }

    fun encode(): String = counts.entries.joinToString(";") { "${it.key.first}|${it.key.second}|${it.value}" }

    companion object {
        private fun clean(s: String) = s.replace(Regex("[|;=,\\s]+"), "_").take(40)

        fun decode(text: String?): UsageCounts {
            if (text.isNullOrBlank()) return UsageCounts()
            val map = text.split(";").mapNotNull { part ->
                val p = part.split("|")
                val n = p.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                if (p[0].length != 10 || p[1].isEmpty()) null else (p[0] to p[1]) to n
            }.toMap()
            return UsageCounts(map)
        }

        private fun days(text: String?): List<String> =
            text?.split(",")?.filter { it.length == 10 }?.distinct()?.sorted() ?: emptyList()

        /** Aggiunge [day] all'elenco dei giorni di uso "2026-11-15,2026-11-16". */
        fun addDay(text: String?, day: LocalDate): String = (days(text) + day.toString()).distinct().sorted().joinToString(",")

        fun daysBefore(text: String?, today: LocalDate): List<String> = days(text).filter { it < today.toString() }

        fun daysFrom(text: String?, today: LocalDate): String = days(text).filter { it >= today.toString() }.joinToString(",")
    }
}
