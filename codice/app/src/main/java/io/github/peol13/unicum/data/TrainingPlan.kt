package io.github.peol13.unicum.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** Il programma tenuto nell'app (importato da PDF o scritto a mano): sedute, da dove viene e quando. */
@Serializable
data class LocalPlanData(
    val runs: List<PlannedRun> = emptyList(),
    /** Nome del file PDF da cui è stato importato (null = scritto a mano). */
    val sourceName: String? = null,
    val importedAt: Long = 0L
)

/**
 * Il programma di allenamento tenuto nell'app, per chi non usa Notion: importato da un PDF ([PlanPdfParser]) e/o
 * scritto e corretto a mano. Salvato nelle preferenze `local_plan` e incluso nel backup. Le corse fatte si
 * riconoscono dal giorno (come per Notion); "Saltata" o "Fatta" (corsa non registrata) si segnano a mano.
 */
object LocalPlan {
    private const val PREFS = "local_plan"
    const val ID_PREFIX = "local-"
    const val STATUS_TODO = "Da fare"
    const val STATUS_SKIPPED = "Saltata"
    /** Fatta senza una corsa registrata (es. senza orologio): stesso valore dello "Stato" di Notion. */
    const val STATUS_DONE = "Completata"

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var data by mutableStateOf(LocalPlanData())
        private set

    val runs: List<PlannedRun> get() = data.runs
    val isEmpty: Boolean get() = data.runs.isEmpty()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        data = load(context)
    }

    /** Il programma salvato (anche ad app chiusa, per il backup automatico). */
    fun load(context: Context): LocalPlanData =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", null)
            ?.let { runCatching { json.decodeFromString(LocalPlanData.serializer(), it) }.getOrNull() }
            ?: LocalPlanData()

    private fun save(new: LocalPlanData) {
        data = new.copy(runs = new.runs.sortedBy { it.date ?: "9999" })
        if (::prefs.isInitialized) prefs.edit().putString("data", json.encodeToString(LocalPlanData.serializer(), data)).apply()
    }

    fun newId(): String = ID_PREFIX + UUID.randomUUID().toString().take(8)

    /** Un programma nuovo (da PDF): con [replace] sostituisce quello di prima, altrimenti si aggiunge. */
    fun import(runs: List<PlannedRun>, sourceName: String?, replace: Boolean) {
        val fresh = runs.map { if (it.id.startsWith(ID_PREFIX)) it else it.copy(id = newId()) }
        save(LocalPlanData(if (replace) fresh else data.runs + fresh, sourceName ?: data.sourceName, System.currentTimeMillis()))
    }

    /** Aggiunge o sostituisce (stesso id) una seduta. */
    fun upsert(run: PlannedRun) {
        val r = if (run.id.startsWith(ID_PREFIX)) run else run.copy(id = newId())
        save(data.copy(runs = data.runs.filter { it.id != r.id } + r))
    }

    fun delete(id: String) = save(data.copy(runs = data.runs.filter { it.id != id }))

    fun clear() = save(LocalPlanData())

    /** Dal backup: il programma del file sostituisce quello attuale. */
    fun importBackup(incoming: LocalPlanData) = save(incoming)
}

/**
 * Da dove arriva il programma che si vede nell'app: Notion se collegato, altrimenti quello tenuto nell'app.
 * Tutto ciò che legge il programma (sezione Allenamenti, pagina della corsa, sensazioni, tipi di seduta) passa
 * da qui, non da [NotionPlan] o [LocalPlan] direttamente.
 */
object TrainingPlan {
    enum class Source { NOTION, LOCAL, NONE }

    val source: Source
        get() = when {
            NotionPlan.connected -> Source.NOTION
            !LocalPlan.isEmpty -> Source.LOCAL
            else -> Source.NONE
        }

    val runs: List<PlannedRun> get() = if (NotionPlan.connected) NotionPlan.runs else LocalPlan.runs

    /** Righe del Registro corse di Notion; senza Notion le sensazioni si scrivono nell'app. */
    val log: List<LoggedRun> get() = if (NotionPlan.connected) NotionPlan.log else emptyList()

    /**
     * I tipi di seduta da proporre: quelli di Notion se collegato; altrimenti quelli predefiniti più quelli usati
     * nel programma dell'app.
     */
    val kindNames: List<String>
        get() = if (NotionPlan.connected) NotionPlan.kindNames
        else (WorkoutFeedback.RUN_KINDS + LocalPlan.runs.mapNotNull { it.kind }).distinct()

    fun kindColor(name: String): String? = if (NotionPlan.connected) NotionPlan.kindColor(name) else null

    /** Il programma è nell'app (si può modificare qui). */
    val isLocal: Boolean get() = !NotionPlan.connected
}
