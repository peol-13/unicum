package io.github.peol13.unicum.data

import io.github.peol13.unicum.data.db.customFoodKey
import io.github.peol13.unicum.data.db.toCustomEntity
import io.github.peol13.unicum.data.db.toFood
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomFoodEntityTest {

    @Test
    fun `un alimento personale torna uguale dopo il salvataggio`() {
        val food = Food(
            "Torta della nonna", 380, 6f, 52f, 17f, category = "Dolci", brand = "Casa", barcode = "123",
            fiberPer100g = 1.5f, sugarsPer100g = 30f, saltPer100g = 0.2f
        )
        assertEquals(food, food.toCustomEntity(1000L).toFood())
        // Senza i dati facoltativi
        val plain = Food("Mio", 100)
        assertEquals(plain, plain.toCustomEntity(5L).toFood())
    }

    @Test
    fun `la chiave ignora maiuscole e spazi ai lati`() {
        assertEquals("pasta al forno", customFoodKey("  Pasta al Forno "))
        val e = Food("  Pasta al Forno ", 150).toCustomEntity(0L)
        assertEquals("pasta al forno", e.nameKey)
        assertEquals("Pasta al Forno", e.name)
    }
}
