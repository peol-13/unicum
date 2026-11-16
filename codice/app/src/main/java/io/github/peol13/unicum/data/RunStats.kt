package io.github.peol13.unicum.data

import io.github.peol13.unicum.data.RunProgress.Run
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Statistiche delle corse fatte per periodo (settimana, mese, da sempre): totali, medie, confronto
 * con il periodo precedente, colonne per il grafico e record. Logica pura (vedi `RunStatsTest`).
 */
object RunStats {

    enum class Period(val label: String) { WEEK("Settimana"), MONTH("Mese"), ALL("Sempre") }

    /** Le corse più corte di così non contano per il ritmo migliore (un allungo non è un record). */
    private const val MIN_KM_RECORD = 2.0

    data class Totals(
        val count: Int,
        val km: Double,
        val seconds: Int,
        /** Ritmo medio (s/km) sulle sole corse con distanza. */
        val paceSec: Int?,
        /** FC media pesata sulla durata. */
        val avgHr: Int?,
        val beatsPerKm: Int?,
        val avgCadence: Int?,
        val avgRpe: Double?,
        val longest: Run?,
        val fastest: Run?
    )

    fun totals(runs: List<Run>): Totals {
        val withKm = runs.filter { (it.km ?: 0.0) > 0 }
        val km = withKm.sumOf { it.km!! }
        val pace = if (km > 0) (withKm.sumOf { it.seconds } / km).roundToInt() else null
        val withHr = runs.filter { it.hr != null && it.seconds > 0 }
        val hr = if (withHr.isEmpty()) null
        else (withHr.sumOf { it.hr!!.toDouble() * it.seconds } / withHr.sumOf { it.seconds }).roundToInt()
        val withCad = runs.filter { it.cadence != null && it.seconds > 0 }
        val cadence = if (withCad.isEmpty()) null
        else (withCad.sumOf { it.cadence!!.toDouble() * it.seconds } / withCad.sumOf { it.seconds }).roundToInt()
        // Battiti per km sulle corse che hanno sia FC sia ritmo, così le due misure parlano delle stesse uscite
        val both = withKm.filter { it.hr != null }
        val bpk = if (both.isEmpty()) null else {
            val bKm = both.sumOf { it.km!! }
            val beats = both.sumOf { it.hr!!.toDouble() * it.seconds / 60.0 }
            (beats / bKm).roundToInt()
        }
        return Totals(
            count = runs.size,
            km = km,
            seconds = runs.sumOf { it.seconds },
            paceSec = pace,
            avgHr = hr,
            beatsPerKm = bpk,
            avgCadence = cadence,
            avgRpe = runs.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average(),
            longest = withKm.maxByOrNull { it.km!! },
            fastest = withKm.filter { it.km!! >= MIN_KM_RECORD && it.paceSec != null }.minByOrNull { it.paceSec!! }
        )
    }

    /** Intervallo di giorni [start, end) (fine esclusa). */
    data class Window(val start: LocalDate, val end: LocalDate) {
        operator fun contains(d: LocalDate) = !d.isBefore(start) && d.isBefore(end)
    }

    fun current(period: Period, today: LocalDate, runs: List<Run>): Window = when (period) {
        Period.WEEK -> DateUtils.weekStart(today).let { Window(it, it.plusWeeks(1)) }
        Period.MONTH -> today.withDayOfMonth(1).let { Window(it, it.plusMonths(1)) }
        Period.ALL -> Window(runs.minOfOrNull { it.date } ?: today, today.plusDays(1))
    }

    /** Il periodo prima di quello in corso (null per "Sempre"). */
    fun previous(period: Period, today: LocalDate): Window? = when (period) {
        Period.WEEK -> DateUtils.weekStart(today).let { Window(it.minusWeeks(1), it) }
        Period.MONTH -> today.withDayOfMonth(1).let { Window(it.minusMonths(1), it) }
        Period.ALL -> null
    }

    fun inWindow(runs: List<Run>, w: Window): List<Run> = runs.filter { it.date in w }

    data class Bucket(val start: LocalDate, val totals: Totals)

    /**
     * Colonne del grafico, dalla più vecchia: 12 settimane, 6 mesi, oppure (Sempre) tutti i mesi dalla
     * prima corsa (almeno 6), anche quelli senza uscite.
     */
    fun buckets(runs: List<Run>, period: Period, today: LocalDate): List<Bucket> {
        val starts: List<LocalDate> = when (period) {
            Period.WEEK -> DateUtils.weekStart(today).let { m -> (11 downTo 0).map { m.minusWeeks(it.toLong()) } }
            Period.MONTH -> today.withDayOfMonth(1).let { m -> (5 downTo 0).map { m.minusMonths(it.toLong()) } }
            Period.ALL -> {
                val last = today.withDayOfMonth(1)
                val first = (runs.minOfOrNull { it.date } ?: today).withDayOfMonth(1)
                val n = ChronoUnit.MONTHS.between(first, last).toInt().coerceAtLeast(5)
                (n downTo 0).map { last.minusMonths(it.toLong()) }
            }
        }
        val step: (LocalDate) -> LocalDate = if (period == Period.WEEK) { d -> d.plusWeeks(1) } else { d -> d.plusMonths(1) }
        return starts.map { s -> Bucket(s, totals(inWindow(runs, Window(s, step(s))))) }
    }

    /** Settimana e mese con più km (record del "Sempre"). */
    fun bestWeek(runs: List<Run>): Bucket? =
        runs.groupBy { DateUtils.weekStart(it.date) }.map { (m, l) -> Bucket(m, totals(l)) }.maxByOrNull { it.totals.km }

    fun bestMonth(runs: List<Run>): Bucket? =
        runs.groupBy { it.date.withDayOfMonth(1) }.map { (m, l) -> Bucket(m, totals(l)) }.maxByOrNull { it.totals.km }

    /**
     * Inizio contro adesso: le prime [n] corse e le ultime [n] (servono almeno 2n corse), per vedere
     * come sono cambiati ritmo, battito e battiti per km da quando si è cominciato.
     */
    fun firstVsLast(runs: List<Run>, n: Int = 4): Pair<Totals, Totals>? {
        val sorted = runs.sortedBy { it.date }
        if (sorted.size < n * 2) return null
        return totals(sorted.take(n)) to totals(sorted.takeLast(n))
    }

    /** Media dei km per settimana da [start] a oggi (settimane intere o in corso). */
    fun kmPerWeek(runs: List<Run>, start: LocalDate, today: LocalDate): Double {
        val weeks = ChronoUnit.WEEKS.between(DateUtils.weekStart(start), DateUtils.weekStart(today)) + 1
        return runs.sumOf { it.km ?: 0.0 } / weeks.coerceAtLeast(1)
    }
}
