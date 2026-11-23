package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.i18n.tk

import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AdviceCategory(private val labelIt: String) {
    GIORNATA(tk("La tua giornata")),
    ATTIVITA(tk("Attività")),
    SONNO(tk("Sonno")),
    ALIMENTAZIONE(tk("Alimentazione"));

    val label: String get() = tr(labelIt)
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
                AdviceCategory.GIORNATA, tr("Oltre di %,d kcal: nessun dramma", over),
                tr("Una camminata svelta di %d minuti brucia circa %,d kcal. Domani non serve saltare pasti: riparti dal tuo obiettivo normale.", minutes, (minutes * walkKcalPerMin).roundToInt()),
                    
                tr("Hai assunto %,d kcal", i.eatenKcal)
            )
        } else if (i.remainingKcal >= 0) {
            // Budget per i pasti principali che restano oggi
            val upcoming = mealWindows.filter { (type, end) -> type !in i.mealTypes && now < end }.map { it.first }
            if (upcoming.isNotEmpty() && i.remainingKcal >= 100) {
                val next = upcoming.first()
                val kcal = round10(i.remainingKcal / upcoming.size)
                val protein = proteinLeft / upcoming.size
                val rest = if (upcoming.size > 1) " (${upcoming.joinToString(" " + tr("e") + " ") { tr(it).lowercase(I18n.locale) }})" else ""
                out += Advice(
                    AdviceCategory.GIORNATA, tr("%s: circa %,d kcal", mealLabel(next), kcal),
                    tr("Ti restano %,d kcal per %s", i.remainingKcal, if (upcoming.size == 1) tr("un pasto") else tr("%d pasti", upcoming.size)) + rest +
                        ": " + (if (protein >= 10) tr("punta a circa %,d kcal con %d g di proteine.", kcal, protein) else tr("punta a circa %,d kcal.", kcal)),
                    tr("Finora %,d kcal e %d g di proteine", i.eatenKcal, i.proteinEaten.roundToInt())
                )
            } else if (upcoming.isEmpty() && now >= 20 * 60 + 30 && i.remainingKcal > 800) {
                // Sera, con un deficit molto ampio
                out += Advice(
                    AdviceCategory.GIORNATA, tr("Oggi hai mangiato poco"),
                    tr("Ti restano ancora %,d kcal: non serve arrivare a zero, ma un deficit troppo forte ripetuto nei giorni fa perdere muscolo ed energia. Uno spuntino proteico ci sta.", i.remainingKcal),
                        
                    tr("Hai assunto %,d kcal", i.eatenKcal)
                )
            }
        }

        // Allenamento importante: margine in più se si ha fame
        if (workoutMargin > 0 && i.remainingKcal < 300) {
            out += Advice(
                AdviceCategory.GIORNATA, tr("Allenamento da %,d kcal", i.activityKcal),
                if (i.remainingKcal < 0)
                    tr("Sei oltre l'obiettivo di %,d kcal, ma con l'allenamento il deficit di oggi resta in linea: nessuna compensazione necessaria.", -i.remainingKcal)
                else
                    tr("Il deficit di oggi è già più ampio del previsto: se hai fame puoi aggiungere fino a circa %,d kcal (meglio proteiche) restando comunque oltre il tuo obiettivo di deficit.", workoutMargin)
                        
            )
        }

        // Sera e proteine indietro
        if (now >= 18 * 60 && i.proteinGoal > 0 && i.proteinEaten < i.proteinGoal * 0.7f && i.eatenKcal > 0) {
            out += Advice(
                AdviceCategory.GIORNATA, tr("Mancano %d g di proteine", proteinLeft),
                if ("Cena" in i.mealTypes) tr("Uno spuntino proteico (yogurt greco, ricotta, fiocchi di latte) aiuta a chiudere la giornata.")
                else tr("Per cena scegli una porzione abbondante di pesce, carne magra, legumi o uova; uno yogurt greco dopo cena aiuta a chiudere."),
                tr("Proteine a %d g su %d g", i.proteinEaten.roundToInt(), i.proteinGoal)
            )
        }

        // Pomeriggio fermo
        if (now in 16 * 60 until 21 * 60 && i.activityKcal == 0 && i.steps != null && i.steps < 5000) {
            out += Advice(
                AdviceCategory.GIORNATA, tr("Muoviti un po' prima di sera"),
                tr("Una camminata di 30 minuti adesso aggiunge circa %,d kcal al deficit e circa 3.500 passi.", (30 * walkKcalPerMin).roundToInt()),
                tr("Finora %d passi e nessun allenamento", i.steps)
            )
        }
        return out.take(4)
    }

    private fun mealLabel(type: String) = when (type) {
        "Colazione" -> tr("A colazione")
        "Pranzo" -> tr("A pranzo")
        else -> tr("A cena")
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
            reasons += tr("HRV %s rispetto alla tua media", "${if (pct >= 0) "+" else ""}$pct%")
        }
        if (sleepMin != null) reasons += tr("hai dormito %s", duration(sleepMin))
        val reason = reasons.joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { null }

        val lowRecovery = (hrvDelta != null && hrvDelta <= -10.0) || (sleepMin != null && sleepMin < 6 * 60)
        val goodRecovery = !lowRecovery && (sleepMin != null && sleepMin >= 7 * 60) && (hrvDelta == null || hrvDelta >= -3.0)

        out += when {
            lowRecovery -> Advice(
                AdviceCategory.ATTIVITA, tr("Oggi recupero attivo"),
                tr("Camminata di 30–40 minuti a ritmo facile, mobilità e stretching. Evita allenamenti intensi: il corpo non è al meglio."),
                reason
            )
            goodRecovery -> Advice(
                AdviceCategory.ATTIVITA, tr("Buon momento per un allenamento intenso"),
                tr("Sei ben recuperato: scegli una corsa a intervalli, una sessione di forza o un giro in bici impegnativo."),
                reason
            )
            sleepMin != null || hrvDelta != null -> Advice(
                AdviceCategory.ATTIVITA, tr("Allenamento moderato"),
                tr("Una corsa facile di 30–45 minuti, bici o forza leggera: recupero discreto, meglio non spingere al massimo."),
                reason
            )
            else -> Advice(
                AdviceCategory.ATTIVITA, tr("Allenamento moderato"),
                tr("Senza dati di sonno e recupero ti consiglio una sessione moderata di 30–45 minuti. Collega Health Connect dal Profilo per avere consigli su misura.")
                    
            )
        }

        // Obiettivo settimanale (150 minuti consigliati dall'OMS)
        if (i.weekMinutes.isNotEmpty()) {
            val total = i.weekMinutes.sum()
            val missing = WEEKLY_ACTIVITY_GOAL_MIN - total
            out += if (missing > 0) {
                val perSession = ((missing / 3f) / 5f).roundToInt() * 5
                Advice(
                    AdviceCategory.ATTIVITA, tr("Ti mancano %d minuti questa settimana", missing),
                    tr("Per i 150 minuti di attività consigliati a settimana bastano 3 sessioni da circa %d minuti.", perSession.coerceAtLeast(10)),
                    tr("Negli ultimi 7 giorni hai fatto %d minuti", total)
                )
            } else {
                Advice(
                    AdviceCategory.ATTIVITA, tr("Obiettivo settimanale raggiunto"),
                    tr("Hai superato i 150 minuti consigliati: mantieni il ritmo e alterna giorni intensi e leggeri."),
                    tr("Negli ultimi 7 giorni hai fatto %d minuti", total)
                )
            }

            // Giorni fermi
            val lastIndex = i.weekMinutes.indexOfLast { it > 0 }
            val daysStill = if (lastIndex == -1) i.weekMinutes.size else i.weekMinutes.size - 1 - lastIndex
            if (daysStill >= 3 && !lowRecovery) {
                out += Advice(
                    AdviceCategory.ATTIVITA, tr("Riparti con una sessione leggera"),
                    tr("Sono passati %s giorni senza allenamento: oggi bastano 20–30 minuti facili per rimetterti in moto.", if (lastIndex == -1) tr("più di %d", i.weekMinutes.size) else daysStill.toString()),
                        
                    tr("Nessun allenamento registrato di recente")
                )
            }
        }

        // Passi
        if (i.steps != null && i.steps < 6000 && i.isToday && !i.proactive()) {
            out += Advice(
                AdviceCategory.ATTIVITA, tr("Qualche passo in più"),
                tr("Una camminata di 20 minuti ti avvicina agli 8.000 passi giornalieri consigliati."),
                tr("Finora %d passi", i.steps)
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
            reasonWake = tr("Ti svegli in media alle %s (ultime %d notti)", clock(wakeMinutes), nights.size)
        } else {
            wakeMinutes = 7 * 60
            reasonWake = tr("Nessuna notte registrata: ipotizzo il risveglio alle 07:00")
        }
        val bed = wakeMinutes - SleepRegularity.SLEEP_GOAL_MIN - SleepRegularity.FALL_ASLEEP_MIN
        out += Advice(
            AdviceCategory.SONNO, tr("Vai a letto verso le %s", clock(bed)),
            tr("Per svegliarti alle %s con 8 ore di sonno, considerando circa 15 minuti per addormentarti.", clock(wakeMinutes)),
            reasonWake
        )

        if (nights.isNotEmpty()) {
            val avg = nights.map { it.durationMinutes }.average().roundToInt()
            if (avg < 7 * 60) {
                out += Advice(
                    AdviceCategory.SONNO, tr("Dormi poco in media"),
                    tr("Prova ad anticipare l'orario di 30–45 minuti per qualche sera: l'obiettivo sono 7–9 ore."),
                    tr("Media delle ultime %d notti: %s", nights.size, duration(avg))
                )
            }

            // Regolarità degli orari di addormentamento
            if (summary != null && nights.size >= 3 && summary.bedSpread > 60) {
                out += Advice(
                    AdviceCategory.SONNO, tr("Orari troppo variabili"),
                    tr("Cerca di andare a letto alla stessa ora anche nel weekend: un ritmo regolare migliora il recupero."),
                    tr("L'orario in cui ti addormenti varia di circa %d minuti", summary.bedSpread)
                )
            }
        }

        val last = i.lastNight
        if (last != null && last.efficiencyPercent in 1..84) {
            out += Advice(
                AdviceCategory.SONNO, tr("Sonno poco continuo"),
                tr("Evita schermi e caffeina nell'ultima ora prima di dormire e tieni la stanza fresca e buia."),
                tr("Efficienza della scorsa notte: %d%%", last.efficiencyPercent)
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
                    AdviceCategory.ALIMENTAZIONE, tr("Registra i pasti"),
                    tr("Quando aggiungi i pasti del giorno posso dirti quante calorie e proteine ti restano.")
                )
            )
        }
        val out = mutableListOf<Advice>()
        if (i.proactive()) {
            // Calorie rimaste e proteine della sera sono già in "La tua giornata"
        } else if (i.remainingKcal > 0) {
            out += Advice(
                AdviceCategory.ALIMENTAZIONE, tr("Ti restano %,d kcal", i.remainingKcal),
                tr("Puoi ancora fare un pasto completo o uno spuntino senza uscire dal tuo obiettivo."),
                tr("Finora %,d kcal assunte", i.eatenKcal)
            )
        } else {
            out += Advice(
                AdviceCategory.ALIMENTAZIONE, tr("Obiettivo calorico raggiunto"),
                tr("Per il resto della giornata scegli acqua, verdure o tisane."),
                tr("Hai assunto %,d kcal", i.eatenKcal)
            )
        }
        if (i.proteinGoal > 0 && i.proteinEaten < i.proteinGoal * 0.5f && !(i.proactive() && i.minuteOfDay!! >= 18 * 60)) {
            out += Advice(
                AdviceCategory.ALIMENTAZIONE, tr("Aggiungi proteine"),
                tr("Una fonte proteica (yogurt greco, uova, legumi, pesce) ti aiuta a recuperare e a sentirti sazio."),
                tr("Proteine a %d g su %d g", i.proteinEaten.roundToInt(), i.proteinGoal)
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
