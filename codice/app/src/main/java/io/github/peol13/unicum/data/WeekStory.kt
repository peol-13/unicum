package io.github.peol13.unicum.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Una pagina del racconto della settimana: un numero grande e una frase che lo spiega. */
data class StoryPage(
    val kind: Kind,
    val title: String,
    val value: String,
    val unit: String,
    val sentence: String
) {
    enum class Kind { INTRO, DEFICIT, ATTIVITA, SONNO, RECUPERO, PESO, PROSSIMA }
}

/**
 * Il riepilogo di una settimana finita, in pagine (deficit, attività, sonno, recupero, peso e un consiglio
 * per la settimana dopo). Le pagine senza dati non compaiono. Logica pura, con test.
 */
object WeekStory {
    const val WEEKLY_ACTIVITY_GOAL = 150

    private val dayNames = mapOf(
        DayOfWeek.MONDAY to "lunedì", DayOfWeek.TUESDAY to "martedì", DayOfWeek.WEDNESDAY to "mercoledì",
        DayOfWeek.THURSDAY to "giovedì", DayOfWeek.FRIDAY to "venerdì", DayOfWeek.SATURDAY to "sabato",
        DayOfWeek.SUNDAY to "domenica"
    )

    fun dayName(date: LocalDate): String = dayNames.getValue(date.dayOfWeek)

    /**
     * [days]: i sette giorni della settimana; [weights]: peso per giorno nella settimana (anche vuoto);
     * [deficitGoal]: deficit giornaliero obiettivo.
     */
    fun build(days: List<WeekDay>, weights: Map<LocalDate, Double>, deficitGoal: Int): List<StoryPage> {
        val pages = mutableListOf<StoryPage>()
        val mealDays = days.filter { it.deficitKcal != null }
        val minutes = days.sumOf { it.activityMinutes }
        val workouts = days.sumOf { it.workouts }
        val nights = days.filter { it.sleepMinutes != null }

        pages += StoryPage(
            StoryPage.Kind.INTRO, "La tua settimana", DateUtils.weekLabel(days.first().date), "",
            listOf(
                count(mealDays.size, "giorno con i pasti", "giorni con i pasti"),
                count(workouts, "allenamento", "allenamenti"),
                count(nights.size, "notte registrata", "notti registrate")
            ).joinToString(", ") + "."
        )

        if (mealDays.isNotEmpty()) {
            val values = mealDays.map { it.deficitKcal!! }
            val avg = values.average().roundToInt()
            val reached = values.count { it >= deficitGoal }
            pages += StoryPage(
                StoryPage.Kind.DEFICIT, if (avg >= 0) "Deficit medio" else "Surplus medio", fmt(abs(avg)), "kcal al giorno",
                if (avg >= 0) (if (reached == 0) "Obiettivo di $deficitGoal kcal non ancora raggiunto in nessun giorno. "
                    else "Obiettivo di $deficitGoal kcal raggiunto in $reached ${if (reached == 1) "giorno" else "giorni"} su ${values.size}. ") +
                    "In tutta la settimana: ${fmt(values.sum())} kcal, circa ${kg(values.sum() / BodyTrend.KCAL_PER_KG)} di grasso."
                else "Hai mangiato più di quanto hai consumato: ${fmt(-values.sum())} kcal di surplus in tutta la settimana."
            )
        }

        if (minutes > 0 || workouts > 0) {
            val best = days.maxByOrNull { it.activityMinutes }!!
            pages += StoryPage(
                StoryPage.Kind.ATTIVITA, "Attività", minutes.toString(), "minuti",
                (if (minutes >= WEEKLY_ACTIVITY_GOAL) "Obiettivo dei $WEEKLY_ACTIVITY_GOAL minuti settimanali raggiunto"
                else "Mancano ${WEEKLY_ACTIVITY_GOAL - minutes} minuti ai $WEEKLY_ACTIVITY_GOAL settimanali consigliati") +
                    ", con ${count(workouts, "allenamento", "allenamenti")}. Il giorno più attivo: ${dayName(best.date)} (${best.activityMinutes} min)."
            )
        }

        if (nights.isNotEmpty()) {
            val avg = nights.map { it.sleepMinutes!! }.average().roundToInt()
            val best = nights.maxByOrNull { it.sleepMinutes!! }!!
            pages += StoryPage(
                StoryPage.Kind.SONNO, "Sonno medio", duration(avg), "a notte",
                "La notte migliore: ${dayName(best.date)} (${duration(best.sleepMinutes!!)}). " +
                    if (avg >= 7 * 60) "Sei nella fascia delle 7-9 ore consigliate." else "Sotto le 7 ore consigliate: c'è margine."
            )
        }

        val hrvDays = days.filter { it.hrv != null }
        if (hrvDays.isNotEmpty()) {
            val avg = hrvDays.map { it.hrv!! }.average().roundToInt()
            val high = hrvDays.maxByOrNull { it.hrv!! }!!
            val low = hrvDays.minByOrNull { it.hrv!! }!!
            pages += StoryPage(
                StoryPage.Kind.RECUPERO, "HRV media", avg.toString(), "ms",
                "Recupero migliore ${dayName(high.date)} (${high.hrv!!.roundToInt()} ms), il più basso " +
                    "${dayName(low.date)} (${low.hrv!!.roundToInt()} ms)."
            )
        }

        if (weights.size >= 2) {
            val sorted = weights.toSortedMap()
            val change = sorted.values.last() - sorted.values.first()
            pages += StoryPage(
                StoryPage.Kind.PESO, "Peso", kgSigned(change), "kg",
                "Da ${dayName(sorted.firstKey())} (${kg1(sorted.values.first())} kg) a ${dayName(sorted.lastKey())} " +
                    "(${kg1(sorted.values.last())} kg). Conta la tendenza di più settimane, non il singolo giorno."
            )
        }

        pages += nextWeekPage(mealDays.map { it.deficitKcal!! }, deficitGoal, minutes, nights.map { it.sleepMinutes!! })
        return pages
    }

