package io.github.peol13.unicum.data

import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AdviceCategory(val label: String) {
    GIORNATA("La tua giornata"),
    ATTIVITA("Attività"),
    SONNO("Sonno"),
    ALIMENTAZIONE("Alimentazione")
}

/** Un consiglio con il motivo che l'ha generato (così si capisce da quali dati arriva). */
data class Advice(
    val category: AdviceCategory,
    val title: String,
    val text: String,
    val reason: String? = null
)

/** Dati su cui si basano i consigli. I valori null significano "dato non disponibile". */
data class AdviceInput(
    val lastNight: SleepNight? = null,
    /** Ultime notti, dalla più vecchia alla più recente. */
    val recentNights: List<SleepNight> = emptyList(),
    val hrv: Double? = null,
    val hrvBaseline: Double? = null,
    /** Minuti di allenamento negli ultimi 7 giorni, compreso il giorno mostrato (dal più vecchio). */
    val weekMinutes: List<Int> = emptyList(),
    val steps: Int? = null,
    val eatenKcal: Int = 0,
    val remainingKcal: Int = 0,
    val proteinEaten: Float = 0f,
    val proteinGoal: Int = 0,
    val isToday: Boolean = true,
    /** Minuto del giorno attuale: se presente (e il giorno è oggi) nascono i consigli su come chiudere la giornata. */
    val minuteOfDay: Int? = null,
    /** Tipi di pasto già registrati oggi (vedi [MealTime.TYPES]). */
    val mealTypes: Set<String> = emptySet(),
    val activityKcal: Int = 0,
    val weightKg: Int = 70
)

/**
 * Regole che trasformano i dati di sonno, recupero e attività in consigli.
 * Non inventa nulla: senza dati lo dice e dà solo indicazioni generali.
 */
object AdviceEngine {

    private const val WEEKLY_ACTIVITY_GOAL_MIN = 150

    fun generate(input: AdviceInput, zone: ZoneId = ZoneId.systemDefault()): List<Advice> =
        dayAdvice(input) + activityAdvice(input) + sleepAdvice(input, zone) + nutritionAdvice(input)

    /** I consigli di oggi sono "proattivi": guardano l'ora e cosa è già successo nella giornata. */
    private fun AdviceInput.proactive() = isToday && minuteOfDay != null

    // ------------------------------------------------------------------
    // La tua giornata: cosa fare da adesso a stasera
    // ------------------------------------------------------------------

    /** Fine della fascia di ogni pasto principale: dopo quest'ora quel pasto non si propone più. */
    private val mealWindows = listOf("Colazione" to 11 * 60, "Pranzo" to 15 * 60 + 30, "Cena" to 22 * 60)

