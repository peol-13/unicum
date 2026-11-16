package io.github.peol13.unicum.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * I conti della dashboard "Progressi corsa" (logica pura, con test): battiti per km, stima dei 5 km (e 7/10 km con Riegel),
 * scenari con FC diversa, confronto tra corse dello stesso tipo, km per settimana divisi per tipo.
 * Tipo di seduta e RPE vengono dalle Sensazioni dell'utente ([WorkoutFeedback]).
 */
object RunProgress {
    /** Distanze delle stime (selettore della card): i 5 km dalla formula, gli altri con Riegel. */
    val DISTANCES = listOf(5.0, 7.0, 10.0)

    /** Esponente di Riegel per passare dai 5 ai 7 km (con la forchetta 1,06–1,10). */
    private const val RIEGEL = 1.08
    private const val RIEGEL_LO = 1.06
    private const val RIEGEL_HI = 1.10

    /** Tipo delle ripetute (escluse dalla distanza continua); le uscite facili: [isEasyKind]. */
    private const val INTERVALS = "Intervalli"
    const val NO_KIND = "Senza tipo"

    /** Una corsa con le misure della dashboard. */
    data class Run(
        val id: String,
        val date: LocalDate,
        val kind: String,
        val km: Double?,
        val seconds: Int,
        val paceSec: Int?,
        val hr: Int?,
        val hrMax: Int?,
        val cadence: Int?,
        val rpe: Int?,
        val vo2: Double?,
        /** FC dello sforzo: nelle ripetute quella dei soli tratti veloci, altrimenti la FC media. */
        val effortHr: Int? = hr
    ) {
        /** Battiti per km: FC media × minuti al km. Più basso = corri spendendo meno. */
        val beatsPerKm: Int? get() = if (hr != null && paceSec != null) (hr * paceSec / 60.0).roundToInt() else null
    }

    /** Le corse in ordine di data (dalla più vecchia), con tipo e RPE dalle Sensazioni. */
    fun runs(workouts: List<WorkoutDetail>, feedback: Map<String, WorkoutFeedback>): List<Run> =
        workouts.filter { it.type == "Corsa" }.sortedBy { it.startMillis }.map { w ->
            val f = feedback[w.externalId]
            Run(
                id = w.externalId,
                date = LocalDate.parse(w.date),
                kind = f?.kind ?: NO_KIND,
                km = w.distanceM?.div(1000),
                seconds = w.movingSeconds,
                paceSec = WorkoutAnalysis.averagePace(w),
                hr = w.avgHr,
                hrMax = w.maxHr,
                cadence = WorkoutAnalysis.averageCadence(w),
                rpe = f?.rpe,
                vo2 = w.vo2max,
                effortHr = f?.intervals?.workHr ?: w.avgHr
            )
        }

    // ---------- stima dei 5 km

    /** Regole della stima (5/11/2026, al posto della formula della dashboard che con pochi dati dava tempi impossibili). */
    private const val MIN_EASY = 3
    private const val EASY_WINDOW = 4
    private const val EASY_MAX_RPE = 6
    private const val MIN_KM = 2.0
    /** Un'uscita facile con battiti per km lontani più di così dalla mediana è un dato sbagliato (es. FC persa). */
    private const val OUTLIER = 0.15
    private const val RECENT_DAYS = 90L

    /**
     * Stima dei 5 km dopo una corsa: media di due stime.
     * - **Dal battito**: battiti per km delle uscite facili (mediana delle ultime 4, scartate le anomale)
     *   ÷ FC media degli sforzi duri degli ultimi 90 giorni = ritmo che terresti in gara.
     * - **Dalla prova reale**: il miglior sforzo continuo (RPE ≥ 7, almeno 2 km, non ripetute) degli ultimi
     *   90 giorni, portato a 5 km con Riegel. Se non c'è, resta solo la prima.
     */
    data class EstimatePoint(
        val date: LocalDate,
        val runId: String,
        val beatsPerKm: Int,
        val hardHr: Int,
        val hardCount: Int,
        val easyCount: Int,
        /** Stima dal battito. */
        val hrSeconds5k: Int,
        /** Stima dalla prova reale e la corsa da cui viene. */
        val perfSeconds5k: Int?,
        val perfRun: Run?,
        val seconds5k: Int
    ) {
        val pace5k: Int get() = (seconds5k / 5.0).roundToInt()
    }

    /** Uscita facile valida per la stima: tipo facile, RPE fino a 6 (o non segnato), almeno 2 km, con FC. */
    private fun isEasy(r: Run) = isEasyKind(r.kind) && (r.rpe ?: 0) <= EASY_MAX_RPE && (r.km ?: 0.0) >= MIN_KM && r.beatsPerKm != null

    private fun isHard(r: Run) = (r.rpe ?: 0) >= 7

