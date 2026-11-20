package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** I numeri di un giorno usati per le correlazioni. null = dato mancante. */
data class DaySample(
    val date: LocalDate,
    /** Minuti dormiti nella notte che finisce in questo giorno. */
    val sleepMinutes: Int?,
    /** HRV della notte che finisce in questo giorno. */
    val hrv: Double?,
    val workouts: Int,
    val steps: Int?,
    val deficitKcal: Int?
)

/** Una relazione trovata nei dati: cosa cambia, di quanto e su quanti giorni. */
data class Insight(
    val title: String,
    val text: String,
    /** Numero di giorni nei due gruppi confrontati. */
    val daysWith: Int,
    val daysWithout: Int
)

/**
 * Correlazioni personali: confronta due gruppi di giorni (es. con e senza allenamento) e guarda come cambia
 * il recupero o il sonno della notte dopo. Mostra solo differenze abbastanza grandi e con almeno
 * [MIN_GROUP] giorni per gruppo. Sono correlazioni, non cause. Logica pura, con test.
 */
object InsightEngine {
    const val MIN_GROUP = 4
    /** Sotto queste soglie la differenza non viene mostrata. */
    const val MIN_HRV_PCT = 3.0
    const val MIN_SLEEP_MIN = 10.0

    fun compute(days: List<DaySample>): List<Insight> {
        val byDate = days.associateBy { it.date }
        fun next(d: DaySample) = byDate[d.date.plusDays(1)]
        val out = mutableListOf<Insight>()

        // 1. notti lunghe o corte -> HRV della stessa notte
        hrvInsight(
            days.filter { (it.sleepMinutes ?: 0) >= 7 * 60 }.mapNotNull { it.hrv },
            days.filter { it.sleepMinutes != null && it.sleepMinutes < 7 * 60 }.mapNotNull { it.hrv },
            tr("Sonno e HRV")
        ) { pct, more -> tr("Dopo le notti di almeno 7 ore la tua HRV è in media %s rispetto alle notti più corte.", more(pct)) }
            ?.let { out += it }

        // 2. allenamento -> HRV della notte dopo
        hrvInsight(
            days.filter { it.workouts > 0 }.mapNotNull { next(it)?.hrv },
            days.filter { it.workouts == 0 }.mapNotNull { next(it)?.hrv },
            tr("Allenamento e HRV")
        ) { pct, more -> tr("Nella notte dopo un allenamento la tua HRV è in media %s rispetto alle altre notti.", more(pct)) }
            ?.let { out += it }

        // 3. deficit forte -> HRV della notte dopo
        hrvInsight(
            days.filter { (it.deficitKcal ?: Int.MIN_VALUE) > 700 }.mapNotNull { next(it)?.hrv },
            days.filter { it.deficitKcal != null && it.deficitKcal <= 700 }.mapNotNull { next(it)?.hrv },
            tr("Deficit e HRV")
        ) { pct, more -> tr("Dopo i giorni con un deficit oltre 700 kcal la tua HRV è in media %s.", more(pct)) }
            ?.let { out += it }

        // 4. passi -> sonno della notte dopo
        sleepInsight(
            days.filter { (it.steps ?: -1) >= 8000 }.mapNotNull { next(it)?.sleepMinutes },
            days.filter { it.steps != null && it.steps < 8000 }.mapNotNull { next(it)?.sleepMinutes },
            tr("Passi e sonno")
        ) { diff -> tr("Dopo i giorni con almeno 8.000 passi dormi in media %s rispetto ai giorni più sedentari.", diff) }
            ?.let { out += it }

        // 5. allenamento -> sonno della notte dopo
        sleepInsight(
            days.filter { it.workouts > 0 }.mapNotNull { next(it)?.sleepMinutes },
            days.filter { it.workouts == 0 }.mapNotNull { next(it)?.sleepMinutes },
            tr("Allenamento e sonno")
        ) { diff -> tr("Nella notte dopo un allenamento dormi in media %s.", diff) }
            ?.let { out += it }

        return out
    }

    private fun hrvInsight(
        with: List<Double>,
        without: List<Double>,
        title: String,
        text: (Int, (Int) -> String) -> String
    ): Insight? {
        if (with.size < MIN_GROUP || without.size < MIN_GROUP) return null
        val pct = (with.average() - without.average()) / without.average() * 100
        if (abs(pct) < MIN_HRV_PCT) return null
        val rounded = pct.roundToInt()
        val more: (Int) -> String = { p -> if (p >= 0) tr("più alta del %d%%", p) else tr("più bassa del %d%%", -p) }
        return Insight(title, text(rounded, more), with.size, without.size)
    }

    private fun sleepInsight(with: List<Int>, without: List<Int>, title: String, text: (String) -> String): Insight? {
        if (with.size < MIN_GROUP || without.size < MIN_GROUP) return null
        val diff = with.average() - without.average()
        if (abs(diff) < MIN_SLEEP_MIN) return null
        val minutes = abs(diff).roundToInt()
        return Insight(title, text(if (diff > 0) tr("%d minuti in più", minutes) else tr("%d minuti in meno", minutes)), with.size, without.size)
    }
}
