package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingLoadTest {

    private fun workout(minutes: Int, bpm: Int?, series: Boolean = true, maxHr: Int? = null) = WorkoutDetail(
        externalId = "x", source = "s", date = "2026-11-05", type = "Corsa", exerciseType = 56,
        startMillis = 0, endMillis = minutes * 60_000L, movingSeconds = minutes * 60,
        avgHr = bpm, maxHr = maxHr,
        hr = if (series && bpm != null) (0..minutes * 60 step 5).map { WorkoutSample(it, bpm.toFloat()) } else emptyList()
    )

    @Test
    fun cr10Scale() {
        assertEquals(5.0, TrainingLoad.cr10(.84), 1e-9)
        assertEquals(7.0, TrainingLoad.cr10(.92), 1e-9)
        assertEquals(6.5, TrainingLoad.cr10(.90), 1e-9)
        assertEquals(0.8, TrainingLoad.cr10(.40), 1e-9)
        assertEquals(10.0, TrainingLoad.cr10(1.1), 1e-9)
    }

    @Test
    fun sessionRpeRaisesTheLoadOfStrength() {
        assertEquals(.84, TrainingLoad.fractionFor(5.0), 1e-9)
        assertEquals(.90, TrainingLoad.fractionFor(6.5), 1e-9)
        // 50' di pesi con battito basso (60%: zona 2, carico 100): con RPE 7 (92%, zona 5) il carico sale a 250
        val hr = TrainingLoad.effort(workout(50, 120), 200)!!
        assertEquals(100.0, hr.load, 1.0)
        val withRpe = TrainingLoad.withSessionRpe(hr, 7, 50.0)!!
        assertEquals(250.0, withRpe.load, 1e-9)
        assertEquals("il punteggio resta quello del battito", hr.score, withRpe.score)
        // RPE basso: resta il carico del battito; senza RPE o senza battito non cambia nulla
        assertEquals(hr.load, TrainingLoad.withSessionRpe(hr, 1, 50.0)!!.load, 1e-9)
        assertEquals(hr, TrainingLoad.withSessionRpe(hr, null, 50.0))
        assertNull(TrainingLoad.withSessionRpe(null, 7, 50.0))
    }

    @Test
    fun effortFromHeartRateSeries() {
        // 30' fissi all'84% della FC max: zona 4 (Edwards), CR10 = 5
        val e = TrainingLoad.effort(workout(30, 168), 200)!!
        assertEquals(5, e.score)
        assertEquals(120.0, e.load, 0.5)
        assertEquals(30, e.hardMinutes)
        assertTrue(e.fromSeries)
        // Un'ora allo stesso battito: +0,5 per la durata e carico doppio
        val long = TrainingLoad.effort(workout(60, 168), 200)!!
        assertEquals(6, long.score)
        assertEquals(240.0, long.load, 1.0)
    }

    @Test
    fun hardPartsWeighMore() {
        // Metà facile (60%) e metà dura (92%): conta anche l'80° percentile, non solo la media
        val hr = (0..1800 step 5).map { WorkoutSample(it, if (it < 900) 120f else 184f) }
        val e = TrainingLoad.effort(workout(30, 152).copy(hr = hr), 200)!!
        // media (2 + 7) / 2 = 4,5; p80 = 7 → 5,75
        assertEquals(5.75, e.rawScore, 0.05)
        assertEquals(6, e.score)
    }

    @Test
    fun fallbackOnAverageAndMissingData() {
        val e = TrainingLoad.effort(workout(30, 184, series = false), 200)!!
        assertEquals(7, e.score)
        assertEquals(150.0, e.load, 1e-9)
        assertFalse(e.fromSeries)
        assertNull(TrainingLoad.effort(workout(30, null), 200))
    }

    @Test
    fun personalMaxHrUsesHighestRecorded() {
        assertEquals(187, TrainingLoad.personalMaxHr(30, emptyList()))
        assertEquals(199, TrainingLoad.personalMaxHr(30, listOf(workout(20, 170, maxHr = 199))))
    }

    @Test
    fun compareFeelingWithData() {
        assertEquals("Sensazioni in linea con i dati.", TrainingLoad.compare(5, 6))
        assertTrue(TrainingLoad.compare(2, 6).startsWith("Ti è sembrata molto più facile"))
        assertTrue(TrainingLoad.compare(4, 6).startsWith("Ti è sembrata più facile"))
        assertTrue(TrainingLoad.compare(8, 6).startsWith("L'hai sentita più dura"))
    }

    @Test
    fun onlyRunsAndManuallyStartedWorkoutsCountForRecovery() {
        // Allenamento avviato a mano: battito ogni 5 s per tutta la sessione
        assertTrue(TrainingLoad.countsForRecovery(workout(20, 120).copy(type = "Allenamento")))
        // Riconosciuto in automatico: campioni a tratti, buchi di 150 s
        val sparse = (0..1200 step 150).flatMap { b -> (0..20 step 2).map { WorkoutSample(b + it, 115f) } }
        assertFalse(TrainingLoad.countsForRecovery(workout(20, 115).copy(type = "Ciclismo", hr = sparse)))
        // Le corse contano sempre, le sessioni marcate automatiche mai, senza battito no
        assertTrue(TrainingLoad.countsForRecovery(workout(20, 160).copy(hr = sparse)))
        assertFalse(TrainingLoad.countsForRecovery(workout(20, 160).copy(automatic = true)))
        assertFalse(TrainingLoad.countsForRecovery(workout(20, null).copy(type = "Allenamento")))
    }
}
