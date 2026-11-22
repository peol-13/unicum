package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StrengthStatsTest {

    private val today = LocalDate.of(2026, 11, 19)   // giovedì

    private fun gym(date: String, minutes: Int, kcal: Double?, hr: Int?) = WorkoutDetail(
        externalId = date, source = "s", date = date, type = Sport.GYM_WORKOUT, exerciseType = 70,
        startMillis = 0, endMillis = minutes * 60_000L, movingSeconds = minutes * 60, kcal = kcal, avgHr = hr
    )

    @Test
    fun periodsAndTotals() {
        val list = listOf(gym("2026-11-18", 60, 300.0, 120), gym("2026-11-16", 30, 150.0, 90), gym("2026-11-10", 45, null, null), gym("2026-10-30", 40, 200.0, 100))
        val week = StrengthStats.totals(StrengthStats.inPeriod(list, RunStats.Period.WEEK, today))
        assertEquals(2, week.count)
        assertEquals(90 * 60, week.seconds)
        assertEquals(450, week.kcal)
        assertEquals("pesata sulla durata", 110, week.avgHr)
        assertEquals(3, StrengthStats.inPeriod(list, RunStats.Period.MONTH, today).size)
        assertEquals(4, StrengthStats.totals(StrengthStats.inPeriod(list, RunStats.Period.ALL, today)).count)
        val empty = StrengthStats.totals(emptyList())
        assertNull(empty.kcal)
        assertNull(empty.avgHr)
    }

    @Test
    fun adherenceCountsOnlyStrengthSessions() {
        fun planned(id: String, date: String, sport: Sport, status: String = "Da fare") =
            PlannedRun(id, id, date, null, status, sport = sport.key.takeIf { sport != Sport.RUN })
        val plan = listOf(
            planned("a", "2026-11-17", Sport.WEIGHTS),
            planned("b", "2026-11-12", Sport.BODYWEIGHT, status = "Completata"),
            planned("c", "2026-11-10", Sport.WEIGHTS),
            planned("corsa", "2026-11-17", Sport.RUN),
            planned("futura", "2026-11-21", Sport.WEIGHTS)
        )
        val done = setOf(NotionPlan.doneKey("2026-11-17", Sport.Family.STRENGTH))
        assertEquals(2 to 3, StrengthStats.adherence(plan, done, today))
        assertNull(StrengthStats.adherence(plan.filter { it.sportKind == Sport.RUN }, done, today))
    }
}
