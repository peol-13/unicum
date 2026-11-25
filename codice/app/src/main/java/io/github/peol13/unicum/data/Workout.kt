package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.i18n.tk

import kotlinx.serialization.Serializable

/** Un valore di una serie (battito, velocità, cadenza) a [t] secondi dall'inizio dell'allenamento. */
@Serializable
data class WorkoutSample(val t: Int, val v: Float)

/** Un punto del percorso GPS a [t] secondi dall'inizio; [alt] in metri se la fonte lo dà. */
@Serializable
data class RoutePoint(val t: Int, val lat: Double, val lon: Double, val alt: Float? = null)

/** Un giro registrato dalla fonte (es. ogni km sull'orologio). */
@Serializable
data class WorkoutLap(val startS: Int, val endS: Int, val meters: Double? = null)

/** Stato del percorso GPS: presente, da autorizzare (Health Connect chiede il consenso), assente. */
enum class RouteStatus { DATA, CONSENT, NONE }

/**
 * Dettaglio completo di un allenamento importato da Health Connect: totali e serie nel tempo.
 * Si salva come JSON in `workout_details` (una riga per sessione) e non cambia dopo l'importazione.
 */
@Serializable
data class WorkoutDetail(
    val externalId: String,
    val source: String,
    val date: String,
    val type: String,
    val exerciseType: Int,
    val title: String? = null,
    val startMillis: Long,
    val endMillis: Long,
    /** Tempo in movimento: durata meno le pause registrate dalla fonte. */
    val movingSeconds: Int,
    /** true se la sessione è stata riconosciuta in automatico (es. camminate rilevate da Samsung Health). */
    val automatic: Boolean = false,
    val distanceM: Double? = null,
    val kcal: Double? = null,
    val steps: Long? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val minHr: Int? = null,
    val elevationGainM: Double? = null,
    /** VO₂ max (ml/kg/min) misurato nella sessione o, se manca, l'ultimo valore registrato prima. */
    val vo2max: Double? = null,
    val vo2maxFromSession: Boolean = false,
    val maxSpeedMs: Double? = null,
    /** Cadenza media (passi/min), salvata a parte perché il riepilogo non ha le serie. */
    val avgCadence: Int? = null,
    val hr: List<WorkoutSample> = emptyList(),
    val speed: List<WorkoutSample> = emptyList(),
    val cadence: List<WorkoutSample> = emptyList(),
    val route: List<RoutePoint> = emptyList(),
    val routeStatus: RouteStatus = RouteStatus.NONE,
    val laps: List<WorkoutLap> = emptyList(),
    val device: String? = null,
    /** Ultima modifica della sessione in Health Connect quando è stata letta per intero (null = da rileggere). */
    val modifiedMillis: Long? = null
) {
    val durationSeconds: Int get() = ((endMillis - startMillis) / 1000).toInt()

    /** Con la correzione a mano dell'utente (durata e distanza), se c'è. */
    fun corrected(f: WorkoutFeedback?): WorkoutDetail =
        if (f == null || !f.isCorrected) this
        else copy(
            distanceM = f.distanceM ?: distanceM, movingSeconds = f.movingSeconds ?: movingSeconds,
            avgHr = f.avgHr ?: avgHr, maxHr = f.maxHr ?: maxHr
        )
}

/** Una parte di una seduta a ripetute: dura [seconds] oppure copre [meters] (uno dei due). */
@Serializable
data class IntervalPart(val seconds: Int? = null, val meters: Double? = null) {
    val isSet: Boolean get() = (seconds ?: 0) > 0 || (meters ?: 0.0) > 0
}

/** Tipo di un pezzo della seduta (vedi [WorkoutAnalysis.IntervalKind] per le etichette). */
@Serializable
enum class StepKind { WARM_UP, WORK, REST, COOL_DOWN, LAP }

/** Un pezzo della seduta in ordine: [rep] = numero della ripetuta o del blocco. */
@Serializable
data class PlanStep(val kind: StepKind, val rep: Int? = null, val part: IntervalPart)

/**
 * Struttura di una seduta a ripetute: riscaldamento facoltativo, poi [reps] volte veloce + recupero; quello che
 * avanza alla fine è il defaticamento. Es. 10' + 3 × (4' veloce, 2' recupero). Scritta dall'utente nell'app
 * oppure ricavata dal programma di Notion ([fromPlan]): in quel caso ci sono i pezzi in ordine ([steps], vale
 * anche per i progressivi) e [offsetS] = secondi registrati prima dell'inizio della struttura.
 */
