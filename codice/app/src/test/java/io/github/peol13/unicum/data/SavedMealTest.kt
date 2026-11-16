package io.github.peol13.unicum.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedMealTest {
    private val latte = Food("Latte parzialmente scremato", 46, 3.3f, 5f, 1.6f)
    private val avena = Food("Fiocchi d'avena", 372, 13f, 60f, 7f)
    private val colazione = SavedMeal("Colazione", listOf(DraftItem(latte, 200), DraftItem(avena, 50)))

    @Test fun caloriesSumPortions() {
        assertEquals(92 + 186, colazione.calories)
    }

    @Test fun upsertReplacesSameNameIgnoringCase() {
        val updated = SavedMeal("colazione", listOf(DraftItem(latte, 250)))
        val list = SavedMeal.upsert(listOf(colazione, SavedMeal("Spuntino", emptyList())), updated)
        assertEquals(listOf("colazione", "Spuntino"), list.map { it.name })
        assertEquals(250, list.first().items.single().grams)
    }

    @Test fun removeAndMerge() {
        val spuntino = SavedMeal("Spuntino", listOf(DraftItem(avena, 30)))
        assertEquals(listOf("Spuntino"), SavedMeal.remove(listOf(colazione, spuntino), "COLAZIONE").map { it.name })
        // Dal backup entra solo quello che manca
        val merged = SavedMeal.merge(listOf(colazione), listOf(SavedMeal("Colazione", emptyList()), spuntino))
        assertEquals(listOf("Colazione", "Spuntino"), merged.map { it.name })
        assertEquals(2, merged.first().items.size)
    }

    @Test fun searchIgnoresAccentsAndCase() {
        val list = listOf(colazione, SavedMeal("Pranzo veloce", emptyList()))
        assertEquals(2, SavedMeal.search(list, "").size)
        assertEquals(listOf("Pranzo veloce"), SavedMeal.search(list, "VELOCE").map { it.name })
        assertEquals(listOf("Colazione"), SavedMeal.search(list, "colazióne").map { it.name })
    }

    @Test fun jsonRoundTrip() {
        val json = Json.encodeToString(listOf(colazione))
        assertEquals(listOf(colazione), Json.decodeFromString<List<SavedMeal>>(json))
    }
}
