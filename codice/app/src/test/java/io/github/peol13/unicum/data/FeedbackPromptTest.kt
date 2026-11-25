package io.github.peol13.unicum.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeedbackPromptTest {
    private val install = LocalDate.of(2026, 11, 1)

    private fun show(
        today: LocalDate,
        daysUsed: Int = 10,
        answered: Boolean = false,
        dismissals: Int = 0,
        snoozedUntil: LocalDate? = null,
    ) = FeedbackPrompt.shouldShow(today, install, daysUsed, answered, dismissals, snoozedUntil)

    @Test
    fun `compare solo dopo 5 giorni dall'installazione e 3 giorni d'uso`() {
        assertFalse(show(install.plusDays(4)))
        assertTrue(show(install.plusDays(5)))
        assertFalse(show(install.plusDays(30), daysUsed = 2))
        assertTrue(show(install.plusDays(30), daysUsed = 3))
    }

    @Test
    fun `chi ha risposto non la rivede piu`() {
        assertFalse(show(install.plusDays(60), answered = true))
    }

    @Test
    fun `non ora la rimanda di 7 giorni e dopo due volte basta`() {
        val snoozeDay = install.plusDays(10)
        val until = snoozeDay.plusDays(FeedbackPrompt.SNOOZE_DAYS.toLong())
        assertFalse(show(until.minusDays(1), dismissals = 1, snoozedUntil = until))
        assertTrue(show(until, dismissals = 1, snoozedUntil = until))
        assertFalse(show(until.plusDays(100), dismissals = 2, snoozedUntil = until))
    }
}
