package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Prontezza all'allenamento (0-100%): come stai adesso, dalle ultime notti e da quello che hai fatto da quando sei
 * sveglio. È diversa dal recupero di [RecoveryEstimate], che guarda solo quanto manca a smaltire un allenamento preciso.
 * Logica pura, con test.
 *
 * **Al risveglio** ([compute]): formula lineare tarata sui valori di prontezza dell'app Ultrahuman dal 18 al 25/11/2026
 * (errore 0-6 punti):
 * - HRV della notte rispetto alla notte prima: +6,8 punti ogni 10% sopra. È quello che conta di più: dopo tre notti
 *   basse una notte che risale dà una prontezza alta, anche se è ancora vicina alla media;
 * - durata del sonno: +0,66 punti per ora (fino a 9,5 h);
 * - sonno profondo + REM: +2,5 punti per ora (fino a 5 h).
 * Le calorie attive di ieri non entrano: con 8 giorni il loro peso veniva zero (il 25/11, dopo la corsa del giorno
 * prima, Ultrahuman dava 90% e la vecchia formula, che le contava, 66%). Lo scarto dalla media delle notti dei
 * [BASE_DAYS] giorni prima si mostra ma non cambia il punteggio. La temperatura non entra: non spiegava niente (resta
 * l'avviso di [RecoveryAlerts]).
 *
 * **Durante il giorno** ([duringDay], solo per la notte di oggi), sottraendo al valore del risveglio:
 * - ogni allenamento finito dopo il risveglio: [PER_RECOVERY_HOUR] punti per ogni ora del suo recupero che resta
 *   (ore di [RecoveryEstimate.modelHours] × fattore personale, contate dalla fine dell'allenamento): scende subito
 *   dopo e risale man mano che passa il tempo; in tutto al massimo [MAX_WORKOUT_POINTS];
 * - giornata lunga: [PER_AWAKE_HOUR] punti per ogni ora da sveglio oltre le [AWAKE_FREE_HOURS], al massimo
 *   [MAX_AWAKE_POINTS].
 * La notte dopo si riparte dal valore del risveglio: quanto pesa ancora l'allenamento lo dicono HRV e sonno.
 */
object Readiness {
    private const val BASE_DAYS = 14L
    /** Fino a quanti giorni indietro si cerca la notte prima; se non c'è si confronta con la media. */
    private const val LAST_NIGHT_DAYS = 3L
    private const val CONSTANT = 56.9
    private const val PER_10_PCT_HRV_VS_LAST_NIGHT = 6.8
    private const val PER_HOUR_SLEEP = 0.66
    private const val PER_HOUR_RESTORATIVE = 2.5

    const val PER_RECOVERY_HOUR = 0.65
    const val MAX_WORKOUT_POINTS = 45.0
    const val AWAKE_FREE_HOURS = 16.0
    const val PER_AWAKE_HOUR = 2.0
    const val MAX_AWAKE_POINTS = 20.0
    private const val HOUR = 3_600_000.0

    class Result(
        /** Giorno del risveglio della notte usata. */
        val date: LocalDate,
        /** Prontezza adesso (uguale a [morningScore] finché non si applica [duringDay]). */
        val score: Int,
        val hrv: Double,
        /** Scarto % dell'HRV dalla media delle notti dei 14 giorni prima (solo da mostrare). */
        val hrvDeltaPct: Int,
        /** Scarto % dell'HRV dalla notte prima (o dalla media, se la notte prima manca): entra nel punteggio. */
        val hrvVsLastNightPct: Int,
        val sleepMinutes: Int,
        /** Profondo + REM. */
        val restorativeMinutes: Int,
        /** Ora del risveglio (fine della notte). */
        val wakeMillis: Long,
        /** Prontezza al risveglio. */
        val morningScore: Int = score,
        /** Punti tolti dagli allenamenti fatti dopo il risveglio (0 = nessuno o già recuperati). */
        val workoutPoints: Int = 0,
        /** Allenamenti dopo il risveglio che pesano ancora. */
        val workoutsToday: Int = 0,
        /** Punti tolti perché sei sveglio da più di [AWAKE_FREE_HOURS] ore. */
        val awakePoints: Int = 0,
        /** Ore da sveglio quando è stato calcolato [score] (0 = valore del risveglio). */
        val awakeHours: Double = 0.0
    ) {
        val level: String get() = levelLabel(score)
        val morningLevel: String get() = levelLabel(morningScore)
    }

    /** Fasce come quelle dell'anello: Riposo 0-29, Leggero 30-49, Moderato 50-69, Pronto 70-89, Ottimale 90-100. */
    fun levelLabel(score: Int): String = when {
        score < 30 -> tr("Riposo")
        score < 50 -> tr("Leggero")
        score < 70 -> tr("Moderato")
        score < 90 -> tr("Pronto")
        else -> tr("Ottimale")
    }

    /** Il punteggio al risveglio dai dati già preparati (vedi [Result]). */
    fun score(hrvVsLastNightPct: Double, sleepMinutes: Int, restorativeMinutes: Int): Int {
        val s = CONSTANT +
            PER_10_PCT_HRV_VS_LAST_NIGHT * hrvVsLastNightPct.coerceIn(-40.0, 40.0) / 10 +
            PER_HOUR_SLEEP * (sleepMinutes / 60.0).coerceAtMost(9.5) +
            PER_HOUR_RESTORATIVE * (restorativeMinutes / 60.0).coerceAtMost(5.0)
        return s.roundToInt().coerceIn(0, 100)
    }

    /** Punti tolti da un allenamento che richiede [recoveryHours] di recupero, [hoursSinceEnd] ore dopo la fine. */
    fun workoutPoints(recoveryHours: Double, hoursSinceEnd: Double): Double =
        PER_RECOVERY_HOUR * (recoveryHours - hoursSinceEnd.coerceAtLeast(0.0)).coerceAtLeast(0.0)

    /** Punti tolti dopo [hours] ore da sveglio. */
    fun awakePoints(hours: Double): Double =
        (PER_AWAKE_HOUR * (hours - AWAKE_FREE_HOURS)).coerceIn(0.0, MAX_AWAKE_POINTS)

    /**
     * Prontezza al risveglio del giorno [day] (la notte che finisce quel giorno). null se mancano la notte, la sua HRV o
     * almeno 3 notti affidabili ([HrvData.reliable]) nei 14 giorni prima.
     */
    fun compute(day: LocalDate, hrv: Map<LocalDate, Double>, nights: Map<LocalDate, SleepNight>): Result? {
        val h = hrv[day]?.takeIf { HrvData.reliable(day) } ?: return null
        val night = nights[day] ?: return null
        val base = hrv.filterKeys { it.isBefore(day) && !it.isBefore(day.minusDays(BASE_DAYS)) && HrvData.reliable(it) }.values
        if (base.size < 3) return null
        val mean = base.average()
        val pct = (h - mean) / mean * 100
        val last = (1..LAST_NIGHT_DAYS).map { day.minusDays(it) }
            .firstNotNullOfOrNull { d -> hrv[d]?.takeIf { HrvData.reliable(d) } }
        val vsLast = last?.let { (h - it) / it * 100 } ?: pct
        val restorative = night.deepMinutes + night.remMinutes
        return Result(
            date = day,
            score = score(vsLast, night.durationMinutes, restorative),
            hrv = h,
            hrvDeltaPct = kotlin.math.round(pct).toInt(),
            hrvVsLastNightPct = kotlin.math.round(vsLast).toInt(),
            sleepMinutes = night.durationMinutes,
            restorativeMinutes = restorative,
            wakeMillis = night.endMillis
        )
    }

    /**
     * La prontezza [now], partendo da quella al risveglio [morning]: tolti gli allenamenti finiti dopo il risveglio
     * (con il recupero che resta) e la giornata lunga. [personalFactor] è quello di [RecoveryEstimate].
     */
    fun duringDay(morning: Result, sessions: List<RecoveryEstimate.Session>, personalFactor: Double, now: Long): Result {
        val wake = morning.wakeMillis
        if (now <= wake) return morning
        var workout = 0.0
        var count = 0
        for (s in sessions) {
            if (s.endMillis <= wake || s.endMillis > now) continue
            val p = workoutPoints(RecoveryEstimate.modelHours(s.effort) * personalFactor, (now - s.endMillis) / HOUR)
            if (p > 0) { workout += p; count++ }
        }
        val awakeHours = (now - wake) / HOUR
        val w = workout.coerceAtMost(MAX_WORKOUT_POINTS).roundToInt()
        val a = awakePoints(awakeHours).roundToInt()
        return Result(
            date = morning.date,
            score = (morning.morningScore - w - a).coerceIn(0, 100),
            hrv = morning.hrv,
            hrvDeltaPct = morning.hrvDeltaPct,
            hrvVsLastNightPct = morning.hrvVsLastNightPct,
            sleepMinutes = morning.sleepMinutes,
            restorativeMinutes = morning.restorativeMinutes,
            wakeMillis = wake,
            morningScore = morning.morningScore,
            workoutPoints = w,
            workoutsToday = count,
            awakePoints = a,
            awakeHours = awakeHours
        )
    }

    /**
     * La prontezza più recente: dalla notte di [today] o, se non è ancora arrivata, da quella di ieri (allora resta il
     * valore del risveglio di ieri). Con [now] e la notte di oggi, si applica [duringDay].
     */
    fun latest(
        today: LocalDate,
        hrv: Map<LocalDate, Double>,
        nights: Map<LocalDate, SleepNight>,
        sessions: List<RecoveryEstimate.Session> = emptyList(),
        personalFactor: Double = 1.0,
        now: Long? = null
    ): Result? {
        val r = compute(today, hrv, nights) ?: return compute(today.minusDays(1), hrv, nights)
        return if (now != null) duringDay(r, sessions, personalFactor, now) else r
    }
}
