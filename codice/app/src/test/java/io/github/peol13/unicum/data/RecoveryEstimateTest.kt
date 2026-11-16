package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RecoveryEstimateTest {

    /** Questi test sono scritti per un HRV che cambia scala il 26/10/2026. */
    @Before
    fun hrvShift() { HrvData.reliableFrom = LocalDate.of(2026, 10, 26) }

    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val easyRun = TrainingLoad.Effort(5, 5.0, 120.0, 30, true)

    private fun session(date: String, hour: Int, effort: TrainingLoad.Effort = easyRun) =
        RecoveryEstimate.Session("id-$date", "Corsa", LocalDate.parse(date), at(date, hour), effort)

    /** HRV di 50 ms (±1) dal 28/10 al [to] compreso, più i valori in [extra]. */
    private fun hrv(to: String, extra: Map<String, Double> = emptyMap()): Map<LocalDate, Double> {
        val out = mutableMapOf<LocalDate, Double>()
        var d = LocalDate.of(2026, 10, 28)
        var i = 0
        while (!d.isAfter(LocalDate.parse(to))) { out[d] = if (i++ % 2 == 0) 49.0 else 51.0; d = d.plusDays(1) }
        extra.forEach { (k, v) -> out[LocalDate.parse(k)] = v }
        return out
    }

    @Test
    fun modelHoursGrowWithLoadAndIntensity() {
        assertEquals(29.1, RecoveryEstimate.modelHours(easyRun), 0.2)
        assertEquals(0.0, RecoveryEstimate.modelHours(TrainingLoad.Effort(2, 2.0, 3.0, 0, true)), 1e-9)
        assertEquals(RecoveryEstimate.MAX_HOURS, RecoveryEstimate.modelHours(TrainingLoad.Effort(10, 10.0, 5000.0, 300, true)), 1e-9)
        val hard = TrainingLoad.Effort(8, 8.0, 120.0, 25, true)
        assertTrue(RecoveryEstimate.modelHours(hard) > RecoveryEstimate.modelHours(easyRun))
    }

    @Test
    fun historyFindsTheNightHrvComesBack() {
        val s = session("2026-11-03", 19)
        val h = RecoveryEstimate.history(listOf(s), hrv("2026-11-03", mapOf("2026-11-04" to 44.0, "2026-11-05" to 51.0)), emptyMap(), zone)
        assertEquals(1, h.size)
        assertEquals(2, h[0].night)
        assertEquals(36.0, h[0].observedHours, 1e-9)   // dalle 19 del 3/11 alle 7 del 5/11
        assertEquals(12.0, h[0].notYetHours, 1e-9)     // il 4/11 alle 7 era ancora bassa
        assertEquals(1.0, h[0].ratio!!, 1e-9)           // modello di 29 h: tra 12 e 36, quindi giusto
    }

    @Test
    fun ratioComparesTheModelWithTheNightInterval() {
        val s = session("2026-11-03", 19)
        assertEquals(0.5, RecoveryEstimate.PastResponse(s, 30.0, 15.0, 0.0, 1).ratio!!, 1e-9)     // già la prima notte
        assertEquals(1.0, RecoveryEstimate.PastResponse(s, 30.0, 36.0, 12.0, 2).ratio!!, 1e-9)    // dentro l'intervallo
        assertEquals(2.0, RecoveryEstimate.PastResponse(s, 18.0, 60.0, 36.0, 3).ratio!!, 1e-9)    // ancora bassa dopo 36 h
        // Non ancora tornata dopo 15 h, ma il modello ne prevedeva 20: non dice niente
        assertEquals(null, RecoveryEstimate.PastResponse(s, 20.0, 15.0, 15.0, null).ratio)
        assertEquals(1.0, RecoveryEstimate.personalFactor(listOf(RecoveryEstimate.PastResponse(s, 20.0, 15.0, 15.0, null),
            RecoveryEstimate.PastResponse(s, 30.0, 15.0, 0.0, 1))), 1e-9)
    }

    @Test
    fun hrvBeforeTheShiftIsIgnored() {
        val old = mapOf(LocalDate.of(2026, 10, 18) to 88.0, LocalDate.of(2026, 10, 19) to 90.0,
            LocalDate.of(2026, 10, 20) to 87.0, LocalDate.of(2026, 10, 21) to 70.0)
        assertTrue(RecoveryEstimate.history(listOf(session("2026-10-20", 18)), old, emptyMap(), zone).isEmpty())
        assertEquals(null, RecoveryEstimate.baseline(old, LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 21)))
        assertFalse(HrvData.reliable("2026-10-25"))
        assertTrue(HrvData.reliable("2026-10-26"))
    }

    @Test
    fun personalFactorShrinksTowardsOne() {
        val s = session("2026-11-03", 19)
        val fast = RecoveryEstimate.PastResponse(s, 30.0, 15.0, 0.0, 1)
        assertEquals(1.0, RecoveryEstimate.personalFactor(listOf(fast)), 1e-9)
        assertEquals(0.75, RecoveryEstimate.personalFactor(listOf(fast, fast)), 1e-9)
        val slow = RecoveryEstimate.PastResponse(s, 10.0, 40.0, 40.0, null)
        assertEquals(1.5, RecoveryEstimate.personalFactor(List(10) { slow }), 1e-9)
    }

    @Test
    fun justAfterAWorkout() {
        val s = session("2026-11-10", 18)
        val r = RecoveryEstimate.estimate(listOf(s), hrv("2026-11-10"), emptyMap(), emptyMap(), emptyMap(), at("2026-11-10", 20), zone)
        assertFalse(r.recovered)
        assertEquals(29.1 - 2, r.remainingHours, 0.2)
        assertEquals(s, r.main)
        assertNotNull(r.readyAtMillis)
        assertTrue(r.progress < .1f)
    }

    @Test
    fun lowHrvAfterTheNightKeepsYouRecovering() {
        val s = session("2026-11-09", 18)
        val r = RecoveryEstimate.estimate(listOf(s), hrv("2026-11-09", mapOf("2026-11-10" to 42.0)), emptyMap(), emptyMap(), emptyMap(),
            at("2026-11-10", 12), zone)
        // Modello: 29,1 − 18 = 11,1 h × 1,2; ma almeno fino al risveglio di domani (19 h)
        assertEquals(19.0, r.remainingHours, 0.01)
        assertEquals(false, r.today.hrvNormal)
        assertEquals(1, r.reasons.size)
    }

    @Test
    fun normalHrvShortensTheRecovery() {
        val s = session("2026-11-09", 18)
        val r = RecoveryEstimate.estimate(listOf(s), hrv("2026-11-10"), emptyMap(), emptyMap(), emptyMap(), at("2026-11-10", 12), zone)
        // Al risveglio (7:00) mancavano 29,1 − 13 h; HRV 51 con media 49,9 e dev. std. 1,5 (z = 0,76) → × 0,66; poi 5 h
        assertEquals((29.11 - 13) * 0.6613 - 5, r.remainingHours, 0.15)
        assertEquals(true, r.today.hrvNormal)
    }

    @Test
    fun readyTimeDoesNotMoveDuringTheDay() {
        val s = session("2026-11-09", 18)
        val hrv = hrv("2026-11-09", mapOf("2026-11-10" to 42.0))
        val sleep = mapOf(LocalDate.of(2026, 11, 10) to 300)
        val a = RecoveryEstimate.estimate(listOf(s), hrv, emptyMap(), sleep, emptyMap(), at("2026-11-10", 9), zone)
        val b = RecoveryEstimate.estimate(listOf(s), hrv, emptyMap(), sleep, emptyMap(), at("2026-11-10", 21), zone)
        assertNotNull(a.readyAtMillis)
        assertEquals(a.readyAtMillis, b.readyAtMillis)
        assertEquals(a.totalHours, b.totalHours, 1e-9)
        assertEquals(12.0, a.remainingHours - b.remainingHours, 1e-6)
        // Anche con due allenamenti vicini
        val c = session("2026-11-10", 8)
        val d = session("2026-11-10", 10)
        val e = RecoveryEstimate.estimate(listOf(c, d), emptyMap(), emptyMap(), emptyMap(), emptyMap(), at("2026-11-10", 11), zone)
        val f = RecoveryEstimate.estimate(listOf(c, d), emptyMap(), emptyMap(), emptyMap(), emptyMap(), at("2026-11-10", 23), zone)
        assertEquals(e.readyAtMillis, f.readyAtMillis)
    }

    @Test
    fun followingNightsAreChained() {
        // Sera del 9: notte del 10 con HRV bassa (almeno fino al risveglio dell'11), notte dell'11 nella norma
        val s = session("2026-11-09", 18, TrainingLoad.Effort(8, 8.0, 200.0, 40, true))
        val hrv = hrv("2026-11-09", mapOf("2026-11-10" to 30.0, "2026-11-11" to 60.0))
        val r = RecoveryEstimate.estimate(listOf(s), hrv, emptyMap(), emptyMap(), emptyMap(), at("2026-11-11", 9), zone)
        val model = RecoveryEstimate.modelHours(s.effort)
        // HRV molto bassa il 10 (× 1,3) e molto alta l'11 (× 0,6): al risveglio del 10 (13 h dopo) ne mancano model − 13,
        // al risveglio dell'11 (37 h dopo) quello che resta
        val afterFirst = 13 + (model - 13) * 1.3
        val expectedTotal = 37 + (afterFirst - 37) * 0.6
        assertEquals(expectedTotal, r.totalHours, 0.05)
        assertEquals(2, r.reasons.size)
        assertTrue(r.reasons[0].startsWith("Ieri al risveglio"))
        assertTrue(r.reasons[1].startsWith("Stanotte"))
    }

    @Test
    fun factorsAreGradual() {
        assertEquals(0.86, RecoveryEstimate.hrvFactor(0.0), 1e-9)
        assertEquals(1.0, RecoveryEstimate.hrvFactor(-0.5), 0.02)     // alla soglia della norma non cambia niente
        assertEquals(0.6, RecoveryEstimate.hrvFactor(3.0), 1e-9)
        assertEquals(1.3, RecoveryEstimate.hrvFactor(-5.0), 1e-9)
        // Niente salti: due HRV quasi uguali, ai due lati della soglia, danno quasi lo stesso effetto
        assertEquals(RecoveryEstimate.hrvFactor(-0.49), RecoveryEstimate.hrvFactor(-0.51), 0.01)
        assertTrue(RecoveryEstimate.hrvFactor(-1.0) > RecoveryEstimate.hrvFactor(-0.6))
        assertEquals(0.0, RecoveryEstimate.hrvFloorShare(0.95), 1e-9)
        assertEquals(0.5, RecoveryEstimate.hrvFloorShare(1.1), 1e-9)
        assertEquals(1.0, RecoveryEstimate.hrvFloorShare(1.3), 1e-9)
        assertEquals(1.0, RecoveryEstimate.sleepFactor(480), 1e-9)
        assertEquals(1.075, RecoveryEstimate.sleepFactor(360), 1e-9)
        assertEquals(1.15, RecoveryEstimate.sleepFactor(240), 1e-9)
        assertEquals(1.0, RecoveryEstimate.tempFactor(0.1), 1e-9)
        assertEquals(1.075, RecoveryEstimate.tempFactor(0.4), 1e-9)
        assertEquals(1.15, RecoveryEstimate.tempFactor(0.9), 1e-9)
    }

    @Test
    fun hrvJustBelowTheNormBarelyChangesTheEstimate() {
        val s = session("2026-11-09", 18)
        // Soglia della norma 49,1: con 49,0 l'effetto è ~ +1% e quasi niente "almeno fino a domattina"
        val near = RecoveryEstimate.estimate(listOf(s), hrv("2026-11-09", mapOf("2026-11-10" to 49.0)), emptyMap(), emptyMap(),
            emptyMap(), at("2026-11-10", 12), zone)
        assertEquals((29.11 - 13) * 1.009 - 5, near.remainingHours, 0.2)
        // Con 45 l'effetto è pieno (× 1,3 e almeno fino al risveglio dopo: 19 h)
        val far = RecoveryEstimate.estimate(listOf(s), hrv("2026-11-09", mapOf("2026-11-10" to 45.0)), emptyMap(), emptyMap(),
            emptyMap(), at("2026-11-10", 12), zone)
        assertEquals(19.0, far.remainingHours, 0.01)
        assertTrue(near.reasons[0].contains("vicina alla tua norma"))
        assertTrue(far.reasons[0].contains("si allunga del 30%"))
    }

    @Test
    fun enoughSleepAndNormalTemperatureHaveNoEffect() {
        val s = session("2026-11-09", 18)
        val temp = (3..10).associate { LocalDate.of(2026, 11, it) to 34.8 }
        val r = RecoveryEstimate.estimate(listOf(s), emptyMap(), emptyMap(), mapOf(LocalDate.of(2026, 11, 10) to 460), temp,
            at("2026-11-10", 12), zone)
        assertEquals(29.11 - 18, r.remainingHours, 0.2)
        assertTrue(r.reasons.isEmpty())
    }

    @Test
    fun theNightBeforeTheWorkoutDoesNotCount() {
        val s = session("2026-11-10", 10)
        val r = RecoveryEstimate.estimate(listOf(s), hrv("2026-11-09", mapOf("2026-11-10" to 42.0)), emptyMap(),
            mapOf(LocalDate.of(2026, 11, 10) to 300), emptyMap(), at("2026-11-10", 12), zone)
        assertEquals(29.1 - 2, r.remainingHours, 0.2)
        assertTrue(r.reasons.isEmpty())
        assertEquals(false, r.today.hrvNormal)   // la card la mostra comunque
    }

    @Test
    fun lowHrvFromAnEarlierWorkoutCarriesOver() {
        // Corsa la sera del 9, HRV bassa al risveglio del 10, nuova corsa alle 10 del 10
        val a = session("2026-11-09", 18)
        val b = session("2026-11-10", 10).copy(id = "b")
        val r = RecoveryEstimate.estimate(listOf(a, b), hrv("2026-11-09", mapOf("2026-11-10" to 42.0)), emptyMap(), emptyMap(),
            emptyMap(), at("2026-11-10", 12), zone)
        assertEquals("b", r.main?.id)
        // La prima, per l'HRV bassa, recupera solo al risveglio dell'11 (7:00): 21 h dentro il recupero della seconda
        assertEquals((29.1 - 2) + 0.3 * 21, r.remainingHours, 0.2)
        assertEquals(1, r.reasons.size)
    }

    @Test
    fun nightHrvIsPreferredWhenItCoversTheReliableDays() {
        val day = (0L..60L).associate { LocalDate.of(2026, 9, 15).plusDays(it) to 50.0 }
        val night = (0L..15L).associate { LocalDate.of(2026, 10, 28).plusDays(it) to 40.0 }
        assertTrue(RecoveryEstimate.useNightHrv(day, night))
        assertFalse(RecoveryEstimate.useNightHrv(day, night.filterKeys { it.dayOfMonth % 3 == 0 }))
    }

    @Test
    fun shortSleepAndHighTemperatureSlowDown() {
        val s = session("2026-11-09", 18)
        val temp = (3..10).associate { LocalDate.of(2026, 11, it) to 34.8 } + (LocalDate.of(2026, 11, 10) to 35.4)
        val r = RecoveryEstimate.estimate(listOf(s), emptyMap(), emptyMap(), mapOf(LocalDate.of(2026, 11, 10) to 300), temp,
            at("2026-11-10", 10), zone)
        // Al risveglio (7:00) mancavano 29,1 − 13 h, × 1,15 × 1,15, poi 3 h fino alle 10
        assertEquals((29.1 - 13) * 1.15 * 1.15 - 3, r.remainingHours, 0.3)
        assertEquals(2, r.reasons.size)
    }

    @Test
    fun nothingRecentMeansRecovered() {
        val r = RecoveryEstimate.estimate(listOf(session("2026-11-01", 18)), hrv("2026-11-10"), emptyMap(), emptyMap(), emptyMap(),
            at("2026-11-10", 12), zone)
        assertTrue(r.recovered)
        assertEquals(1f, r.progress)
        assertEquals("id-2026-11-01", r.lastSession?.id)
    }

    @Test
    fun workoutsCloseTogetherAddUp() {
        val a = session("2026-11-10", 8)
        val b = session("2026-11-10", 10)
        val r = RecoveryEstimate.estimate(listOf(a, b), emptyMap(), emptyMap(), emptyMap(), emptyMap(), at("2026-11-10", 12), zone)
        // Conta la seconda (finisce di recuperare dopo); della prima, le 27,1 h che cadono dopo le 10 contano al 30%
        assertEquals((29.1 - 2) + 0.3 * (29.1 - 2), r.remainingHours, 0.3)
    }
}
