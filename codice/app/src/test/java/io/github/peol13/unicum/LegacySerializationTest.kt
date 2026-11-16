package io.github.peol13.unicum

import io.github.peol13.unicum.data.Activity
import io.github.peol13.unicum.data.Meal
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La vecchia versione dell'app salvava pasti e attività come JSON nelle SharedPreferences,
 * senza id. La migrazione verso Room deve poter leggere ancora quei dati.
 */
class LegacySerializationTest {

    @Test
    fun legge_un_pasto_salvato_dalla_vecchia_versione_senza_id() {
        val json = """
            [{"name":"Pasta","calories":350,"mealType":"Pranzo","quantityGrams":100,
              "protein":13.0,"carbohydrates":72.0,"fats":1.5}]
        """.trimIndent()

        val meals = Json.decodeFromString<List<Meal>>(json)

        assertEquals(1, meals.size)
        assertEquals("Pasta", meals[0].name)
        assertEquals(350, meals[0].calories)
        assertEquals("Pranzo", meals[0].mealType)
        assertEquals(100, meals[0].quantityGrams)
        assertEquals(13f, meals[0].protein, 0.001f)
        assertEquals(72f, meals[0].carbohydrates, 0.001f)
        assertEquals(1.5f, meals[0].fats, 0.001f)
        assertEquals(0L, meals[0].id)
    }

    @Test
    fun un_pasto_con_solo_i_campi_obbligatori_usa_i_valori_di_default() {
        val meals = Json.decodeFromString<List<Meal>>("""[{"name":"Mela","calories":52}]""")

        assertEquals("Altro", meals[0].mealType)
        assertEquals(0, meals[0].quantityGrams)
        assertEquals(0f, meals[0].protein, 0.001f)
    }

    @Test
    fun legge_un_allenamento_salvato_dalla_vecchia_versione_senza_id() {
        val json = """[{"type":"Corsa","durationMinutes":30,"calories":250}]"""

        val activities = Json.decodeFromString<List<Activity>>(json)

        assertEquals(1, activities.size)
        assertEquals("Corsa", activities[0].type)
        assertEquals(30, activities[0].durationMinutes)
        assertEquals(250, activities[0].calories)
        assertEquals(0L, activities[0].id)
    }

    @Test
    fun una_lista_vuota_viene_letta_come_lista_vuota() {
        assertEquals(0, Json.decodeFromString<List<Meal>>("[]").size)
        assertEquals(0, Json.decodeFromString<List<Activity>>("[]").size)
    }

    @Test
    fun scrittura_e_lettura_restituiscono_gli_stessi_dati() {
        val meals = listOf(Meal("Riso", 360, "Cena", 80, 7f, 78f, 0.9f, id = 5))
        val activities = listOf(Activity("Nuoto", 45, 300, id = 9))

        assertEquals(meals, Json.decodeFromString<List<Meal>>(Json.encodeToString(meals)))
        assertEquals(activities, Json.decodeFromString<List<Activity>>(Json.encodeToString(activities)))
    }
}
