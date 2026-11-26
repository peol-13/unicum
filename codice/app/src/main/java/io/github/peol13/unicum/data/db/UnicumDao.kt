package io.github.peol13.unicum.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Nota: i metodi del DAO non sono suspend. Con KSP 1.0.28 + Room 2.6.1 i metodi
// suspend con tipi generici generano codice Java che non compila. Le scritture
// vengono quindi eseguite dal repository su Dispatchers.IO.
@Dao
interface UnicumDao {

    // PASTI
    @Insert
    fun insertMeals(meals: List<MealEntity>): List<Long>

    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY createdAt")
    fun mealsForDate(date: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal_entries WHERE date BETWEEN :from AND :to ORDER BY date")
    fun mealsBetween(from: String, to: String): Flow<List<MealEntity>>

    @Query("DELETE FROM meal_entries WHERE id = :id")
    fun deleteMealById(id: Long): Int

    @Query("SELECT * FROM meal_entries WHERE id IN (:ids)")
    fun mealsByIds(ids: List<Long>): List<MealEntity>

    // createdAt è anche l'orario del pasto (modificabile dall'utente)
    @Query(
        "UPDATE meal_entries SET mealType = :mealType, grams = :grams, kcal = :kcal, protein = :protein, " +
            "carbs = :carbs, fats = :fats, fiber = :fiber, sugars = :sugars, salt = :salt, createdAt = :time WHERE id = :id"
    )
    fun updateMeal(
        id: Long, mealType: String, grams: Float, kcal: Float,
        protein: Float, carbs: Float, fats: Float, fiber: Float?, sugars: Float?, salt: Float?, time: Long
    ): Int

    // ATTIVITÀ
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertActivities(activities: List<ActivityEntity>): List<Long>

    @Query("SELECT * FROM activities WHERE date = :date ORDER BY id")
    fun activitiesForDate(date: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE date BETWEEN :from AND :to ORDER BY date")
    fun activitiesBetween(from: String, to: String): Flow<List<ActivityEntity>>

    @Query("DELETE FROM activities WHERE id = :id")
    fun deleteActivityById(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertActivities(activities: List<ActivityEntity>): List<Long>

    // DETTAGLIO ALLENAMENTI
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertWorkoutDetails(details: List<WorkoutDetailEntity>): List<Long>

    @Query("SELECT externalId FROM workout_details")
    fun workoutDetailIds(): List<String>

    @Query("SELECT externalId FROM activities WHERE externalId IS NOT NULL")
    fun activityExternalIds(): List<String>

    /** Allenamenti importati che si sono rivelati copie di un altro (stessa sessione scritta da due app). */
    @Query("DELETE FROM activities WHERE externalId IN (:ids)")
    fun deleteActivitiesByExternalIds(ids: List<String>): Int

    /** Riepiloghi (senza le serie, che servono solo nella pagina del singolo allenamento), dal più recente. */
    @Query("SELECT summary FROM workout_details WHERE automatic = 0 ORDER BY startMillis DESC")
    fun workoutSummaries(): List<String>

    /** Tutti i riepiloghi, anche delle sessioni automatiche (per sapere quali sono già lette). */
    @Query("SELECT summary FROM workout_details")
    fun allWorkoutSummaries(): List<String>

    @Query("SELECT summary FROM workout_details WHERE externalId = :id LIMIT 1")
    fun workoutSummary(id: String): String?

    @Query("SELECT externalId FROM workout_details WHERE date >= :from")
    fun workoutDetailIdsFrom(from: String): List<String>

    @Query("DELETE FROM workout_details WHERE externalId IN (:ids)")
    fun deleteWorkoutDetails(ids: List<String>): Int

    @Query("SELECT json FROM workout_details WHERE externalId = :id LIMIT 1")
    fun workoutDetailJson(id: String): String?

    @Upsert
    fun upsertWorkoutFeedback(feedback: WorkoutFeedbackEntity)

    @Query("SELECT * FROM workout_feedback")
    fun allWorkoutFeedback(): List<WorkoutFeedbackEntity>

    // SONNO
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSleep(sessions: List<SleepEntity>): List<Long>

    @Query("SELECT * FROM sleep_sessions WHERE date BETWEEN :from AND :to ORDER BY endMillis")
    fun sleepBetween(from: String, to: String): Flow<List<SleepEntity>>

    @Query("DELETE FROM sleep_sessions WHERE id IN (:ids)")
    fun deleteSleepByIds(ids: List<Long>): Int

    // METRICHE GIORNALIERE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMetrics(metrics: List<DailyMetricEntity>): List<Long>

    @Query("SELECT * FROM daily_metrics WHERE date BETWEEN :from AND :to")
    fun metricsBetween(from: String, to: String): Flow<List<DailyMetricEntity>>

    // CATALOGO ALIMENTI
    @Upsert
    fun upsertFoods(foods: List<FoodEntity>): List<Long>

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :query || '%' LIMIT 50")
    fun searchFoods(query: String): List<FoodEntity>

    // Prodotti salvati dalla ricerca online
    @Query("SELECT * FROM foods WHERE name LIKE '%' || :word || '%' OR brand LIKE '%' || :word || '%' ORDER BY fetchedAt DESC LIMIT 300")
    fun cachedFoodsLike(word: String): List<FoodEntity>

    @Query("SELECT * FROM foods ORDER BY fetchedAt DESC LIMIT 50")
    fun recentCachedFoods(): List<FoodEntity>

    // BACKUP
    @Query("SELECT * FROM meal_entries")
    fun allMeals(): List<MealEntity>

    @Query("SELECT * FROM activities")
    fun allActivities(): List<ActivityEntity>

    @Query("SELECT * FROM foods")
    fun allFoods(): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM meal_entries WHERE date = :date AND mealType = :mealType")
    fun countMealsOfType(date: String, mealType: String): Int

    /** Pasti "Altro", anche quelli con un nome proprio (es. "Spuntino mattina"). */
    @Query("SELECT COUNT(*) FROM meal_entries WHERE date = :date AND mealType NOT IN ('Colazione', 'Pranzo', 'Cena')")
    fun countOtherMeals(date: String): Int

    /** Le ultime voci registrate in un tipo di pasto (per proporre gli alimenti già usati lì). */
    @Query("SELECT * FROM meal_entries WHERE mealType = :mealType COLLATE NOCASE ORDER BY date DESC, id DESC LIMIT 300")
    fun recentMealsOfType(mealType: String): List<MealEntity>

    @Query("SELECT * FROM meal_entries WHERE mealType NOT IN ('Colazione', 'Pranzo', 'Cena') ORDER BY date DESC, id DESC LIMIT 300")
    fun recentOtherMeals(): List<MealEntity>

    /** Gli alimenti mai registrati (una voce per nome, la più recente), dal più recente. */
    @Query("SELECT * FROM meal_entries WHERE id IN (SELECT MAX(id) FROM meal_entries GROUP BY foodName) ORDER BY date DESC, id DESC LIMIT 400")
    fun latestEntryPerFood(): List<MealEntity>

    /** Nomi dei pasti già usati, dal più recente (per riproporre i nomi dei pasti "Altro"). */
    @Query("SELECT mealType FROM meal_entries GROUP BY mealType ORDER BY MAX(date) DESC, MAX(id) DESC LIMIT 40")
    fun recentMealTypes(): List<String>

    // ALIMENTI PERSONALI
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCustomFoods(foods: List<CustomFoodEntity>): List<Long>

    @Query("SELECT * FROM custom_foods ORDER BY savedAt DESC")
    fun allCustomFoods(): List<CustomFoodEntity>

    @Query("SELECT nameKey FROM custom_foods")
    fun customFoodKeys(): List<String>

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    fun foodById(id: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE name IN (:names)")
    fun foodsByNames(names: List<String>): List<FoodEntity>
}
