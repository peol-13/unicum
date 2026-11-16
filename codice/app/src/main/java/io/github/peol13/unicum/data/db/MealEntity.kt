package io.github.peol13.unicum.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meal_entries",
    indices = [Index("date")]
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,              // "2026-10-12"
    val mealType: String,          // Colazione, Pranzo, Cena, Altro
    val foodId: String?,           // "off:8001234567890", null se inserito a mano
    val foodName: String,
    val grams: Float,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fats: Float,
    val source: String = "manual",
    // Orario del pasto: per le voci vecchie è l'ora di inserimento (si usa solo l'ora del giorno)
    val createdAt: Long = System.currentTimeMillis(),
    // Fibre, zuccheri e sale in grammi (versione 4); null se l'alimento non ha il dato
    val fiber: Float? = null,
    val sugars: Float? = null,
    val salt: Float? = null
)