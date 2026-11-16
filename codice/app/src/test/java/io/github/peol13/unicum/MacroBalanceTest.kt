package io.github.peol13.unicum

import io.github.peol13.unicum.data.Macro
import io.github.peol13.unicum.data.MacroBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroBalanceTest {

    private val goalPct = Triple(25, 45, 30)
    private val goalGrams = Triple(100, 180, 60)

    @Test
    fun le_percentuali_sommano_sempre_100() {
        val p = MacroBalance.percentages(listOf(33.3f, 33.3f, 33.3f))!!
        assertEquals(100, p.sum())
        val q = MacroBalance.percentages(listOf(1f, 1f, 1f))!!
        assertEquals(100, q.sum())
    }

    @Test
    fun senza_cibo_non_ci_sono_percentuali() {
        assertNull(MacroBalance.percentages(listOf(0f, 0f, 0f)))
    }

    @Test
    fun le_quote_si_calcolano_sulle_calorie_dei_macro() {
        // 50 g proteine = 200 kcal, 100 g carbo = 400 kcal, 0 g grassi → 33% / 67% / 0%
        val s = MacroBalance.shares(50f, 100f, 0f, goalPct, goalGrams)
        assertEquals(listOf(33, 67, 0), s.map { it.effectivePct })
    }

    @Test
    fun i_grassi_pesano_nove_kcal_al_grammo() {
        // 10 g di grassi = 90 kcal; 22.5 g di carbo = 90 kcal → 50% e 50%
        val s = MacroBalance.shares(0f, 22.5f, 10f, goalPct, goalGrams)
        assertEquals(50, s[1].effectivePct)
        assertEquals(50, s[2].effectivePct)
    }

    @Test
    fun la_differenza_e_effettivo_meno_consigliato() {
        val s = MacroBalance.shares(50f, 100f, 0f, goalPct, goalGrams)
        assertEquals(33 - 25, s[0].deltaPct)
        assertEquals(0 - 30, s[2].deltaPct)
    }

    @Test
    fun i_grammi_mancanti_non_sono_mai_negativi() {
        val s = MacroBalance.shares(150f, 0f, 0f, goalPct, goalGrams)
        assertEquals(0, s[0].missingGrams)
        assertEquals(180, s[1].missingGrams)
    }

    @Test
    fun il_suggerimento_dice_cosa_aumentare_e_cosa_diminuire() {
        // pochi grassi, troppi carboidrati
        val s = MacroBalance.shares(25f, 150f, 5f, goalPct, goalGrams)
        val hint = MacroBalance.nextMealHint(s, remainingKcal = 800)
        assertTrue(hint, hint.contains("più"))
        assertTrue(hint, hint.contains("meno carboidrati"))
        assertTrue(hint, hint.contains("grassi"))
    }

    @Test
    fun una_ripartizione_in_linea_non_chiede_cambi() {
        // 25 / 45 / 30 esatti in kcal: 100 kcal P, 180 kcal C, 120 kcal G
        val s = MacroBalance.shares(25f, 45f, 120f / 9f, goalPct, goalGrams)
        val hint = MacroBalance.nextMealHint(s, remainingKcal = 800)
        assertTrue(hint, hint.contains("in linea"))
    }

    @Test
    fun senza_pasti_invita_a_registrare() {
        val s = MacroBalance.shares(0f, 0f, 0f, goalPct, goalGrams)
        assertTrue(MacroBalance.nextMealHint(s, 1500).contains("Registra"))
    }

    @Test
    fun con_obiettivo_calorico_raggiunto_consiglia_cibi_leggeri() {
        val s = MacroBalance.shares(25f, 150f, 5f, goalPct, goalGrams)
        assertTrue(MacroBalance.nextMealHint(s, remainingKcal = 0).contains("leggeri"))
    }

    @Test
    fun scostamento_massimo() {
        val s = MacroBalance.shares(0f, 0f, 10f, goalPct, goalGrams)
        assertEquals(70, MacroBalance.maxDeviation(s))
        assertEquals(Macro.GRASSI, s[2].macro)
    }
}
