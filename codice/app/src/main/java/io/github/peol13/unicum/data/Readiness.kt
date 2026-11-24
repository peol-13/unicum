package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Prontezza all'allenamento (0-100%): come stai oggi in generale, dalle ultime notti e dall'attività di ieri.
 * È diversa dal recupero di [RecoveryEstimate], che guarda solo quanto manca a smaltire un allenamento preciso.
 * Logica pura, con test.
 *
 * Formula lineare tarata sui valori di prontezza dell'app Ultrahuman dal 18 al 24/11/2026 (errore 0-5 punti):
 * - HRV della notte rispetto alla media delle notti dei [BASE_DAYS] giorni prima: +3 punti ogni 10% sopra;
 * - durata del sonno: +2,26 punti per ora (fino a 9,5 h);
 * - sonno profondo + REM: +8,65 punti per ora (fino a 5 h);
 * - calorie attive di ieri rispetto alla tua media dei 14 giorni prima: −4,46 punti ogni 100 kcal in più.
 * Il peso dell'HRV è fissato a mano: in quei 7 giorni l'HRV era quasi sempre sotto la media e dai soli dati non si
 * ricavava (veniva zero). La temperatura non entra: non spiegava niente (resta l'avviso di [RecoveryAlerts]).
 */
object Readiness {
    private const val BASE_DAYS = 14L
    private const val CONSTANT = 20.9
    private const val PER_10_PCT_HRV = 3.0
    private const val PER_HOUR_SLEEP = 2.26
    private const val PER_HOUR_RESTORATIVE = 8.65
    private const val PER_100_KCAL = -4.46

    class Result(
        /** Giorno del risveglio della notte usata. */
        val date: LocalDate,
        val score: Int,
        val hrv: Double,
        /** Scarto % dell'HRV dalla media delle notti prima. */
        val hrvDeltaPct: Int,
        val sleepMinutes: Int,
        /** Profondo + REM. */
        val restorativeMinutes: Int,
        /** Calorie attive di ieri rispetto alla media; null se mancano i dati. */
        val kcalDelta: Int?
    ) {
        val level: String get() = levelLabel(score)
    }

    /** Fasce come quelle dell'anello: Riposo 0-29, Leggero 30-49, Moderato 50-69, Pronto 70-89, Ottimale 90-100. */
    fun levelLabel(score: Int): String = when {
        score < 30 -> tr("Riposo")
        score < 50 -> tr("Leggero")
        score < 70 -> tr("Moderato")
        score < 90 -> tr("Pronto")
        else -> tr("Ottimale")
    }

    /** Il punteggio dai dati già preparati (vedi [Result]). */
    fun score(hrvDeltaPct: Double, sleepMinutes: Int, restorativeMinutes: Int, kcalDelta: Double?): Int {
        val s = CONSTANT +
            PER_10_PCT_HRV * hrvDeltaPct.coerceIn(-40.0, 40.0) / 10 +
            PER_HOUR_SLEEP * (sleepMinutes / 60.0).coerceAtMost(9.5) +
            PER_HOUR_RESTORATIVE * (restorativeMinutes / 60.0).coerceAtMost(5.0) +
            PER_100_KCAL * ((kcalDelta ?: 0.0) / 100).coerceIn(-5.0, 5.0)
        return s.roundToInt().coerceIn(0, 100)
    }

    /**
     * Prontezza del giorno [day] (la notte che finisce quel giorno). null se mancano la notte, la sua HRV o almeno
     * 3 notti affidabili ([HrvData.reliable]) nei 14 giorni prima.
     */
    fun compute(
        day: LocalDate, hrv: Map<LocalDate, Double>, nights: Map<LocalDate, SleepNight>, activeKcal: Map<LocalDate, Double>
    ): Result? {
        val h = hrv[day]?.takeIf { HrvData.reliable(day) } ?: return null
        val night = nights[day] ?: return null
        val base = hrv.filterKeys { it.isBefore(day) && !it.isBefore(day.minusDays(BASE_DAYS)) && HrvData.reliable(it) }.values
        if (base.size < 3) return null
        val mean = base.average()
        val pct = (h - mean) / mean * 100
        val yesterday = day.minusDays(1)
        val kcalBase = activeKcal.filterKeys { it.isBefore(yesterday) && !it.isBefore(yesterday.minusDays(BASE_DAYS)) }.values
        val kcalDelta = activeKcal[yesterday]?.takeIf { kcalBase.size >= 3 }?.let { it - kcalBase.average() }
        val restorative = night.deepMinutes + night.remMinutes
        return Result(
            date = day,
            score = score(pct, night.durationMinutes, restorative, kcalDelta),
            hrv = h,
            hrvDeltaPct = kotlin.math.round(pct).toInt(),
            sleepMinutes = night.durationMinutes,
            restorativeMinutes = restorative,
            kcalDelta = kcalDelta?.roundToInt()
        )
    }

    /** La prontezza più recente: dalla notte di [today] o, se non è ancora arrivata, da quella di ieri. */
    fun latest(
        today: LocalDate, hrv: Map<LocalDate, Double>, nights: Map<LocalDate, SleepNight>, activeKcal: Map<LocalDate, Double>
    ): Result? = compute(today, hrv, nights, activeKcal) ?: compute(today.minusDays(1), hrv, nights, activeKcal)
}
