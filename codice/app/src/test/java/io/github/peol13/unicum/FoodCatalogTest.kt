package io.github.peol13.unicum

import io.github.peol13.unicum.data.FoodCatalog
import io.github.peol13.unicum.data.UnicumRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FoodCatalogTest {

    private val foods = FoodCatalog.defaultFoods

    @Test
    fun il_catalogo_non_e_vuoto() {
        assertTrue(foods.isNotEmpty())
    }

    @Test
    fun i_nomi_degli_alimenti_sono_unici_e_non_vuoti() {
        assertTrue(foods.all { it.name.isNotBlank() })
        assertEquals(foods.size, foods.map { it.name }.toSet().size)
    }

    @Test
    fun i_valori_nutrizionali_non_sono_negativi() {
        foods.forEach {
            assertTrue("${it.name}: kcal negative", it.caloriesPer100g >= 0)
            assertTrue("${it.name}: proteine negative", it.proteinPer100g >= 0f)
            assertTrue("${it.name}: carboidrati negativi", it.carbsPer100g >= 0f)
            assertTrue("${it.name}: grassi negativi", it.fatsPer100g >= 0f)
        }
    }

    @Test
    fun le_calorie_sono_coerenti_con_i_macronutrienti() {
        // 4 kcal/g per proteine e carboidrati, 9 kcal/g per i grassi.
        // Tolleranza del 15% (o 12 kcal per gli alimenti poverissimi di calorie, dove contano
        // le fibre). Gli alcolici sono esclusi: l'alcol apporta 7 kcal/g e non è un macro.
        foods.filter { it.category != "Alcolici" }.forEach {
            val stimate = 4 * it.proteinPer100g + 4 * it.carbsPer100g + 9 * it.fatsPer100g
            val scarto = abs(stimate - it.caloriesPer100g)
            assertTrue(
                "${it.name}: ${it.caloriesPer100g} kcal dichiarate contro ${stimate} stimate",
                scarto <= maxOf(it.caloriesPer100g * 0.15f, 12f)
            )
        }
    }

    @Test
    fun la_data_di_oggi_ha_il_formato_anno_mese_giorno() {
        assertTrue(UnicumRepository.today().matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }
}
