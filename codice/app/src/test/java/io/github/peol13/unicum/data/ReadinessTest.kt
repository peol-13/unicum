package io.github.peol13.unicum.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

class ReadinessTest {

    @After
    fun reset() {
        HrvData.reliableFrom = null
    }

    private fun d(s: String) = LocalDate.parse(s)

    private fun night(date: String, minutes: Int, deep: Int, rem: Int, end: Long = 0) =
        SleepNight(date, end - minutes * 60_000L, end, minutes, deep, rem, minutes - deep - rem, 10, "test")

    private val hour = 3_600_000L
    private val wake = 1_000 * hour
    private val easyRun = TrainingLoad.Effort(5, 5.0, 120.0, 30, true)
    private fun session(endMillis: Long) = RecoveryEstimate.Session("s$endMillis", "Corsa", d("2026-11-24"), endMillis, easyRun)

    /** Prontezza al risveglio di 80 sul 24/11, con la notte finita a [wake]. */
    private fun morning(): Readiness.Result {
        val hrv = (10..23).associate { d("2026-11-$it") to 36.0 } + (d("2026-11-24") to 36.0)
        return Readiness.compute(d("2026-11-24"), hrv, mapOf(d("2026-11-24") to night("2026-11-24", 480, 120, 120, wake)))!!
    }

    /** I valori veri di Ultrahuman (18-25/11/2026) e quelli dati dalla formula: errore massimo 6 punti. */
    @Test
    fun matchesTheRingWithin6Points() {
        // (scarto HRV % dalla notte prima, minuti di sonno, profondo + REM, Ultrahuman)
        val days = listOf(
            listOf(-5.6, 537.0, 211.0, 62.0),
            listOf(-8.6, 498.0, 251.0, 70.0),
            listOf(-9.1, 408.0, 182.0, 67.0),
            listOf(6.0, 499.0, 230.0, 77.0),
            listOf(-10.3, 443.0, 163.0, 62.0),
            listOf(-6.0, 384.0, 217.0, 62.0),
            listOf(1.7, 468.0, 280.0, 76.0),
            listOf(26.9, 445.0, 235.0, 90.0)
        )
        for (x in days) {
            val s = Readiness.score(x[0], x[1].toInt(), x[2].toInt())
            assertTrue("$x → $s", kotlin.math.abs(s - x[3]) <= 6)
        }
    }

    /** 25/11/2026: tre notti basse, poi una che risale; il giorno prima una corsa. Ultrahuman 90%, prima l'app dava 66%. */
    @Test
    fun nightThatBouncesBackAfterLowNights() {
        val hrv = listOf(34.3, 33.9, 32.0, 38.8, 44.2, 37.6, 42.0, 39.6, 36.2, 32.9, 34.9, 31.3, 29.4, 29.9, 37.9)
            .mapIndexed { i, v -> d("2026-11-11").plusDays(i.toLong()) to v }.toMap()
        val r = Readiness.compute(d("2026-11-25"), hrv, mapOf(d("2026-11-25") to night("2026-11-25", 445, 115, 120)))!!
        assertEquals(27, r.hrvVsLastNightPct)
        assertEquals(7, r.hrvDeltaPct)
        assertTrue("${r.score}", r.score in 88..92)
    }

    @Test
    fun scoreStaysBetween0And100() {
        assertTrue(Readiness.score(80.0, 700, 400) <= 100)
        assertEquals(30, Readiness.score(-80.0, 0, 0))
    }

    @Test
    fun levels() {
        assertEquals("Riposo", Readiness.levelLabel(29))
        assertEquals("Leggero", Readiness.levelLabel(30))
        assertEquals("Moderato", Readiness.levelLabel(69))
        assertEquals("Pronto", Readiness.levelLabel(76))
        assertEquals("Ottimale", Readiness.levelLabel(90))
    }

    @Test
    fun computeComparesWithTheNightBefore() {
        val hrv = (10..23).associate { d("2026-11-$it") to 36.0 } + (d("2026-11-24") to 30.0)
        val r = Readiness.compute(d("2026-11-24"), hrv, mapOf(d("2026-11-24") to night("2026-11-24", 468, 120, 160)))!!
        assertEquals(-17, r.hrvDeltaPct)
        assertEquals(-17, r.hrvVsLastNightPct)
        assertEquals(280, r.restorativeMinutes)
        assertEquals(Readiness.score(-16.67, 468, 280), r.score)
    }