    private fun dayAdvice(i: AdviceInput): List<Advice> {
        if (!i.proactive()) return emptyList()
        val now = i.minuteOfDay!!
        val out = mutableListOf<Advice>()
        val proteinLeft = (i.proteinGoal - i.proteinEaten).roundToInt().coerceAtLeast(0)
        val walkKcalPerMin = 3.8 * i.weightKg / 60.0   // camminata a passo svelto, MET 3,8

        // Metà delle calorie di un allenamento importante si possono "rimangiare" restando sopra il deficit voluto
        val workoutMargin = if (i.activityKcal >= 300) (i.activityKcal / 2 / 50) * 50 else 0

        // Oltre l'obiettivo: quanto movimento lo compensa, senza sensi di colpa
        if (i.remainingKcal < 0 && -i.remainingKcal > workoutMargin) {
            val over = -i.remainingKcal
            val minutes = (((over / walkKcalPerMin) / 5).roundToInt() * 5).coerceIn(15, 90)
            out += Advice(
                AdviceCategory.GIORNATA, "Oltre di $over kcal: nessun dramma",
                "Una camminata svelta di $minutes minuti brucia circa ${(minutes * walkKcalPerMin).roundToInt()} kcal. " +
                    "Domani non serve saltare pasti: riparti dal tuo obiettivo normale.",
                "Hai assunto ${i.eatenKcal} kcal"
            )
        } else if (i.remainingKcal >= 0) {
            // Budget per i pasti principali che restano oggi
            val upcoming = mealWindows.filter { (type, end) -> type !in i.mealTypes && now < end }.map { it.first }
            if (upcoming.isNotEmpty() && i.remainingKcal >= 100) {
                val next = upcoming.first()
                val kcal = round10(i.remainingKcal / upcoming.size)
                val protein = proteinLeft / upcoming.size
                val rest = if (upcoming.size > 1) " (${upcoming.joinToString(" e ") { it.lowercase() }})" else ""
                out += Advice(
                    AdviceCategory.GIORNATA, "${mealLabel(next)}: circa $kcal kcal",
                    "Ti restano ${i.remainingKcal} kcal per ${if (upcoming.size == 1) "un pasto" else "${upcoming.size} pasti"}$rest" +
                        (if (protein >= 10) ": punta a circa $kcal kcal con $protein g di proteine." else ": punta a circa $kcal kcal."),
                    "Finora ${i.eatenKcal} kcal e ${i.proteinEaten.roundToInt()} g di proteine"
                )
            } else if (upcoming.isEmpty() && now >= 20 * 60 + 30 && i.remainingKcal > 800) {
                // Sera, con un deficit molto ampio
                out += Advice(
                    AdviceCategory.GIORNATA, "Oggi hai mangiato poco",
                    "Ti restano ancora ${i.remainingKcal} kcal: non serve arrivare a zero, ma un deficit troppo forte " +
                        "ripetuto nei giorni fa perdere muscolo ed energia. Uno spuntino proteico ci sta.",
                    "Hai assunto ${i.eatenKcal} kcal"
                )
            }
        }

        // Allenamento importante: margine in più se si ha fame
        if (workoutMargin > 0 && i.remainingKcal < 300) {
            out += Advice(
                AdviceCategory.GIORNATA, "Allenamento da ${i.activityKcal} kcal",
                if (i.remainingKcal < 0)
                    "Sei oltre l'obiettivo di ${-i.remainingKcal} kcal, ma con l'allenamento il deficit di oggi resta in linea: nessuna compensazione necessaria."
                else
                    "Il deficit di oggi è già più ampio del previsto: se hai fame puoi aggiungere fino a circa $workoutMargin kcal " +
                        "(meglio proteiche) restando comunque oltre il tuo obiettivo di deficit."
            )
        }

        // Sera e proteine indietro
        if (now >= 18 * 60 && i.proteinGoal > 0 && i.proteinEaten < i.proteinGoal * 0.7f && i.eatenKcal > 0) {
            out += Advice(
                AdviceCategory.GIORNATA, "Mancano $proteinLeft g di proteine",
                if ("Cena" in i.mealTypes) "Uno spuntino proteico (yogurt greco, ricotta, fiocchi di latte) aiuta a chiudere la giornata."
                else "Per cena scegli una porzione abbondante di pesce, carne magra, legumi o uova; uno yogurt greco dopo cena aiuta a chiudere.",
                "Proteine a ${i.proteinEaten.roundToInt()} g su ${i.proteinGoal} g"
            )
        }

        // Pomeriggio fermo
        if (now in 16 * 60 until 21 * 60 && i.activityKcal == 0 && i.steps != null && i.steps < 5000) {
            out += Advice(
                AdviceCategory.GIORNATA, "Muoviti un po' prima di sera",
                "Una camminata di 30 minuti adesso aggiunge circa ${(30 * walkKcalPerMin).roundToInt()} kcal al deficit e circa 3.500 passi.",
                "Finora ${i.steps} passi e nessun allenamento"
            )
        }
        return out.take(4)
    }

    private fun mealLabel(type: String) = when (type) {
        "Colazione" -> "A colazione"
        "Pranzo" -> "A pranzo"
        else -> "A cena"
    }

    private fun round10(v: Int) = (v / 10f).roundToInt() * 10

    // ------------------------------------------------------------------
    // Attività
    // ------------------------------------------------------------------

