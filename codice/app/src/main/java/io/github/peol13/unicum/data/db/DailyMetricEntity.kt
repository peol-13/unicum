package io.github.peol13.unicum.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un valore giornaliero (passi, HRV, battito a riposo...) per fonte.
 * Un solo valore per giorno, tipo e fonte: una nuova sincronizzazione lo sostituisce.
 */
@Entity(
    tableName = "daily_metrics",
    indices = [Index(value = ["date", "type", "source"], unique = true)]
)
data class DailyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String,              // vedi MetricType
    val value: Double,
    val source: String
)
