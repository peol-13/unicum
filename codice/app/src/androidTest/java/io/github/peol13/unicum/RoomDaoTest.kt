package io.github.peol13.unicum

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peol13.unicum.data.db.ActivityEntity
import io.github.peol13.unicum.data.db.FoodEntity
import io.github.peol13.unicum.data.db.MealEntity
import io.github.peol13.unicum.data.db.UnicumDao
import io.github.peol13.unicum.data.db.UnicumDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prova il DAO su un database in memoria: non tocca i dati reali dell'app.
 */
@RunWith(AndroidJUnit4::class)
class RoomDaoTest {

    private lateinit var db: UnicumDatabase
    private lateinit var dao: UnicumDao

    @Before
    fun creaDatabaseInMemoria() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, UnicumDatabase::class.java).build()
        dao = db.dao()
    }

    @After
    fun chiudiDatabase() {
        db.close()
    }

    private fun pasto(date: String, name: String, kcal: Float = 100f, createdAt: Long = 0L) =
        MealEntity(
            date = date, mealType = "Pranzo", foodId = null, foodName = name,
            grams = 100f, kcal = kcal, protein = 1f, carbs = 2f, fats = 3f,
            createdAt = createdAt
        )

    private fun attivita(date: String, type: String, source: String = "manual", externalId: String? = null) =
        ActivityEntity(
            date = date, type = type, durationMinutes = 30, kcal = 250,
            source = source, externalId = externalId
        )

    // ==============================
    // PASTI
    // ==============================

    @Test
    fun un_pasto_inserito_si_rilegge_con_tutti_i_campi() = runBlocking {
        dao.insertMeals(listOf(pasto("2026-10-12", "Pasta", kcal = 350f)))

        val letti = dao.mealsForDate("2026-10-12").first()

        assertEquals(1, letti.size)
        assertEquals("Pasta", letti[0].foodName)
        assertEquals(350f, letti[0].kcal, 0.001f)
        assertEquals("Pranzo", letti[0].mealType)
        assertEquals("manual", letti[0].source)
        assertTrue(letti[0].id > 0)
    }

    @Test
    fun i_pasti_sono_filtrati_per_data() = runBlocking {
        dao.insertMeals(
            listOf(
                pasto("2026-10-11", "Ieri"),
                pasto("2026-10-12", "Oggi"),
                pasto("2026-10-13", "Domani")
            )
        )

        val oggi = dao.mealsForDate("2026-10-12").first()

        assertEquals(listOf("Oggi"), oggi.map { it.foodName })
    }

    @Test
    fun i_pasti_di_un_giorno_sono_ordinati_per_inserimento() = runBlocking {
        dao.insertMeals(
            listOf(
                pasto("2026-10-12", "Terzo", createdAt = 300),
                pasto("2026-10-12", "Primo", createdAt = 100),
                pasto("2026-10-12", "Secondo", createdAt = 200)
            )
        )

        val nomi = dao.mealsForDate("2026-10-12").first().map { it.foodName }

        assertEquals(listOf("Primo", "Secondo", "Terzo"), nomi)
    }

    @Test
    fun l_intervallo_di_date_include_gli_estremi() = runBlocking {
        dao.insertMeals(
            listOf(
                pasto("2026-10-10", "Fuori"),
                pasto("2026-10-11", "Inizio"),
                pasto("2026-10-12", "Mezzo"),
                pasto("2026-10-13", "Fine"),
                pasto("2026-10-14", "Fuori2")
            )
        )

        val nomi = dao.mealsBetween("2026-10-11", "2026-10-13").first().map { it.foodName }

        assertEquals(listOf("Inizio", "Mezzo", "Fine"), nomi)
    }

    @Test
    fun eliminare_un_pasto_per_id_lascia_gli_altri() = runBlocking {
        dao.insertMeals(listOf(pasto("2026-10-12", "A"), pasto("2026-10-12", "B")))
        val daEliminare = dao.mealsForDate("2026-10-12").first().first { it.foodName == "A" }

        val righe = dao.deleteMealById(daEliminare.id)

        assertEquals(1, righe)
        assertEquals(listOf("B"), dao.mealsForDate("2026-10-12").first().map { it.foodName })
    }

    @Test
    fun eliminare_un_id_inesistente_non_cancella_nulla() = runBlocking {
        dao.insertMeals(listOf(pasto("2026-10-12", "A")))

        val righe = dao.deleteMealById(9999)

        assertEquals(0, righe)
        assertEquals(1, dao.mealsForDate("2026-10-12").first().size)
    }

    // ==============================
    // ATTIVITÀ
    // ==============================

    @Test
    fun un_allenamento_inserito_si_rilegge_per_data() = runBlocking {
        dao.insertActivities(listOf(attivita("2026-10-12", "Corsa"), attivita("2026-10-13", "Nuoto")))

        val letti = dao.activitiesForDate("2026-10-12").first()

        assertEquals(listOf("Corsa"), letti.map { it.type })
        assertEquals(250, letti[0].kcal)
        assertEquals(30, letti[0].durationMinutes)
    }

    @Test
    fun un_allenamento_si_elimina_per_id() = runBlocking {
        dao.insertActivities(listOf(attivita("2026-10-12", "Corsa")))
        val id = dao.activitiesForDate("2026-10-12").first().first().id

        assertEquals(1, dao.deleteActivityById(id))
        assertTrue(dao.activitiesForDate("2026-10-12").first().isEmpty())
    }

    @Test
    fun lo_stesso_allenamento_da_una_fonte_esterna_non_viene_duplicato() = runBlocking {
        // Indice univoco su (source, externalId): serve alla sincronizzazione incrementale.
        val giaImportato = attivita("2026-10-12", "Corsa", source = "samsung", externalId = "abc-1")

        dao.insertActivities(listOf(giaImportato))
        dao.insertActivities(listOf(giaImportato.copy(kcal = 999)))

        val letti = dao.activitiesForDate("2026-10-12").first()
        assertEquals(1, letti.size)
        assertEquals(250, letti[0].kcal)
    }

    @Test
    fun lo_stesso_externalId_da_fonti_diverse_e_permesso() = runBlocking {
        dao.insertActivities(
            listOf(
                attivita("2026-10-12", "Corsa", source = "samsung", externalId = "1"),
                attivita("2026-10-12", "Corsa", source = "ultrahuman", externalId = "1")
            )
        )

        assertEquals(2, dao.activitiesForDate("2026-10-12").first().size)
    }

    @Test
    fun gli_allenamenti_manuali_identici_non_vengono_scartati() = runBlocking {
        dao.insertActivities(listOf(attivita("2026-10-12", "Corsa")))
        dao.insertActivities(listOf(attivita("2026-10-12", "Corsa")))

        assertEquals(2, dao.activitiesForDate("2026-10-12").first().size)
    }

    // ==============================
    // CATALOGO ALIMENTI
    // ==============================

    @Test
    fun la_ricerca_alimenti_trova_per_nome_parziale() {
        dao.upsertFoods(
            listOf(
                FoodEntity("off:1", "Yogurt greco", null, 59f, 10f, 3.6f, 0.4f, "off"),
                FoodEntity("off:2", "Pasta integrale", "Barilla", 350f, 13f, 72f, 1.5f, "off")
            )
        )

        assertEquals(listOf("Yogurt greco"), dao.searchFoods("gurt").map { it.name })
        assertTrue(dao.searchFoods("zzz").isEmpty())
    }

    @Test
    fun l_upsert_di_un_alimento_esistente_lo_aggiorna() {
        dao.upsertFoods(listOf(FoodEntity("off:1", "Pane", null, 245f, 9f, 45f, 2.5f, "off")))
        dao.upsertFoods(listOf(FoodEntity("off:1", "Pane", null, 250f, 9f, 45f, 2.5f, "off")))

        val trovati = dao.searchFoods("Pane")

        assertEquals(1, trovati.size)
        assertEquals(250f, trovati[0].kcalPer100g!!, 0.001f)
    }
}