    /** Un solo consiglio per la settimana dopo: il punto più debole tra sonno, attività e deficit. */
    private fun nextWeekPage(deficits: List<Int>, goal: Int, minutes: Int, sleeps: List<Int>): StoryPage {
        val sleepAvg = sleeps.takeIf { it.isNotEmpty() }?.average()
        val deficitAvg = deficits.takeIf { it.isNotEmpty() }?.average()
        val sentence = when {
            sleepAvg != null && sleepAvg < 6.5 * 60 ->
                "Il sonno è il punto da curare: prova ad andare a letto 30 minuti prima per qualche sera."
            minutes < WEEKLY_ACTIVITY_GOAL ->
                "Aggiungi una o due sessioni da 30 minuti: bastano per arrivare ai $WEEKLY_ACTIVITY_GOAL minuti."
            deficitAvg != null && deficitAvg < goal * 0.6 ->
                "Il deficit è sotto l'obiettivo: registra tutti i pasti e tieni d'occhio gli spuntini."
            deficits.isEmpty() ->
                "Registra i pasti ogni giorno: la prossima settimana potrai vedere anche il bilancio calorico."
            else -> "Settimana equilibrata: mantieni lo stesso ritmo."
        }
        return StoryPage(StoryPage.Kind.PROSSIMA, "Per la prossima settimana", "", "", sentence)
    }

    private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
    private fun fmt(v: Int) = String.format(Locale.ITALY, "%,d", v)
    private fun duration(m: Int) = "${m / 60} h ${"%02d".format(m % 60)}"
    private fun kg(v: Double) = String.format(Locale.ITALY, "%.1f kg", v)
    private fun kg1(v: Double) = String.format(Locale.ITALY, "%.1f", v)
    private fun kgSigned(v: Double) = (if (v >= 0) "+" else "−") + kg1(abs(v))
}
