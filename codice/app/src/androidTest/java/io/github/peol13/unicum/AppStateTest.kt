package io.github.peol13.unicum

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peol13.unicum.data.Activity
import io.github.peol13.unicum.data.Meal
import io.github.peol13.unicum.data.db.UnicumDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prova lo stato che la UI osserva (UnicumAppState): le modifiche passano dal database
 * e poi si riflettono su dayData. Usa un database in memoria e gira solo sull'emulatore (vedi [TestSafety]).
 */
@RunWith(AndroidJUnit4::class)
class AppStateTest {

    private lateinit var context: Context
    private var db: UnicumDatabase? = null

    @Before
    fun preparaAmbiente() {
        TestSafety.requireEmulator()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = TestSafety.useInMemoryDatabase(context)
        runBlocking { UnicumAppState.initPersistence(context) }
    }

    @After
    fun ripulisciAmbiente() {
        TestSafety.release(db)
    }

    private fun attendi(descrizione: String, timeoutMs: Long = 5000, condizione: () -> Boolean) {
        val limite = System.currentTimeMillis() + timeoutMs
        while (!condizione()) {
            if (System.currentTimeMillis() > limite) throw AssertionError("Timeout: $descrizione")
            Thread.sleep(50)
        }
    }

    @Test
    fun aggiungere_e_rimuovere_un_pasto_aggiorna_lo_stato() {
        UnicumAppState.addMeals(context, listOf(Meal("Mela", 52, "Colazione", 100, 0.3f, 14f, 0.2f)))
        attendi("il pasto compare nello stato") { UnicumAppState.dayData.meals.size == 1 }

        val mela = UnicumAppState.dayData.meals.first()
        assertEquals("Mela", mela.name)
        assertTrue(mela.id > 0)
        assertEquals(52, UnicumAppState.dayData.caloriesConsumed)

        UnicumAppState.removeMeal(context, mela)
        attendi("il pasto sparisce dallo stato") { UnicumAppState.dayData.meals.isEmpty() }
        assertEquals(0, UnicumAppState.dayData.caloriesConsumed)
    }

    @Test
    fun aggiungere_un_allenamento_aggiorna_minuti_e_calorie() {
        UnicumAppState.addActivity(context, Activity("Corsa", 30, 250))
        attendi("l'allenamento compare nello stato") { UnicumAppState.dayData.activities.size == 1 }

        assertEquals(30, UnicumAppState.dayData.activityMinutes)
        assertEquals(250, UnicumAppState.dayData.activityCalories)
    }

    @Test
    fun piu_pasti_aggiunti_insieme_hanno_id_diversi_e_totali_corretti() {
        UnicumAppState.addMeals(
            context,
            listOf(Meal("Pasta", 350, "Pranzo"), Meal("Mela", 52, "Pranzo"))
        )
        attendi("entrambi i pasti compaiono") { UnicumAppState.dayData.meals.size == 2 }

        val ids = UnicumAppState.dayData.meals.map { it.id }
        assertEquals(2, ids.toSet().size)
        assertEquals(402, UnicumAppState.dayData.caloriesConsumed)
    }
}
