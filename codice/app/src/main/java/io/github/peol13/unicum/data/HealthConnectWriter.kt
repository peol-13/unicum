package io.github.peol13.unicum.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import io.github.peol13.unicum.data.db.MealEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * Scrive su Health Connect i pasti registrati in UNICUM (così compaiono anche in Samsung Health).
 * Ogni alimento diventa un NutritionRecord con un id stabile ("unicum-meal-<id>"): riscriverlo
 * lo aggiorna, non lo duplica. Senza il permesso di scrittura non fa nulla. Non solleva eccezioni.
 */
object HealthConnectWriter {
    val writePermission: String = HealthPermission.getWritePermission(NutritionRecord::class)

    private fun clientId(mealId: Long) = "unicum-meal-$mealId"

    private suspend fun client(context: Context): HealthConnectClient? {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return null
        val client = HealthConnectClient.getOrCreate(context)
        return if (writePermission in client.permissionController.getGrantedPermissions()) client else null
    }

    /** Scrive (o aggiorna) gli alimenti indicati. Restituisce quanti ne ha scritti. */
    suspend fun upsertMeals(context: Context, meals: List<MealEntity>): Int = withContext(Dispatchers.IO) {
        if (meals.isEmpty()) return@withContext 0
        guarded(0) {
            val client = client(context) ?: return@guarded 0
            val version = System.currentTimeMillis()
            meals.map { it.toRecord(version) }.chunked(100).forEach { client.insertRecords(it) }
            meals.size
        }
    }

    suspend fun deleteMeals(context: Context, mealIds: List<Long>) = withContext(Dispatchers.IO) {
        if (mealIds.isEmpty()) return@withContext
        guarded(Unit) {
            val client = client(context) ?: return@guarded
            client.deleteRecords(NutritionRecord::class, emptyList(), mealIds.map(::clientId))
        }
    }

    private suspend fun <T> guarded(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.printStackTrace()
        fallback
    }

    private fun MealEntity.toRecord(version: Long): NutritionRecord {
        // L'orario salvato vale come ora del giorno: lo si applica sempre alla data del pasto
        val start = Instant.ofEpochMilli(MealTime.toMillis(date, MealTime.fromMillis(createdAt)))
        val end = start.plusSeconds(60)
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        return NutritionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = offset,
            metadata = Metadata.manualEntry(clientId(id), version),
            energy = Energy.kilocalories(kcal.toDouble()),
            protein = Mass.grams(protein.toDouble()),
            totalCarbohydrate = Mass.grams(carbs.toDouble()),
            totalFat = Mass.grams(fats.toDouble()),
            dietaryFiber = fiber?.let { Mass.grams(it.toDouble()) },
            sugar = sugars?.let { Mass.grams(it.toDouble()) },
            // Health Connect vuole il sodio: sale = sodio × 2,5
            sodium = salt?.let { Mass.grams(it / 2.5) },
            name = if (grams > 0) "$foodName (${grams.toInt()} g)" else foodName,
            mealType = when (mealType) {
                "Colazione" -> MealType.MEAL_TYPE_BREAKFAST
                "Pranzo" -> MealType.MEAL_TYPE_LUNCH
                "Cena" -> MealType.MEAL_TYPE_DINNER
                // "Altro" e i pasti con un nome proprio (es. "Spuntino mattina")
                else -> MealType.MEAL_TYPE_SNACK
            }
        )
    }
}
