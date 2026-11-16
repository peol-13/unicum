package io.github.peol13.unicum.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MealEntity::class, ActivityEntity::class, FoodEntity::class,
        SleepEntity::class, DailyMetricEntity::class,
        WorkoutDetailEntity::class, WorkoutFeedbackEntity::class, CustomFoodEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class UnicumDatabase : RoomDatabase() {

    abstract fun dao(): UnicumDao

    companion object {
        @Volatile
        private var instance: UnicumDatabase? = null

        /** v1 → v2: distanza e FC negli allenamenti, tabelle per sonno e metriche giornaliere. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `activities` ADD COLUMN `distanceMeters` INTEGER")
                db.execSQL("ALTER TABLE `activities` ADD COLUMN `avgHeartRate` INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sleep_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, `deepMinutes` INTEGER NOT NULL, `remMinutes` INTEGER NOT NULL, `lightMinutes` INTEGER NOT NULL, `awakeMinutes` INTEGER NOT NULL, `source` TEXT NOT NULL, `externalId` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_date` ON `sleep_sessions` (`date`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_sessions_source_externalId` ON `sleep_sessions` (`source`, `externalId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_metrics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `type` TEXT NOT NULL, `value` REAL NOT NULL, `source` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_metrics_date_type_source` ON `daily_metrics` (`date`, `type`, `source`)")
            }
        }

        /** v2 → v3: fasi del sonno con gli orari (per il grafico a gradini). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `stages` TEXT")
            }
        }

        /** v3 → v4: fibre, zuccheri e sale nei pasti e nei prodotti salvati. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `meal_entries` ADD COLUMN `fiber` REAL")
                db.execSQL("ALTER TABLE `meal_entries` ADD COLUMN `sugars` REAL")
                db.execSQL("ALTER TABLE `meal_entries` ADD COLUMN `salt` REAL")
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `fiberPer100g` REAL")
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `sugarsPer100g` REAL")
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `saltPer100g` REAL")
            }
        }

        /** v4 → v5: orario di inizio degli allenamenti. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `activities` ADD COLUMN `startMinute` INTEGER")
            }
        }

        /** v5 → v6: dettaglio degli allenamenti (serie e percorso) e sforzo percepito/nota dell'utente. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_details` (`externalId` TEXT NOT NULL, `source` TEXT NOT NULL, `date` TEXT NOT NULL, `type` TEXT NOT NULL, `startMillis` INTEGER NOT NULL, `automatic` INTEGER NOT NULL, `summary` TEXT NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`externalId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_details_date` ON `workout_details` (`date`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_feedback` (`key` TEXT NOT NULL, `rpe` INTEGER, `kind` TEXT, `note` TEXT, PRIMARY KEY(`key`))")
            }
        }

        /** v6 → v7: correzione a mano di distanza e durata di un allenamento. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_feedback` ADD COLUMN `distanceM` REAL")
                db.execSQL("ALTER TABLE `workout_feedback` ADD COLUMN `movingSeconds` INTEGER")
            }
        }

        /** v7 → v8: FC media e massima del tratto corso, con la correzione a mano. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_feedback` ADD COLUMN `avgHr` INTEGER")
                db.execSQL("ALTER TABLE `workout_feedback` ADD COLUMN `maxHr` INTEGER")
            }
        }

        /** v8 → v9: struttura delle ripetute scritta dall'utente. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_feedback` ADD COLUMN `intervals` TEXT")
            }
        }

        /** v9 → v10: alimenti personali nel database (prima nelle preferenze). */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `custom_foods` (`nameKey` TEXT NOT NULL, `name` TEXT NOT NULL, `caloriesPer100g` INTEGER NOT NULL, `proteinPer100g` REAL NOT NULL, `carbsPer100g` REAL NOT NULL, `fatsPer100g` REAL NOT NULL, `category` TEXT NOT NULL, `brand` TEXT, `barcode` TEXT, `fiberPer100g` REAL, `sugarsPer100g` REAL, `saltPer100g` REAL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`nameKey`))")
            }
        }

        /** Tutte le migrazioni, in ordine: la catena completa dalla versione 1. Una nuova versione si aggiunge qui. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10
        )

        fun get(context: Context): UnicumDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    UnicumDatabase::class.java,
                    "unicum.db"
                ).addMigrations(*MIGRATIONS).build().also { instance = it }
            }

        /**
         * Solo per i test strumentati: fa usare all'app un database in memoria al posto di quello vero
         * (null = torna a quello vero). Così i test non toccano mai i dati dell'utente.
         */
        @androidx.annotation.VisibleForTesting
        fun useForTests(db: UnicumDatabase?) {
            synchronized(this) { instance = db }
        }
    }
}