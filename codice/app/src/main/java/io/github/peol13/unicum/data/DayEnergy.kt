package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.I18n

import java.time.LocalDate

/**
 * Calcoli dell'energia di una giornata, in un punto solo per Home, Diario e Statistiche.
 * Deficit = calorie bruciate − calorie assunte (negativo = surplus).
 */
object DayEnergy {

    /**
     * Calorie bruciate oltre il metabolismo basale: movimento e allenamenti.
     * Le calorie attive di Health Connect comprendono già gli allenamenti importati,
     * quindi si prende il maggiore dei due e si aggiungono solo gli allenamenti manuali.
     */
    fun extraBurn(activeKcalHealthConnect: Double?, importedWorkoutKcal: Int, manualWorkoutKcal: Int): Int =
        maxOf(activeKcalHealthConnect?.toInt() ?: 0, importedWorkoutKcal) + manualWorkoutKcal

    fun deficit(eatenKcal: Int, restingBurnKcal: Int, extraBurnKcal: Int): Int =
        restingBurnKcal + extraBurnKcal - eatenKcal

    /** Calorie ancora assumibili oggi: l'obiettivo è fisso, l'attività fisica non lo cambia. */
    fun remaining(goalKcal: Int, eatenKcal: Int): Int = goalKcal - eatenKcal

    /** Circa 7.700 kcal di deficit equivalgono a 1 kg di grasso corporeo (stima indicativa). */
    const val KCAL_PER_KG = 7700.0

    /** Variazione di peso stimata da un deficit totale: negativa = peso perso. */
    fun weightChangeKg(totalDeficitKcal: Int): Double = -totalDeficitKcal / KCAL_PER_KG

    /** "−0,4 kg", "+0,2 kg"; sotto i 100 g due decimali, così piccole variazioni non sembrano zero. */
    fun weightChangeText(kg: Double): String {
        val sign = if (kg < 0) "−" else "+"
        val abs = kotlin.math.abs(kg)
        return sign + String.format(I18n.locale, if (abs < 0.1) "%.2f" else "%.1f", abs) + " kg"
    }
}

/** Un giorno della settimana mostrata nelle Statistiche. I valori null significano "nessun dato". */
data class WeekDay(
    val date: LocalDate,
    val isToday: Boolean,
    val isFuture: Boolean,
    val eatenKcal: Int,
    val activityMinutes: Int,
    val workouts: Int,
    /** Deficit del giorno; null se non ci sono pasti registrati o il giorno non è ancora arrivato. */
    val deficitKcal: Int?,
    /** Il giorno conta per medie e peso stimato: sempre, oppure solo se segnato come completo (vedi UserSettings). */
    val countsForAverages: Boolean = true,
    /** Minuti dormiti nella notte che finisce in questo giorno. */
    val sleepMinutes: Int?,
    /** Inizio e fine di quella notte (millisecondi), per gli orari del sonno. */
    val sleepStartMillis: Long? = null,
    val sleepEndMillis: Long? = null,
    /** HRV notturna (ms) del giorno. */
    val hrv: Double? = null,
    /** Temperatura notturna (°C) del giorno; se la fonte dà solo lo scarto dalla base, vedi [tempIsDelta]. */
    val nightTemp: Double? = null,
    val tempIsDelta: Boolean = false,
    val steps: Int? = null
)
