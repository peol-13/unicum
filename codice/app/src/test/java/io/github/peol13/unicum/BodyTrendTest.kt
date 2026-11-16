package io.github.peol13.unicum

import io.github.peol13.unicum.data.BodyTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BodyTrendTest {
    private val d0 = LocalDate.of(2026, 10, 1)
    private fun day(n: Long) = d0.plusDays(n)

    @Test
    fun `la media mobile usa solo i giorni della finestra`() {
        val values = mapOf(day(0) to 80.0, day(3) to 78.0, day(10) to 76.0)
        val trend = BodyTrend.movingAverage(values, windowDays = 7)
        assertEquals(80.0, trend.getValue(day(0)), 1e-9)
        assertEquals(79.0, trend.getValue(day(3)), 1e-9)   // 80 e 78 nei 7 giorni
        assertEquals(76.0, trend.getValue(day(10)), 1e-9)  // il giorno 3 è fuori dalla finestra (4-10)
    }

    @Test
    fun `la tendenza attenua un picco isolato`() {
        val values = (0L..6L).associate { day(it) to 75.0 } + (day(7) to 77.0)
        val trend = BodyTrend.movingAverage(values)
        // un +2 kg in un solo giorno pesa per 1/7 sulla tendenza
        assertEquals(75.0 + 2.0 / 7.0, trend.getValue(day(7)), 1e-9)
    }

    @Test
    fun `variazione della tendenza tra prima e ultima misura`() {
        val values = mapOf(day(0) to 80.0, day(20) to 78.0)
        assertEquals(-2.0, BodyTrend.trendChange(values)!!, 1e-9)
        assertNull(BodyTrend.trendChange(mapOf(day(0) to 80.0)))
    }

    @Test
    fun `7700 kcal di deficit tolgono un chilo dal giorno dopo`() {
        val deficits = mapOf(day(0) to 3850, day(1) to 3850)
        val p = BodyTrend.projected(80.0, day(0), day(2), deficits)
        assertEquals(80.0, p.getValue(day(0)), 1e-9)
        assertEquals(79.5, p.getValue(day(1)), 1e-9)
        assertEquals(79.0, p.getValue(day(2)), 1e-9)
    }

    @Test
    fun `giorni senza pasti e surplus`() {
        val deficits = mapOf<LocalDate, Int?>(day(0) to null, day(1) to -770)
        val p = BodyTrend.projected(70.0, day(0), day(2), deficits)
        assertEquals(70.0, p.getValue(day(1)), 1e-9)       // giorno senza dato: invariato
        assertEquals(70.1, p.getValue(day(2)), 1e-9)       // surplus di 770 kcal: +0,1 kg
        assertEquals(3, p.size)
    }
}
