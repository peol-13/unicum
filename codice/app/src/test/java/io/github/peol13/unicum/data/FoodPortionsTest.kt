package io.github.peol13.unicum.data

import org.junit.Assert.assertTrue
import org.junit.Test

class FoodPortionsTest {
    @Test
    fun everyPortionBelongsToACatalogFood() {
        val catalog = FoodCatalog.defaultFoods.map { it.name }.toSet()
        val missing = FoodPortions.names - catalog
        assertTrue("Porzioni per alimenti inesistenti: $missing", missing.isEmpty())
    }
}
