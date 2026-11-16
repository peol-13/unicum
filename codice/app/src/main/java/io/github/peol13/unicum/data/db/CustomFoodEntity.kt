package io.github.peol13.unicum.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.peol13.unicum.data.Food

/**
 * Un alimento creato dall'utente (nome + valori per 100 g). Prima stava nelle preferenze; ora è nel database,
 * insieme a pasti e allenamenti, così sta nello stesso backup e si può ritrovare anche per codice a barre.
 */
@Entity(tableName = "custom_foods")
data class CustomFoodEntity(
    /** Il nome senza maiuscole né spazi ai lati: due alimenti con lo stesso nome (maiuscole a parte) sono uno solo. */
    @PrimaryKey val nameKey: String,
    val name: String,
    val caloriesPer100g: Int,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatsPer100g: Float,
    val category: String,
    val brand: String?,
    val barcode: String?,
    val fiberPer100g: Float?,
    val sugarsPer100g: Float?,
    val saltPer100g: Float?,
    /** Quando è stato salvato l'ultima volta: l'elenco va dal più recente. */
    val savedAt: Long
)

fun customFoodKey(name: String): String = name.trim().lowercase()

fun Food.toCustomEntity(savedAt: Long) = CustomFoodEntity(
    nameKey = customFoodKey(name), name = name.trim(), caloriesPer100g = caloriesPer100g,
    proteinPer100g = proteinPer100g, carbsPer100g = carbsPer100g, fatsPer100g = fatsPer100g,
    category = category, brand = brand, barcode = barcode,
    fiberPer100g = fiberPer100g, sugarsPer100g = sugarsPer100g, saltPer100g = saltPer100g, savedAt = savedAt
)

fun CustomFoodEntity.toFood() = Food(
    name = name, caloriesPer100g = caloriesPer100g, proteinPer100g = proteinPer100g, carbsPer100g = carbsPer100g,
    fatsPer100g = fatsPer100g, category = category, brand = brand, barcode = barcode,
    fiberPer100g = fiberPer100g, sugarsPer100g = sugarsPer100g, saltPer100g = saltPer100g
)
