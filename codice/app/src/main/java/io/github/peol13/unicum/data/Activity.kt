package io.github.peol13.unicum.data

import kotlinx.serialization.Serializable

@Serializable
data class Activity(
    val type: String,
    val durationMinutes: Int,
    val calories: Int,
    val id: Long = 0,
    val source: String = "manual",
    val distanceMeters: Int? = null,
    val avgHeartRate: Int? = null,
    /** Orario di inizio, in minuti dalla mezzanotte; null per le voci vecchie. */
    val startMinute: Int? = null
) {
    val isManual: Boolean get() = source == "manual"
}
