package io.github.peol13.unicum.data

import io.github.peol13.unicum.data.GoalProgress.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RunGoalsTest {

    private val start: LocalDate = LocalDate.of(2026, 10, 1)

    private fun run(day: Long, kind: String, km: Double = 4.0, pace: Int = 420, hr: Int = 150, rpe: Int? = null) = RunProgress.Run(
        id = "r$day", date = start.plusDays(day), kind = kind, km = km, seconds = (km * pace).toInt(),
        paceSec = pace, hr = hr, hrMax = hr + 15, cadence = 150, rpe = rpe, vo2 = null, effortHr = hr
    )

    /** Uscite facili ogni 4 giorni con battiti per km che scendono di [step] a ogni uscita (o salgono se negativo). */
    private fun improving(count: Int, step: Int): List<RunProgress.Run> =
        listOf(run(0, "Intervalli", hr = 180, rpe = 8)) +
            (1..count).map { i -> run(i * 4L, "Facile", pace = 420, hr = 160 - i * step / 7) }

    @Test
    fun parsePaceAndKm() {
        assertEquals(390, RunGoal.parsePace("6:30"))
        assertEquals(390, RunGoal.parsePace("6'30\""))
        assertEquals(390, RunGoal.parsePace("6.30"))
        assertEquals(390, RunGoal.parsePace("630"))
        assertEquals(360, RunGoal.parsePace("6"))
        assertNull(RunGoal.parsePace("6:75"))
        assertNull(RunGoal.parsePace("1:00"))
        assertNull(RunGoal.parsePace("abc"))
        assertEquals(7.5, RunGoal.parseKm("7,5")!!, 1e-9)
        assertNull(RunGoal.parseKm("0,5"))
        assertEquals(1950, RunGoal(5.0, 390, "2026-12-31").targetSeconds)
    }

    @Test
    fun goalForDistancePrefersOpenDeadline() {
        val goals = RunGoals(
            short = RunGoal(5.0, 390, "2026-11-01"),
            long = RunGoal(5.0, 360, "2027-06-30")
        )
        val today = LocalDate.of(2026, 11, 13)
        assertEquals(360, goals.forKm(5.0, today)?.paceSec)
        assertNull(goals.forKm(10.0, today))
    }

    @Test
    fun projectionSlowsDown() {
        assertEquals(0.0, GoalProgress.effectiveDays(0), 1e-9)
        // Una settimana vale quasi una settimana, sei settimane valgono meno di quattro, mai oltre 30 giorni
        assertTrue(GoalProgress.effectiveDays(7) > 6.0)
        assertTrue(GoalProgress.effectiveDays(42) < 28.0)
        assertTrue(GoalProgress.effectiveDays(1000) <= 30.0)
    }

    @Test
    fun trendNeedsEnoughPoints() {
        val today = start.plusDays(40)
        assertNull(GoalProgress.trendPerDay(listOf(start.plusDays(39) to 2000, start.plusDays(40) to 1990), today))
        val pts = (0..4).map { start.plusDays(20L + it * 5) to 2000 - it * 10 }
        assertEquals(-2.0, GoalProgress.trendPerDay(pts, today)!!, 1e-6)
    }

    @Test
    fun improvingTrendIsOnTrackOrAhead() {
        val runs = improving(8, 7)  // battito facile in calo: stima che migliora
        val today = start.plusDays(32)
        val est = RunProgress.estimate(runs)!!.seconds
        // Obiettivo poco sotto la stima di oggi, lontano nel tempo: ci si arriva
        val goal = RunGoal(5.0, ((est - 20) / 5.0).toInt(), today.plusDays(60).toString())
        val p = GoalProgress.of(goal, runs, today)
        assertTrue(p.perWeek!! < 0)
        assertTrue(p.projected!! < p.current!!)
        assertTrue(p.status == Status.AHEAD || p.status == Status.ON_TRACK)
        assertTrue(p.neededPerWeek!! < 0)
    }

    @Test
    fun worseningTrendIsBehindAndProjectionIsCapped() {
        val runs = improving(8, -7)  // battito in salita: stima che peggiora
        val today = start.plusDays(32)
        val current = RunProgress.estimate(runs)!!.seconds
        val goal = RunGoal(5.0, ((current - 30) / 5.0).toInt(), today.plusDays(400).toString())
        val p = GoalProgress.of(goal, runs, today)
        assertEquals(Status.BEHIND, p.status)
        // Mai oltre il 10% della stima attuale, anche con una scadenza lontana
        assertTrue(p.projected!! <= current * 1.1 + 1)
    }

    @Test
    fun reachedMissedAndNoData() {
        val runs = improving(8, 7)
        val today = start.plusDays(32)
        val current = RunProgress.estimate(runs)!!.seconds
        val easy = RunGoal(5.0, (current / 5.0).toInt() + 10, today.plusDays(30).toString())
        assertEquals(Status.REACHED, GoalProgress.of(easy, runs, today).status)
        assertNull(GoalProgress.of(easy, runs, today).neededPerWeek)

        val hard = RunGoal(5.0, 240, today.minusDays(1).toString())
        val missed = GoalProgress.of(hard, runs, today)
        assertEquals(Status.MISSED, missed.status)
        assertTrue(missed.expired)

        val none = GoalProgress.of(easy, runs.take(2), today)
        assertEquals(Status.NO_DATA, none.status)
        assertNull(none.current)
    }

    @Test
    fun goalOnLongerDistanceUsesRiegel() {
        val runs = improving(8, 7)
        val today = start.plusDays(32)
        val p = GoalProgress.of(RunGoal(10.0, 420, today.plusDays(90).toString()), runs, today)
        val five = RunProgress.estimateHistory(runs).last().seconds5k
        assertEquals(RunProgress.atDistance(five, 10.0), p.current)
        assertEquals(4.0, p.longestKm, 0.01)
    }
}
