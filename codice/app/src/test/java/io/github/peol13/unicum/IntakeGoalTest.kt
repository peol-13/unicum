package io.github.peol13.unicum

import io.github.peol13.unicum.data.ActivityLevel
import io.github.peol13.unicum.data.DayEnergy
import io.github.peol13.unicum.data.OverridableValue
import io.github.peol13.unicum.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntakeGoalTest {

    @Test
    fun la_stima_e_basale_per_livello_di_attivita_meno_deficit() {
        // 1800 × 1,55 = 2790; meno 500 di deficit
        assertEquals(2290, UserSettings.estimateIntakeGoal(1800, ActivityLevel.MODERATO, 500, male = true))
    }

    @Test
    fun un_livello_piu_alto_da_un_obiettivo_piu_alto() {
        val sedentario = UserSettings.estimateIntakeGoal(1800, ActivityLevel.SEDENTARIO, 500, true)
        val molto = UserSettings.estimateIntakeGoal(1800, ActivityLevel.MOLTO_ATTIVO, 500, true)
        assertTrue(molto > sedentario)
    }

    @Test
    fun un_deficit_piu_grande_abbassa_l_obiettivo() {
        val d500 = UserSettings.estimateIntakeGoal(2000, ActivityLevel.LEGGERO, 500, false)
        val d800 = UserSettings.estimateIntakeGoal(2000, ActivityLevel.LEGGERO, 800, false)
        assertEquals(300, d500 - d800)
    }

    @Test
    fun la_stima_non_scende_sotto_il_minimo_di_sicurezza() {
        // 1200 × 1,2 = 1440 − 800 = 640 → si ferma al minimo
        assertEquals(1500, UserSettings.estimateIntakeGoal(1200, ActivityLevel.SEDENTARIO, 800, male = true))
        assertEquals(1200, UserSettings.estimateIntakeGoal(1200, ActivityLevel.SEDENTARIO, 800, male = false))
    }

    @Test
    fun l_attivita_del_giorno_non_cambia_le_calorie_assumibili() {
        val goal = 2200
        val eaten = 1500
        // le calorie ancora assumibili non hanno nessun parametro legato all'attività
        assertEquals(700, DayEnergy.remaining(goal, eaten))
    }

    @Test
    fun l_attivita_aumenta_solo_il_deficit() {
        val senza = DayEnergy.deficit(eatenKcal = 1500, restingBurnKcal = 1900, extraBurnKcal = 0)
        val con = DayEnergy.deficit(eatenKcal = 1500, restingBurnKcal = 1900, extraBurnKcal = 450)
        assertEquals(400, senza)
        assertEquals(850, con)
        assertEquals(DayEnergy.remaining(2200, 1500), DayEnergy.remaining(2200, 1500))
    }

    @Test
    fun il_deficit_del_giorno_e_bruciate_meno_assunte() {
        assertEquals(650, DayEnergy.deficit(eatenKcal = 1850, restingBurnKcal = 2000, extraBurnKcal = 500))
        assertEquals(-100, DayEnergy.deficit(eatenKcal = 2100, restingBurnKcal = 2000, extraBurnKcal = 0))
    }

    @Test
    fun di_default_vale_la_stima() {
        val v = OverridableValue(defaultManual = 1800)
        assertFalse(v.isManual)
        assertEquals(2290, v.resolve(estimate = 2290))
    }

    @Test
    fun un_valore_a_mano_non_viene_ricalcolato_da_solo() {
        val v = OverridableValue(defaultManual = 1800)
        v.set(2400, 800..6000)
        // la stima cambia (es. cambia il peso) ma il valore a mano resta
        assertEquals(2400, v.resolve(estimate = 2290))
        assertEquals(2400, v.resolve(estimate = 2500))
    }

    @Test
    fun ricalcola_stima_riporta_al_valore_automatico() {
        val v = OverridableValue(defaultManual = 1800)
        v.set(2400, 800..6000)
        v.set(null, 800..6000)
        assertFalse(v.isManual)
        assertEquals(2290, v.resolve(estimate = 2290))
        // il numero a mano resta memorizzato ma non è in uso
        assertEquals(2400, v.manualValue)
    }

    @Test
    fun il_valore_a_mano_e_limitato_all_intervallo() {
        val v = OverridableValue(defaultManual = 1800)
        v.set(99999, 800..6000)
        assertEquals(6000, v.manualValue)
    }
}
