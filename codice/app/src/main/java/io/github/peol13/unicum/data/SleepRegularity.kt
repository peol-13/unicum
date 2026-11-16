package io.github.peol13.unicum.data

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Orari del sonno: a che ora ti addormenti e ti svegli in media, quanto variano, e la fascia consigliata
 * (8 ore prima della sveglia abituale più il tempo per addormentarsi). Logica pura, con test.
 *
 * Gli orari si contano in "minuti della notte": dalla mezzanotte del giorno prima, con le ore del mattino
 * dopo le 24 (00:30 = 1470), così 23:30 viene prima di 00:30 e le medie non saltano a mezzanotte.
 */
object SleepRegularity {
    const val SLEEP_GOAL_MIN = 8 * 60
    const val FALL_ASLEEP_MIN = 15

    data class Summary(
        val avgBed: Int,
        val avgWake: Int,
        /** Deviazione standard dell'orario di addormentamento, in minuti. */
        val bedSpread: Int,
        val recommendedBed: Int,
        val recommendedWake: Int,
        val nights: Int
    )

    /** Minuto della notte di un istante: le ore prima di mezzogiorno vanno dopo le 24. */
    fun nightMinute(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        val m = t.hour * 60 + t.minute
        return if (m < 12 * 60) m + 24 * 60 else m
    }

    /** "23:40" per un minuto della notte (anche oltre le 24). */
    fun clock(nightMinute: Int): String {
        val m = ((nightMinute % 1440) + 1440) % 1440
        return MealTime.format(m)
    }

    /** Riepilogo delle notti date come (inizio, fine) in millisecondi; null se non ce ne sono. */
    fun summarize(nights: List<Pair<Long, Long>>, zone: ZoneId = ZoneId.systemDefault()): Summary? {
        if (nights.isEmpty()) return null
        val beds = nights.map { nightMinute(it.first, zone) }
        val wakes = nights.map { nightMinute(it.second, zone) }
        val meanBed = beds.average()
        val spread = sqrt(beds.map { (it - meanBed) * (it - meanBed) }.average())
        val avgWake = wakes.average().roundToInt()
        return Summary(
            avgBed = meanBed.roundToInt(),
            avgWake = avgWake,
            bedSpread = spread.roundToInt(),
            recommendedBed = avgWake - SLEEP_GOAL_MIN - FALL_ASLEEP_MIN,
            recommendedWake = avgWake,
            nights = nights.size
        )
    }
}
