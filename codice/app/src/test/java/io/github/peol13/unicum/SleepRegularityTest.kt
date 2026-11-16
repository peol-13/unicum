package io.github.peol13.unicum

import io.github.peol13.unicum.data.SleepRegularity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SleepRegularityTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 12)

    /** Istante del giorno [d] (0 = sera del 12, 1 = mattina del 13) all'orario h:m. */
    private fun at(d: Long, h: Int, m: Int = 0) =
        day.plusDays(d).atTime(h, m).toInstant(zone).toEpochMilli()

    @Test
    fun `le ore dopo mezzanotte vengono dopo le 24`() {
        assertEquals(23 * 60 + 30, SleepRegularity.nightMinute(at(0, 23, 30), zone))
        assertEquals(24 * 60 + 30, SleepRegularity.nightMinute(at(1, 0, 30), zone))
        assertEquals("00:30", SleepRegularity.clock(24 * 60 + 30))
    }

    @Test
    fun `media a cavallo della mezzanotte`() {
        // 23:30 e 00:30 -> media a mezzanotte, non a mezzogiorno
        val s = SleepRegularity.summarize(listOf(at(0, 23, 30) to at(1, 7), at(1, 0, 30) to at(1, 7)), zone)!!
        assertEquals("00:00", SleepRegularity.clock(s.avgBed))
        assertEquals(30, s.bedSpread)
        assertEquals("07:00", SleepRegularity.clock(s.avgWake))
    }

    @Test
    fun `fascia consigliata otto ore prima della sveglia piu quindici minuti`() {
        val s = SleepRegularity.summarize(listOf(at(0, 23) to at(1, 7, 15)), zone)!!
        assertEquals("23:00", SleepRegularity.clock(s.recommendedBed))
        assertEquals("07:15", SleepRegularity.clock(s.recommendedWake))
        assertEquals(1, s.nights)
    }

    @Test
    fun `nessuna notte`() {
        assertNull(SleepRegularity.summarize(emptyList(), zone))
    }
}
