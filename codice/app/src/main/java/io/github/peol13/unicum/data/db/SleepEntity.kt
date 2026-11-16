package io.github.peol13.unicum.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una notte di sonno importata da una fonte esterna (es. Health Connect). */
@Entity(
    tableName = "sleep_sessions",
    indices = [
        Index("date"),
        Index(value = ["source", "externalId"], unique = true)
    ]
)
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,              // giorno del risveglio, "2026-10-12"
    val startMillis: Long,
    val endMillis: Long,
    val durationMinutes: Int,      // tempo effettivamente dormito (senza risvegli)
    val deepMinutes: Int,
    val remMinutes: Int,
    val lightMinutes: Int,
    val awakeMinutes: Int,
    val source: String,            // pacchetto dell'app che ha scritto il dato
    val externalId: String,
    val stages: String? = null     // fasi con orari, vedi SleepStages
)
