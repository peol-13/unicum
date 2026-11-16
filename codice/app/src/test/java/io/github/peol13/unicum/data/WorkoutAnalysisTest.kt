package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutAnalysisTest {

    private fun run(
        distanceM: Double? = 3000.0,
        seconds: Int = 900,
        speed: List<WorkoutSample> = emptyList(),
        route: List<RoutePoint> = emptyList(),
        hr: List<WorkoutSample> = emptyList(),
        steps: Long? = null,
        date: String = "2026-11-05"
    ) = WorkoutDetail(
        externalId = "x", source = "s", date = date, type = "Corsa", exerciseType = 56,
        startMillis = 0, endMillis = seconds * 1000L, movingSeconds = seconds,
        distanceM = distanceM, steps = steps, speed = speed, route = route, hr = hr
    )

    @Test
    fun paceAndFormats() {
        assertEquals(300, WorkoutAnalysis.paceSecPerKm(3000.0, 900))
        assertNull(WorkoutAnalysis.paceSecPerKm(50.0, 900))
        assertEquals("5'00\"", WorkoutAnalysis.formatPace(300))
        assertEquals("5'32\"", WorkoutAnalysis.formatPace(332))
        assertEquals("42:10", WorkoutAnalysis.formatDuration(2530))
        assertEquals("1:02:10", WorkoutAnalysis.formatDuration(3730))
        assertEquals("8,43", WorkoutAnalysis.formatKm(8432.0))
    }

    @Test
    fun splitsFromConstantSpeed() {
        // 3,33 m/s per 15 minuti = 3 km a 5'00"/km
        val speed = (0..900 step 5).map { WorkoutSample(it, 1000f / 300f) }
        val splits = WorkoutAnalysis.splits(run(speed = speed))
        assertEquals(3, splits.size)
        splits.forEach { assertTrue("ritmo ${it.paceSec}", it.paceSec in 295..305) }
    }

    @Test
    fun lastSplitCanBeShort() {
        val speed = (0..900 step 5).map { WorkoutSample(it, 1000f / 300f) }
        val splits = WorkoutAnalysis.splits(run(distanceM = 3500.0, speed = speed))
        assertEquals(4, splits.size)
        assertEquals(500.0, splits.last().meters, 1.0)
    }

    @Test
    fun splitsFromRouteAreRescaledOnSourceDistance() {
        // Punti lungo un meridiano: 0,009° di latitudine ≈ 1 km
        val route = (0..30).map { RoutePoint(it * 10, 45.0 + it * 0.0003, 9.0) }
        val d = run(distanceM = 1000.0, seconds = 300, route = route)
        val line = WorkoutAnalysis.distanceTimeline(d)
        assertEquals(1000.0, line.last().second, 0.5)
        assertEquals(1, WorkoutAnalysis.splits(d).size)
    }

    /** 10' a 2 m/s, poi 3 × (4' a 3 m/s, 2' a 1,5 m/s), poi 1' a 2 m/s; FC 170 nelle veloci, 140 altrove. */
    private fun intervalRun(): WorkoutDetail {
        fun fast(t: Int) = t >= 600 && t < 1680 && (t - 600) % 360 < 240
        fun v(t: Int) = when { t < 600 || t >= 1680 -> 2f; fast(t) -> 3f; else -> 1.5f }
        val speed = (0..1740).map { WorkoutSample(it, v(it)) }
        val hr = (0..1740 step 5).map { WorkoutSample(it, if (fast(it)) 170f else 140f) }
        val total = (0 until 1740).sumOf { v(it).toDouble() }
        return run(distanceM = total, seconds = 1740, speed = speed, hr = hr)
    }

    @Test
    fun intervalsByTime() {
        val plan = IntervalPlan(IntervalPart(seconds = 600), 3, IntervalPart(seconds = 240), IntervalPart(seconds = 120))
        val s = WorkoutAnalysis.intervals(intervalRun(), plan)
        assertEquals(
            listOf("WARM_UP", "WORK", "REST", "WORK", "REST", "WORK", "REST", "COOL_DOWN"),
            s.map { it.kind.name }
        )
        val work = s.filter { it.kind == WorkoutAnalysis.IntervalKind.WORK }
        work.forEach {
            assertEquals(240, it.seconds)
            assertEquals(720.0, it.meters, 8.0)
            assertTrue("ritmo ${it.paceSec}", it.paceSec!! in 330..337) // 3 m/s = 5'33"/km
            assertEquals(170, it.avgHr)
        }
        assertEquals(1680, s.last().startS)
    }

    @Test
    fun intervalsByDistanceAndShortRecording() {
        // Veloci da 720 m (= 4' a 3 m/s), recuperi da 180 m (= 2' a 1,5 m/s): stessi confini della prova a tempo
        val plan = IntervalPlan(IntervalPart(meters = 1200.0), 3, IntervalPart(meters = 720.0), IntervalPart(meters = 180.0))
        val s = WorkoutAnalysis.intervals(intervalRun(), plan)
        val work = s.filter { it.kind == WorkoutAnalysis.IntervalKind.WORK }
        assertEquals(3, work.size)
        work.forEachIndexed { i, w -> assertTrue("inizio ${w.startS}", w.startS in (600 + i * 360 - 3)..(600 + i * 360 + 3)) }
        // Più ripetute di quelle fatte: ci si ferma alla fine della registrazione, nessun defaticamento
        val tooMany = WorkoutAnalysis.intervals(intervalRun(), plan.copy(reps = 10))
        assertTrue(tooMany.last().endS <= 1740)
        assertTrue(tooMany.none { it.kind == WorkoutAnalysis.IntervalKind.COOL_DOWN })
    }

    @Test
    fun cadenceFromStepsWhenNoSamples() {
        assertEquals(170, WorkoutAnalysis.averageCadence(run(seconds = 600, steps = 1700)))
    }

    @Test
    fun hrZonesCountSecondsAndSkipGaps() {
        val samples = listOf(
            WorkoutSample(0, 150f), WorkoutSample(10, 150f), // 10 s in zona 3 (140-160 con max 200)
            WorkoutSample(20, 185f), WorkoutSample(200, 185f) // 10 s zona 3, poi buco: contano 30 s in zona 5
        )
        val zones = WorkoutAnalysis.hrZones(samples, 200)
        assertEquals(20, zones[2].seconds)
        assertEquals(30, zones[4].seconds)
    }

    @Test
    fun weeklyTotalsIncludeEmptyWeeks() {
        val today = LocalDate.parse("2026-11-05") // giovedì
        val totals = WorkoutAnalysis.weeklyTotals(
            listOf(run(date = "2026-11-03"), run(date = "2026-11-05"), run(date = "2026-10-20")), 4, today
        )
        assertEquals(4, totals.size)
        assertEquals(2, totals.last().count)
        assertEquals(6000.0, totals.last().meters, 0.1)
        assertEquals(0, totals[2].count)
        assertEquals(1, totals[1].count)
    }

    @Test
    fun elevationGainIgnoresSmallNoise() {
        // Sale di 20 m, scende di 5, risale di 8 (28 m veri); sopra, oscillazioni di ±0,5 m che non devono contare
        val alts = (0..40).map { 100f + it * .5f } + (0..20).map { 120f - it * .25f } + (0..32).map { 115f + it * .25f }
        val route = alts.mapIndexed { i, a -> RoutePoint(i * 5, 45.0, 9.0 + i * 0.0001, a + if (i % 2 == 0) .5f else -.5f) }
        val gain = WorkoutAnalysis.elevationGain(run(route = route))!!
        // L'addolcimento smussa un po' le cime: si accetta qualche metro in meno, mai il rumore in più
        assertTrue("dislivello $gain", gain in 22.0..29.0)
        assertNull(WorkoutAnalysis.elevationGain(run()))
    }

    @Test
    fun findSegmentPicksTheRunInsideWarmUpAndWalk() {
        // 10 min di cammino a 1,5 m/s, 17 min di corsa a 2,66 m/s (6'17"/km), 10 min di cammino
        val speed = (0..2220 step 5).map { t -> WorkoutSample(t, if (t in 600..1620) 2.66f else 1.5f) }
        val hr = (0..2220 step 5).map { t -> WorkoutSample(t, if (t in 600..1620) 165f else 110f) }
        val total = 600 * 1.5 + 1020 * 2.66 + 600 * 1.5
        val d = run(distanceM = total, seconds = 2220, speed = speed, hr = hr)
        val seg = WorkoutAnalysis.findSegment(d, 1020, 1020 * 2.66)!!
        assertTrue("inizio ${seg.first}", seg.first in 590..610)
        assertEquals(165.0, WorkoutAnalysis.hrIn(d, seg)!!.first.toDouble(), 3.0)
    }

    @Test
    fun dedupeKeepsPreferredSource() {
        val samsung = run().copy(externalId = "a", source = "com.sec.android.app.shealth", startMillis = 0, endMillis = 1_800_000)
        val healthSync = run(distanceM = null).copy(externalId = "b", source = "nl.appyhapps.healthsync", startMillis = 10_000, endMillis = 1_790_000)
        val other = run().copy(externalId = "c", startMillis = 5_000_000, endMillis = 6_000_000)
        val kept = WorkoutAnalysis.dedupe(listOf(healthSync, samsung, other))
        assertEquals(listOf("c", "a"), kept.map { it.externalId })
    }

    @Test
    fun dedupeSessionsDropsCopiesFromOtherApps() {
        data class S(val id: String, val type: String, val start: Long, val end: Long, val source: String)
        val items = listOf(
            S("hs", "Camminata", 0, 600_000, "nl.appyhapps.healthsync"),
            S("sh", "Camminata", 0, 600_000, "com.sec.android.app.shealth"),
            S("run", "Corsa", 100_000, 500_000, "com.sec.android.app.shealth"), // altro tipo: resta
            S("later", "Camminata", 700_000, 900_000, "nl.appyhapps.healthsync") // non si sovrappone: resta
        )
        val kept = WorkoutAnalysis.dedupeSessions(items, { it.type }, { it.start }, { it.end }, { it.source })
        assertEquals(setOf("sh", "run", "later"), kept.map { it.id }.toSet())
    }

    @Test
    fun paceSeriesSkipsStops() {
        val speed = (0..120 step 2).map { WorkoutSample(it, if (it in 40..80) 0f else 3f) }
        val pace = WorkoutAnalysis.paceSeries(run(speed = speed), windowSec = 4)
        assertTrue(pace.none { it.t in 46..74 })
        assertTrue(pace.any { it.t < 30 && it.v in 330f..340f })
    }
}
