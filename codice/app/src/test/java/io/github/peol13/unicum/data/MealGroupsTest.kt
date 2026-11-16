package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealGroupsTest {
    private fun meal(type: String, minute: Int?) = Meal("x", 100, mealType = type, minuteOfDay = minute)

    @Test
    fun nomeProprioDiAltro() {
        assertEquals("Spuntino mattina", MealTime.compose("Altro", "  spuntino   mattina "))
        assertEquals("Altro", MealTime.compose("Altro", "   "))
        assertEquals("Pranzo", MealTime.compose("Pranzo", "ignorato"))
        assertEquals("Altro", MealTime.baseType("Spuntino mattina"))
        assertEquals("Cena", MealTime.baseType("cena"))
        assertEquals("Spuntino mattina", MealTime.customName("Spuntino mattina"))
        assertNull(MealTime.customName("Altro"))
        assertNull(MealTime.customName("Pranzo"))
    }

    @Test
    fun dueSpuntiniSeparatiInOrdineDiOrario() {
        val meals = listOf(
            meal("Colazione", 7 * 60),
            meal("Spuntino pomeriggio", 17 * 60),
            meal("Spuntino mattina", 10 * 60 + 30),
            meal("spuntino mattina", 10 * 60 + 40),
            meal("Pranzo", 13 * 60)
        )
        val groups = MealTime.groups(meals, withEmptyOther = false)
        assertEquals(
            listOf("Colazione", "Spuntino mattina", "Pranzo", "Spuntino pomeriggio", "Cena"),
            groups.map { it.first }
        )
        assertEquals(2, groups[1].second.size)
        // Con withEmptyOther si aggiunge in fondo un "Altro" vuoto per un nuovo spuntino
        assertEquals("Altro", MealTime.groups(meals, withEmptyOther = true).last().first)
    }

    @Test
    fun altroSenzaNomeNonSiDuplica() {
        val groups = MealTime.groups(listOf(meal("Altro", 16 * 60)), withEmptyOther = true)
        assertEquals(listOf("Colazione", "Pranzo", "Altro", "Cena"), groups.map { it.first })
    }
}
