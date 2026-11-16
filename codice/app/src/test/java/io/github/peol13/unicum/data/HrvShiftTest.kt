package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HrvShiftTest {

    /** Serie giornaliera da [start] con i valori dati, un giorno dopo l'altro. */
    private fun series(start: String, values: List<Double>): Map<String, Double> {
        val d0 = LocalDate.parse(start)
        return values.mapIndexed { i, v -> d0.plusDays(i.toLong()).toString() to v }.toMap()
    }

    private fun noisy(n: Int, center: Double, spread: Double) = List(n) { i -> center + listOf(-1.0, .5, 1.0, -.5, 0.0)[i % 5] * spread }

    @Test
    fun algorithmChangeIsFound() {
        // ~90 ms per 20 giorni, poi ~50 ms: il salto è il 21° giorno
        val s = series("2026-10-06", noisy(20, 90.0, 5.0) + noisy(15, 50.0, 5.0))
        assertEquals(LocalDate.parse("2026-10-26"), HrvData.detectShift(s))
    }

    @Test
    fun normalVariationIsNotAShift() {
        // Giornate buone e cattive (±15 ms attorno a 60) senza cambio di scala
        val values = List(40) { i -> 60.0 + listOf(-15.0, 10.0, 0.0, 15.0, -8.0, 5.0, -12.0)[i % 7] }
        assertNull(HrvData.detectShift(series("2026-09-01", values)))
    }

    @Test
    fun temporaryDipIsNotAShift() {
        // Due settimane di HRV basso (malattia) e poi di nuovo come prima: lo storico resta buono
        val s = series("2026-09-01", noisy(25, 60.0, 4.0) + noisy(14, 38.0, 3.0) + noisy(12, 60.0, 4.0))
        assertNull(HrvData.detectShift(s))
    }

    @Test
    fun tooFewDaysGiveNoShift() {
        assertNull(HrvData.detectShift(series("2026-10-20", noisy(6, 90.0, 3.0) + noisy(6, 50.0, 3.0))))
    }

    @Test
    fun reliableFollowsChosenDate() {
        HrvData.reliableFrom = LocalDate.parse("2026-11-01")
        assertEquals(false, HrvData.reliable("2026-10-31"))
        assertEquals(true, HrvData.reliable("2026-11-01"))
        HrvData.reliableFrom = null
        assertEquals(true, HrvData.reliable("2020-01-01"))
    }
}
