package io.github.peol13.unicum

import io.github.peol13.unicum.data.Activity
import io.github.peol13.unicum.data.DayData
import io.github.peol13.unicum.data.Meal
import org.junit.Assert.assertEquals
import org.junit.Test

class DayDataTest {

    private val pasta = Meal("Pasta", calories = 350, mealType = "Pranzo", quantityGrams = 100)
    private val mela = Meal("Mela", calories = 52, mealType = "Colazione", quantityGrams = 100)
    private val corsa = Activity("Corsa", durationMinutes = 30, calories = 250)
    private val nuoto = Activity("Nuoto", durationMinutes = 45, calories = 300)

    @Test
    fun giornata_vuota_ha_tutti_i_totali_a_zero() {
        val day = DayData()

        assertEquals(0, day.caloriesConsumed)
        assertEquals(0, day.mealCount)
        assertEquals(0, day.activityMinutes)
        assertEquals(0, day.activityCalories)
        assertEquals(0, day.activityCount)
    }

    @Test
    fun somma_le_calorie_dei_pasti() {
        val day = DayData(meals = mutableListOf(pasta, mela))

        assertEquals(402, day.caloriesConsumed)
    }

    @Test
    fun conta_i_pasti() {
        val day = DayData(meals = mutableListOf(pasta, mela, pasta))

        assertEquals(3, day.mealCount)
    }

    @Test
    fun somma_minuti_e_calorie_delle_attivita() {
        val day = DayData(activities = mutableListOf(corsa, nuoto))

        assertEquals(75, day.activityMinutes)
        assertEquals(550, day.activityCalories)
        assertEquals(2, day.activityCount)
    }

    @Test
    fun i_totali_seguono_le_modifiche_alle_liste() {
        val day = DayData()

        day.meals.add(pasta)
        day.activities.add(corsa)
        assertEquals(350, day.caloriesConsumed)
        assertEquals(250, day.activityCalories)

        day.meals.remove(pasta)
        day.activities.clear()
        assertEquals(0, day.caloriesConsumed)
        assertEquals(0, day.activityCalories)
    }

    @Test
    fun pasti_e_attivita_non_si_influenzano_a_vicenda() {
        val day = DayData(meals = mutableListOf(pasta), activities = mutableListOf(corsa))

        assertEquals(350, day.caloriesConsumed)
        assertEquals(250, day.activityCalories)
    }
}
