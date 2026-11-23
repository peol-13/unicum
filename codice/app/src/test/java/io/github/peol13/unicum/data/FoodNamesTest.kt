package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.AppLanguage
import io.github.peol13.unicum.i18n.I18n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ogni alimento del catalogo e ogni porzione media ha il nome in tutte le lingue (tabella foods.psv). */
class FoodNamesTest {

    @After fun reset() = I18n.setForTest(AppLanguage.IT)

    @Test
    fun everyCatalogFoodIsTranslated() {
        val missing = FoodCatalog.defaultFoods.map { it.name }.filter { it !in FoodNames.table }
        assertTrue("Alimenti senza traduzione:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun everyPortionIsTranslated() {
        val labels = FoodCatalog.defaultFoods.flatMap { FoodPortions.of(it) }.map { it.label }.toSet()
        val missing = labels.filter { it !in FoodNames.table }
        assertTrue("Porzioni senza traduzione:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun noStaleRows() {
        val known = FoodCatalog.defaultFoods.map { it.name }.toSet() +
            FoodCatalog.defaultFoods.flatMap { FoodPortions.of(it) }.map { it.label }
        val stale = FoodNames.table.keys.filter { it !in known }
        assertTrue("Righe di foods.psv che non corrispondono a niente:\n" + stale.joinToString("\n"), stale.isEmpty())
    }

    @Test
    fun searchFindsTranslatedAndItalianNames() {
        I18n.setForTest(AppLanguage.EN)
        assertEquals("Apple", FoodNames.label("Mela"))
        assertTrue(FoodCatalog.search("chicken breast").any { it.name == "Petto di pollo crudo" })
        assertTrue("anche in italiano", FoodCatalog.search("pollo").any { it.name == "Petto di pollo crudo" })
        assertEquals("i nomi personali restano", "Torta della nonna", FoodNames.label("Torta della nonna"))
        I18n.setForTest(AppLanguage.IT)
        assertEquals("Mela", FoodNames.label("Mela"))
    }
}
