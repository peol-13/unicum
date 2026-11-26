package io.github.peol13.unicum.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import io.github.peol13.unicum.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * **Conteggio anonimo delle aperture**, per tutti, senza chiedere e senza interruttore (scelta dell'utente, 26/11/2026).
 *
 * Diverso dalle [UsageStats] (facoltative, con il codice dell'installazione): qui **non c'è nessun codice**,
 * quindi nessun invio si può collegare a un altro né a un telefono. Rientra nelle statistiche che il Garante
 * tratta come "tecniche" (linee guida cookie e altri strumenti di tracciamento, 10/6/2021, par. 7.2):
 * solo statistiche aggregate su questa app, nessun identificatore, servizio che non salva l'IP e non riusa i dati.
 *
 * - Una volta al giorno, alla prima apertura, l'app manda a GoatCounter ([BuildConfig.ANON_COUNT_URL]) conteggi
 *   separati tra loro: "aperta oggi", versione, fascia di anzianità dell'installazione, lingua, versione di Android,
 *   funzioni collegate, schermata principale e tema; per i giorni passati, una volta ciascuna, le schermate aperte
 *   (il nome, non quante volte). Ogni voce è un conteggio a sé, con una sessione casuale usa e getta: dal servizio
 *   non si possono incrociare.
 * - Mai dati di salute, pasti, allenamenti, profilo, nomi o codici.
 * - Le build di debug (telefono di chi sviluppa) e gli emulatori non inviano niente ([DevDevice]), salvo
 *   [BuildConfig.ANON_COUNT_DEBUG] per le prove.
 * - Senza indirizzo o chiave (chi compila il codice per sé) il conteggio è spento.
 */
object AnonCount {
    private const val PREFS = "anon_count"
    private const val K_OPEN_DAYS = "open_days"
    private const val K_SCREENS = "screens"
    private const val K_LAST_SENT = "last_sent"

    private lateinit var prefs: SharedPreferences
    private var installDay: LocalDate? = null

    /** Indirizzo e chiave presenti: senza, il conteggio non parte. */
    val available: Boolean get() = BuildConfig.ANON_COUNT_URL.isNotBlank() && BuildConfig.ANON_COUNT_TOKEN.isNotBlank()

    private val active: Boolean
        get() = available && ::prefs.isInitialized && (BuildConfig.ANON_COUNT_DEBUG || !DevDevice.notCounted)

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        installDay = runCatching {
            val time = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()
    }

    /** Una schermata aperta oggi (solo il nome, senza id). Conta una volta per giorno. */
    fun screen(name: String, today: LocalDate = LocalDate.now()) {
        if (!active) return
        prefs.edit().putString(K_SCREENS, AnonHits.addScreen(prefs.getString(K_SCREENS, null), today, name)).apply()
    }

    /**
     * Alla prima apertura del giorno: segna oggi tra i giorni d'uso e invia quello che manca
     * (i giorni d'uso non ancora inviati, oggi compreso, e le schermate dei giorni passati).
     * Se l'invio non riesce resta tutto e si riprova alla prossima apertura.
     */
    suspend fun sendIfDue(features: List<String>, today: LocalDate = LocalDate.now()) {
        if (!active) return
        val openDays = UsageCounts.addDay(prefs.getString(K_OPEN_DAYS, null), today)
        prefs.edit().putString(K_OPEN_DAYS, openDays).apply()
        if (prefs.getString(K_LAST_SENT, null) == today.toString()) return
        val batch = AnonHits.build(
            openDays = openDays,
            screens = prefs.getString(K_SCREENS, null),
            today = today,
            info = AnonHits.Info(
                version = BuildConfig.VERSION_NAME,
                androidSdk = Build.VERSION.SDK_INT,
                language = io.github.peol13.unicum.i18n.I18n.language.code,
                features = features,
                installDay = installDay,
            ),
            now = Instant.now(),
        )
        if (batch.hits.isEmpty()) return
        if (post(AnonHits.body(batch.hits))) {
            // Si tolgono solo le cose inviate: un'apertura arrivata nel frattempo resta per il prossimo invio
            prefs.edit()
                .putString(K_OPEN_DAYS, AnonHits.without(prefs.getString(K_OPEN_DAYS, null), batch.sentDays))
                .putString(K_SCREENS, AnonHits.screensWithout(prefs.getString(K_SCREENS, null), batch.sentScreenDays))
                .putString(K_LAST_SENT, today.toString())
                .apply()
        }
    }

    private suspend fun post(body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(BuildConfig.ANON_COUNT_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = false
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", AppInfo.USER_AGENT)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer " + BuildConfig.ANON_COUNT_TOKEN)
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                conn.responseCode in 200..299
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

/** Costruzione dei conteggi da inviare. Logica pura, con test. */
object AnonHits {
    /** Al massimo tanti giorni arretrati per invio (un telefono rimasto a lungo senza rete non manda una valanga). */
    const val MAX_DAYS = 7

    data class Info(
        val version: String,
        val androidSdk: Int,
        val language: String,
        val features: List<String>,
        val installDay: LocalDate?,
    )

    data class Hit(val path: String, val title: String, val createdAt: Instant)

    /** I conteggi e i giorni che contengono (da togliere dal telefono se l'invio riesce). */
    data class Batch(val hits: List<Hit>, val sentDays: List<String>, val sentScreenDays: List<String>)

    /** Fascia di anzianità dell'installazione nel giorno [day]: larga apposta, perché non renda riconoscibile nessuno. */
    fun ageBand(installDay: LocalDate?, day: LocalDate): String {
        if (installDay == null) return "sconosciuta"
        val days = ChronoUnit.DAYS.between(installDay, day)
        return when {
            days <= 1 -> "nuova"
            days <= 7 -> "2-7-giorni"
            days <= 30 -> "8-30-giorni"
            else -> "oltre-30-giorni"
        }
    }

    private fun clean(s: String) = s.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').take(40).ifEmpty { "altro" }

    /** Schermate per giorno, salvate come "2026-11-25|Home;2026-11-25|Diario" (ogni nome una volta per giorno). */
    fun addScreen(text: String?, day: LocalDate, name: String): String {
        val entry = day.toString() + "|" + name.replace(Regex("[|;\\s]+"), "_").take(40)
        val list = text?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        return if (entry in list) text.orEmpty() else (list + entry).joinToString(";")
    }

    private fun screensByDay(text: String?): Map<String, List<String>> =
        text?.split(";")?.mapNotNull { e ->
            val p = e.split("|")
            if (p.size == 2 && p[0].length == 10 && p[1].isNotBlank()) p[0] to p[1] else null
        }?.groupBy({ it.first }, { it.second }) ?: emptyMap()

    fun without(daysText: String?, sent: List<String>): String =
        daysText?.split(",")?.filter { it.length == 10 && it !in sent }?.joinToString(",").orEmpty()

    fun screensWithout(text: String?, sentDays: List<String>): String =
        text?.split(";")?.filter { it.isNotBlank() && it.substringBefore("|") !in sentDays }?.joinToString(";").orEmpty()

    /** Mezzogiorno UTC di un giorno passato; per oggi l'ora vera. */
    private fun at(day: LocalDate, today: LocalDate, now: Instant): Instant =
        if (day == today) now else day.atTime(12, 0).toInstant(ZoneOffset.UTC)

    fun build(openDays: String?, screens: String?, today: LocalDate, info: Info, now: Instant): Batch {
        val days = openDays?.split(",")?.filter { it.length == 10 && it <= today.toString() }?.distinct()?.sorted()
            ?.takeLast(MAX_DAYS) ?: emptyList()
        val byDay = screensByDay(screens).filterKeys { it < today.toString() }
        val screenDays = byDay.keys.sorted().takeLast(MAX_DAYS)
        val hits = mutableListOf<Hit>()
        for (d in days) {
            val day = LocalDate.parse(d)
            val t = at(day, today, now)
            hits += Hit("/aperta", "Aperta (installazioni attive)", t)
            hits += Hit("/versione/" + clean(info.version), "Versione " + info.version, t)
            hits += Hit("/anzianita/" + ageBand(info.installDay, day), "Anzianità dell'installazione", t)
            hits += Hit("/lingua/" + clean(info.language.ifBlank { "sistema" }), "Lingua dell'app", t)
            hits += Hit("/android/" + info.androidSdk, "Versione di Android (API)", t)
            for (f in info.features.distinct()) {
                hits += when {
                    f.startsWith("home_") -> Hit("/schermata-principale/" + clean(f.removePrefix("home_")), "Schermata principale", t)
                    f.startsWith("tema_") -> Hit("/tema/" + clean(f.removePrefix("tema_")), "Tema", t)
                    else -> Hit("/funzione/" + clean(f), "Funzione collegata", t)
                }
            }
        }
        for (d in screenDays) {
            val t = at(LocalDate.parse(d), today, now)
            for (s in byDay.getValue(d).distinct()) hits += Hit("/schermata/" + clean(s), "Schermata aperta", t)
        }
        return Batch(hits, days, screenDays)
    }

    /**
     * Corpo per l'API di GoatCounter. Ogni conteggio ha una "sessione" casuale nuova, mai salvata né riusata:
     * GoatCounter conta una visita per sessione, quindi così ogni conteggio vale uno e nessuno si collega a un altro.
     * (Con `no_sessions` GoatCounter li unisce tutti in una sola visita: provato il 26/11/2026.)
     */
    fun body(hits: List<Hit>, newSession: () -> String = { UUID.randomUUID().toString() }): String = buildJsonObject {
        put("hits", buildJsonArray {
            for (h in hits) add(buildJsonObject {
                put("path", h.path)
                put("title", h.title)
                put("created_at", JsonPrimitive(h.createdAt.truncatedTo(ChronoUnit.SECONDS).toString()))
                put("session", newSession())
            })
        })
    }.toString()
}
