package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.time.LocalDate

/**
 * Una seduta del programma di allenamento (corsa o forza, vedi [Sport]): dal database Notion del programma, da un
 * PDF o scritta nell'app. Per la corsa contano km, minuti, ritmo e [structure] (ripetute); per la forza
 * [structure] contiene gli esercizi ([exercises]).
 */
@Serializable
data class PlannedRun(
    val id: String,
    val name: String,
    /** yyyy-MM-dd; null = senza data (non viene mostrata). */
    val date: String?,
    val kind: String?,
    val status: String?,
    val km: Double? = null,
    val minutes: Int? = null,
    val pace: String? = null,
    val rpe: Int? = null,
    val structure: String? = null,
    val focus: String? = null,
    val week: Int? = null,
    val url: String? = null,
    /** [Sport.key] della seduta; null (programmi salvati prima) = corsa. */
    val sport: String? = null
) {
    val localDate: LocalDate? get() = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val sportKind: Sport get() = Sport.of(sport) ?: Sport.RUN
    val family: Sport.Family get() = sportKind.family
    /** Gli esercizi di una seduta di forza (vuoto per la corsa o se il testo non si riconosce). */
    val exercises: List<Exercise> get() = if (sportKind.isStrength) StrengthStructure.parse(structure) else emptyList()
    val isClosed: Boolean get() = status == "Completata" || status == "Saltata"
}

/**
 * Una riga del database Notion "Registro corse": quello che conta per l'app è tipo, RPE e nota (i numeri
 * della corsa arrivano da Health Connect). [km] serve solo a scegliere la corsa giusta se nello stesso
 * giorno ce n'è più di una; [plannedId] = seduta del programma collegata.
 */
@Serializable
data class LoggedRun(
    val id: String,
    val name: String,
    val date: String?,
    val kind: String? = null,
    val rpe: Int? = null,
    val note: String? = null,
    val km: Double? = null,
    val plannedId: String? = null,
    val url: String? = null,
    /** Testo della proprietà facoltativa "Esercizi" (sedute di forza: come le hai fatte davvero). */
    val exercisesText: String? = null
) {
    /** Gli esercizi scritti nel Registro, letti come la Struttura del programma (vuoto = non scritti). */
    val exercises: List<Exercise> get() = exercisesText?.let { StrengthStructure.parse(it) }.orEmpty()
}

/** Un database Notion da cui legge l'app: id, nome come è scritto in Notion e link per aprirlo (null = non ancora letti). */
@Serializable
data class NotionSource(val id: String, val title: String? = null, val url: String? = null)

/** Un'opzione della proprietà "Tipo" di un database Notion: nome e colore scelto in Notion (green, red, …). */
@Serializable
data class NotionKind(val name: String, val color: String? = null)

/**
 * Lettura del programma delle corse da Notion (API ufficiale, sola lettura). Serve un'integrazione
 * interna creata dall'utente su notion.so/profile/integrations, con accesso alla pagina dei database:
 * il token resta nelle preferenze dell'app. L'ultimo programma letto resta salvato, così la sezione
 * si vede anche senza rete. Il database del programma lo sceglie l'utente; il registro è facoltativo
 * (link a mano, oppure cercato tra i database condivisi con l'integrazione, vedi [pickLogDatabase]).
 */