    private fun activityAdvice(i: AdviceInput): List<Advice> {
        val out = mutableListOf<Advice>()
        val hrvDelta = if (i.hrv != null && i.hrvBaseline != null && i.hrvBaseline > 0)
            (i.hrv - i.hrvBaseline) / i.hrvBaseline * 100.0 else null
        val sleepMin = i.lastNight?.durationMinutes

        val reasons = mutableListOf<String>()
        if (hrvDelta != null) {
            val pct = hrvDelta.roundToInt()
            reasons += "HRV ${if (pct >= 0) "+" else ""}$pct% rispetto alla tua media"
        }
        if (sleepMin != null) reasons += "hai dormito ${duration(sleepMin)}"
        val reason = reasons.joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { null }

        val lowRecovery = (hrvDelta != null && hrvDelta <= -10.0) || (sleepMin != null && sleepMin < 6 * 60)
        val goodRecovery = !lowRecovery && (sleepMin != null && sleepMin >= 7 * 60) && (hrvDelta == null || hrvDelta >= -3.0)

        out += when {
            lowRecovery -> Advice(
                AdviceCategory.ATTIVITA, "Oggi recupero attivo",
                "Camminata di 30–40 minuti a ritmo facile, mobilità e stretching. Evita allenamenti intensi: il corpo non è al meglio.",
                reason
            )
            goodRecovery -> Advice(
                AdviceCategory.ATTIVITA, "Buon momento per un allenamento intenso",
                "Sei ben recuperato: scegli una corsa a intervalli, una sessione di forza o un giro in bici impegnativo.",
                reason
            )
            sleepMin != null || hrvDelta != null -> Advice(
                AdviceCategory.ATTIVITA, "Allenamento moderato",
                "Una corsa facile di 30–45 minuti, bici o forza leggera: recupero discreto, meglio non spingere al massimo.",
                reason
            )
            else -> Advice(
                AdviceCategory.ATTIVITA, "Allenamento moderato",
                "Senza dati di sonno e recupero ti consiglio una sessione moderata di 30–45 minuti. " +
                    "Collega Health Connect dal Profilo per avere consigli su misura."
            )
        }

        // Obiettivo settimanale (150 minuti consigliati dall'OMS)
        if (i.weekMinutes.isNotEmpty()) {
            val total = i.weekMinutes.sum()
            val missing = WEEKLY_ACTIVITY_GOAL_MIN - total
            out += if (missing > 0) {
                val perSession = ((missing / 3f) / 5f).roundToInt() * 5
                Advice(
                    AdviceCategory.ATTIVITA, "Ti mancano $missing minuti questa settimana",
                    "Per i 150 minuti di attività consigliati a settimana bastano 3 sessioni da circa ${perSession.coerceAtLeast(10)} minuti.",
                    "Negli ultimi 7 giorni hai fatto $total minuti"
                )
            } else {
                Advice(
                    AdviceCategory.ATTIVITA, "Obiettivo settimanale raggiunto",
                    "Hai superato i 150 minuti consigliati: mantieni il ritmo e alterna giorni intensi e leggeri.",
                    "Negli ultimi 7 giorni hai fatto $total minuti"
                )
            }

            // Giorni fermi
            val lastIndex = i.weekMinutes.indexOfLast { it > 0 }
            val daysStill = if (lastIndex == -1) i.weekMinutes.size else i.weekMinutes.size - 1 - lastIndex
            if (daysStill >= 3 && !lowRecovery) {
                out += Advice(
                    AdviceCategory.ATTIVITA, "Riparti con una sessione leggera",
                    "Sono passati ${if (lastIndex == -1) "più di ${i.weekMinutes.size}" else daysStill.toString()} giorni senza allenamento: " +
                        "oggi bastano 20–30 minuti facili per rimetterti in moto.",
                    "Nessun allenamento registrato di recente"
                )
            }
        }

        // Passi
        if (i.steps != null && i.steps < 6000 && i.isToday && !i.proactive()) {
            out += Advice(
                AdviceCategory.ATTIVITA, "Qualche passo in più",
                "Una camminata di 20 minuti ti avvicina agli 8.000 passi giornalieri consigliati.",
                "Finora ${i.steps} passi"
            )
        }
        return out
    }

    // ------------------------------------------------------------------
    // Sonno
    // ------------------------------------------------------------------

