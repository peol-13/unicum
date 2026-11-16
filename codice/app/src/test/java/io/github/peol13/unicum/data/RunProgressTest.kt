package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RunProgressTest {

    private fun run(
        day: Int, kind: String, km: Double = 4.0, pace: Int = 420, hr: Int? = 160, rpe: Int? = null, id: String = "r$day",
        effortHr: Int? = hr
    ) = RunProgress.Run(
        id = id, date = LocalDate.of(2026, 10, day), kind = kind, km = km, seconds = (km * pace).toInt(),
        paceSec = pace, hr = hr, hrMax = hr?.plus(15), cadence = 150, rpe = rpe, vo2 = 41.0, effortHr = effortHr
    )

    @Test
    fun beatsPerKm() {
        // 160 bpm × 7 minuti al km
        assertEquals(1120, run(1, "Facile").beatsPerKm)
        assertNull(run(1, "Facile", hr = null).beatsPerKm)
    }

    @Test
    fun estimateNeedsThreeEasyRunsAndDropsOutliers() {
        val runs = listOf(
            run(1, "Facile", pace = 450, hr = 150),        // 1125 battiti/km
            // Ripetute: la FC dello sforzo è quella dei tratti veloci (185), non della seduta (170)
            run(3, "Intervalli", pace = 360, hr = 170, rpe = 8, effortHr = 185),
            run(5, "Facile", pace = 420, hr = 150),        // 1050
            run(8, "Facile", pace = 420, hr = 160),        // 1120
            run(10, "Facile", pace = 420, hr = 150),       // 1050
            run(12, "Facile", pace = 300, hr = 100)        // 500: dato sbagliato, scartato
        )
        val history = RunProgress.estimateHistory(runs)
        // Prima della terza uscita facile niente stima
        assertEquals(listOf(8, 10, 12), history.map { it.date.dayOfMonth })
        assertEquals(1120, history.first().beatsPerKm)  // mediana di 1125, 1050, 1120
        val last = history.last()
        assertEquals(1085, last.beatsPerKm)              // mediana di 1125, 1050, 1120, 1050: il 500 non c'è
        assertEquals(4, last.easyCount)
        assertEquals(185, last.hardHr)
        // Senza prove continue dure resta la stima dal battito: 1085 / 185 minuti al km × 5 km
        assertNull(last.perfSeconds5k)
        assertEquals(1085.0 / 185 * 60 * 5, last.seconds5k.toDouble(), 1.0)
    }

    @Test
    fun estimateAveragesHeartRateAndRealEffort() {
        val runs = listOf(
            run(1, "Intervalli", pace = 360, hr = 170, rpe = 8, effortHr = 185),
            run(2, "Facile", pace = 450, hr = 150),
            run(4, "Facile", pace = 420, hr = 150),
            run(6, "Facile", pace = 420, hr = 160),
            run(8, "Progressivo", km = 3.0, pace = 390, hr = 175, rpe = 8), // prova reale: 3 km in 19:30
            // "Facile" con RPE 9: non è un'uscita facile, conta solo come sforzo
            run(10, "Lungo facile", km = 5.0, pace = 420, hr = 182, rpe = 9)
        )
        val last = RunProgress.estimateHistory(runs).last()
        assertEquals(3, last.easyCount)
        assertEquals(3, last.hardCount)
        assertEquals(181, last.hardHr)                  // (185 + 175 + 182) / 3
        // Migliore prova: il progressivo portato a 5 km con Riegel (meglio dei 5 km in 35:00)
        assertEquals("r8", last.perfRun?.id)
        assertEquals(RunProgress.riegel(1170, 3.0), last.perfSeconds5k)
        assertEquals((last.hrSeconds5k + last.perfSeconds5k!!) / 2.0, last.seconds5k.toDouble(), 1.0)

        val e5 = RunProgress.estimate(runs)!!
        assertEquals(last.seconds5k, e5.seconds)
        assertEquals(e5.seconds, e5.low)
        assertEquals(5.0, e5.longestKm, 0.01)
        // 7 e 10 km: Riegel dalla stima dei 5, con la forchetta 1,06–1,10
        val e7 = RunProgress.estimate(runs, 7.0)!!
        assertEquals(RunProgress.riegel(last.seconds5k, 5.0, 7.0), e7.seconds)
        assertTrue(e7.low < e7.seconds && e7.seconds < e7.high)
        val e10 = RunProgress.estimate(runs, 10.0)!!
        assertTrue(e10.seconds > 2 * e5.seconds)
        assertTrue(e10.pace > e7.pace && e7.pace > e5.pace)
    }

    @Test
    fun realRunsAtDistance() {
        val runs = listOf(
            run(1, "Facile", km = 4.95, pace = 420),
            run(3, "Lungo facile", km = 10.0, pace = 450),
            run(5, "Intervalli", km = 7.0, pace = 380)  // ripetute: non contano
        )
        assertEquals(listOf("r1", "r3"), RunProgress.realAt(runs, 5.0).map { it.first.id })
        assertEquals(listOf("r3"), RunProgress.realAt(runs, 7.0).map { it.first.id })
        assertEquals(4500, RunProgress.realAt(runs, 10.0).single().second)
        assertEquals(10.0, RunProgress.longestKm(runs), 0.01)
    }

    @Test
    fun scenariosMoveWithHr() {
        val runs = listOf(
            run(1, "Intervalli", hr = 180, rpe = 8),
            run(2, "Facile", pace = 420, hr = 150), run(3, "Facile", pace = 420, hr = 150), run(4, "Facile", pace = 420, hr = 150)
        )
        val s = RunProgress.scenarios(runs)
        assertEquals(listOf("Prudente", "Centrale", "Ottimista"), s.map { it.name })
        assertTrue(s[0].seconds > s[1].seconds && s[1].seconds > s[2].seconds)
        val s10 = RunProgress.scenarios(runs, 10.0)
        assertEquals(RunProgress.atDistance(s[1].seconds, 10.0), s10[1].seconds)
    }

    @Test
    fun compareLastWithPreviousAndBest() {
        val runs = listOf(run(1, "Progressivo", pace = 430), run(4, "Progressivo", pace = 400), run(7, "Progressivo", pace = 410))
        val pace = RunProgress.compare(runs, "Progressivo").first { it.metric == RunProgress.Metric.PACE }
        assertEquals("r7", pace.a?.id)
        assertEquals("r4", pace.b?.id)
        assertEquals(10.0, pace.delta!!, 0.01)
        assertEquals(false, pace.deltaGood) // più lento della precedente
        assertEquals("r4", pace.best?.id)
        assertEquals(3, pace.count)
    }

    @Test
    fun paceIsNeutralForEasyRunsAndManualChoiceWorks() {
        val runs = listOf(run(1, "Facile", pace = 430), run(4, "Facile", pace = 400), run(7, "Facile", pace = 410))
        val pace = RunProgress.compare(runs, "Facile", a = runs[0], b = runs[2]).first { it.metric == RunProgress.Metric.PACE }
        assertEquals("r1", pace.a?.id)
        assertEquals("r7", pace.b?.id)
        assertNull(pace.deltaGood)
        assertNull(pace.best)
    }

    @Test
    fun weeklyByKindSplitsKm() {
        val runs = listOf(run(26, "Facile", km = 4.0), run(28, "Progressivo", km = 3.0), run(29, "Facile", km = 5.0))
        val weeks = RunProgress.weeklyByKind(runs, 2, LocalDate.of(2026, 10, 30))
        assertEquals(9.0, weeks.last().km["Facile"]!!, 0.01)
        assertEquals(12.0, weeks.last().total, 0.01)
        assertEquals(0, weeks.first().count)
    }
}
