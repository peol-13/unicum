package io.github.peol13.unicum.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Un obiettivo di corsa: [km] al ritmo [paceSec] (secondi al km) entro [deadline] (yyyy-MM-dd). */
@Serializable
data class RunGoal(val km: Double, val paceSec: Int, val deadline: String) {
    val targetSeconds: Int get() = (km * paceSec).roundToInt()
    val deadlineDate: LocalDate get() = LocalDate.parse(deadline)

    companion object {
        const val MIN_KM = 1.0
        const val MAX_KM = 50.0
        const val MIN_PACE = 150
        const val MAX_PACE = 900

        /** "6:30", "6'30", "6.30", "630" → 390 secondi al km; null se non è un ritmo sensato. */
        fun parsePace(text: String): Int? {
            val t = text.trim().replace("\"", "")
            val parts = t.split(':', '\'', '.', ',', ' ').filter { it.isNotEmpty() }
            val (m, s) = when {
                parts.size == 2 -> parts[0].toIntOrNull() to parts[1].toIntOrNull()
                parts.size == 1 && parts[0].length in 3..4 -> parts[0].dropLast(2).toIntOrNull() to parts[0].takeLast(2).toIntOrNull()
                parts.size == 1 -> parts[0].toIntOrNull() to 0
                else -> null to null
            }
            if (m == null || s == null || s !in 0..59) return null
            return (m * 60 + s).takeIf { it in MIN_PACE..MAX_PACE }
        }

        /** "5", "7,5", "21.1" → km; null fuori da 1–50 km. */
        fun parseKm(text: String): Double? =
            text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in MIN_KM..MAX_KM }
    }
}

/** I due obiettivi dell'utente: a breve e a lungo termine (null = non impostato). */
@Serializable
data class RunGoals(val short: RunGoal? = null, val long: RunGoal? = null) {
    val all: List<RunGoal> get() = listOfNotNull(short, long)

    /** L'obiettivo su questa distanza (se sono due, quello che scade prima tra quelli non scaduti). */
    fun forKm(km: Double, today: LocalDate): RunGoal? {
        val same = all.filter { abs(it.km - km) < 0.05 }
        return same.filter { !it.deadlineDate.isBefore(today) }.minByOrNull { it.deadline } ?: same.maxByOrNull { it.deadline }
    }

    companion object {
        /** Nessun obiettivo finché l'utente non ne sceglie uno (o arriva da personal.properties, vedi [PersonalSeed]). */
        val DEFAULT = RunGoals()
    }
}

/**
 * Come sta andando un obiettivo (logica pura, con test): la stima sulla distanza dell'obiettivo dopo ogni corsa,
 * la tendenza delle ultime settimane e dove si arriva alla scadenza se si continua così.
 */
object GoalProgress {
    /** Finestra della tendenza; se ci sono pochi punti si allarga a [TREND_DAYS_WIDE]. */
    private const val TREND_DAYS = 42L
    private const val TREND_DAYS_WIDE = 84L
    private const val MIN_POINTS = 3
    private const val MIN_SPAN_DAYS = 10
    /** Scarto (in frazione del tempo obiettivo) entro cui si è "in linea". */
    const val ON_TRACK_BAND = 0.02
    /** La tendenza non si proietta oltre il 10% della stima attuale. */
    private const val MAX_CHANGE = 0.10
    /**
     * I miglioramenti rallentano: il ritmo di oggi si dimezza in circa 3 settimane (costante di 30 giorni),
     * quindi una tendenza non si prolunga in linea retta fino alla scadenza.
     */
    private const val SLOWDOWN_DAYS = 30.0

    /** Giorni "a ritmo pieno" equivalenti a [days] giorni con il rallentamento: al massimo [SLOWDOWN_DAYS]. */
    fun effectiveDays(days: Int): Double =
        if (days <= 0) 0.0 else SLOWDOWN_DAYS * (1 - kotlin.math.exp(-days / SLOWDOWN_DAYS))

    enum class Status {
        /** La stima di oggi è già entro l'obiettivo. */
        REACHED,
        /** Continuando così si arriva alla scadenza con margine (oltre il 2%). */
        AHEAD,
        /** Continuando così si arriva all'obiettivo (entro il 2%). */
        ON_TRACK,
        /** Continuando così si arriva poco sopra (entro il 2%). */
        CLOSE,
        /** Continuando così non ci si arriva. */
        BEHIND,
        /** Stima c'è ma i punti recenti non bastano per una tendenza. */
        NO_TREND,
        /** Nessuna stima (servono le uscite facili e uno sforzo). */
        NO_DATA,
        /** Scaduto senza arrivarci. */
        MISSED
    }

