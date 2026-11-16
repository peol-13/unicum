package io.github.peol13.unicum.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activities",
    indices = [
        Index("date"),
        Index(value = ["source", "externalId"], unique = true)
    ]
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String,
    val durationMinutes: Int,
    val kcal: Int,
    val source: String = "manual",
    val externalId: String? = null,
    val distanceMeters: Int? = null,
    val avgHeartRate: Int? = null,
    val startMinute: Int? = null
)