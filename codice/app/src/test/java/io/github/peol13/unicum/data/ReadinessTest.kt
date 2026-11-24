package io.github.peol13.unicum.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadinessTest {

    @After
    fun reset() {
        HrvData.reliableFrom = null
    }

    private fun d(s: String) = LocalDate.parse(s)

    private fun night(date: String, minutes: Int, deep: Int, rem: Int) =
        SleepNight(date, 0, 0, minutes, deep, rem, minutes - deep - rem, 10, "test")

    /** I valori veri di Ultrahuman (18-24/11/2026) e quelli dati dalla formula: errore massimo 5 punti. */
    @Test
    fun matchesTheRingWithin5Points() {
        // (scarto HRV %, minuti di sonno, profondo + REM, kcal attive di ieri rispetto al solito, Ultrahuman)
        val days = listOf(
            listOf(7.2, 537.0, 211.0, 144.0, 62.0),
            listOf(-3.4, 498.0, 251.0, 167.0, 70.0),
            listOf(-12.9, 408.0, 182.0, -195.0, 67.0),
            listOf(-5.9, 499.0, 230.0, -42.0, 77.0),
            listOf(-14.9, 443.0, 163.0, -84.0, 62.0),
            listOf(-19.3, 384.0, 217.0, -87.0, 62.0),
            listOf(-16.7, 468.0, 280.0, -47.0, 76.0)
        )
        for (x in days) {
            val s = Readiness.score(x[0], x[1].toInt(), x[2].toInt(), x[3])
            assertTrue("$x → $s", kotlin.math.abs(s - x[4]) <= 5)
        }
    }

    @Test
    fun scoreStaysBetween0And100() {
        assertEquals(100, Readiness.score(80.0, 700, 400, -900.0))
        assertEquals(0, Readiness.score(-80.0, 0, 0, 2000.0))
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
    fun computeUsesThePreviousNightsAndYesterdayActivity() {
        val hrv = (10..23).associate { d("2026-11-$it") to 36.0 } + (d("2026-11-24") to 30.0)
        val kcal = (9..22).associate { d("2026-11-${"%02d".format(it)}") to 300.0 } + (d("2026-11-23") to 400.0)
        val r = Readiness.compute(d("2026-11-24"), hrv, mapOf(d("2026-11-24") to night("2026-11-24", 468, 120, 160)), kcal)!!
        assertEquals(-17, r.hrvDeltaPct)
        assertEquals(280, r.restorativeMinutes)
        assertEquals(100, r.kcalDelta)
        assertEquals(Readiness.score(-16.67, 468, 280, 100.0), r.score)
    }

    @Test
    fun noValueWithoutEnoughNights() {
        val hrv = mapOf(d("2026-11-22") to 35.0, d("2026-11-23") to 35.0, d("2026-11-24") to 30.0)
        val nights = mapOf(d("2026-11-24") to night("2026-11-24", 468, 120, 160))
        assertNull(Readiness.compute(d("2026-11-24"), hrv, nights, emptyMap()))
        // Notti prima del salto di scala dell'HRV: non contano
        val more = hrv + (d("2026-11-21") to 35.0)
        HrvData.reliableFrom = d("2026-11-22")
        assertNull(Readiness.compute(d("2026-11-24"), more, nights, emptyMap()))
    }

    @Test
    fun latestFallsBackToLastNight() {
        val hrv = (10..23).associate { d("2026-11-$it") to 36.0 }
        val nights = mapOf(d("2026-11-23") to night("2026-11-23", 400, 100, 100))
        assertEquals(d("2026-11-23"), Readiness.latest(d("2026-11-24"), hrv, nights, emptyMap())?.date)
    }
}
