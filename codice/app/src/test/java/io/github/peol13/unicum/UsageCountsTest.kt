package io.github.peol13.unicum

import io.github.peol13.unicum.data.UsageCounts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UsageCountsTest {
    private val d15 = LocalDate.of(2026, 11, 15)
    private val d16 = LocalDate.of(2026, 11, 16)

    @Test
    fun countsAndSummaryFromMostOpened() {
        val c = UsageCounts().add(d15, "Home").add(d15, "Home").add(d16, "Home").add(d15, "Allenamenti")
        assertEquals("Home=3;Allenamenti=1", c.summary())
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val c = UsageCounts().add(d15, "Home").add(d16, "Profilo/backup").add(d16, "Profilo/backup")
        assertEquals(c, UsageCounts.decode(c.encode()))
        assertEquals(UsageCounts(), UsageCounts.decode(null))
        assertEquals(UsageCounts(), UsageCounts.decode("rotto;2026|x"))
    }

    @Test
    fun separatorsInNamesAreCleaned() {
        val c = UsageCounts().add(d15, "a|b;c=d e")
        assertEquals("a_b_c_d_e=1", c.summary())
        assertEquals(c, UsageCounts.decode(c.encode()))
    }

    @Test
    fun splitKeepsTodayForLater() {
        val c = UsageCounts().add(d15, "Home").add(d16, "Diario")
        val (past, current) = c.split(d16)
        assertEquals("Home=1", past.summary())
        assertEquals("Diario=1", current.summary())
    }

    @Test
    fun daysOfUse() {
        var days = UsageCounts.addDay(null, d16)
        days = UsageCounts.addDay(days, d15)
        days = UsageCounts.addDay(days, d16)
        assertEquals("2026-11-15,2026-11-16", days)
        assertEquals(listOf("2026-11-15"), UsageCounts.daysBefore(days, d16))
        assertEquals("2026-11-16", UsageCounts.daysFrom(days, d16))
    }
}
