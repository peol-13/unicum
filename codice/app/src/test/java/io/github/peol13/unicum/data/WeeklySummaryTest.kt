package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WeeklySummaryTest {

    private val monday = LocalDate.of(2026, 11, 23)

    @Test
    fun dueOnMondayFromNineAndOncePerWeek() {
        assertFalse("lunedì prima delle 9", WeeklySummary.isDue(monday, LocalTime.of(8, 30), null))
        assertTrue(WeeklySummary.isDue(monday, LocalTime.of(9, 5), null))
        assertTrue("martedì recupera", WeeklySummary.isDue(monday.plusDays(1), LocalTime.of(7, 0), null))
        assertFalse("già inviata per la settimana 16-22", WeeklySummary.isDue(monday, LocalTime.of(12, 0), "2026-11-16"))
        assertFalse("mercoledì no", WeeklySummary.isDue(monday.plusDays(2), LocalTime.of(12, 0), null))
    }

    private fun day(d: Int, deficit: Int?, minutes: Int = 0, sleep: Int? = null, counts: Boolean = true) = WeekDay(
        date = LocalDate.of(2026, 11, 16).plusDays(d.toLong()), isToday = false, isFuture = false, eatenKcal = 0,
        activityMinutes = minutes, workouts = 0, deficitKcal = deficit, countsForAverages = counts, sleepMinutes = sleep
    )

    @Test
    fun textSummarisesWeek() {
        val days = listOf(day(0, 400, 30, 420), day(1, 600, 0, 440), day(2, null, 45, null), day(3, -2000, counts = false))
        assertEquals(
            "Deficit medio 500 kcal · 75 min di attività · sonno medio 7 h 10. Tocca per il racconto della settimana.",
            WeeklySummary.text(days)
        )
        assertNull("niente da raccontare", WeeklySummary.text(listOf(day(0, null))))
    }
}
