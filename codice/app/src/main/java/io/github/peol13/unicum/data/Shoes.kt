package io.github.peol13.unicum.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Un paio di scarpe da corsa. */
@Serializable
data class Shoe(
    val id: String,
    val name: String,
    /** In uso dal (yyyy-MM-dd): se è la predefinita, le corse da questo giorno le vengono assegnate da sole. */
    val since: String,
    /** Km fatti prima di registrarla nell'app (contano nel totale). */
    val startKm: Double = 0.0,
    /** Km dopo i quali di solito si cambia (600–800 per una scarpa da allenamento). */
    val limitKm: Int = Shoes.DEFAULT_LIMIT_KM,
    val retired: Boolean = false
)

/**
 * Scarpe e corse in cui sono state usate. [runs]: externalId della corsa → id della scarpa;
 * "" = nessuna scarpa (scelto a mano: la predefinita non viene più assegnata a quella corsa).
 */
@Serializable
data class ShoeData(
    val shoes: List<Shoe> = emptyList(),
    val defaultId: String? = null,
    val runs: Map<String, String> = emptyMap()
) {
    val default: Shoe? get() = shoes.firstOrNull { it.id == defaultId && !it.retired }

    fun shoeOf(externalId: String): Shoe? = runs[externalId]?.let { id -> shoes.firstOrNull { it.id == id } }

    /** Le corse ancora senza scelta, dal giorno in cui la predefinita è in uso, vanno alla predefinita. */
    fun autoAssign(all: List<WorkoutDetail>): ShoeData {
        val d = default ?: return this
        val add = all.filter { it.externalId !in runs && it.date >= d.since }.associate { it.externalId to d.id }
        return if (add.isEmpty()) this else copy(runs = runs + add)
    }

    /** Scarpe delle corse da un backup: le corse che hanno già una scelta la tengono. */
    fun withRuns(add: Map<String, String>): ShoeData = copy(runs = add + runs)

    /** Assegna la scarpa [shoeId] alla corsa; null = nessuna scarpa. */
    fun assign(externalId: String, shoeId: String?): ShoeData = copy(runs = runs + (externalId to (shoeId ?: "")))

    fun upsert(shoe: Shoe, makeDefault: Boolean): ShoeData {
        val list = if (shoes.any { it.id == shoe.id }) shoes.map { if (it.id == shoe.id) shoe else it } else shoes + shoe
        val def = when {
            makeDefault && !shoe.retired -> shoe.id
            defaultId == shoe.id && (shoe.retired || !makeDefault) -> null
            else -> defaultId
        }
        return copy(shoes = list, defaultId = def)
    }

    /** Toglie la scarpa e le sue assegnazioni (quelle corse tornano senza scarpa). */
    fun remove(id: String): ShoeData = copy(
        shoes = shoes.filterNot { it.id == id },
        defaultId = defaultId.takeIf { it != id },
        runs = runs.mapValues { (_, v) -> if (v == id) "" else v }
    )

    /** Backup: le scarpe con lo stesso id e le corse già assegnate restano quelle presenti. */
    fun merge(incoming: ShoeData): ShoeData = ShoeData(
        shoes = shoes + incoming.shoes.filter { s -> shoes.none { it.id == s.id } },
        defaultId = defaultId ?: incoming.defaultId,
        runs = incoming.runs + runs
    )
}

/** Statistiche di utilizzo di una scarpa (logica pura, con test). */
object ShoeStats {
    /** Finestra per il ritmo di usura (km a settimana). */
    private const val WEEKS_WINDOW = 8L

    data class Stats(
        val shoe: Shoe,
        /** Corse con questa scarpa, dalla più recente. */
        val runs: List<WorkoutDetail>,
        /** Km totali: quelli di partenza + quelli delle corse. */
        val km: Double,
        val runKm: Double,
        val seconds: Int,
        val paceSec: Int?,
        val avgHr: Int?,
        val longestKm: Double?,
        val first: LocalDate?,
        val last: LocalDate?,
        val last30Km: Double,
        /** Km a settimana nelle ultime 8 settimane (o da quando è in uso, se meno). */
        val weeklyKm: Double?,
        /** Km per tipo di seduta, dal tipo con più km. */
        val byKind: List<Pair<String, Double>>
    ) {
        val count: Int get() = runs.size
        val fraction: Float get() = if (shoe.limitKm > 0) (km / shoe.limitKm).toFloat() else 0f
        val remainingKm: Double get() = (shoe.limitKm - km).coerceAtLeast(0.0)
        val beatsPerKm: Int? get() = if (avgHr != null && paceSec != null) (avgHr * paceSec / 60.0).roundToInt() else null
        /** Settimane al limite dei km al ritmo attuale (null se ritirata, senza ritmo o già oltre). */
        val weeksLeft: Int?
            get() = weeklyKm?.takeIf { it > 0.1 && !shoe.retired && remainingKm > 0 }?.let { (remainingKm / it).roundToInt() }
    }