    data class Progress(
        val goal: RunGoal,
        val target: Int,
        /** Stima sulla distanza dell'obiettivo dopo ogni corsa. */
        val points: List<Pair<LocalDate, Int>>,
        val current: Int?,
        /** Tendenza in secondi a settimana (negativo = migliora). */
        val perWeek: Double?,
        /** Secondi a settimana che servono per arrivarci (negativo = bisogna migliorare); null se già fatto o scaduto. */
        val neededPerWeek: Double?,
        /** Tempo previsto alla scadenza continuando così. */
        val projected: Int?,
        val daysLeft: Int,
        /** Distanza continua più lunga corsa finora. */
        val longestKm: Double,
        val status: Status
    ) {
        val expired: Boolean get() = daysLeft < 0
        /** Stima di oggi − obiettivo (positivo = più lento dell'obiettivo). */
        val gap: Int? get() = current?.let { it - target }
        /** Previsto alla scadenza − obiettivo. */
        val projectedGap: Int? get() = projected?.let { it - target }
    }

    /** Tendenza in secondi al giorno: retta dei minimi quadrati sui punti recenti; null se i punti non bastano. */
    fun trendPerDay(points: List<Pair<LocalDate, Int>>, today: LocalDate): Double? {
        for (days in listOf(TREND_DAYS, TREND_DAYS_WIDE)) {
            val recent = points.filter { ChronoUnit.DAYS.between(it.first, today) in 0..days }
            if (recent.size < MIN_POINTS) continue
            val xs = recent.map { it.first.toEpochDay().toDouble() }
            if (xs.max() - xs.min() < MIN_SPAN_DAYS) continue
            val ys = recent.map { it.second.toDouble() }
            val mx = xs.average()
            val my = ys.average()
            val den = xs.sumOf { (it - mx) * (it - mx) }
            if (den == 0.0) continue
            return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
        }
        return null
    }

    fun of(goal: RunGoal, runs: List<RunProgress.Run>, today: LocalDate): Progress {
        val target = goal.targetSeconds
        val points = RunProgress.estimateHistory(runs).map { it.date to RunProgress.atDistance(it.seconds5k, goal.km) }
        val current = points.lastOrNull()?.second
        val daysLeft = ChronoUnit.DAYS.between(today, goal.deadlineDate).toInt()
        val longest = RunProgress.longestKm(runs)
        if (current == null) return Progress(goal, target, points, null, null, null, null, daysLeft, longest, Status.NO_DATA)

        val perDay = trendPerDay(points, today)
        val projected = perDay?.let {
            val change = (it * effectiveDays(daysLeft)).coerceIn(-current * MAX_CHANGE, current * MAX_CHANGE)
            (current + change).roundToInt()
        }
        val needed = if (daysLeft > 0 && current > target) (target - current) / (daysLeft / 7.0) else null
        val status = when {
            daysLeft < 0 -> if (current <= target) Status.REACHED else Status.MISSED
            current <= target -> Status.REACHED
            projected == null -> Status.NO_TREND
            projected <= target * (1 - ON_TRACK_BAND) -> Status.AHEAD
            projected <= target -> Status.ON_TRACK
            projected <= target * (1 + ON_TRACK_BAND) -> Status.CLOSE
            else -> Status.BEHIND
        }
        return Progress(goal, target, points, current, perDay?.times(7), needed, projected, daysLeft, longest, status)
    }
}

/** Gli obiettivi salvati (preferenze `run_goals`), osservati dalla UI e inclusi nel backup. */
object RunGoalStore {
    private const val PREFS = "run_goals"
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var goals by mutableStateOf(RunGoals.DEFAULT)
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        goals = load(context)
    }

    /** Gli obiettivi salvati letti dalle preferenze (anche ad app chiusa, per il backup automatico). */
    fun load(context: Context): RunGoals =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", null)
            ?.let { runCatching { json.decodeFromString(RunGoals.serializer(), it) }.getOrNull() }
            ?: RunGoals.DEFAULT

    private fun save(new: RunGoals) {
        goals = new
        if (::prefs.isInitialized) prefs.edit().putString("data", json.encodeToString(RunGoals.serializer(), new)).apply()
    }

    /** [short] = true: l'obiettivo a breve termine; [goal] = null lo toglie. */
    fun set(short: Boolean, goal: RunGoal?) = save(if (short) goals.copy(short = goal) else goals.copy(long = goal))

    /** Dal backup: gli obiettivi del file sostituiscono quelli attuali. */
    fun importGoals(incoming: RunGoals) = save(incoming)
}
