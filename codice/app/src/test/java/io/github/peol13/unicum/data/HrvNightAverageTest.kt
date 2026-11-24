package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrvNightAverageTest {

    private val min = 60_000L

    /** Un campione ogni 6 minuti, come scrive l'anello. */
    private fun everySixMinutes(values: List<Double>) = values.mapIndexed { i, v -> i * 6 * min to v }

    @Test
    fun noSamplesNoValue() {
        assertNull(HrvData.nightAverage(emptyList()))
    }

    @Test
    fun burstOfDuplicatesCountsOnce() {
        // 15 campioni normali da 30 ms, poi 30 copie da 11 ms nello stesso minuto (come il 23/11 alle 4:00);
        // restano 16 campioni, troppo pochi per il taglio del 5%
        val normal = everySixMinutes(List(15) { 30.0 })
        val burst = List(30) { 200 * min + it * 1_000L to 11.0 }
        val avg = HrvData.nightAverage(normal + burst)!!
        // Senza togliere i doppioni la media sarebbe (15·30 + 30·11) / 45 = 17,3
        assertEquals((15 * 30.0 + 11.0) / 16, avg, 0.01)
    }

    @Test
    fun spikesAreTrimmed() {
        // 40 campioni: 38 da 30 ms, un picco da 150 e uno da 5 → si scarta il 5% per lato (2 valori)
        val values = List(38) { 30.0 } + 150.0 + 5.0
        assertEquals(30.0, HrvData.nightAverage(everySixMinutes(values))!!, 0.01)
    }

    @Test
    fun fewSamplesAreAPlainAverage() {
        // Meno di 20 campioni: il 5% è meno di un valore, non si taglia niente
        assertEquals(20.0, HrvData.nightAverage(everySixMinutes(listOf(10.0, 20.0, 30.0)))!!, 0.01)
    }

    @Test
    fun unsortedInputIsHandled() {
        val s = everySixMinutes(listOf(10.0, 20.0, 30.0)).reversed()
        assertEquals(20.0, HrvData.nightAverage(s)!!, 0.01)
    }
}
