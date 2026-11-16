package io.github.peol13.unicum

import io.github.peol13.unicum.data.HealthData
import io.github.peol13.unicum.data.MetricType
import io.github.peol13.unicum.data.SleepNight
import io.github.peol13.unicum.data.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthModelTest {

    private fun night(duration: Int, inBedMinutes: Int, deep: Int = 0, rem: Int = 0, light: Int = 0) = SleepNight(
        date = "2026-10-16",
        startMillis = 0L,
        endMillis = inBedMinutes * 60_000L,
        durationMinutes = duration,
        deepMinutes = deep, remMinutes = rem, lightMinutes = light, awakeMinutes = 0,
        source = "com.ultrahuman.android"
    )

    @Test
    fun efficienza_e_il_sonno_effettivo_sul_tempo_a_letto() {
        assertEquals(90, night(duration = 450, inBedMinutes = 500).efficiencyPercent)
    }

    @Test
    fun efficienza_non_supera_100_e_non_divide_per_zero() {
        assertEquals(100, night(duration = 600, inBedMinutes = 500).efficiencyPercent)
        assertEquals(0, night(duration = 60, inBedMinutes = 0).efficiencyPercent)
    }

    @Test
    fun le_fasi_ci_sono_solo_se_la_fonte_le_fornisce() {
        assertFalse(night(duration = 400, inBedMinutes = 420).hasStages)
        assertTrue(night(duration = 400, inBedMinutes = 420, deep = 80, rem = 90, light = 230).hasStages)
    }

    @Test
    fun ultrahuman_ha_la_precedenza_su_samsung_health() {
        val ultrahuman = SourceInfo.rank("com.ultrahuman.android")
        val samsung = SourceInfo.rank("com.sec.android.app.shealth")
        val altro = SourceInfo.rank("com.example.altro")
        assertTrue(ultrahuman < samsung)
        assertTrue(samsung < altro)
    }

    @Test
    fun i_nomi_delle_fonti_sono_leggibili() {
        assertEquals("Ultrahuman", SourceInfo.label("com.ultrahuman.android"))
        assertEquals("Samsung Health", SourceInfo.label("com.sec.android.app.shealth"))
        assertEquals("Manuale", SourceInfo.label("manual"))
    }

    @Test
    fun metrica_giornaliera_assente_restituisce_null() {
        val data = HealthData(metrics = mapOf(MetricType.STEPS to mapOf("2026-10-16" to 8000.0)))
        assertEquals(8000.0, data.metric(MetricType.STEPS, "2026-10-16")!!, 0.0)
        assertNull(data.metric(MetricType.STEPS, "2026-10-15"))
        assertNull(data.metric(MetricType.HRV, "2026-10-16"))
    }
}