object NotionPlan {
    private const val API = "https://api.notion.com/v1"
    private const val VERSION = "2022-06-28"
    private const val PREFS = "notion"
    private const val MIN_INTERVAL_MS = 10 * 60_000L

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    var runs by mutableStateOf<List<PlannedRun>>(emptyList())
        private set
    /** Righe del "Registro corse" (vuoto se non collegato o non leggibile). */
    var log by mutableStateOf<List<LoggedRun>>(emptyList())
        private set
    /**
     * Tipi di seduta come sono ora in Notion: le opzioni di "Tipo" del Programma, poi quelle in più del Registro.
     * Vuoto = mai letti (si usano quelli predefiniti, vedi [kindNames]).
     */
    var kinds by mutableStateOf<List<NotionKind>>(emptyList())
        private set
    /** Database del programma e del registro con il nome letto da Notion (vedi [databaseSource]). */
    var sources by mutableStateOf<List<NotionSource>>(emptyList())
        private set
    var lastSyncMillis by mutableStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set
    var connected by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        connected = !prefs.getString("token", null).isNullOrBlank()
        lastSyncMillis = prefs.getLong("synced", 0L)
        runs = runCatching { json.decodeFromString<List<PlannedRun>>(prefs.getString("plan", null) ?: "[]") }.getOrDefault(emptyList())
        log = runCatching { json.decodeFromString<List<LoggedRun>>(prefs.getString("log", null) ?: "[]") }.getOrDefault(emptyList())
        kinds = runCatching { json.decodeFromString<List<NotionKind>>(prefs.getString("kinds", null) ?: "[]") }.getOrDefault(emptyList())
        sources = runCatching { json.decodeFromString<List<NotionSource>>(prefs.getString("sources", null) ?: "[]") }.getOrDefault(emptyList())
    }

    /** Programma e registro: con nome e link se già letti, altrimenti solo l'id (null = database non scelto). */
    val planSource: NotionSource? get() = databaseId?.let { id -> sources.firstOrNull { it.id == id } ?: NotionSource(id) }
    val logSource: NotionSource? get() = logDatabaseId?.let { id -> sources.firstOrNull { it.id == id } ?: NotionSource(id) }

    /** I tipi di seduta da proporre nell'app: quelli di Notion se letti, altrimenti quelli predefiniti. */
    val kindNames: List<String> get() = kinds.map { it.name }.ifEmpty { WorkoutFeedback.RUN_KINDS }

    /** Colore Notion di un tipo (null se il tipo non è in Notion). */
    fun kindColor(name: String): String? = kinds.firstOrNull { it.name == name }?.color

    /** Database "Programma corse" scelto dall'utente (null = non ancora scelto). */
    val databaseId: String? get() = if (::prefs.isInitialized) prefs.getString("database", null) else null
    /** Database "Registro corse": scelto a mano o trovato alla prima lettura (null = nessun registro). */
    val logDatabaseId: String? get() = if (::prefs.isInitialized) prefs.getString("log_database", null) else null

    /**
     * Salva token, programma e registro (link o id). Registro vuoto = lo cerca l'app tra i database condivisi.
     * Token vuoto = scollega e dimentica il programma.
     */
    fun connect(token: String, database: String, logDatabase: String = ""): String? {
        val id = parseDatabaseId(database) ?: return tr("Link del database del programma non valido.")
        val logId = if (logDatabase.isBlank()) null else parseDatabaseId(logDatabase) ?: return tr("Link del registro non valido.")
        val t = token.trim()
        prefs.edit().putString("token", t.ifEmpty { null }).putString("database", id).putString("log_database", logId).apply()
        connected = t.isNotEmpty()
        if (!connected) disconnect()
        return null
    }

    /** Il collegamento da mettere nel backup (null se non collegato). */
    fun exportConnection(): BackupNotion? {
        if (!::prefs.isInitialized) return null
        val token = prefs.getString("token", null)?.takeIf { it.isNotBlank() } ?: return null
        val database = databaseId ?: return null
        return BackupNotion(token, database, logDatabaseId)
    }

    /** Ricollega Notion da un backup. Restituisce un errore se i database del file non sono validi. */
    fun importConnection(b: BackupNotion): String? = connect(b.token, b.database, b.logDatabase.orEmpty())

    fun disconnect() {
        prefs.edit().remove("token").remove("log_database").remove("plan").remove("log").remove("kinds").remove("sources").remove("synced").apply()
        connected = false
        runs = emptyList()
        log = emptyList()
        kinds = emptyList()
        sources = emptyList()
        lastSyncMillis = 0L
        error = null
    }

    /** Rilegge il programma se collegato; con [force] = false solo se l'ultima lettura è vecchia. */
    suspend fun sync(context: Context, force: Boolean = true) {
        init(context)
        val token = prefs.getString("token", null)?.takeIf { it.isNotBlank() } ?: return
        val planId = databaseId ?: run { withContext(Dispatchers.Main) { error = tr("Manca il link del database del programma.") }; return }
        if (syncing) return
        if (!force && System.currentTimeMillis() - lastSyncMillis < MIN_INTERVAL_MS) return
        withContext(Dispatchers.Main) { syncing = true }
        val result = withContext(Dispatchers.IO) {
            runCatchingIo {
                val plan = queryAll(token, planId, ::parsePage).sortedBy { it.date ?: "9999" }
                // Registro: quello scelto, altrimenti si cerca una volta tra i database condivisi con l'integrazione
                val logId = logDatabaseId ?: runCatching { findLogDatabase(token, planId) }.getOrNull()
                    ?.also { prefs.edit().putString("log_database", it).apply() }
                val logged = logId?.let { queryAll(token, it, ::parseLogPage).sortedBy { r -> r.date ?: "" } }.orEmpty()
                // Schema dei due database: tag di "Tipo", nome e link (se la lettura non riesce restano i precedenti)
                val planDb = runCatching { get("$API/databases/$planId", token) }.getOrNull()
                val logDb = logId?.let { runCatching { get("$API/databases/$it", token) }.getOrNull() }
                val tags = if (planDb == null && logDb == null) null
                else mergeKinds(planDb?.let(::kindOptions).orEmpty(), logDb?.let(::kindOptions).orEmpty())
                val read = listOfNotNull(planDb?.let { databaseSource(planId, it) }, logDb?.let { databaseSource(logId, it) })
                Synced(plan, logged, tags, read)
            }
        }
        withContext(Dispatchers.Main) {
            syncing = false
            result.onSuccess { (plan, logged, tags, read) ->
                runs = plan
                log = logged
                if (tags != null) kinds = tags
                if (read.isNotEmpty()) sources = (read + sources).distinctBy { it.id }
                lastSyncMillis = System.currentTimeMillis()
                error = null
                prefs.edit()
                    .putString("plan", json.encodeToString(plan))
                    .putString("log", json.encodeToString(logged))
                    .putString("kinds", json.encodeToString(kinds))
                    .putString("sources", json.encodeToString(sources))
                    .putLong("synced", lastSyncMillis).apply()
            }.onFailure { error = friendlyError(it) }
        }
    }

    private data class Synced(
        val plan: List<PlannedRun>,
        val logged: List<LoggedRun>,
        val tags: List<NotionKind>?,
        val sources: List<NotionSource>
    )

    private inline fun <T> runCatchingIo(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun <T> queryAll(token: String, database: String, parse: (JSONObject) -> T?): List<T> {
        val all = mutableListOf<T>()
        var cursor: String? = null
        do {
            val body = JSONObject().put("page_size", 100)
            if (cursor != null) body.put("start_cursor", cursor)
            val page = JSONObject(post("$API/databases/$database/query", token, body.toString()))
            all += pages(page).mapNotNull(parse)
            cursor = if (page.optBoolean("has_more")) page.optString("next_cursor").takeIf { it.isNotEmpty() } else null
        } while (cursor != null && all.size < 2000)
        return all
    }

    /** Il database del registro tra quelli che l'integrazione vede (POST /search), escluso il programma. */
    private fun findLogDatabase(token: String, planId: String): String? {
        val body = JSONObject().put("page_size", 100)
            .put("filter", JSONObject().put("property", "object").put("value", "database"))
        return pickLogDatabase(JSONObject(post("$API/search", token, body.toString())), planId)
    }

    /**
     * Sceglie il registro tra i risultati di una ricerca di database: nome con "registro" o "log", campo "RPE",
     * relazione "Sessione pianificata". Serve almeno un indizio; con più candidati vince quello con più indizi.
     */
    fun pickLogDatabase(search: JSONObject, planId: String): String? {
        val results = search.optJSONArray("results") ?: return null
        val plan = planId.replace("-", "").lowercase()
        return (0 until results.length()).map { results.getJSONObject(it) }
            .filter { it.optString("object") == "database" && !it.optBoolean("archived") && !it.optBoolean("in_trash") }
            .mapNotNull { db ->
                val id = db.optString("id").replace("-", "").lowercase().takeIf { it.length == 32 && it != plan }
                    ?: return@mapNotNull null
                val title = text(db, "title").orEmpty().lowercase()
                val props = db.optJSONObject("properties")
                var score = 0
                if ("registro" in title || Regex("""\blog\b""").containsMatchIn(title)) score += 2
                if (props?.has("RPE") == true) score++
                if (props?.has("Sessione pianificata") == true) score++
                if (score == 0) null else id to score
            }
            .maxByOrNull { it.second }?.first
    }

    private fun post(url: String, token: String, body: String): String = request("POST", url, token, body)

    private fun get(url: String, token: String): JSONObject = JSONObject(request("GET", url, token, null))

    private fun request(method: String, url: String, token: String, body: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 15_000
            doOutput = body != null
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Notion-Version", VERSION)
            if (body != null) setRequestProperty("Content-Type", "application/json")
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            if (status != 200) throw NotionError(status)
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private class NotionError(val status: Int) : IOException("HTTP $status")

    fun friendlyError(e: Throwable): String = when {
        e is UnknownHostException -> tr("Nessuna connessione a internet.")
        e is NotionError && e.status == 401 -> tr("Token di Notion non valido.")
        e is NotionError && e.status == 404 ->
            tr("Database non trovato: in Notion apri la pagina dei database → ••• → Connessioni e aggiungi l'integrazione.")
        e is NotionError && e.status == 429 -> tr("Notion chiede di aspettare un momento: riprova tra poco.")
        else -> tr("Lettura da Notion non riuscita (%s).", e.message)
    }

    /** Id del database da un link di Notion (…/0123abcd…?v=…) o dall'id con o senza trattini; null se non c'è. */
    fun parseDatabaseId(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val path = t.substringBefore('?').substringBefore('#')
        val hex = Regex("[0-9a-fA-F]{32}").findAll(path.replace("-", "")).lastOrNull()?.value
        return hex?.lowercase()
    }

    // ------------------------------------------------------------------
    // Lettura della risposta di /databases/<id>/query
    // ------------------------------------------------------------------

    private fun pages(root: JSONObject): List<JSONObject> {
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { results.getJSONObject(it) }
            .filter { !it.optBoolean("archived") && !it.optBoolean("in_trash") }
    }

    /** Le opzioni della proprietà "Tipo" (select) dalla risposta di GET /databases/<id>. */
    fun kindOptions(database: JSONObject): List<NotionKind> {
        val options = database.optJSONObject("properties")?.optJSONObject("Tipo")?.optJSONObject("select")
            ?.optJSONArray("options") ?: return emptyList()
        return (0 until options.length()).map { options.getJSONObject(it) }
            .mapNotNull { o -> o.optString("name").trim().takeIf { it.isNotEmpty() }?.let { NotionKind(it, o.optString("color").takeIf { c -> c.isNotEmpty() }) } }
    }

    /** Nome (titolo del database in Notion) e link di un database, dalla risposta di GET /databases/{id}. */
    fun databaseSource(id: String, database: JSONObject): NotionSource = NotionSource(
        id = id,
        title = text(database, "title"),
        url = database.optString("url").takeIf { it.startsWith("http") }
    )

    /** Prima i tipi del Programma, poi quelli che ci sono solo nel Registro (stesso nome = uno solo). */
    fun mergeKinds(plan: List<NotionKind>, log: List<NotionKind>): List<NotionKind> =
        (plan + log).distinctBy { it.name.lowercase() }

    fun parseResults(root: JSONObject): List<PlannedRun> = pages(root).mapNotNull(::parsePage)

    fun parseLogResults(root: JSONObject): List<LoggedRun> = pages(root).mapNotNull(::parseLogPage)

    private fun parseLogPage(page: JSONObject): LoggedRun? {
        val p = page.optJSONObject("properties") ?: return null
        return LoggedRun(
            id = page.optString("id"),
            name = text(p.optJSONObject("Corsa"), "title") ?: "Corsa",
            date = date(p.optJSONObject("Data")),
            kind = p.optJSONObject("Tipo")?.optJSONObject("select")?.optString("name")?.takeIf { it.isNotEmpty() },
            rpe = number(p.optJSONObject("RPE"))?.let { Math.round(it).toInt() }?.takeIf { it in 1..10 },
            note = text(p.optJSONObject("Note"), "rich_text"),
            km = number(p.optJSONObject("Distanza km")),
            plannedId = p.optJSONObject("Sessione pianificata")?.optJSONArray("relation")
                ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() },
            url = page.optString("url").takeIf { it.isNotEmpty() },
            exercisesText = text(p.optJSONObject("Esercizi"), "rich_text")
        )
    }

    private fun date(prop: JSONObject?): String? =
        prop?.optJSONObject("date")?.optString("start")?.takeIf { it.length >= 10 }?.take(10)

    private fun parsePage(page: JSONObject): PlannedRun? {
        val p = page.optJSONObject("properties") ?: return null
        val title = text(p.optJSONObject("Allenamento"), "title")
        val kind = p.optJSONObject("Tipo")?.optJSONObject("select")?.optString("name")?.takeIf { it.isNotEmpty() }
        // "Sport" (facoltativo) se c'è e si riconosce, altrimenti dal tipo o dal nome ("Forza", "Corpo libero")
        val sport = Sport.of(p.optJSONObject("Sport")?.optJSONObject("select")?.optString("name")) ?: Sport.infer(kind, title)
        return PlannedRun(
            id = page.optString("id"),
            name = title ?: sport.key,
            date = date(p.optJSONObject("Data")),
            kind = kind,
            sport = sport.key,
            status = p.optJSONObject("Stato")?.optJSONObject("status")?.optString("name")?.takeIf { it.isNotEmpty() },
            km = number(p.optJSONObject("Obiettivo km")),
            minutes = number(p.optJSONObject("Durata min"))?.let { Math.round(it).toInt() },
            pace = text(p.optJSONObject("Ritmo target"), "rich_text"),
            rpe = number(p.optJSONObject("RPE target"))?.let { Math.round(it).toInt() },
            structure = text(p.optJSONObject("Struttura"), "rich_text"),
            focus = text(p.optJSONObject("Focus"), "rich_text"),
            week = number(p.optJSONObject("Settimana"))?.let { Math.round(it).toInt() },
            url = page.optString("url").takeIf { it.isNotEmpty() }
        )
    }

    private fun text(prop: JSONObject?, key: String): String? {
        val parts: JSONArray = prop?.optJSONArray(key) ?: return null
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("plain_text") }
            .trim().takeIf { it.isNotEmpty() }
    }

    private fun number(prop: JSONObject?): Double? =
        prop?.takeIf { it.has("number") && !it.isNull("number") }?.optDouble("number")?.takeIf { !it.isNaN() }

    // ------------------------------------------------------------------
    // Programma e corse fatte
    // ------------------------------------------------------------------

    /**
     * Le sedute ancora da fare (da oggi in poi, non chiuse in Notion), con l'eventuale corsa già registrata
     * nello stesso giorno: una seduta di oggi già corsa conta come fatta e resta visibile fino a domani.
     */
    fun upcoming(plan: List<PlannedRun>, today: LocalDate): List<PlannedRun> =
        plan.filter { r ->
            val d = r.localDate ?: return@filter false
            !d.isBefore(today) && (!r.isClosed || d == today)
        }.sortedBy { it.date }

    /** Le sedute degli ultimi [days] giorni (escluso oggi), dalla più recente: per il confronto piano/fatto. */
    fun recent(plan: List<PlannedRun>, today: LocalDate, days: Long = 14): List<PlannedRun> =
        plan.filter { r ->
            val d = r.localDate ?: return@filter false
            d.isBefore(today) && !d.isBefore(today.minusDays(days))
        }.sortedByDescending { it.date }

    /**
     * true se nel giorno della seduta c'è un allenamento registrato dello stesso tipo (corsa o forza) o se è
     * segnata completata. [doneKeys] = chiavi [doneKey] degli allenamenti registrati.
     */
    fun isDone(r: PlannedRun, doneKeys: Set<String>): Boolean = r.status == "Completata" || doneKey(r.date, r.family) in doneKeys

    /** Giorno e famiglia di sport: una seduta di pesi non diventa "fatta" per una corsa dello stesso giorno. */
    fun doneKey(date: String?, family: Sport.Family): String = "$date|$family"
}
