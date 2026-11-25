package io.github.peol13.unicum.data

import android.content.Context
import kotlin.math.abs

/**
 * Quello che si sa di un allenamento (corsa o forza) oltre ai numeri di Health Connect, da tre fonti (il programma è quello di
 * [TrainingPlan]: Notion, oppure importato da PDF / scritto nell'app, e allora il registro non c'è):
 * - **Registro corse** di Notion: tipo, RPE e nota. Se ci sono, **vincono** su quelli scritti nell'app.
 * - **Programma corse** di Notion: per le sedute a intervalli o progressive la struttura divide la corsa
 *   (se nell'app non sono state scritte le ripetute a mano, che restano più precise).
 * - Sensazioni scritte nell'app ([WorkoutFeedback]): correzioni di durata/distanza, ripetute, e tipo/RPE/nota
 *   finché in Notion mancano. Senza tipo né nel registro né nell'app vale quello della seduta programmata.
 */
object RunFeedback {

    /** Il risultato per una corsa: [feedback] = da usare ovunque; [logged]/[planned] = righe Notion collegate. */
    data class Effective(
        val feedback: WorkoutFeedback,
        val logged: LoggedRun?,
        val planned: PlannedRun?,
        /** Ripetute ricavate dal programma (null se scritte nell'app o se la seduta non ha struttura). */
        val autoPlan: IntervalPlan?
    )

    private fun sameId(a: String?, b: String?) = a != null && b != null && a.replace("-", "") == b.replace("-", "")

    /**
     * Riga del registro per ogni corsa (chiave: externalId): stesso giorno; se quel giorno ci sono più corse
     * o più righe, si abbinano per distanza più vicina.
     */
    fun matchLog(runs: List<WorkoutDetail>, log: List<LoggedRun>): Map<String, LoggedRun> {
        val out = mutableMapOf<String, LoggedRun>()
        // Corse con le righe di corsa, allenamenti di forza con le righe di forza (dal tipo o dal nome)
        runs.groupBy { familyOf(it) }.forEach { (family, list) ->
            out += matchLogSameSport(list, log.filter { Sport.infer(it.kind, it.name).family == family })
        }
        return out
    }

    private fun familyOf(w: WorkoutDetail): Sport.Family = (Sport.ofWorkout(w.type) ?: Sport.RUN).family

    private fun matchLogSameSport(runs: List<WorkoutDetail>, log: List<LoggedRun>): Map<String, LoggedRun> {
        val out = mutableMapOf<String, LoggedRun>()
        val byDay = runs.groupBy { it.date }
        log.filter { it.date != null }.groupBy { it.date!! }.forEach { (day, entries) ->
            val free = byDay[day].orEmpty().toMutableList()
            entries.sortedByDescending { it.km != null }.forEach { e ->
                val pick = if (e.km != null) free.minByOrNull { abs((it.distanceM ?: 0.0) / 1000 - e.km) } else free.minByOrNull { it.startMillis }
                if (pick != null) { out[pick.externalId] = e; free.remove(pick) }
            }
        }
        return out
    }

    /** La seduta del programma: quella collegata nel registro oppure quella dello stesso giorno e dello stesso sport. */
    fun plannedFor(run: WorkoutDetail, logged: LoggedRun?, plan: List<PlannedRun>): PlannedRun? =
        logged?.plannedId?.let { id -> plan.firstOrNull { sameId(it.id, id) } }
            ?: plan.firstOrNull { it.date == run.date && it.family == familyOf(run) }

    /**
     * Tipo, RPE e nota del registro sopra quelli dell'app; il resto (correzioni, ripetute) resta dell'app.
     * Se il tipo manca sia nel registro sia nell'app vale quello della seduta del programma ([planned]).
     */
    fun merge(app: WorkoutFeedback?, logged: LoggedRun?, planned: PlannedRun? = null): WorkoutFeedback {
        val base = app ?: WorkoutFeedback()
        return base.copy(
            kind = logged?.kind ?: base.kind ?: planned?.kind,
            rpe = logged?.rpe ?: base.rpe,
            note = logged?.note ?: base.note,
            // Esercizi scritti nel Registro di Notion (sedute di forza): vincono su quelli corretti nell'app
            exercises = logged?.exercises?.takeIf { it.isNotEmpty() } ?: base.exercises
        )
    }

    /** I pezzi della struttura del programma, se la seduta è a intervalli o progressiva. */
    fun planSteps(kind: String?, planned: PlannedRun?): List<PlanStep>? {
        if (planned == null) return null
        // Conta il tipo della corsa fatta (registro o app); se manca, quello previsto
        if ((kind ?: planned.kind) !in PlanStructure.STRUCTURED_KINDS) return null
        return PlanStructure.parse(planned.structure)
    }

    /**
     * Pezzi del programma sulla registrazione [raw] (con le serie), con la FC dei tratti veloci: i cambi si cercano nei
     * dati veri ([LapDetection]); senza serie di velocità, pezzi a durata fissa da dove la corsa li segue meglio.
     * [strict] = false per una seduta che non è segnata come Intervalli o Progressivo: la struttura vale solo se tutti
     * i cambi sono sicuri (una corsa davvero facile non si spezza a forza).
     */
    fun autoPlan(raw: WorkoutDetail, steps: List<PlanStep>, planned: PlannedRun, strict: Boolean = true): IntervalPlan? {
        LapDetection.detect(raw, steps, planned.name)?.let { found ->
            if (!strict && found.uncertainChanges > 0) return null
            return WorkoutAnalysis.withWorkHr(raw, found)
        }
        if (!strict) return null
        val plan = IntervalPlan.ofSteps(steps, planned.name) ?: return null
        return WorkoutAnalysis.withWorkHr(raw, plan.copy(offsetS = WorkoutAnalysis.alignPlan(raw, steps)))
    }

    // Le ripetute dal programma richiedono le serie complete: si calcolano una volta per corsa e struttura
    private val autoCache: MutableMap<String, IntervalPlan?> = java.util.Collections.synchronizedMap(mutableMapOf())

    /**
     * Il quadro completo per le corse [runs] (riepiloghi): unisce registro, programma e sensazioni dell'app.
     * Per le sedute strutturate legge il dettaglio completo della corsa (solo la prima volta).
     */
    suspend fun load(context: Context, runs: List<WorkoutDetail>): Map<String, Effective> {
        val app = UnicumRepository.loadWorkoutFeedback(context)
        val logged = matchLog(runs, TrainingPlan.log)
        val plan = TrainingPlan.runs
        return runs.associate { w ->
            val l = logged[w.externalId]
            val planned = plannedFor(w, l, plan)
            val merged = merge(app[w.externalId], l, planned)
            var auto: IntervalPlan? = null
            if (merged.intervals == null) {
                // Seduta a intervalli o progressiva: struttura sempre; altrimenti solo con cambi sicuri nei dati
                val strictSteps = planSteps(merged.kind, planned)
                val steps = strictSteps ?: planned?.let { PlanStructure.parse(it.structure) }
                if (steps != null && planned != null) {
                    val key = "${w.externalId}|${planned.structure}|${strictSteps != null}"
                    auto = if (autoCache.containsKey(key)) autoCache[key] else {
                        UnicumRepository.loadWorkoutDetail(context, w.externalId)?.let { autoPlan(it, steps, planned, strictSteps != null) }
                            .also { autoCache[key] = it }
                    }
                }
            }
            w.externalId to Effective(if (auto != null) merged.copy(intervals = auto) else merged, l, planned, auto)
        }
    }
}
