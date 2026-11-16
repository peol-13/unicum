package io.github.peol13.unicum.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "foods",
    indices = [Index("name")]
)
data class FoodEntity(
    @PrimaryKey val id: String,          // "off:<barcode>"
    val name: String,
    val brand: String?,
    val kcalPer100g: Float?,
    val proteinPer100g: Float?,
    val carbsPer100g: Float?,
    val fatsPer100g: Float?,
    val source: String,
    val fetchedAt: Long = System.currentTimeMillis(),
    // Per 100 g (versione 4); null se il prodotto non ha il dato
    val fiberPer100g: Float? = null,
    val sugarsPer100g: Float? = null,
    val saltPer100g: Float? = null
)