package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.tr

import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Un avviso sul recupero: cosa è cambiato rispetto alla tua media e cosa fare oggi. */
data class RecoveryAlert(
    val date: LocalDate,
    val title: String,
    val text: String,
    /** Calo dell'HRV rispetto alla media, in percentuale positiva (es. 24); null se l'HRV è nella norma. */
    val hrvDropPct: Int?,
    /** Aumento della temperatura notturna rispetto alla media, in °C; null se è nella norma. */
    val tempRise: Double?
)

/**
 * Variazioni anomale rispetto al proprio baseline: HRV sotto la media di almeno il 20% per due notti di fila
 * e/o temperatura notturna di almeno 0,5 °C sopra la media. Logica pura, con test.
 */
object RecoveryAlerts {
    const val HRV_DROP = 0.20
    const val TEMP_RISE = 0.5
    /** Notti di storico necessarie per una media affidabile. */
    const val MIN_BASELINE = 4
    private const val BASELINE_DAYS = 14L

    /**
     * Controlla la notte che finisce in [day] e quella prima. La media dell'HRV usa le notti dei 14 giorni prima
     * delle due controllate, quella della temperatura i 14 giorni prima di [day]. null = nessun avviso.
     */
    /**
     * L'avviso per il giorno [day] dai dati di salute già letti (HRV, temperatura notturna o, se la fonte dà solo lo
     * scarto, quello). Lo usano l'app e l'aggiornamento ad app chiusa.
     */
    fun fromHealth(data: HealthData, day: LocalDate): RecoveryAlert? {
        fun series(type: String) = data.metrics[type].orEmpty().mapKeys { LocalDate.parse(it.key) }
        val temp = series(MetricType.NIGHT_TEMP).ifEmpty { series(MetricType.NIGHT_TEMP_DELTA) }
        return check(series(MetricType.HRV), temp, day)
    }

    fun check(hrv: Map<LocalDate, Double>, temp: Map<LocalDate, Double>, day: LocalDate): RecoveryAlert? {
        val hrvDrop = hrvDrop(hrv, day)
        val tempRise = tempRise(temp, day)
        if (hrvDrop == null && tempRise == null) return null
        val drop = hrvDrop?.let { (it * 100).roundToInt() }
        val rise = tempRise?.let { String.format(I18n.locale, "+%.1f °C", it) }
        val (title, text) = when {
            drop != null && rise != null -> tr("Recupero basso da due notti") to
                tr("HRV sotto la tua media del %d%% da due notti e temperatura notturna %s: possibili segni di stress o di un inizio di malattia. Oggi meglio riposo, acqua e una notte lunga.", drop, rise)
                
            drop != null -> tr("HRV bassa da due notti") to
                tr("L'HRV è sotto la tua media del %d%% per due notti di fila: il corpo sta recuperando poco. Oggi allenamento leggero o riposo.", drop)
                
            else -> tr("Temperatura notturna più alta") to
                tr("Stanotte la temperatura è %s rispetto alla tua media. Tieni d'occhio come ti senti e riposa se serve.", rise)
        }
        return RecoveryAlert(day, title, text, drop, tempRise)
    }

    /** Calo relativo dell'HRV (0,24 = −24%) se entrambe le ultime due notti sono sotto soglia. */
    private fun hrvDrop(hrv: Map<LocalDate, Double>, day: LocalDate): Double? {
        val last = hrv[day] ?: return null
        val before = hrv[day.minusDays(1)] ?: return null
        val base = average(hrv, day.minusDays(1 + BASELINE_DAYS), day.minusDays(2)) ?: return null
        val limit = base * (1 - HRV_DROP)
        if (last > limit || before > limit) return null
        return 1 - maxOf(last, before) / base
    }

    private fun tempRise(temp: Map<LocalDate, Double>, day: LocalDate): Double? {
        val last = temp[day] ?: return null
        val base = average(temp, day.minusDays(BASELINE_DAYS), day.minusDays(1)) ?: return null
        val rise = last - base
        return rise.takeIf { it >= TEMP_RISE - 1e-9 }
    }

    /** Media dei valori tra [from] e [to] compresi, se ce ne sono abbastanza. */
    private fun average(values: Map<LocalDate, Double>, from: LocalDate, to: LocalDate): Double? {
        val inRange = values.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values
        return if (inRange.size >= MIN_BASELINE) inRange.average() else null
    }
}