@Serializable
data class IntervalPlan(
    val warmUp: IntervalPart? = null,
    val reps: Int,
    val work: IntervalPart,
    val rest: IntervalPart? = null,
    /** FC media dei soli tratti veloci, calcolata al salvataggio: è lo sforzo vero per la stima dei 5 km. */
    val workHr: Int? = null,
    val steps: List<PlanStep>? = null,
    val offsetS: Int = 0,
    /** Nome della seduta del programma da cui viene la struttura (null = scritta nell'app). */
    val fromPlan: String? = null,
    /**
     * Struttura a giri liberi, come i "Giri" dell'orologio: [steps] sono i giri in ordine (durate o distanze
     * diverse), [StepKind.WORK] quelli segnati come veloci, [StepKind.LAP] gli altri; [PlanStep.rep] = numero del giro.
     */
    val laps: Boolean = false,
    /** true = giri letti da Health Connect (non salvati: si ricavano a ogni apertura). */
    val fromWatch: Boolean = false
) {
    /** I pezzi in ordine: quelli del programma oppure riscaldamento + reps × (veloce, recupero). */
    fun steps(): List<PlanStep> = steps ?: buildList {
        warmUp?.takeIf { it.isSet }?.let { add(PlanStep(StepKind.WARM_UP, null, it)) }
        for (r in 1..reps) {
            add(PlanStep(StepKind.WORK, r, work))
            rest?.takeIf { it.isSet }?.let { add(PlanStep(StepKind.REST, r, it)) }
        }
    }

    companion object {
        /** Giri in ordine, ognuno con la sua durata o distanza; [fast] = segnato come veloce. */
        fun ofLaps(parts: List<Pair<IntervalPart, Boolean>>, fromWatch: Boolean = false, offsetS: Int = 0): IntervalPlan? {
            val set = parts.filter { it.first.isSet }
            if (set.isEmpty()) return null
            val steps = set.mapIndexed { i, (p, fast) -> PlanStep(if (fast) StepKind.WORK else StepKind.LAP, i + 1, p) }
            return IntervalPlan(reps = steps.size, work = steps.first().part, steps = steps, laps = true, fromWatch = fromWatch, offsetS = offsetS)
        }

        /** I giri registrati dall'orologio, se la fonte li scrive in Health Connect (almeno due). */
        fun fromWatchLaps(laps: List<WorkoutLap>): IntervalPlan? {
            val sorted = laps.filter { it.endS > it.startS }.sortedBy { it.startS }
            if (sorted.size < 2) return null
            return ofLaps(sorted.map { IntervalPart(seconds = it.endS - it.startS) to false }, fromWatch = true, offsetS = sorted.first().startS.coerceAtLeast(0))
        }

        /** Struttura a passi (dal programma): reps e work riassumono i tratti veloci. */
        fun ofSteps(steps: List<PlanStep>, name: String?): IntervalPlan? {
            val work = steps.filter { it.kind == StepKind.WORK }
            if (work.isEmpty()) return null
            return IntervalPlan(
                warmUp = steps.firstOrNull()?.takeIf { it.kind == StepKind.WARM_UP }?.part,
                reps = work.size, work = work.first().part,
                rest = steps.firstOrNull { it.kind == StepKind.REST }?.part,
                steps = steps, fromPlan = name
            )
        }
    }
}

/**
 * Quello che l'utente aggiunge a mano a un allenamento: sforzo percepito (1–10), tipo di seduta, nota e,
 * se l'orologio ha registrato anche riscaldamento o cammino, durata e distanza corrette.
 */
@Serializable
data class WorkoutFeedback(
    val rpe: Int? = null,
    val kind: String? = null,
    val note: String? = null,
    val distanceM: Double? = null,
    val movingSeconds: Int? = null,
    /** FC media e massima del solo tratto corso (vedi [WorkoutAnalysis.findSegment]). */
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    /** Struttura delle ripetute scritta dall'utente (i giri dell'orologio non arrivano in Health Connect). */
    val intervals: IntervalPlan? = null,
    /**
     * Esercizi di una seduta di forza come sono stati fatti davvero (null = quelli del programma, che si seguono
     * se il programma cambia). Health Connect non ha né carichi né ripetizioni.
     */
    val exercises: List<Exercise>? = null
) {
    val isCorrected: Boolean get() = distanceM != null || movingSeconds != null

    companion object {
        private const val PLAN_PREFIX = "plan:"

        /**
         * Chiave delle sensazioni di una seduta del programma senza allenamento registrato (fatta senza orologio):
         * gli esercizi fatti si salvano sulla seduta invece che su una sessione di Health Connect.
         */
        fun planKey(plannedId: String): String = PLAN_PREFIX + plannedId
        fun isPlanKey(key: String): Boolean = key.startsWith(PLAN_PREFIX)

        /** Tipi di seduta della corsa: gli stessi del "Registro corse" su Notion. */
        val RUN_KINDS = listOf(tk("Facile"), tk("Lungo facile"), tk("Progressivo"), tk("Intervalli"), tk("Test"), tk("Libera"))

        /** Le etichette della prima versione, portate su quelle del registro. */
        fun normalizeKind(kind: String?): String? = when (kind) {
            "Lento", "Recupero" -> "Facile"
            "Lungo" -> "Lungo facile"
            "Ripetute" -> "Intervalli"
            "Gara" -> "Test"
            "Medio" -> "Libera"
            else -> kind
        }
    }
}

/** Nome di un tipo di seduta da mostrare: i tipi predefiniti dell'app tradotti, i tag di Notion così come sono. */
fun runKindLabel(kind: String): String = if (kind in WorkoutFeedback.RUN_KINDS) tr(kind) else kind
