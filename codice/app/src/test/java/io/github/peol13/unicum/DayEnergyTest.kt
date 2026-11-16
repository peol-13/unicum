package io.github.peol13.unicum

import io.github.peol13.unicum.data.DayEnergy
import org.junit.Assert.assertEquals
import org.junit.Test

class DayEnergyTest {

    @Test
    fun il_deficit_e_bruciate_meno_assunte() {
        assertEquals(650, DayEnergy.deficit(eatenKcal = 1850, restingBurnKcal = 2000, extraBurnKcal = 500))
    }

    @Test
    fun se_si_mangia_piu_di_quanto_si_brucia_il_deficit_e_negativo() {
        assertEquals(-300, DayEnergy.deficit(eatenKcal = 2300, restingBurnKcal = 2000, extraBurnKcal = 0))
    }

    @Test
    fun l_attivita_aumenta_il_deficit_e_non_le_calorie_assumibili() {
        val senza = DayEnergy.deficit(1500, 1700, 0)
        val con = DayEnergy.deficit(1500, 1700, 400)
        assertEquals(senza + 400, con)
    }

    @Test
    fun extra_senza_calorie_attive_usa_solo_gli_allenamenti() {
        assertEquals(300, DayEnergy.extraBurn(null, importedWorkoutKcal = 0, manualWorkoutKcal = 300))
    }

    @Test
    fun le_calorie_attive_comprendono_gli_allenamenti_importati() {
        // 600 kcal attive in totale, di cui 250 sono un allenamento importato: non si sommano
        assertEquals(600, DayEnergy.extraBurn(600.0, importedWorkoutKcal = 250, manualWorkoutKcal = 0))
    }

    @Test
    fun se_gli_allenamenti_importati_superano_le_attive_vale_il_maggiore() {
        assertEquals(400, DayEnergy.extraBurn(100.0, importedWorkoutKcal = 400, manualWorkoutKcal = 0))
    }

    @Test
    fun gli_allenamenti_manuali_si_aggiungono_sempre() {
        assertEquals(850, DayEnergy.extraBurn(600.0, importedWorkoutKcal = 250, manualWorkoutKcal = 250))
    }

    @Test
    fun peso_stimato_dal_deficit_della_settimana() {
        assertEquals("−0,5 kg", DayEnergy.weightChangeText(DayEnergy.weightChangeKg(3850)))
        assertEquals("+0,05 kg", DayEnergy.weightChangeText(DayEnergy.weightChangeKg(-400)))
    }
}
