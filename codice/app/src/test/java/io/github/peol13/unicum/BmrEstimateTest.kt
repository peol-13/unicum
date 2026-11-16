package io.github.peol13.unicum

import io.github.peol13.unicum.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BmrEstimateTest {

    @Test
    fun uomo_di_30_anni_80kg_180cm() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780, UserSettings.estimateBmr(male = true, weightKg = 80, heightCm = 180, age = 30))
    }

    @Test
    fun donna_di_30_anni_60kg_165cm() {
        // 10*60 + 6.25*165 - 5*30 - 161 = 1320.25
        assertEquals(1320, UserSettings.estimateBmr(male = false, weightKg = 60, heightCm = 165, age = 30))
    }

    @Test
    fun a_parita_di_dati_l_uomo_ha_un_metabolismo_piu_alto() {
        val m = UserSettings.estimateBmr(true, 70, 175, 40)
        val f = UserSettings.estimateBmr(false, 70, 175, 40)
        assertEquals(166, m - f)
    }

    @Test
    fun piu_eta_meno_metabolismo_piu_peso_piu_metabolismo() {
        assertTrue(UserSettings.estimateBmr(true, 70, 175, 50) < UserSettings.estimateBmr(true, 70, 175, 30))
        assertTrue(UserSettings.estimateBmr(true, 80, 175, 30) > UserSettings.estimateBmr(true, 70, 175, 30))
    }
}
