package io.github.peol13.unicum

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peol13.unicum.data.db.UnicumDatabase
import io.github.peol13.unicum.data.db.toCustomEntity
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Prova le migrazioni di Room su file di database separati (mai su quello vero dell'app): si costruisce un database
 * vecchio con SQL, lo si apre con Room e tutte le migrazioni, e Room controlla che lo schema finale sia quello atteso.
 *
 * - dalla versione 1: la catena completa (la v1 è ricostruita a mano: tre tabelle, prima delle colonne aggiunte dopo);
 * - da uno schema esportato (app/schemas/<versione>.json): la versione di partenza è esatta. Quando si aggiunge una
 *   versione, il suo JSON esce dalla compilazione e va tenuto in git: serve a questo test;
 * - regole che valgono da sole per le versioni future: ogni passo ha la sua migrazione, lo schema corrente è in git,
 *   e da ogni schema esportato nessuna tabella perde righe.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dbFile: File

    @Before
    fun preparaFile() {
        dbFile = context.getDatabasePath("migration-test.db")
        dbFile.parentFile?.mkdirs()
        cancellaFile()
    }

    @After
    fun ripulisci() = cancellaFile()

    private fun cancellaFile() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    }

    private fun creaDatabaseVecchio(versione: Int, sql: List<String>) {
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sql.forEach { raw.execSQL(it) }
        raw.version = versione
        raw.close()
    }

    /** Apre con Room e tutte le migrazioni: l'apertura le esegue e controlla lo schema (altrimenti lancia un'eccezione). */
    private fun apriConRoom(): UnicumDatabase =
        Room.databaseBuilder(context, UnicumDatabase::class.java, dbFile.name)
            .addMigrations(*UnicumDatabase.MIGRATIONS).build()
            .also { it.openHelper.writableDatabase }

    // ------------------------------------------------------------------ dalla versione 1

    private val schemaV1 = listOf(
        "CREATE TABLE IF NOT EXISTS `meal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `mealType` TEXT NOT NULL, `foodId` TEXT, `foodName` TEXT NOT NULL, `grams` REAL NOT NULL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, `carbs` REAL NOT NULL, `fats` REAL NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_meal_entries_date` ON `meal_entries` (`date`)",
        "CREATE TABLE IF NOT EXISTS `activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `type` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, `kcal` INTEGER NOT NULL, `source` TEXT NOT NULL, `externalId` TEXT)",
        "CREATE INDEX IF NOT EXISTS `index_activities_date` ON `activities` (`date`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_activities_source_externalId` ON `activities` (`source`, `externalId`)",
        "CREATE TABLE IF NOT EXISTS `foods` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, `kcalPer100g` REAL, `proteinPer100g` REAL, `carbsPer100g` REAL, `fatsPer100g` REAL, `source` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_foods_name` ON `foods` (`name`)",
        "INSERT INTO meal_entries (date, mealType, foodId, foodName, grams, kcal, protein, carbs, fats, source, createdAt) VALUES ('2026-09-20', 'Pranzo', NULL, 'Pasta', 100, 350, 12, 70, 2, 'manual', 1000)",
        "INSERT INTO activities (date, type, durationMinutes, kcal, source, externalId) VALUES ('2026-09-20', 'Corsa', 30, 300, 'manual', NULL)",
        "INSERT INTO foods (id, name, brand, kcalPer100g, proteinPer100g, carbsPer100g, fatsPer100g, source, fetchedAt) VALUES ('off:1', 'Biscotti', 'Marca', 450, 6, 70, 16, 'openfoodfacts', 5)"
    )

    @Test
    fun dalla_versione_1_alla_corrente_i_dati_restano() {
        creaDatabaseVecchio(1, schemaV1)

        val db = apriConRoom()
        try {
            val dao = db.dao()
            val pasti = dao.allMeals()
            assertEquals(1, pasti.size)
            assertEquals("Pasta", pasti[0].foodName)
            assertEquals(350f, pasti[0].kcal, 0f)
            assertEquals(null, pasti[0].fiber)           // colonna nata nella v4

            val attivita = dao.allActivities()
            assertEquals(1, attivita.size)
            assertEquals("Corsa", attivita[0].type)
            assertEquals(null, attivita[0].startMinute)  // colonna nata nella v5

            assertEquals(listOf("off:1"), dao.allFoods().map { it.id })
            // Le tabelle nate dopo la v1 ci sono e sono vuote
            assertTrue(dao.allWorkoutFeedback().isEmpty())
            assertTrue(dao.workoutDetailIds().isEmpty())
            assertTrue(dao.allCustomFoods().isEmpty())
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------ da uno schema esportato

    private val cartellaSchemi = "io.github.peol13.unicum.data.db.UnicumDatabase"
    private val assets get() = InstrumentationRegistry.getInstrumentation().context.assets

    /** Il blocco "database" dello schema esportato di una versione (null se il file non c'è). */
    private fun schema(versione: Int): JSONObject? = runCatching {
        assets.open("$cartellaSchemi/$versione.json").bufferedReader().use { JSONObject(it.readText()) }.getJSONObject("database")
    }.getOrNull()

    /** Le versioni di cui c'è lo schema esportato in app/schemas. */
    private fun versioniEsportate(): List<Int> =
        assets.list(cartellaSchemi).orEmpty().mapNotNull { it.removeSuffix(".json").toIntOrNull() }.sorted()

    /** La versione dichiarata in @Database (letta aprendo un database nuovo in memoria). */
    private fun versioneCorrente(): Int {
        val db = Room.inMemoryDatabaseBuilder(context, UnicumDatabase::class.java).build()
        return try { db.openHelper.writableDatabase.version } finally { db.close() }
    }

    private fun tabelle(schema: JSONObject): List<JSONObject> {
        val entita = schema.getJSONArray("entities")
        return (0 until entita.length()).map { entita.getJSONObject(it) }
    }

    /** Una riga valida per una tabella dello schema: valori qualsiasi nelle colonne obbligatorie, NULL nelle altre. */
    private fun rigaDiProva(tabella: JSONObject): String {
        val campi = tabella.getJSONArray("fields")
        val colonne = mutableListOf<String>()
        val valori = mutableListOf<String>()
        for (i in 0 until campi.length()) {
            val f = campi.getJSONObject(i)
            colonne += "`${f.getString("columnName")}`"
            valori += if (!f.optBoolean("notNull")) "NULL" else when (f.getString("affinity")) {
                "INTEGER" -> "1"
                "REAL" -> "1.5"
                "BLOB" -> "X'00'"
                else -> "'prova'"
            }
        }
        return "INSERT INTO `${tabella.getString("tableName")}` (${colonne.joinToString()}) VALUES (${valori.joinToString()})"
    }

    /** Costruisce il database di una versione dal suo JSON esportato da Room (stesso SQL che Room avrebbe eseguito). */
    private fun creaDaSchemaEsportato(versione: Int, righe: List<String> = emptyList()) {
        val database = schema(versione) ?: error("Manca app/schemas/$cartellaSchemi/$versione.json")
        val sql = mutableListOf<String>()
        val entita = database.getJSONArray("entities")
        for (i in 0 until entita.length()) {
            val e = entita.getJSONObject(i)
            val tabella = e.getString("tableName")
            sql += e.getString("createSql").replace("\${TABLE_NAME}", tabella)
            val indici = e.optJSONArray("indices")
            for (j in 0 until (indici?.length() ?: 0)) {
                sql += indici!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tabella)
            }
        }
        val setup = database.optJSONArray("setupQueries")
        for (i in 0 until (setup?.length() ?: 0)) sql += setup!!.getString(i)
        creaDatabaseVecchio(versione, sql + righe)
    }

    // ------------------------------------------------------------------ regole per le versioni future

    /**
     * Ogni passo da una versione alla successiva ha la sua migrazione, e lo schema della versione corrente è in git.
     * Se si aumenta la versione senza migrazione o senza tenere il JSON, questo test fallisce.
     */
    @Test
    fun ogni_versione_ha_migrazione_e_schema() {
        val corrente = versioneCorrente()
        for (v in 1 until corrente) {
            val passi = UnicumDatabase.MIGRATIONS.filter { it.startVersion == v && it.endVersion == v + 1 }
            assertEquals("Migrazione $v → ${v + 1}", 1, passi.size)
        }
        val ultimo = schema(corrente)
        assertTrue("Manca lo schema esportato della versione $corrente (app/schemas)", ultimo != null)
        assertEquals(corrente, ultimo!!.getInt("version"))
    }

    /**
     * Da ogni schema esportato più vecchio della versione corrente: una riga in ogni tabella, migrazione fino alla
     * corrente, e ogni tabella che esiste ancora deve avere la sua riga. Vale da solo anche per le versioni future.
     */
    @Test
    fun da_ogni_schema_esportato_nessuna_tabella_perde_righe() {
        val corrente = versioneCorrente()
        val tabelleCorrenti = tabelle(schema(corrente)!!).map { it.getString("tableName") }.toSet()
        val vecchie = versioniEsportate().filter { it < corrente }
        assertTrue("Serve almeno lo schema della versione precedente", vecchie.isNotEmpty())
        for (v in vecchie) {
            cancellaFile()
            val tabelleVecchie = tabelle(schema(v)!!)
            creaDaSchemaEsportato(v, tabelleVecchie.map(::rigaDiProva))
            val db = apriConRoom()
            try {
                val sql = db.openHelper.writableDatabase
                for (t in tabelleVecchie.map { it.getString("tableName") }.filter { it in tabelleCorrenti }) {
                    sql.query("SELECT COUNT(*) FROM `$t`").use { c ->
                        c.moveToFirst()
                        assertEquals("Tabella $t dalla versione $v", 1, c.getInt(0))
                    }
                }
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun dallo_schema_9_alla_corrente_i_dati_restano() {
        creaDaSchemaEsportato(
            9, listOf(
                "INSERT INTO meal_entries (date, mealType, foodId, foodName, grams, kcal, protein, carbs, fats, source, createdAt) VALUES ('2026-11-01', 'Cena', NULL, 'Riso', 100, 130, 3, 28, 0.3, 'manual', 1000)",
                "INSERT INTO workout_feedback (`key`, rpe, kind, note) VALUES ('sessione-1', 6, 'Facile', 'ok')"
            )
        )

        val db = apriConRoom()
        try {
            assertEquals(listOf("Riso"), db.dao().allMeals().map { it.foodName })
            val feedback = db.dao().allWorkoutFeedback()
            assertEquals(listOf("sessione-1"), feedback.map { it.key })
            assertEquals(6, feedback[0].rpe)
            // La tabella degli alimenti personali (v10) c'è e funziona
            assertTrue(db.dao().allCustomFoods().isEmpty())
            db.dao().upsertCustomFoods(listOf(io.github.peol13.unicum.data.Food("Mio", 100).toCustomEntity(1L)))
            assertEquals(listOf("Mio"), db.dao().allCustomFoods().map { it.name })
        } finally {
            db.close()
        }
    }
}
