package io.github.peol13.unicum.data

import java.time.LocalDate

/** Tipi di metrica giornaliera salvati in `daily_metrics`. */
object MetricType {
    const val STEPS = "steps"
    const val ACTIVE_KCAL = "active_kcal"      // calorie attive (movimento + allenamenti)
    const val DISTANCE_M = "distance_m"
    const val WEIGHT_KG = "weight_kg"          // kg
    const val BODY_FAT_PCT = "body_fat_pct"    // % di grasso corporeo
    const val HRV = "hrv_rmssd"                // ms, media di tutti i campioni del giorno
    const val HRV_NIGHT = "hrv_night"          // ms, media dei soli campioni dentro la notte di sonno (data = risveglio)
    const val NIGHT_TEMP = "night_temp"        // °C, media notturna della pelle/corpo
    const val NIGHT_TEMP_DELTA = "night_temp_delta" // °C rispetto alla base personale (se la fonte non dà il valore assoluto)
}

/**
 * Da quando l'HRV è confrontabile. Se la fonte cambia algoritmo (o si cambia anello/orologio) i valori fanno un
 * salto (per esempio ~85-95 ms, poi ~45-55 ms) e le medie mescolerebbero due scale diverse: le letture del
 * repository scartano l'HRV precedente, così ogni media, confronto e statistica parte da [reliableFrom].
 * [reliableFrom] = data scelta a mano nel Profilo oppure il salto trovato da [detectShift] (vedi
 * `UserSettings.hrvReliableFrom`); null = tutta la serie è buona.
 */
object HrvData {
    @Volatile
    var reliableFrom: LocalDate? = null

    /** Punti minimi prima e dopo il salto, e quanti se ne guardano al massimo per lato. */
    private const val MIN_SIDE = 7
    private const val MAX_SIDE = 21
    /** Il salto deve cambiare la mediana di almeno il 30%… */
    private const val MIN_RATIO = 0.30
    /** …ed essere netto rispetto alla variabilità normale dei due tratti (distanza tra mediane / somma delle MAD). */
    private const val MIN_SEPARATION = 2.5

    fun isHrv(type: String) = type == MetricType.HRV || type == MetricType.HRV_NIGHT

    fun reliable(date: LocalDate) = reliableFrom?.let { !date.isBefore(it) } ?: true

    fun reliable(isoDate: String) = reliableFrom?.let { isoDate >= it.toString() } ?: true

    /**
     * Il salto di livello più netto nella serie giornaliera dell'HRV (data ISO → ms): il primo giorno dopo il salto,
     * oppure null se non ce n'è uno chiaro. Logica pura, con test. Per ogni giorno si confrontano le mediane dei
     * [MAX_SIDE] valori prima e dopo: conta solo un cambio di almeno il [MIN_RATIO] e ben separato dal rumore.
     */
    fun detectShift(series: Map<String, Double>): LocalDate? {
        val points = series.entries.filter { it.value > 0 }.sortedBy { it.key }
        if (points.size < 2 * MIN_SIDE) return null
        // Il nuovo livello deve durare fino a oggi: un calo di qualche giorno (malattia, settimana storta) che poi
        // rientra non è un cambio di scala e non deve far scartare lo storico
        val recent = median(points.takeLast(MIN_SIDE).map { it.value })
        var best: Pair<Int, Double>? = null
        for (i in MIN_SIDE..points.size - MIN_SIDE) {
            val before = points.subList(maxOf(0, i - MAX_SIDE), i).map { it.value }
            val after = points.subList(i, minOf(points.size, i + MAX_SIDE)).map { it.value }
            val mb = median(before)
            val ma = median(after)
            val ratio = kotlin.math.abs(ma - mb) / maxOf(ma, mb)
            if (ratio < MIN_RATIO) continue
            val separation = kotlin.math.abs(ma - mb) / (mad(before, mb) + mad(after, ma) + 1.0)
            if (separation < MIN_SEPARATION) continue
            if (kotlin.math.abs(recent - ma) >= kotlin.math.abs(recent - mb)) continue
            // …e diverso anche dal livello di lungo periodo: il rientro dopo un calo torna al livello di prima
            val longBefore = median(points.subList(maxOf(0, i - 3 * MAX_SIDE), i).map { it.value })
            if (kotlin.math.abs(ma - longBefore) / maxOf(ma, longBefore) < MIN_RATIO) continue
            if (best == null || separation > best.second) best = i to separation
        }
        val found = best?.first ?: return null
        // Le mediane trovano il salto ma non il giorno esatto (qualche giorno prima o dopo dà lo stesso valore):
        // attorno al salto si sceglie il taglio che lascia i due tratti più compatti (somma degli scarti al quadrato)
        val lo = maxOf(0, found - MAX_SIDE)
        val hi = minOf(points.size, found + MAX_SIDE)
        val window = points.subList(lo, hi).map { it.value }
        val cut = (2 until window.size - 1).minByOrNull { j -> sse(window.subList(0, j)) + sse(window.subList(j, window.size)) }
            ?: (found - lo)
        return runCatching { LocalDate.parse(points[lo + cut].key) }.getOrNull()
    }