    @Test
    fun withoutTheNightBeforeUsesTheAverage() {
        // Notte prima (23/11) bassa: conta lei, non la media
        val hrv = (10..22).associate { d("2026-11-$it") to 36.0 } + (d("2026-11-23") to 25.0) + (d("2026-11-24") to 30.0)
        val nights = mapOf(d("2026-11-24") to night("2026-11-24", 468, 120, 160))
        assertEquals(20, Readiness.compute(d("2026-11-24"), hrv, nights)!!.hrvVsLastNightPct)
        // Mancano le ultime 3 notti: si confronta con la media
        val gap = (5..20).associate { d("2026-11-${"%02d".format(it)}") to 36.0 } + (d("2026-11-24") to 30.0)
        assertEquals(-17, Readiness.compute(d("2026-11-24"), gap, nights)!!.hrvVsLastNightPct)
    }

    @Test
    fun noValueWithoutEnoughNights() {
        val hrv = mapOf(d("2026-11-22") to 35.0, d("2026-11-23") to 35.0, d("2026-11-24") to 30.0)
        val nights = mapOf(d("2026-11-24") to night("2026-11-24", 468, 120, 160))
        assertNull(Readiness.compute(d("2026-11-24"), hrv, nights))
        // Notti prima del salto di scala dell'HRV: non contano
        val more = hrv + (d("2026-11-21") to 35.0)
        HrvData.reliableFrom = d("2026-11-22")
        assertNull(Readiness.compute(d("2026-11-24"), more, nights))
    }

    @Test
    fun latestFallsBackToLastNight() {
        val hrv = (10..23).associate { d("2026-11-$it") to 36.0 }
        val nights = mapOf(d("2026-11-23") to night("2026-11-23", 400, 100, 100))
        assertEquals(d("2026-11-23"), Readiness.latest(d("2026-11-24"), hrv, nights)?.date)
    }

    @Test
    fun workoutAfterWakingLowersItThenItComesBack() {
        val m = morning()
        val hours = RecoveryEstimate.modelHours(easyRun) // ~29 h
        val run = listOf(session(wake + 3 * hour))
        // Prima dell'allenamento resta il valore del risveglio
        assertEquals(m.morningScore, Readiness.duringDay(m, run, 1.0, wake + 2 * hour).score)
        // Subito dopo: 0,65 punti per ora di recupero
        val after = Readiness.duringDay(m, run, 1.0, wake + 3 * hour)
        assertEquals((0.65 * hours).roundToInt(), after.workoutPoints)
        assertEquals(1, after.workoutsToday)
        assertEquals(m.morningScore - after.workoutPoints, after.score)
        // 10 ore dopo è risalita
        val later = Readiness.duringDay(m, run, 1.0, wake + 13 * hour)
        assertEquals((0.65 * (hours - 10)).roundToInt(), later.workoutPoints)
        assertTrue(later.score > after.score)
        // Il fattore personale allunga o accorcia il recupero
        assertTrue(Readiness.duringDay(m, run, 1.3, wake + 3 * hour).workoutPoints > after.workoutPoints)
    }

    @Test
    fun workoutsBeforeWakingAlreadyCountInTheNight() {
        val m = morning()
        val r = Readiness.duringDay(m, listOf(session(wake - 12 * hour)), 1.0, wake + 2 * hour)
        assertEquals(0, r.workoutPoints)
        assertEquals(m.morningScore, r.score)
    }

    @Test
    fun longDayLowersItALittle() {
        val m = morning()
        assertEquals(0, Readiness.duringDay(m, emptyList(), 1.0, wake + 16 * hour).awakePoints)
        assertEquals(4, Readiness.duringDay(m, emptyList(), 1.0, wake + 18 * hour).awakePoints)
        assertEquals(20, Readiness.duringDay(m, emptyList(), 1.0, wake + 40 * hour).awakePoints)
        // Tanti allenamenti: al massimo 45 punti
        val many = (1..5).map { session(wake + it * hour) }
        assertEquals(45, Readiness.duringDay(m, many, 1.0, wake + 6 * hour).workoutPoints)
    }

    @Test
    fun yesterdaysNightStaysAsItWasInTheMorning() {
        val hrv = (10..23).associate { d("2026-11-$it") to 36.0 }
        val nights = mapOf(d("2026-11-23") to night("2026-11-23", 400, 100, 100, wake))
        val r = Readiness.latest(d("2026-11-24"), hrv, nights, listOf(session(wake + hour)), 1.0, wake + 30 * hour)!!
        assertEquals(d("2026-11-23"), r.date)
        assertEquals(r.morningScore, r.score)
    }
}
