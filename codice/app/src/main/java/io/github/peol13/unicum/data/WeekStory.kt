package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.trp

import io.github.peol13.unicum.i18n.tr

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

    fun dayName(date: LocalDate): String =
        if (I18n.language == io.github.peol13.unicum.i18n.AppLanguage.IT) dayNames.getValue(date.dayOfWeek)
        else date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, I18n.locale)

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
            StoryPage.Kind.INTRO, tr("La tua settimana"), DateUtils.weekLabel(days.first().date), "",
            listOf(
                trp(mealDays.size, "%d giorno con i pasti", "%d giorni con i pasti"),
                trp(workouts, "%d allenamento", "%d allenamenti"),
                trp(nights.size, "%d notte registrata", "%d notti registrate")
            ).joinToString(", ") + "."
        )

        if (mealDays.isNotEmpty()) {
            val values = mealDays.map { it.deficitKcal!! }
            val avg = values.average().roundToInt()
            val reached = values.count { it >= deficitGoal }
            pages += StoryPage(
                StoryPage.Kind.DEFICIT, if (avg >= 0) tr("Deficit medio") else tr("Surplus medio"), fmt(abs(avg)), tr("kcal al giorno"),
                if (avg >= 0) (if (reached == 0) tr("Obiettivo di %,d kcal non ancora raggiunto in nessun giorno.", deficitGoal) + " "
                    else tr("Obiettivo di %,d kcal raggiunto in %s su %d.", deficitGoal, trp(reached, "%d giorno", "%d giorni"), values.size) + " ") +
                    tr("In tutta la settimana: %s kcal, circa %s di grasso.", fmt(values.sum()), kg(values.sum() / BodyTrend.KCAL_PER_KG))
                else tr("Hai mangiato più di quanto hai consumato: %s kcal di surplus in tutta la settimana.", fmt(-values.sum()))
            )
        }

        if (minutes > 0 || workouts > 0) {
            val best = days.maxByOrNull { it.activityMinutes }!!
            pages += StoryPage(
                StoryPage.Kind.ATTIVITA, tr("Attività"), minutes.toString(), tr("minuti"),
                (if (minutes >= WEEKLY_ACTIVITY_GOAL) tr("Obiettivo dei %d minuti settimanali raggiunto", WEEKLY_ACTIVITY_GOAL)
                else tr("Mancano %d minuti ai %d settimanali consigliati", WEEKLY_ACTIVITY_GOAL - minutes, WEEKLY_ACTIVITY_GOAL)) +
                    ", " + tr("con %s.", trp(workouts, "%d allenamento", "%d allenamenti")) + " " + tr("Il giorno più attivo: %s (%d min).", dayName(best.date), best.activityMinutes)
            )
        }

        if (nights.isNotEmpty()) {
            val avg = nights.map { it.sleepMinutes!! }.average().roundToInt()
            val best = nights.maxByOrNull { it.sleepMinutes!! }!!
            pages += StoryPage(
                StoryPage.Kind.SONNO, tr("Sonno medio"), duration(avg), tr("a notte"),
                tr("La notte migliore: %s (%s).", dayName(best.date), duration(best.sleepMinutes!!)) + " " +
                    if (avg >= 7 * 60) tr("Sei nella fascia delle 7-9 ore consigliate.") else tr("Sotto le 7 ore consigliate: c'è margine.")
            )
        }

        val hrvDays = days.filter { it.hrv != null }
        if (hrvDays.isNotEmpty()) {
            val avg = hrvDays.map { it.hrv!! }.average().roundToInt()
            val high = hrvDays.maxByOrNull { it.hrv!! }!!
            val low = hrvDays.minByOrNull { it.hrv!! }!!
            pages += StoryPage(
                StoryPage.Kind.RECUPERO, tr("HRV media"), avg.toString(), "ms",
                tr("Recupero migliore %s (%d ms), il più basso %s (%d ms).", dayName(high.date), high.hrv!!.roundToInt(), dayName(low.date), low.hrv!!.roundToInt())
                    
            )
        }

        if (weights.size >= 2) {
            val sorted = weights.toSortedMap()
            val change = sorted.values.last() - sorted.values.first()
            pages += StoryPage(
                StoryPage.Kind.PESO, tr("Peso"), kgSigned(change), "kg",
                tr("Da %s (%s kg) a %s (%s kg). Conta la tendenza di più settimane, non il singolo giorno.", dayName(sorted.firstKey()), kg1(sorted.values.first()), dayName(sorted.lastKey()), kg1(sorted.values.last()))
                    
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
                tr("Il sonno è il punto da curare: prova ad andare a letto 30 minuti prima per qualche sera.")
            minutes < WEEKLY_ACTIVITY_GOAL ->
                tr("Aggiungi una o due sessioni da 30 minuti: bastano per arrivare ai %d minuti.", WEEKLY_ACTIVITY_GOAL)
            deficitAvg != null && deficitAvg < goal * 0.6 ->
                tr("Il deficit è sotto l'obiettivo: registra tutti i pasti e tieni d'occhio gli spuntini.")
            deficits.isEmpty() ->
                tr("Registra i pasti ogni giorno: la prossima settimana potrai vedere anche il bilancio calorico.")
            else -> tr("Settimana equilibrata: mantieni lo stesso ritmo.")
        }
        return StoryPage(StoryPage.Kind.PROSSIMA, tr("Per la prossima settimana"), "", "", sentence)
    }


    private fun fmt(v: Int) = String.format(I18n.locale, "%,d", v)
    private fun duration(m: Int) = "${m / 60} h ${"%02d".format(m % 60)}"
    private fun kg(v: Double) = String.format(I18n.locale, "%.1f kg", v)
    private fun kg1(v: Double) = String.format(I18n.locale, "%.1f", v)
    private fun kgSigned(v: Double) = (if (v >= 0) "+" else "−") + kg1(abs(v))
}