    private fun sleepAdvice(i: AdviceInput, zone: ZoneId): List<Advice> {
        val out = mutableListOf<Advice>()
        val nights = i.recentNights

        // Orario consigliato per andare a letto: risveglio abituale − 8 ore − tempo per addormentarsi
        val summary = SleepRegularity.summarize(nights.map { it.startMillis to it.endMillis }, zone)
        val wakeMinutes: Int
        val reasonWake: String
        if (summary != null) {
            wakeMinutes = summary.recommendedWake
            reasonWake = "Ti svegli in media alle ${clock(wakeMinutes)} (ultime ${nights.size} notti)"
        } else {
            wakeMinutes = 7 * 60
            reasonWake = "Nessuna notte registrata: ipotizzo il risveglio alle 07:00"
        }
        val bed = wakeMinutes - SleepRegularity.SLEEP_GOAL_MIN - SleepRegularity.FALL_ASLEEP_MIN
        out += Advice(
            AdviceCategory.SONNO, "Vai a letto verso le ${clock(bed)}",
            "Per svegliarti alle ${clock(wakeMinutes)} con 8 ore di sonno, considerando circa 15 minuti per addormentarti.",
            reasonWake
        )

        if (nights.isNotEmpty()) {
            val avg = nights.map { it.durationMinutes }.average().roundToInt()
            if (avg < 7 * 60) {
                out += Advice(
                    AdviceCategory.SONNO, "Dormi poco in media",
                    "Prova ad anticipare l'orario di 30–45 minuti per qualche sera: l'obiettivo sono 7–9 ore.",
                    "Media delle ultime ${nights.size} notti: ${duration(avg)}"
                )
            }

            // Regolarità degli orari di addormentamento
            if (summary != null && nights.size >= 3 && summary.bedSpread > 60) {
                out += Advice(
                    AdviceCategory.SONNO, "Orari troppo variabili",
                    "Cerca di andare a letto alla stessa ora anche nel weekend: un ritmo regolare migliora il recupero.",
                    "L'orario in cui ti addormenti varia di circa ${summary.bedSpread} minuti"
                )
            }
        }

        val last = i.lastNight
        if (last != null && last.efficiencyPercent in 1..84) {
            out += Advice(
                AdviceCategory.SONNO, "Sonno poco continuo",
                "Evita schermi e caffeina nell'ultima ora prima di dormire e tieni la stanza fresca e buia.",
                "Efficienza della scorsa notte: ${last.efficiencyPercent}%"
            )
        }
        return out
    }

    // ------------------------------------------------------------------
    // Alimentazione
    // ------------------------------------------------------------------

    private fun nutritionAdvice(i: AdviceInput): List<Advice> {
        if (i.eatenKcal == 0) {
            return listOf(
                Advice(
                    AdviceCategory.ALIMENTAZIONE, "Registra i pasti",
                    "Quando aggiungi i pasti del giorno posso dirti quante calorie e proteine ti restano."
                )
            )
        }
        val out = mutableListOf<Advice>()
        if (i.proactive()) {
            // Calorie rimaste e proteine della sera sono già in "La tua giornata"
        } else if (i.remainingKcal > 0) {
            out += Advice(
                AdviceCategory.ALIMENTAZIONE, "Ti restano ${i.remainingKcal} kcal",
                "Puoi ancora fare un pasto completo o uno spuntino senza uscire dal tuo obiettivo.",
                "Finora ${i.eatenKcal} kcal assunte"
            )
        } else {
            out += Advice(
                AdviceCategory.ALIMENTAZIONE, "Obiettivo calorico raggiunto",
                "Per il resto della giornata scegli acqua, verdure o tisane.",
                "Hai assunto ${i.eatenKcal} kcal"
            )
        }
        if (i.proteinGoal > 0 && i.proteinEaten < i.proteinGoal * 0.5f && !(i.proactive() && i.minuteOfDay!! >= 18 * 60)) {
            out += Advice(
                AdviceCategory.ALIMENTAZIONE, "Aggiungi proteine",
                "Una fonte proteica (yogurt greco, uova, legumi, pesce) ti aiuta a recuperare e a sentirti sazio.",
                "Proteine a ${i.proteinEaten.roundToInt()} g su ${i.proteinGoal} g"
            )
        }
        return out
    }

    // ------------------------------------------------------------------
    // Utilità
    // ------------------------------------------------------------------

    private fun clock(minutes: Int): String = SleepRegularity.clock(minutes)

    private fun duration(minutes: Int): String = "${minutes / 60} h ${"%02d".format(abs(minutes) % 60)}"
}
