package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class LapDetectionTest {

    /**
     * Una corsa finta come quella del 24/11/2026: [levels] = (secondi in cui inizia il cambio, velocità dopo);
     * la velocità ci arriva con una rampa di [ramp] secondi, il battito segue con ritardo.
     */
    private fun run(levels: List<Pair<Int, Double>>, end: Int, ramp: Int = 30, start: Double = 2.4): WorkoutDetail {
        val speed = mutableListOf<WorkoutSample>()
        val hr = mutableListOf<WorkoutSample>()
        var h = 130.0
        for (t in 0..end step 2) {
            var v = start
            var from = start
            for ((at, to) in levels) {
                if (t >= at) {
                    val k = ((t - at).toDouble() / ramp).coerceIn(0.0, 1.0)
                    v = from + (to - from) * k
                }
                from = to
            }
            v += 0.03 * sin(t / 7.0) // un po' di rumore del sensore
            // Battito: tende a 60 + 45 × velocità, con costante di tempo di 40 s
            h += (60 + 45 * v - h) * 2 / 40.0
            speed += WorkoutSample(t, v.toFloat())
            hr += WorkoutSample(t, h.toFloat())
        }
        return WorkoutDetail(
            externalId = "x", source = "test", date = "2026-11-24", type = "Corsa", exerciseType = 56,
            startMillis = 0, endMillis = end * 1000L, movingSeconds = end, distanceM = speed.sumOf { it.v.toDouble() * 2 },
            hr = hr, speed = speed
        )
    }

    private fun steps(vararg p: Pair<StepKind, Int>) = p.mapIndexed { i, (k, s) -> PlanStep(k, i + 1, IntervalPart(seconds = s)) }

    /** Secondi di inizio di ogni pezzo trovato. */
    private fun starts(plan: IntervalPlan): List<Int> {
        var t = plan.offsetS
        return plan.steps().map { s -> t.also { t += s.part.seconds!! } }
    }

    @Test
    fun findsTheRealChangesNotTheProgrammedOnes() {
        // Giri veri 6:41, 1:59, 6:17, 2:03, poi lento fino alla fine; programma 2 × (6' + 2') + 25'
        val d = run(listOf(401 to 1.5, 520 to 2.6, 897 to 1.55, 1020 to 2.09), end = 2520)
        val plan = LapDetection.detect(
            d, steps(StepKind.WORK to 360, StepKind.REST to 120, StepKind.WORK to 360, StepKind.REST to 120, StepKind.COOL_DOWN to 1500), "B2"
        )!!
        assertTrue(plan.estimated)
        assertEquals("B2", plan.fromPlan)
        val s = starts(plan)
        assertEquals(0, s[0])
        listOf(401, 520, 897, 1020).forEachIndexed { i, want ->
            assertTrue("cambio ${i + 1}: ${s[i + 1]} invece di $want", abs(s[i + 1] - want) <= 8)
        }
        assertEquals(0, plan.uncertainChanges)
        // I pezzi tagliati così finiscono nei Parziali come prima
        val segments = WorkoutAnalysis.intervals(d, plan)
        assertEquals(WorkoutAnalysis.IntervalKind.WORK, segments.first().kind)
        assertTrue(abs(segments.first().endS - 401) <= 8)
    }

    @Test
    fun unwrittenWarmUpBeforeTheStructure() {
        // 8' di riscaldamento non scritto, poi 3 × (3' veloce + 1' lento)
        val levels = mutableListOf(480 to 2.7)
        var t = 480
        repeat(3) { levels += (t + 180) to 1.6; levels += (t + 240) to 2.7; t += 240 }
        levels.removeAt(levels.lastIndex)
        val d = run(levels, end = t, start = 2.1)
        val plan = LapDetection.detect(d, steps(StepKind.WORK to 180, StepKind.REST to 60, StepKind.WORK to 180, StepKind.REST to 60, StepKind.WORK to 180, StepKind.REST to 60), null)!!
        assertTrue("inizio ${plan.offsetS}", abs(plan.offsetS - 480) <= 8)
        val segments = WorkoutAnalysis.intervals(d, plan)
        assertEquals(WorkoutAnalysis.IntervalKind.WARM_UP, segments.first().kind)
    }

    @Test
    fun easyRunInsteadOfIntervalsIsNotSure() {
        // Programma a ripetute, ma corsa tutta allo stesso ritmo: i cambi non si possono confermare
        val d = run(emptyList(), end = 2520)
        val plan = LapDetection.detect(
            d, steps(StepKind.WORK to 360, StepKind.REST to 120, StepKind.WORK to 360, StepKind.REST to 120, StepKind.COOL_DOWN to 1500), null
        )
        assertNotNull(plan)
        assertTrue("incerti: ${plan!!.uncertainChanges}", plan.uncertainChanges >= 3)
    }

    @Test
    fun noSpeedSeriesNoEstimate() {
        val d = WorkoutDetail(externalId = "x", source = "test", date = "2026-11-24", type = "Corsa", exerciseType = 56, startMillis = 0, endMillis = 1_000_000, movingSeconds = 1000)
        assertNull(LapDetection.detect(d, steps(StepKind.WORK to 360, StepKind.REST to 120), null))
    }

    @Test
    fun asLapsKeepsDurationsAndFastParts() {
        // 90 s prima della struttura: diventano il primo giro, così i giri partono dall'inizio come quelli scritti a mano
        val plan = IntervalPlan.ofSteps(steps(StepKind.WORK to 401, StepKind.REST to 119, StepKind.COOL_DOWN to 1500), null)!!.copy(offsetS = 90)
        val laps = plan.asLaps()!!
        assertTrue(laps.laps)
        assertEquals(0, laps.offsetS)
        assertEquals(listOf(90, 401, 119, 1500), laps.steps().map { it.part.seconds })
        assertEquals(listOf(StepKind.LAP, StepKind.WORK, StepKind.LAP, StepKind.LAP), laps.steps().map { it.kind })
        // Con la durata della registrazione, quello che avanza alla fine diventa l'ultimo giro
        assertEquals(listOf(90, 401, 119, 1500, 37), plan.asLaps(2147)!!.steps().map { it.part.seconds })
    }
}