    private fun sse(v: List<Double>): Double {
        val m = v.average()
        return v.sumOf { (it - m) * (it - m) }
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun mad(v: List<Double>, m: Double) = median(v.map { kotlin.math.abs(it - m) })
}

/** Notte di sonno pronta per la UI. */
data class SleepNight(
    val date: String,
    val startMillis: Long,
    val endMillis: Long,
    val durationMinutes: Int,
    val deepMinutes: Int,
    val remMinutes: Int,
    val lightMinutes: Int,
    val awakeMinutes: Int,
    val source: String,
    /** Fasi con orari, per il grafico a gradini; vuoto se la fonte non le fornisce. */
    val stages: List<SleepStage> = emptyList()
) {
    val hasTimeline: Boolean get() = stages.size >= 2

    val hasStages: Boolean get() = deepMinutes + remMinutes + lightMinutes > 0

    /** Minuti totali di ogni fase presente, dalla più profonda a "sveglio" (per barra e legenda). */
    val stageTotals: List<Pair<SleepStageType, Int>>
        get() = listOf(
            SleepStageType.PROFONDO to deepMinutes,
            SleepStageType.REM to remMinutes,
            SleepStageType.LEGGERO to lightMinutes,
            SleepStageType.SVEGLIO to awakeMinutes
        ).filter { it.second > 0 }

    /** Quota di tempo a letto passata davvero a dormire (0-100). */
    val efficiencyPercent: Int
        get() {
            val inBed = ((endMillis - startMillis) / 60_000L).toInt()
            return if (inBed > 0) (durationMinutes * 100 / inBed).coerceIn(0, 100) else 0
        }
}

/** Quanto [value] si scosta da [base], in percentuale intera (null se manca uno dei due). */
fun percentVs(value: Double?, base: Double?): Int? =
    if (value == null || base == null || base <= 0) null else ((value - base) / base * 100).toInt()

/** "+12%" oppure "-5%". */
fun signedPercent(pct: Int): String = "${if (pct >= 0) "+" else ""}$pct%"

/** Dati di salute degli ultimi giorni, già scelti per fonte preferita. */
data class HealthData(
    val sleep: List<SleepNight> = emptyList(),
    /** tipo -> (data -> valore) */
    val metrics: Map<String, Map<String, Double>> = emptyMap()
) {
    fun metric(type: String, date: String): Double? = metrics[type]?.get(date)
}

/** Nome leggibile e priorità delle app che scrivono su Health Connect. */
object SourceInfo {
    fun label(source: String): String = when {
        source == "manual" -> "Manuale"
        source == "health_connect" -> "Health Connect"
        source.contains("ultrahuman", true) -> "Ultrahuman"
        source.contains("shealth", true) || source.startsWith("com.sec.android") -> "Samsung Health"
        source.contains("fitbit", true) -> "Fitbit"
        source.contains("garmin", true) -> "Garmin"
        source.contains("healthsync", true) -> "Health Sync"
        else -> source
    }

    /** Più basso = preferito quando due fonti riportano lo stesso dato. */
    fun rank(source: String): Int = when {
        source.contains("ultrahuman", true) -> 0
        source.contains("shealth", true) || source.startsWith("com.sec.android") -> 1
        else -> 2
    }

    /** Il valore della fonte preferita tra quelli dello stesso giorno e tipo. */
    fun <T> preferred(rows: List<T>, source: (T) -> String): T? = rows.minByOrNull { rank(source(it)) }
}
