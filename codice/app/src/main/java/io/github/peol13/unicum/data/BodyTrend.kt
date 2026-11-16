package io.github.peol13.unicum.data

import java.time.LocalDate

/**
 * Andamento del peso: tendenza (media mobile) e peso previsto dal deficit calorico accumulato.
 * Logica pura, con test.
 */
object BodyTrend {
    /** Calorie di deficit che corrispondono, in media, a un chilo di peso corporeo. */
    const val KCAL_PER_KG = 7700.0

    /**
     * Media mobile su [windowDays] giorni di calendario: per ogni giorno con una misura, la media delle misure
     * dei [windowDays] giorni che finiscono lì. Attenua le oscillazioni dovute all'acqua.
     */
    fun movingAverage(values: Map<LocalDate, Double>, windowDays: Int = 7): Map<LocalDate, Double> {
        val sorted = values.toSortedMap()
        return sorted.keys.associateWith { day ->
            sorted.subMap(day.minusDays(windowDays - 1L), day.plusDays(1)).values.average()
        }
    }

    /** Variazione della tendenza tra la prima e l'ultima misura (null se c'è una misura sola). */
    fun trendChange(values: Map<LocalDate, Double>, windowDays: Int = 7): Double? {
        val trend = movingAverage(values, windowDays)
        if (trend.size < 2) return null
        return trend.values.last() - trend.values.first()
    }

    /**
     * Peso previsto dal deficit: vale [startWeight] il giorno [start] e ogni giorno successivo scende di
     * deficit del giorno prima / 7700 kg. I giorni senza dato (null o assenti) non spostano la previsione.
     * Un valore per ogni giorno da [start] a [end] compresi.
     */
    fun projected(
        startWeight: Double,
        start: LocalDate,
        end: LocalDate,
        deficits: Map<LocalDate, Int?>
    ): Map<LocalDate, Double> {
        val out = LinkedHashMap<LocalDate, Double>()
        var weight = startWeight
        var day = start
        while (!day.isAfter(end)) {
            out[day] = weight
            weight -= (deficits[day] ?: 0) / KCAL_PER_KG
            day = day.plusDays(1)
        }
        return out
    }
}
