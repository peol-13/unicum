package io.github.peol13.unicum.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Raccoglie dal database quello che serve a [RecoveryEstimate]: gli allenamenti recenti senza doppioni (corse e
 * allenamenti avviati a mano, vedi [TrainingLoad.countsForRecovery]) con la fatica misurata, le notti, l'HRV affidabile e la temperatura notturna.
 */
object RecoveryData {

    class Loaded(
        val result: RecoveryEstimate.Result,
        /** FC massima usata per fatica e carico (vedi [TrainingLoad.personalMaxHr]). */
        val maxHr: Int,
        /** true = HRV della sola notte, false = media del giorno (finché la notte non è sincronizzata). */
        val nightHrv: Boolean
    )

    /** Giorni di sonno e temperatura da leggere (la base è di 7 giorni, lo storico HRV parte dal 28/10). */
    private const val HEALTH_DAYS = 60

    /**
     * Fatica già calcolata per ogni allenamento: serve il dettaglio completo (migliaia di campioni), quindi si
     * calcola una volta sola finché l'allenamento o la FC massima non cambiano.
     */
    private val sessionCache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<RecoveryEstimate.Session>>()

    private suspend fun session(context: Context, s: WorkoutDetail, maxHr: Int, all: List<WorkoutDetail>): RecoveryEstimate.Session? {
        val key = "${s.externalId}|$maxHr|${s.endMillis}|${s.movingSeconds}|${s.avgHr}|${s.modifiedMillis}"
        sessionCache[key]?.let { return it.orElse(null) }
        val d = UnicumRepository.loadWorkoutDetail(context, s.externalId, all) ?: s
        // Solo corse e allenamenti avviati a mano: quelli riconosciuti in automatico non contano
        val session = if (!TrainingLoad.countsForRecovery(d)) null
        else TrainingLoad.effort(d, maxHr)?.let { RecoveryEstimate.Session(s.externalId, s.type, LocalDate.parse(s.date), d.endMillis, it) }
        if (sessionCache.size > 500) sessionCache.clear()
        sessionCache[key] = java.util.Optional.ofNullable(session)
        return session
    }

    suspend fun load(context: Context, now: Long = System.currentTimeMillis()): Loaded? = withContext(Dispatchers.IO) {
        val all = UnicumRepository.loadWorkoutSummaries(context)
        val summaries = WorkoutAnalysis.dedupe(all)
        if (summaries.isEmpty()) return@withContext null
        val maxHr = TrainingLoad.personalMaxHr(UserSettings.age, summaries)
        val today = DateUtils.today()
        // Non più indietro dei giorni di sonno e HRV che si leggono: senza quelli un allenamento non dice niente
        val oldest = today.minusDays(HEALTH_DAYS.toLong())
        val from = HrvData.reliableFrom?.let { maxOf(minOf(it.minusDays(7), today.minusDays(6)), oldest) } ?: oldest
        val sessions = summaries.filter { !LocalDate.parse(it.date).isBefore(from) }.mapNotNull { session(context, it, maxHr, all) }

        val health = UnicumRepository.loadHealth(context, days = HEALTH_DAYS)
        fun series(type: String) = health.metrics[type].orEmpty().mapKeys { LocalDate.parse(it.key) }
        val day = series(MetricType.HRV)
        val night = series(MetricType.HRV_NIGHT)
        // La notte quando copre quasi tutti i giorni affidabili, se no la media del giorno
        val useNight = RecoveryEstimate.useNightHrv(day, night)
        val nights = health.sleep.associateBy { LocalDate.parse(it.date) }
        val temp = series(MetricType.NIGHT_TEMP).ifEmpty { series(MetricType.NIGHT_TEMP_DELTA) }

        val result = RecoveryEstimate.estimate(
            sessions = sessions,
            hrv = if (useNight) night else day,
            sleepEnd = nights.mapValues { it.value.endMillis },
            sleepMinutes = nights.mapValues { it.value.durationMinutes },
            nightTemp = temp,
            now = now
        )
        Loaded(result, maxHr, useNight)
    }
}
