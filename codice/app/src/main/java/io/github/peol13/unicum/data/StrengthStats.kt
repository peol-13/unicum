package io.github.peol13.unicum.data

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Numeri delle sedute di forza (pesi e corpo libero) registrate dall'orologio: durata, calorie e battito arrivano
 * da Health Connect, gli esercizi no. Logica pura, con test.
 */
object StrengthStats {

    data class Totals(val count: Int, val seconds: Int, val kcal: Int?, val avgHr: Int?)

    /** Settimana da lunedì, mese dal giorno 1, oppure tutto (come le statistiche della corsa, [RunStats.Period]). */
    fun inPeriod(workouts: List<WorkoutDetail>, period: RunStats.Period, today: LocalDate): List<WorkoutDetail> {
        val from = when (period) {
            RunStats.Period.WEEK -> today.with(DayOfWeek.MONDAY)
            RunStats.Period.MONTH -> today.withDayOfMonth(1)
            RunStats.Period.ALL -> return workouts
        }
        return workouts.filter { LocalDate.parse(it.date) in from..today }
    }

    fun totals(workouts: List<WorkoutDetail>): Totals {
        val kcal = workouts.mapNotNull { it.kcal }
        // FC media pesata sulla durata: una seduta lunga conta più di una breve
        val withHr = workouts.filter { it.avgHr != null }
        val hrSeconds = withHr.sumOf { it.movingSeconds }
        return Totals(
            count = workouts.size,
            seconds = workouts.sumOf { it.movingSeconds },
            kcal = kcal.takeIf { it.isNotEmpty() }?.sum()?.roundToInt(),
            avgHr = if (hrSeconds > 0) (withHr.sumOf { it.avgHr!!.toLong() * it.movingSeconds } / hrSeconds).toInt() else null
        )
    }

    /**
     * Costanza rispetto al programma: sedute di forza previste negli ultimi [days] giorni fino a oggi compreso e
     * quante sono state fatte (allenamento registrato nel giorno o segnata come fatta). null se non ce n'erano.
     */
    fun adherence(plan: List<PlannedRun>, doneKeys: Set<String>, today: LocalDate, days: Long = 28): Pair<Int, Int>? {
        val due = plan.filter { r ->
            val d = r.localDate ?: return@filter false
            r.sportKind.isStrength && !d.isAfter(today) && d.isAfter(today.minusDays(days))
        }
        if (due.isEmpty()) return null
        return due.count { NotionPlan.isDone(it, doneKeys) } to due.size
    }
}
