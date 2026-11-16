package io.github.peol13.unicum

import io.github.peol13.unicum.data.StoryPage
import io.github.peol13.unicum.data.WeekDay
import io.github.peol13.unicum.data.WeekStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekStoryTest {
    private val monday = LocalDate.of(2026, 10, 12)

    private fun day(i: Int, deficit: Int? = null, minutes: Int = 0, workouts: Int = 0, sleep: Int? = null, hrv: Double? = null) =
        WeekDay(
            date = monday.plusDays(i.toLong()), isToday = false, isFuture = false, eatenKcal = 0,
            activityMinutes = minutes, workouts = workouts, deficitKcal = deficit, sleepMinutes = sleep, hrv = hrv
        )

    @Test
    fun `settimana completa`() {
        val days = (0 until 7).map { i ->
            day(i, deficit = if (i < 5) 600 else 300, minutes = if (i % 2 == 0) 45 else 0, workouts = if (i % 2 == 0) 1 else 0,
                sleep = 400 + i * 10, hrv = 50.0 + i)
        }
        val weights = mapOf(monday to 78.0, monday.plusDays(6) to 77.6)
        val pages = WeekStory.build(days, weights, deficitGoal = 500)
        assertEquals(
            listOf(StoryPage.Kind.INTRO, StoryPage.Kind.DEFICIT, StoryPage.Kind.ATTIVITA, StoryPage.Kind.SONNO,
                StoryPage.Kind.RECUPERO, StoryPage.Kind.PESO, StoryPage.Kind.PROSSIMA),
            pages.map { it.kind }
        )
        val deficit = pages.first { it.kind == StoryPage.Kind.DEFICIT }
        assertEquals("514", deficit.value)                              // (5×600 + 2×300) / 7
        assertTrue(deficit.sentence, deficit.sentence.contains("in 5 giorni su 7"))
        val activity = pages.first { it.kind == StoryPage.Kind.ATTIVITA }
        assertEquals("180", activity.value)
        assertTrue(activity.sentence.contains("raggiunto"))
        assertEquals("−0,4", pages.first { it.kind == StoryPage.Kind.PESO }.value)
        assertTrue(pages.first { it.kind == StoryPage.Kind.SONNO }.sentence.contains("domenica"))
    }

    @Test
    fun `settimana vuota mostra solo inizio e consiglio`() {
        val pages = WeekStory.build((0 until 7).map { day(it) }, emptyMap(), 500)
        assertEquals(listOf(StoryPage.Kind.INTRO, StoryPage.Kind.PROSSIMA), pages.map { it.kind })
        assertTrue(pages.last().sentence.contains("sessioni"))
    }

    @Test
    fun `sonno corto e il consiglio principale`() {
        val days = (0 until 7).map { day(it, sleep = 330, minutes = 60, workouts = 1) }
        val next = WeekStory.build(days, emptyMap(), 500).last()
        assertTrue(next.sentence.contains("letto"))
    }
}
