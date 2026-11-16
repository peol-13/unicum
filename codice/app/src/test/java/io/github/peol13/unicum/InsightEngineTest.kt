package io.github.peol13.unicum

import io.github.peol13.unicum.data.DaySample
import io.github.peol13.unicum.data.InsightEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightEngineTest {
    private val start = LocalDate.of(2026, 8, 1)

    private fun day(i: Int, sleep: Int? = null, hrv: Double? = null, workouts: Int = 0, steps: Int? = null, deficit: Int? = null) =
        DaySample(start.plusDays(i.toLong()), sleep, hrv, workouts, steps, deficit)

    @Test
    fun `notti lunghe e HRV piu alta`() {
        val days = (0 until 10).map { i -> if (i % 2 == 0) day(i, sleep = 480, hrv = 60.0) else day(i, sleep = 360, hrv = 50.0) }
        val insight = InsightEngine.compute(days).single { it.title == "Sonno e HRV" }
        assertTrue(insight.text.contains("più alta del 20%"))
        assertEquals(5, insight.daysWith)
        assertEquals(5, insight.daysWithout)
    }

    @Test
    fun `allenamento e HRV della notte dopo`() {
        // allenamento nei giorni pari: la notte dopo (giorno dispari) HRV 45, altrimenti 50
        val days = (0 until 12).map { i ->
            val afterWorkout = i % 2 == 1
            day(i, hrv = if (afterWorkout) 45.0 else 50.0, workouts = if (i % 2 == 0) 1 else 0)
        }
        val insight = InsightEngine.compute(days).single { it.title == "Allenamento e HRV" }
        assertTrue(insight.text, insight.text.contains("più bassa del 10%"))
    }

    @Test
    fun `differenze piccole o pochi giorni non si mostrano`() {
        val piccole = (0 until 10).map { i -> if (i % 2 == 0) day(i, sleep = 480, hrv = 51.0) else day(i, sleep = 360, hrv = 50.0) }
        assertTrue(InsightEngine.compute(piccole).none { it.title == "Sonno e HRV" })
        val pochi = (0 until 5).map { i -> if (i % 2 == 0) day(i, sleep = 480, hrv = 70.0) else day(i, sleep = 360, hrv = 40.0) }
        assertNull(InsightEngine.compute(pochi).firstOrNull { it.title == "Sonno e HRV" })
    }

    @Test
    fun `passi e sonno della notte dopo`() {
        val days = (0 until 12).map { i ->
            val activeYesterday = i % 2 == 1
            day(i, sleep = if (activeYesterday) 450 else 420, steps = if (i % 2 == 0) 10000 else 3000)
        }
        val insight = InsightEngine.compute(days).single { it.title == "Passi e sonno" }
        assertTrue(insight.text, insight.text.contains("30 minuti in più"))
    }
}
