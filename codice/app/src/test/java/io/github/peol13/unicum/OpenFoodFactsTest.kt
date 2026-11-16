package io.github.peol13.unicum

import io.github.peol13.unicum.data.OpenFoodFacts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsTest {

    // Risposta reale (accorciata) della ricerca "nutella": il primo risultato non ha i valori nutrizionali
    private val sample = """
    {"hits":[
      {"code":"2000000120663","brands":["Ferrero"],"product_name":"nutella"},
      {"code":"0000844112871","brands":["Nutella"],"nutriments":{"carbohydrates_100g":57.5,"energy-kcal_100g":533,"fat_100g":30.9,"proteins_100g":6.3},"product_name":"Nutella"},
      {"code":"3017620422003","brands":["Ferrero","Nutella"],"nutriments":{"carbohydrates_100g":57.5,"energy-kcal_100g":539,"fat_100g":30.9,"proteins_100g":6.3},"product_name":"Nutella"},
      {"code":"18759587","brands":["Nutella"],"nutriments":{"carbohydrates_100g":57.5,"energy-kcal_100g":539,"fat_100g":30.9,"proteins_100g":6.3},"product_name":"Nutella"}
    ],"page":1}
    """.trimIndent()

    @Test
    fun scarta_i_prodotti_senza_valori_nutrizionali() {
        val foods = OpenFoodFacts.parseSearch(sample)
        assertTrue(foods.none { it.barcode == "2000000120663" })
    }

    @Test
    fun unisce_i_doppioni_con_lo_stesso_nome() {
        val foods = OpenFoodFacts.parseSearch(sample)
        // "Nutella" di marca Nutella compare due volte con lo stesso nome: ne resta una
        assertEquals(2, foods.size)
        assertEquals(foods.size, foods.map { it.name.lowercase() }.toSet().size)
    }

    @Test
    fun legge_valori_per_100_grammi_marca_e_codice() {
        val f = OpenFoodFacts.parseSearch(sample).first { it.barcode == "3017620422003" }
        assertEquals(539, f.caloriesPer100g)
        assertEquals(6.3f, f.proteinPer100g, 0.01f)
        assertEquals(57.5f, f.carbsPer100g, 0.01f)
        assertEquals(30.9f, f.fatsPer100g, 0.01f)
        assertEquals("Ferrero", f.brand)
        assertEquals(OpenFoodFacts.CATEGORY, f.category)
    }

    @Test
    fun il_nome_contiene_la_marca_solo_se_manca() {
        assertEquals("Biscotti (Mulino Bianco)", OpenFoodFacts.displayName("biscotti", "Mulino Bianco"))
        assertEquals("Nutella", OpenFoodFacts.displayName("Nutella", "Nutella"))
        assertEquals("Pasta", OpenFoodFacts.displayName("Pasta", null))
    }

    @Test
    fun converte_i_kilojoule_se_mancano_le_kcal() {
        val json = """{"hits":[{"code":"1","product_name":"Test","nutriments":{"energy_100g":2092,"proteins_100g":10,"carbohydrates_100g":70,"fat_100g":1}}]}"""
        assertEquals(500, OpenFoodFacts.parseSearch(json).single().caloriesPer100g)
    }

    @Test
    fun scarta_valori_impossibili() {
        val kcalAlte = """{"hits":[{"code":"1","product_name":"A","nutriments":{"energy-kcal_100g":5000,"proteins_100g":1,"carbohydrates_100g":1,"fat_100g":1}}]}"""
        val macroOltre100 = """{"hits":[{"code":"2","product_name":"B","nutriments":{"energy-kcal_100g":300,"proteins_100g":60,"carbohydrates_100g":60,"fat_100g":30}}]}"""
        val negativi = """{"hits":[{"code":"3","product_name":"C","nutriments":{"energy-kcal_100g":300,"proteins_100g":-5,"carbohydrates_100g":10,"fat_100g":1}}]}"""
        assertTrue(OpenFoodFacts.parseSearch(kcalAlte).isEmpty())
        assertTrue(OpenFoodFacts.parseSearch(macroOltre100).isEmpty())
        assertTrue(OpenFoodFacts.parseSearch(negativi).isEmpty())
    }

    @Test
    fun scarta_prodotti_senza_nome_o_senza_codice() {
        val json = """{"hits":[
            {"code":"1","product_name":"  ","nutriments":{"energy-kcal_100g":100,"proteins_100g":1,"carbohydrates_100g":1,"fat_100g":1}},
            {"product_name":"Senza codice","nutriments":{"energy-kcal_100g":100,"proteins_100g":1,"carbohydrates_100g":1,"fat_100g":1}}
        ]}"""
        assertTrue(OpenFoodFacts.parseSearch(json).isEmpty())
    }

    @Test
    fun risposta_vuota_o_senza_hits_non_da_errori() {
        assertTrue(OpenFoodFacts.parseSearch("""{"hits":[]}""").isEmpty())
        assertTrue(OpenFoodFacts.parseSearch("""{"page":1}""").isEmpty())
    }

    @Test
    fun marca_come_testo_separato_da_virgole() {
        val json = """{"hits":[{"code":"9","brands":"Barilla, Altro","product_name":"Penne","nutriments":{"energy-kcal_100g":350,"proteins_100g":12,"carbohydrates_100g":72,"fat_100g":1.5}}]}"""
        assertEquals("Barilla", OpenFoodFacts.parseSearch(json).single().brand)
    }

    @Test
    fun almeno_un_macro_deve_esserci() {
        val json = """{"hits":[{"code":"1","product_name":"Solo kcal","nutriments":{"energy-kcal_100g":100}}]}"""
        assertTrue(OpenFoodFacts.parseSearch(json).isEmpty())
        assertNull(JSONObject(json).optJSONArray("hits")?.optJSONObject(0)?.optJSONObject("nutriments")?.optJSONObject("x"))
    }

    @Test
    fun `fibre zuccheri e sale, il sale anche dal sodio`() {
        val json = """{"hits":[{"code":"5","product_name":"Crackers","nutriments":{"energy-kcal_100g":420,"proteins_100g":10,"carbohydrates_100g":70,"fat_100g":10,"fiber_100g":4.5,"sugars_100g":3,"sodium_100g":0.8}}]}"""
        val food = OpenFoodFacts.parseSearch(json).single()
        assertEquals(4.5f, food.fiberPer100g!!, 0.001f)
        assertEquals(3f, food.sugarsPer100g!!, 0.001f)
        assertEquals(2f, food.saltPer100g!!, 0.001f)
        // in 50 g la metà
        val portion = food.portion(50)
        assertEquals(2.25f, portion.fiber!!, 0.001f)
        assertEquals(1f, portion.salt!!, 0.001f)
        // e cambiando la quantità si ricalcolano
        assertEquals(4.5f, portion.withGrams(100).fiber!!, 0.001f)
    }

    @Test
    fun `senza fibre il dato resta vuoto`() {
        val json = """{"hits":[{"code":"6","product_name":"Pane","nutriments":{"energy-kcal_100g":250,"proteins_100g":8,"carbohydrates_100g":50,"fat_100g":2}}]}"""
        val food = OpenFoodFacts.parseSearch(json).single()
        assertNull(food.fiberPer100g)
        assertNull(food.portion(100).fiber)
    }
}
