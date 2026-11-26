package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.AppLanguage
import io.github.peol13.unicum.i18n.I18n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchTest {

    @After fun reset() = I18n.setForTest(AppLanguage.IT)

    private fun names(q: String, extra: List<Food> = emptyList()) = FoodCatalog.search(q, extra = extra).map { it.name }
    private fun food(name: String, category: String = "Generico") = Food(name, 100, category = category)
    private fun cost(q: String, name: String) =
        FoodSearch.score(FoodSearch.Query(FoodCatalog.normalize(q)), FoodSearch.Entry(food(name), FoodCatalog.normalize(name), name.length))

    @Test
    fun formeDelleParole() {
        assertEquals("mel", FoodSearch.form("mele").base)
        assertEquals("fung", FoodSearch.form("funghi").base)
        assertEquals("aranc", FoodSearch.form("arancia").base)
        assertEquals("aranc", FoodSearch.form("arance").base)
        assertEquals("le parole corte restano come sono", "uva", FoodSearch.form("uva").base)
    }

    @Test
    fun singolareEPluraleSonoLaStessaParola() {
        assertEquals(0, cost("mele", "Mela"))
        assertEquals(0, cost("pomodoro", "Pomodori pelati"))
        assertEquals(0, cost("fungo", "Funghi porcini"))
        assertEquals(0, cost("pesche", "Pesca"))
        assertEquals(0, cost("arance", "Arancia"))
        assertEquals(0, cost("uovo", "Uova intere"))
    }

    @Test
    fun paroleSimiliMaDiverseNonSiConfondono() {
        assertTrue("pesce non è la pesca", cost("pesce", "Pesca") != 0)
        assertTrue("parmigiano non è la parmigiana", cost("parmigiano", "Parmigiana di melanzane") > 0)
        assertEquals("pollo non trova cipolle", -1, cost("pollo", "Cipolle"))
        assertEquals(0, cost("parmigiano", "Parmigiano reggiano"))
    }

    @Test
    fun mentreSiScrive() {
        assertEquals(1, cost("pomod", "Pomodori"))
        assertTrue(names("pomod").take(3).contains("Pomodori"))
    }

    @Test
    fun articoliEPreposizioniNonContano() {
        assertEquals(0, cost("pasta con pomodoro", "Pasta al sugo di pomodoro"))
        assertEquals(listOf("al"), FoodSearch.queryWords("al")) // solo preposizioni: si cercano comunque
    }

    @Test
    fun sinonimi() {
        assertEquals(2, cost("spaghetti al pomodoro", "Pasta al pomodoro"))
        assertTrue(cost("olio evo", "Olio extravergine d'oliva") >= 0)
        assertTrue(cost("panini", "Pane bianco") >= 0)
    }

    @Test
    fun paroleGruppoECategorie() {
        assertTrue(names("pesce").containsAll(listOf("Merluzzo", "Orata", "Salmone fresco")))
        val formaggi = names("formaggi")
        assertTrue(formaggi.containsAll(listOf("Parmigiano reggiano", "Gorgonzola")))
        assertTrue("prima i formaggi, poi i piatti", formaggi.take(20).all { n -> FoodCatalog.defaultFoods.first { it.name == n }.category != "Piatti pronti" })
        assertTrue(names("verdura").contains("Zucchine"))
        assertTrue(names("salumi").contains("Bresaola"))
        I18n.setForTest(AppLanguage.EN)
        assertTrue("anche in inglese", names("fish").contains("Merluzzo"))
    }

    @Test
    fun erroriDiBattitura() {
        assertEquals(5, cost("melanzne", "Parmigiana di melanzane"))
        assertEquals(5, cost("parmigaina", "Parmigiana di melanzane"))
        assertEquals("parole corte: niente tolleranza", -1, cost("rsio", "Riso bianco"))
        assertEquals(1, FoodSearch.distance("melanzane", "melnazane"))
        assertEquals(3, FoodSearch.distance("kitten", "sitting"))
        assertEquals("oltre il limite si ferma", 2, FoodSearch.bounded("abcdef", "uvwxyz", 1))
    }

    @Test
    fun iCibiGenericiVengonoPrimaDeiPiattiEDelleMarche() {
        val r = names("pomodoro")
        assertEquals("Pomodori", r.first())
        assertTrue(r.indexOf("Passata di pomodoro") < r.indexOf("Pasta al pomodoro"))
        val p = names("pollo")
        assertTrue(p.indexOf("Petto di pollo crudo") < p.indexOf("Pollo alle mandorle"))
        val branded = Food("Pasta di semola Barilla", 350, category = "Marche", brand = "Barilla", barcode = "123")
        val withBrand = names("pasta", listOf(branded))
        assertTrue(withBrand.indexOf("Pasta secca") < withBrand.indexOf("Pasta di semola Barilla"))
        assertTrue("la marca si cerca", names("barilla", listOf(branded)).contains("Pasta di semola Barilla"))
    }

    @Test
    fun piattiItalianiDelCatalogo() {
        assertEquals("Pasta all'amatriciana", names("amatriciana").first())
        assertTrue(names("spaghetti al pomodoro").take(3).contains("Pasta al pomodoro"))
        assertTrue(names("gnocchi pomodoro").contains("Gnocchi al pomodoro"))
        assertTrue(names("tortellini").contains("Tortellini cotti"))
        assertTrue(names("focaccia").contains("Pizza bianca (focaccia)"))
        assertTrue(names("gelati").any { it.startsWith("Gelato") })
        assertEquals("Mela", names("mela").first())
    }

    @Test
    fun seNessunoHaTutteLeParoleMostraIPiuSimili() {
        val r = FoodCatalog.searchResult("insalata di pollo e avocado")
        assertTrue(r.partial)
        assertTrue(r.foods.containsAll(listOf("Avocado", "Insalata mista").map { n -> FoodCatalog.defaultFoods.first { it.name == n } }))
        assertTrue(r.strength.all { it == 2 })
        val exact = FoodCatalog.searchResult("pollo")
        assertFalse(exact.partial)
        assertEquals(0, exact.strength.first())
        assertTrue("una parola sola: niente parziali", FoodCatalog.searchResult("xqzw").foods.isEmpty())
    }

    @Test
    fun filtroSuElenchiQualsiasi() {
        val meals = listOf("Colazione con mele", "Pranzo veloce", "Cena di pesce")
        assertEquals(listOf("Colazione con mele"), FoodCatalog.filter("mela", meals) { it })
        assertEquals(meals, FoodCatalog.filter("", meals) { it })
        assertEquals("aranc", FoodCatalog.sqlKeyword("succo di arance"))
        assertFalse(FoodCatalog.filter("pollo", meals) { it }.isNotEmpty())
    }
}