    private fun median(values: List<Int>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    /** Tempo su [toKm] da una prova su [km] (formula di Riegel). */
    fun riegel(seconds: Int, km: Double, toKm: Double = 5.0, k: Double = RIEGEL): Int = (seconds * (toKm / km).pow(k)).roundToInt()

    /** La stima con le corse fino a [r] compresa (in ordine di data); null se i dati non bastano. */
    private fun estimateAfter(upTo: List<Run>, r: Run): EstimatePoint? {
        val allEasy = upTo.filter(::isEasy)
        val easy = if (allEasy.size >= MIN_EASY) {
            val m = median(allEasy.map { it.beatsPerKm!! })
            allEasy.filter { kotlin.math.abs(it.beatsPerKm!! - m) / m <= OUTLIER }
        } else allEasy
        val window = easy.takeLast(EASY_WINDOW)
        if (window.size < MIN_EASY) return null
        val recent = upTo.filter { ChronoUnit.DAYS.between(it.date, r.date) <= RECENT_DAYS }
        val hard = recent.filter { isHard(it) && it.effortHr != null }
        if (hard.isEmpty()) return null
        val bpk = median(window.map { it.beatsPerKm!! }).roundToInt()
        val hardHr = hard.map { it.effortHr!! }.average().roundToInt()
        val fromHr = (bpk.toDouble() / hardHr * 60 * 5).roundToInt()
        val perf = recent.filter { isHard(it) && (it.km ?: 0.0) >= MIN_KM && it.kind != INTERVALS }
            .map { it to riegel(it.seconds, it.km!!) }
            .minByOrNull { it.second }
        val total = perf?.let { ((fromHr + it.second) / 2.0).roundToInt() } ?: fromHr
        return EstimatePoint(r.date, r.id, bpk, hardHr, hard.size, window.size, fromHr, perf?.second, perf?.first, total)
    }

    /** La stima dopo ogni corsa, da quando i dati bastano (almeno 3 uscite facili e uno sforzo duro). */
    fun estimateHistory(runs: List<Run>): List<EstimatePoint> =
        runs.indices.mapNotNull { i -> estimateAfter(runs.subList(0, i + 1), runs[i]) }

    /** Stima dei 5 km portata a [km] con Riegel (i 5 km restano quelli). */
    fun atDistance(seconds5k: Int, km: Double, k: Double = RIEGEL): Int =
        if (abs(km - 5.0) < 1e-6) seconds5k else riegel(seconds5k, 5.0, km, k)

    /** Distanza continua più lunga (ripetute escluse). */
    fun longestKm(runs: List<Run>): Double = runs.filter { it.kind != INTERVALS }.mapNotNull { it.km }.maxOrNull() ?: 0.0

    /** FC media necessaria per correre a [paceSec] con questi battiti per km. */
    fun hrForPace(beatsPerKm: Int, paceSec: Int): Int = (beatsPerKm / (paceSec / 60.0)).roundToInt()

    data class Estimate(
        val km: Double,
        val seconds: Int,
        /** Forchetta di Riegel (esponente 1,06–1,10); sui 5 km uguale alla stima. */
        val low: Int,
        val high: Int,
        /** Differenza dalla stima precedente; negativo = migliorata. */
        val vsPrevious: Int?,
        val beatsPerKm: Int,
        val hardHr: Int,
        val hardCount: Int,
        val longestKm: Double
    ) {
        val pace: Int get() = (seconds / km).roundToInt()
    }

    /** Stima di oggi su [km] (dall'ultima stima dei 5 km); null se i dati non bastano. */
    fun estimate(runs: List<Run>, km: Double = 5.0): Estimate? {
        val history = estimateHistory(runs)
        val last = history.lastOrNull() ?: return null
        val prev = history.getOrNull(history.size - 2)
        val t = atDistance(last.seconds5k, km)
        return Estimate(
            km = km,
            seconds = t,
            low = atDistance(last.seconds5k, km, RIEGEL_LO),
            high = atDistance(last.seconds5k, km, RIEGEL_HI),
            vsPrevious = prev?.let { t - atDistance(it.seconds5k, km) },
            beatsPerKm = last.beatsPerKm,
            hardHr = last.hardHr,
            hardCount = last.hardCount,
            longestKm = longestKm(runs)
        )
    }

    data class Scenario(val name: String, val hr: Int, val pace: Int, val seconds: Int)

    /**
     * La stessa stima su [km] se in gara la FC media fosse 5 bpm più bassa o più alta di quella degli sforzi duri
     * (cambia la parte dal battito; quella dalla prova reale resta).
     */
    fun scenarios(runs: List<Run>, km: Double = 5.0): List<Scenario> {
        val last = estimateHistory(runs).lastOrNull() ?: return emptyList()
        return listOf("Prudente" to -5, "Centrale" to 0, "Ottimista" to 5).map { (name, d) ->
            val hr = last.hardHr + d
            val fromHr = (last.beatsPerKm.toDouble() / hr * 60 * 5).roundToInt()
            val t5 = last.perfSeconds5k?.let { ((fromHr + it) / 2.0).roundToInt() } ?: fromHr
            val t = atDistance(t5, km)
            Scenario(name, hr, (t / km).roundToInt(), t)
        }
    }

    /** Distanza corsa davvero: ogni uscita continua di almeno il 98% di [km], riportata a [km]. */
    fun realAt(runs: List<Run>, km: Double): List<Pair<Run, Int>> =
        runs.filter { (it.km ?: 0.0) >= km * 0.98 && it.kind != INTERVALS }.map { it to (it.seconds * km / it.km!!).roundToInt() }

    // ---------- confronto tra corse dello stesso tipo

    /** Le misure del confronto; [better]: 1 = più alto è meglio, -1 = più basso è meglio, 0 = neutra. */
    enum class Metric(val label: String, val better: Int, val get: (Run) -> Double?) {
        PACE("Ritmo medio", -1, { it.paceSec?.toDouble() }),
        KM("Distanza", 1, { it.km }),
        DURATION("Durata", 1, { it.seconds.toDouble() }),
        HR("FC media", -1, { it.hr?.toDouble() }),
        HR_MAX("FC max", 0, { it.hrMax?.toDouble() }),
        BPK("Battiti per km", -1, { it.beatsPerKm?.toDouble() }),
        CADENCE("Cadenza", 1, { it.cadence?.toDouble() }),
        RPE("Sforzo percepito", -1, { it.rpe?.toDouble() }),
        VO2("VO₂ max", 1, { it.vo2 })
    }

    /** Misure senza un "meglio" per quel tipo: in un facile il ritmo non deve scendere, nelle ripetute conta altro. */
    private fun neutral(kind: String, m: Metric): Boolean = when {
        isEasyKind(kind) -> m == Metric.PACE
        kind == INTERVALS -> m in setOf(Metric.PACE, Metric.KM, Metric.DURATION, Metric.BPK)
        else -> false
    }

    /** Tipi "di fondo": Facile, Lungo facile e qualunque tag di Notion che contiene "facile". */
    fun isEasyKind(kind: String) = kind.contains("facile", ignoreCase = true)

    /** Tipi su una distanza fissa (es. il tag "5km" di Notion, o "Test"): lì un tempo più basso è meglio. */
    fun isDistanceKind(kind: String) = kind == "Test" || Regex("""\d+([.,]\d+)?\s*km""", RegexOption.IGNORE_CASE).matches(kind.trim())

    /** Verso del "meglio" di una misura per quel tipo: 0 = neutra. */
    fun betterFor(kind: String, m: Metric): Int = when {
        neutral(kind, m) -> 0
        isDistanceKind(kind) && m == Metric.DURATION -> -1
        isDistanceKind(kind) && m == Metric.KM -> 0
        else -> m.better
    }

    data class Comparison(
        val metric: Metric,
        val a: Run?, val aValue: Double?,
        val b: Run?, val bValue: Double?,
        /** a − b */
        val delta: Double?,
        /** true = a è migliore di b; null = neutra o senza differenza. */
        val deltaGood: Boolean?,
        val mean: Double?,
        val best: Run?, val bestValue: Double?,
        val count: Int
    )

    /**
     * Confronto tra due corse dello stesso tipo, misura per misura: [a] (di solito l'ultima) contro [b]
     * (la precedente), più media e migliore del tipo. Se mancano, si prendono le ultime due con quel dato.
     */
    fun compare(sameKind: List<Run>, kind: String, a: Run? = null, b: Run? = null): List<Comparison> =
        Metric.values().map { m ->
            val withValue = sameKind.filter { m.get(it) != null }
            val ra = a?.takeIf { m.get(it) != null } ?: if (a == null) withValue.lastOrNull() else null
            val rb = b?.takeIf { m.get(it) != null } ?: if (b == null) withValue.filter { it != ra }.lastOrNull() else null
            val va = ra?.let(m.get)
            val vb = rb?.let(m.get)
            val better = betterFor(kind, m)
            val delta = if (va != null && vb != null) va - vb else null
            val best = if (better != 0) withValue.maxByOrNull { m.get(it)!! * better } else null
            Comparison(
                metric = m, a = ra, aValue = va, b = rb, bValue = vb, delta = delta,
                deltaGood = if (delta == null || better == 0 || kotlin.math.abs(delta) < 1e-9) null else delta * better > 0,
                mean = withValue.takeIf { it.isNotEmpty() }?.map { m.get(it)!! }?.average(),
                best = best, bestValue = best?.let(m.get), count = withValue.size
            )
        }

    // ---------- km per settimana divisi per tipo

    data class WeekByKind(val monday: LocalDate, val km: Map<String, Double>, val count: Int, val seconds: Int) {
        val total: Double get() = km.values.sum()
    }

    fun weeklyByKind(runs: List<Run>, weeks: Int, today: LocalDate): List<WeekByKind> {
        val current = DateUtils.weekStart(today)
        val byWeek = runs.groupBy { DateUtils.weekStart(it.date) }
        return ((weeks - 1) downTo 0).map { i ->
            val monday = current.minusWeeks(i.toLong())
            val list = byWeek[monday].orEmpty()
            WeekByKind(
                monday,
                list.groupBy { it.kind }.mapValues { (_, l) -> l.sumOf { it.km ?: 0.0 } },
                list.size, list.sumOf { it.seconds }
            )
        }
    }
}
