package io.github.peol13.unicum

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peol13.unicum.data.Activity
import io.github.peol13.unicum.data.Food
import io.github.peol13.unicum.data.Meal
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.data.db.UnicumDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prova il repository (UnicumDatabase.get) su un database in memoria, mai su quello vero dell'app, e solo
 * sull'emulatore (vedi [TestSafety]): il test svuota anche le vecchie preferenze usate dalla migrazione.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private val prefsName = "unicum_persistence_prefs"
    private val keyMeals = "key_persisted_meals"
    private val keyActivities = "key_persisted_activities"
    private val keyMigrated = "key_migrated_to_room"

    private lateinit var context: Context
    private var db: UnicumDatabase? = null
    private val oggi = UnicumRepository.today()

    private val pasta = Meal("Pasta", 350, "Pranzo", 100, 13f, 72f, 1.5f)
    private val corsa = Activity("Corsa", 30, 250)

    private fun prefs() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun pulisci() {
        UnicumDatabase.get(context).clearAllTables()
        prefs().edit().clear().commit()
    }

    @Before
    fun preparaAmbiente() {
        TestSafety.requireEmulator()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = TestSafety.useInMemoryDatabase(context)
        pulisci()
    }

    @After
    fun ripulisciAmbiente() {
        if (::context.isInitialized) pulisci()
        TestSafety.release(db)
    }

    // ==============================
    // ALIMENTI PERSONALI
    // ==============================

    @Test
    fun gli_alimenti_personali_si_salvano_si_aggiornano_e_tornano_dal_piu_recente() = runBlocking {
        UnicumRepository.saveCustomFoods(context, listOf(Food("Torta", 380, 6f, 52f, 17f)))
        UnicumRepository.saveCustomFoods(context, listOf(Food("Crema", 250, 3f, 20f, 18f)))
        // Stesso nome (maiuscole a parte): sostituisce e torna in cima
        UnicumRepository.saveCustomFoods(context, listOf(Food("torta", 400, 7f, 50f, 20f)))

        val letti = UnicumRepository.loadCustomFoods(context)

        assertEquals(listOf("torta", "Crema"), letti.map { it.name })
        assertEquals(400, letti[0].caloriesPer100g)
    }

    @Test
    fun gli_alimenti_personali_delle_vecchie_preferenze_passano_nel_database_una_volta_sola() = runBlocking {
        val vecchie = context.getSharedPreferences("test_settings", Context.MODE_PRIVATE)
        val json = """[{"name":"Mio piatto","caloriesPer100g":120,"proteinPer100g":5.0},{"name":"Altro","caloriesPer100g":80}]"""
        vecchie.edit().clear().putString("custom_foods", json).commit()
        // Uno già nel database non viene sovrascritto dalla vecchia copia
        UnicumRepository.saveCustomFoods(context, listOf(Food("Altro", 999)))

        UnicumRepository.migrateCustomFoodsFromPrefs(context, vecchie)

        assertFalse(vecchie.contains("custom_foods"))
        val letti = UnicumRepository.loadCustomFoods(context).associateBy { it.name }
        assertEquals(setOf("Mio piatto", "Altro"), letti.keys)
        assertEquals(120, letti.getValue("Mio piatto").caloriesPer100g)
        assertEquals(999, letti.getValue("Altro").caloriesPer100g)

        // Una seconda volta non cambia niente (e non serve la chiave)
        UnicumRepository.migrateCustomFoodsFromPrefs(context, vecchie)
        assertEquals(2, UnicumRepository.loadCustomFoods(context).size)
        vecchie.edit().clear().commit()
        Unit
    }

    @Test
    fun un_json_illeggibile_resta_nelle_preferenze() = runBlocking {
        val vecchie = context.getSharedPreferences("test_settings", Context.MODE_PRIVATE)
        vecchie.edit().clear().putString("custom_foods", "{non è json").commit()

        UnicumRepository.migrateCustomFoodsFromPrefs(context, vecchie)

        assertTrue(vecchie.contains("custom_foods"))
        assertTrue(UnicumRepository.loadCustomFoods(context).isEmpty())
        vecchie.edit().clear().commit()
        Unit
    }

    @Test
    fun il_backup_esportato_contiene_gli_alimenti_personali_del_database() = runBlocking {
        UnicumRepository.saveCustomFoods(context, listOf(Food("Mio piatto", 120)))

        val file = io.github.peol13.unicum.data.BackupCodec.decode(UnicumRepository.exportBackup(context))!!

        assertEquals(listOf("Mio piatto"), file.settings!!.customFoods.map { it.name })
    }

    // ==============================
    // PASTI E ATTIVITÀ
    // ==============================

    @Test
    fun un_pasto_salvato_si_rilegge_con_tutti_i_valori() = runBlocking {
        UnicumRepository.insertMeals(context, oggi, listOf(pasta))

        val letti = UnicumRepository.loadMeals(context, oggi)

        assertEquals(1, letti.size)
        assertEquals("Pasta", letti[0].name)
        assertEquals(350, letti[0].calories)
        assertEquals("Pranzo", letti[0].mealType)
        assertEquals(100, letti[0].quantityGrams)
        assertEquals(13f, letti[0].protein, 0.001f)
        assertEquals(72f, letti[0].carbohydrates, 0.001f)
        assertEquals(1.5f, letti[0].fats, 0.001f)
        assertTrue(letti[0].id > 0)
    }

    @Test
    fun i_pasti_di_un_altro_giorno_non_compaiono_oggi() = runBlocking {
        UnicumRepository.insertMeals(context, "2020-01-01", listOf(pasta))

        assertTrue(UnicumRepository.loadMeals(context, oggi).isEmpty())
        assertEquals(1, UnicumRepository.loadMeals(context, "2020-01-01").size)
    }

    @Test
    fun si_elimina_solo_il_pasto_scelto() = runBlocking {
        UnicumRepository.insertMeals(context, oggi, listOf(pasta, pasta.copy(name = "Mela", calories = 52)))
        val mela = UnicumRepository.loadMeals(context, oggi).first { it.name == "Mela" }

        UnicumRepository.deleteMeal(context, mela.id)

        assertEquals(listOf("Pasta"), UnicumRepository.loadMeals(context, oggi).map { it.name })
    }

    @Test
    fun un_allenamento_salvato_si_rilegge() = runBlocking {
        UnicumRepository.insertActivity(context, oggi, corsa)

        val letti = UnicumRepository.loadActivities(context, oggi)

        assertEquals(1, letti.size)
        assertEquals("Corsa", letti[0].type)
        assertEquals(30, letti[0].durationMinutes)
        assertEquals(250, letti[0].calories)
        assertTrue(letti[0].id > 0)
    }

    // ==============================
    // MIGRAZIONE DALLE SHAREDPREFERENCES
    // ==============================

    private val jsonPasti =
        """[{"name":"Pasta","calories":350,"mealType":"Pranzo","quantityGrams":100,"protein":13.0,"carbohydrates":72.0,"fats":1.5}]"""
    private val jsonAttivita = """[{"type":"Corsa","durationMinutes":30,"calories":250}]"""

    @Test
    fun la_migrazione_sposta_i_dati_vecchi_nel_database_con_la_data_di_oggi() = runBlocking {
        prefs().edit().putString(keyMeals, jsonPasti).putString(keyActivities, jsonAttivita).commit()

        UnicumRepository.migrateLegacyPrefs(context)

        val pasti = UnicumRepository.loadMeals(context, oggi)
        val attivita = UnicumRepository.loadActivities(context, oggi)
        assertEquals(listOf("Pasta"), pasti.map { it.name })
        assertEquals(350, pasti[0].calories)
        assertEquals(listOf("Corsa"), attivita.map { it.type })
        assertEquals(250, attivita[0].calories)
    }

    @Test
    fun dopo_la_migrazione_le_vecchie_chiavi_sono_rimosse_e_il_flag_e_attivo() = runBlocking {
        prefs().edit().putString(keyMeals, jsonPasti).putString(keyActivities, jsonAttivita).commit()

        UnicumRepository.migrateLegacyPrefs(context)

        assertFalse(prefs().contains(keyMeals))
        assertFalse(prefs().contains(keyActivities))
        assertTrue(prefs().getBoolean(keyMigrated, false))
    }

    @Test
    fun la_migrazione_non_viene_ripetuta_e_non_duplica_i_dati() = runBlocking {
        prefs().edit().putString(keyMeals, jsonPasti).commit()
        UnicumRepository.migrateLegacyPrefs(context)

        // Se per qualsiasi motivo ricomparissero vecchi dati, il flag impedisce di rimportarli.
        prefs().edit().putString(keyMeals, jsonPasti).commit()
        UnicumRepository.migrateLegacyPrefs(context)

        assertEquals(1, UnicumRepository.loadMeals(context, oggi).size)
    }

    @Test
    fun la_migrazione_senza_dati_vecchi_non_crea_nulla_e_imposta_il_flag() = runBlocking {
        UnicumRepository.migrateLegacyPrefs(context)

        assertTrue(UnicumRepository.loadMeals(context, oggi).isEmpty())
        assertTrue(UnicumRepository.loadActivities(context, oggi).isEmpty())
        assertTrue(prefs().getBoolean(keyMigrated, false))
    }

    @Test
    fun un_json_corrotto_non_manda_in_crash_e_non_perde_i_dati() = runBlocking {
        val corrotto = "questo non è json"
        prefs().edit().putString(keyMeals, corrotto).commit()

        UnicumRepository.migrateLegacyPrefs(context)

        assertTrue(UnicumRepository.loadMeals(context, oggi).isEmpty())
        // I dati vecchi restano dove sono e la migrazione non risulta completata
        assertEquals(corrotto, prefs().getString(keyMeals, null))
        assertFalse(prefs().getBoolean(keyMigrated, false))
    }

    @Test
    fun una_migrazione_fallita_viene_ritentata_al_prossimo_avvio() = runBlocking {
        prefs().edit().putString(keyMeals, "questo non è json").commit()
        UnicumRepository.migrateLegacyPrefs(context)

        // Il dato torna leggibile (ad esempio dopo una correzione): al nuovo tentativo viene importato
        prefs().edit().putString(keyMeals, jsonPasti).commit()
        UnicumRepository.migrateLegacyPrefs(context)

        assertEquals(listOf("Pasta"), UnicumRepository.loadMeals(context, oggi).map { it.name })
        assertFalse(prefs().contains(keyMeals))
        assertTrue(prefs().getBoolean(keyMigrated, false))
    }

    @Test
    fun un_errore_sui_pasti_non_blocca_gli_allenamenti_e_non_li_duplica() = runBlocking {
        prefs().edit().putString(keyMeals, "corrotto").putString(keyActivities, jsonAttivita).commit()

        UnicumRepository.migrateLegacyPrefs(context)

        assertEquals(1, UnicumRepository.loadActivities(context, oggi).size)
        assertFalse(prefs().contains(keyActivities))
        assertTrue(prefs().contains(keyMeals))
        assertFalse(prefs().getBoolean(keyMigrated, false))

        // Secondo tentativo: i pasti sono ancora illeggibili, gli allenamenti non vengono reimportati
        UnicumRepository.migrateLegacyPrefs(context)

        assertEquals(1, UnicumRepository.loadActivities(context, oggi).size)
    }
}
