package io.github.peol13.unicum.data

import io.github.peol13.unicum.data.RunStats.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RunStatsTest {

    private fun run(date: String, km: Double?, seconds: Int, hr: Int? = null, cadence: Int? = null, rpe: Int? = null) =
        RunProgress.Run(
            id = date, date = LocalDate.parse(date), kind = "Facile", km = km, seconds = seconds,
            paceSec = km?.let { (seconds / it).toInt() }, hr = hr, hrMax = null, cadence = cadence, rpe = rpe, vo2 = null
        )

    private val today = LocalDate.parse("2026-11-06") // venerdì

    private val runs = listOf(
        run("2026-09-01", 2.0, 840, hr = 150),
        run("2026-10-20", 4.0, 1560, hr = 160, cadence = 160, rpe = 4),
        run("2026-11-03", 3.0, 1200, hr = 150, cadence = 150),   // martedì di questa settimana
        run("2026-11-05", 5.0, 2100, hr = 170, rpe = 6),          // giovedì
        run("2026-10-29", 1.0, 300)                                // settimana scorsa, corta e veloce
    )

    @Test
    fun totalsAreWeightedByTime() {
        val t = RunStats.totals(runs.filter { it.date >= LocalDate.parse("2026-11-02") })
        assertEquals(2, t.count)
        assertEquals(8.0, t.km, 1e-9)
        assertEquals(3300, t.seconds)
        assertEquals(413, t.paceSec) // 3300 / 8 = 412,5
        // FC: (150·1200 + 170·2100) / 3300 = 162,7
        assertEquals(163, t.avgHr)
        assertEquals(150, t.avgCadence) // solo la corsa con cadenza
        assertEquals(6.0, t.avgRpe!!, 1e-9)
        assertEquals("2026-11-05", t.longest!!.id)
        // battiti: 150·20 + 170·35 = 8950 su 8 km
        assertEquals(1119, t.beatsPerKm)
    }

    @Test
    fun fastestIgnoresShortRuns() {
        val t = RunStats.totals(runs)
        assertEquals("2026-10-20", t.fastest!!.id) // 390 s/km; la corsa da 1 km a 300 s/km non conta
    }

    @Test
    fun windows() {
        val week = RunStats.current(Period.WEEK, today, runs)
        assertEquals(LocalDate.parse("2026-11-02"), week.start)
        assertEquals(LocalDate.parse("2026-10-26"), RunStats.previous(Period.WEEK, today)!!.start)
        assertEquals(2, RunStats.inWindow(runs, week).size)
        assertEquals(1, RunStats.inWindow(runs, RunStats.previous(Period.WEEK, today)!!).size)

        val month = RunStats.current(Period.MONTH, today, runs)
        assertEquals(LocalDate.parse("2026-11-01"), month.start)
        assertEquals(2, RunStats.inWindow(runs, RunStats.previous(Period.MONTH, today)!!).size)

        val all = RunStats.current(Period.ALL, today, runs)
        assertEquals(LocalDate.parse("2026-09-01"), all.start)
        assertEquals(5, RunStats.inWindow(runs, all).size)
        assertNull(RunStats.previous(Period.ALL, today))
    }

    @Test
    fun buckets() {
        val weeks = RunStats.buckets(runs, Period.WEEK, today)
        assertEquals(12, weeks.size)
        assertEquals(LocalDate.parse("2026-11-02"), weeks.last().start)
        assertEquals(8.0, weeks.last().totals.km, 1e-9)

        val months = RunStats.buckets(runs, Period.MONTH, today)
        assertEquals(6, months.size)
        assertEquals(5.0, months[4].totals.km, 1e-9) // ottobre

        // Sempre: da settembre a novembre sono 3 mesi, ma il grafico ne mostra almeno 6
        val all = RunStats.buckets(runs, Period.ALL, today)
        assertEquals(6, all.size)
        assertEquals(LocalDate.parse("2026-11-01"), all.last().start)
    }

    @Test
    fun recordsAndProgress() {
        assertEquals(LocalDate.parse("2026-11-02"), RunStats.bestWeek(runs)!!.start)
        assertEquals(LocalDate.parse("2026-11-01"), RunStats.bestMonth(runs)!!.start)
        assertNull(RunStats.firstVsLast(runs, 4))
        val (first, last) = RunStats.firstVsLast(runs, 2)!!
        assertEquals(2, first.count)
        assertEquals(2, last.count)
        // 15 km dal 1/9 (settimana 31/8) al 6/11 (settimana 2/11): 10 settimane
        assertEquals(1.5, RunStats.kmPerWeek(runs, LocalDate.parse("2026-09-01"), today), 1e-9)
    }
}