    /**
     * Le statistiche di [shoe] sulle corse [runs] (già corrette), con la scelta delle scarpe [data] e i tipi
     * di seduta [kinds] (externalId → tipo).
     */
    fun of(shoe: Shoe, runs: List<WorkoutDetail>, data: ShoeData, kinds: Map<String, String?>, today: LocalDate): Stats {
        val mine = runs.filter { data.runs[it.externalId] == shoe.id }.sortedByDescending { it.startMillis }
        val withKm = mine.filter { (it.distanceM ?: 0.0) > 0 }
        val runKm = withKm.sumOf { it.distanceM!! } / 1000
        val kmSeconds = withKm.sumOf { it.movingSeconds }
        val withHr = mine.filter { it.avgHr != null && it.movingSeconds > 0 }
        val hrSeconds = withHr.sumOf { it.movingSeconds }
        val first = mine.lastOrNull()?.let { LocalDate.parse(it.date) }
        val windowStart = listOfNotNull(today.minusWeeks(WEEKS_WINDOW), runCatching { LocalDate.parse(shoe.since) }.getOrNull(), first)
            .maxOrNull()!!
        val windowKm = withKm.filter { !LocalDate.parse(it.date).isBefore(windowStart) }.sumOf { it.distanceM!! } / 1000
        val windowWeeks = (ChronoUnit.DAYS.between(windowStart, today) + 1).coerceAtLeast(7) / 7.0
        return Stats(
            shoe = shoe,
            runs = mine,
            km = shoe.startKm + runKm,
            runKm = runKm,
            seconds = mine.sumOf { it.movingSeconds },
            paceSec = if (runKm > 0.05) (kmSeconds / runKm).roundToInt() else null,
            avgHr = if (hrSeconds > 0) (withHr.sumOf { it.avgHr!!.toLong() * it.movingSeconds } / hrSeconds.toDouble()).roundToInt() else null,
            longestKm = withKm.maxOfOrNull { it.distanceM!! / 1000 },
            first = first,
            last = mine.firstOrNull()?.let { LocalDate.parse(it.date) },
            last30Km = withKm.filter { !LocalDate.parse(it.date).isBefore(today.minusDays(29)) }.sumOf { it.distanceM!! } / 1000,
            weeklyKm = if (mine.isEmpty()) null else windowKm / windowWeeks,
            byKind = withKm.groupBy { kinds[it.externalId] ?: RunProgress.NO_KIND }
                .map { (k, l) -> k to l.sumOf { it.distanceM!! } / 1000 }.sortedByDescending { it.second }
        )
    }
}

/**
 * Le scarpe dell'utente, nelle preferenze "shoes" (nessuna tabella Room): l'elenco, la predefinita e la scarpa
 * di ogni corsa. Entrano nel backup.
 */
object Shoes {
    const val DEFAULT_LIMIT_KM = 700
    private const val PREFS = "shoes"
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var data by mutableStateOf(ShoeData())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        data = runCatching { json.decodeFromString(ShoeData.serializer(), prefs.getString("data", null) ?: "{}") }.getOrDefault(ShoeData())
    }

    private fun save(new: ShoeData) {
        if (new == data) return
        data = new
        if (::prefs.isInitialized) prefs.edit().putString("data", json.encodeToString(ShoeData.serializer(), new)).apply()
    }

    fun newId(): String = "s" + System.currentTimeMillis().toString(36)

    fun upsert(shoe: Shoe, makeDefault: Boolean) = save(data.upsert(shoe, makeDefault))
    fun remove(id: String) = save(data.remove(id))
    fun assign(externalId: String, shoeId: String?) = save(data.assign(externalId, shoeId))
    fun autoAssign(runs: List<WorkoutDetail>) = save(data.autoAssign(runs))
    fun importData(incoming: ShoeData) = save(data.merge(incoming))
    fun importRuns(runs: Map<String, String>) = save(data.withRuns(runs))
}
