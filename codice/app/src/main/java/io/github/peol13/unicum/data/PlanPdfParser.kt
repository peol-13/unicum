package io.github.peol13.unicum.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Ricava le sedute di un programma di corsa dal testo di un PDF (righe già in ordine di lettura). Logica pura, con
 * test. Non pretende di capire ogni impaginazione: trova le sedute e quello che riesce, poi l'utente controlla e
 * corregge nell'anteprima. Riconosce:
 * - sedute con la **data** in testa alla riga (`17/11`, `17.11.2026`, `lun 17 nov`, `Lunedì 17 novembre`);
 * - sedute **relative**: intestazione `Settimana 2` / `Week 2` e poi il giorno (`Lunedì`, `Mar`, `Day 3`):
 *   la data si calcola dal primo giorno del programma, che sceglie l'utente;
 * - le righe successive senza data né giorno continuano la seduta di sopra;
 * - giorni di riposo e allenamenti che non sono corsa (palestra, bici…) vengono saltati e contati.
 * Da ogni seduta: tipo (tra quelli dell'app, più Soglia e Medio), km, minuti, ritmo, RPE; il testo intero
 * diventa la "Struttura", così le ripetute si leggono come quelle di Notion ([PlanStructure]).
 */
object PlanPdfParser {

    /** Una seduta trovata: con [date] se il PDF la scrive, altrimenti [week]/[day] (1 = primo giorno della settimana). */
    data class Draft(
        val date: LocalDate? = null,
        val week: Int? = null,
        val day: Int? = null,
        val kind: String? = null,
        val km: Double? = null,
        val minutes: Int? = null,
        val pace: String? = null,
        val rpe: Int? = null,
        val text: String
    ) {
        /** Giorni dal primo giorno del programma (solo per le sedute relative). */
        val offset: Int? get() = if (date == null && day != null) ((week ?: 1) - 1) * 7 + (day - 1) else null
    }

    data class Result(
        val drafts: List<Draft>,
        /** Giorni di riposo saltati. */
        val restDays: Int,
        /** Allenamenti che non sono corsa (palestra, bici, nuoto…) saltati. */
        val otherActivities: Int
    ) {
        /** Almeno una seduta senza data: serve il primo giorno del programma. */
        val needsStart: Boolean get() = drafts.any { it.date == null }
    }

    // ------------------------------------------------------------------
    // Riconoscimento delle righe
    // ------------------------------------------------------------------

    private val months = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
    private val monthsEn = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val dayNames: List<Pair<Regex, Int>> = listOf(
        "lun(?:ed[iì])?|mon(?:day)?" to 1,
        "mar(?:ted[iì])?|tue(?:s(?:day)?)?" to 2,
        "mer(?:coled[iì])?|wed(?:nesday)?" to 3,
        "gio(?:ved[iì])?|thu(?:rs(?:day)?)?" to 4,
        "ven(?:erd[iì])?|fri(?:day)?" to 5,
        "sab(?:ato)?|sat(?:urday)?" to 6,
        "dom(?:enica)?|sun(?:day)?" to 7
    ).map { (re, n) -> Regex("""^(?:$re)\.?(?=$|[\s:,;\-–|/])""", RegexOption.IGNORE_CASE) to n }

    private val weekHeader = Regex("""^(?:settimana|sett\.?|week|wk|microciclo)\s*(\d{1,2})\b[\s:.\-–|]*""", RegexOption.IGNORE_CASE)
    private val dayNumber = Regex("""^(?:giorno|day)\s*(\d{1,3})\b[\s:.\-–|]*""", RegexOption.IGNORE_CASE)
    /** Una data numerica, ma non una distanza o un intervallo ("5.5 km", "6-8 km", "6/7 RPE"). */
    private val numericDate = Regex(
        """^(\d{1,2})[/.\-](\d{1,2})(?:[/.\-](\d{2,4}))?(?=$|[\s:,;\-–|])(?!\s*(?:km|k|m|min|minuti|rpe|x|×)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val monthWords = (
        listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
            "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december") +
            months + monthsEn + listOf("sett", "sept")
        ).distinct().sortedByDescending { it.length }.joinToString("|")
    private val textDate = Regex("""^(\d{1,2})\s+($monthWords)\.?(?:\s+(\d{4}))?(?=$|[\s:,;\-–|])""", RegexOption.IGNORE_CASE)
    private val separators = Regex("""^[\s:,;\-–|]+""")

    private data class Head(val date: LocalDate?, val week: Int?, val day: Int?, val rest: String)

    /** Data o giorno in testa alla riga (con l'eventuale nome del giorno davanti alla data). */
    private fun head(line: String, week: Int?, today: LocalDate): Head? {
        var s = line
        var dow: Int? = null
        dayNames.firstOrNull { it.first.containsMatchIn(s) }?.let { (re, n) ->
            dow = n
            s = s.replaceFirst(re, "").replaceFirst(separators, "")
        }
        val date = numericDate.find(s)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            s = s.substring(m.range.last + 1)
            makeDate(d, mo, y, today)
        } ?: textDate.find(s)?.let { m ->
            val mo = monthIndex(m.groupValues[2]) ?: return@let null
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            s = s.substring(m.range.last + 1)
            makeDate(m.groupValues[1].toInt(), mo, y, today)
        }
        if (date != null) return Head(date, null, null, s.replaceFirst(separators, "").trim())
        if (dow != null) return Head(null, week ?: 1, dow, s.trim())
        dayNumber.find(line)?.let { m ->
            val n = m.groupValues[1].toInt().takeIf { it in 1..400 } ?: return null
            return Head(null, (n - 1) / 7 + 1, (n - 1) % 7 + 1, line.substring(m.range.last + 1).trim())
        }
        return null
    }

    private fun monthIndex(word: String): Int? {
        val w = word.lowercase().take(3)
        return (months.indexOf(w).takeIf { it >= 0 } ?: monthsEn.indexOf(w).takeIf { it >= 0 })?.plus(1)
    }

    /** Senza anno: quello che mette la data più vicina a oggi (un programma di dicembre-gennaio resta in ordine). */
    private fun makeDate(d: Int, m: Int, y: Int?, today: LocalDate): LocalDate? {
        if (m !in 1..12 || d !in 1..31) return null
        if (y != null) return runCatching { LocalDate.of(y, m, d) }.getOrNull()
        return listOf(today.year - 1, today.year, today.year + 1)
            .mapNotNull { runCatching { LocalDate.of(it, m, d) }.getOrNull() }
            .minByOrNull { kotlin.math.abs(it.toEpochDay() - today.toEpochDay()) }
    }

    // ------------------------------------------------------------------
    // Lettura del testo
    // ------------------------------------------------------------------

    fun parse(lines: List<String>, today: LocalDate): Result {
        data class Open(val head: Head, val text: StringBuilder)
        val sessions = mutableListOf<Open>()
        var week: Int? = null
        // Dopo il titolo di una settimana le righe senza data né giorno non continuano più la seduta di sopra
        var canContinue = true
        for (raw in lines) {
            var line = raw.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
            if (line.isEmpty() || isPageNoise(line)) continue
            var weekTitle = false
            weekHeader.find(line)?.let { m ->
                week = m.groupValues[1].toInt()
                line = line.substring(m.range.last + 1).trim()
                weekTitle = true
                canContinue = false
            }
            if (line.isEmpty()) continue
            val h = head(line, week, today)
            when {
                h != null -> { sessions += Open(h, StringBuilder(h.rest)); canContinue = true }
                // "Settimana 4 - scarico e gara": il resto è il nome della settimana, non una seduta
                weekTitle || !canContinue -> Unit
                else -> sessions.lastOrNull()?.text?.let { if (it.isNotEmpty()) it.append(' '); it.append(line) }
            }
        }
        var rest = 0
        var other = 0
        val drafts = sessions.mapNotNull { o ->
            val text = o.text.toString().trim().trim('-', '–', '|', ':').trim()
            when {
                isRest(text) -> { rest++; null }
                isOtherSport(text) -> { other++; null }
                else -> analyze(o.head, text)
            }
        }
        return Result(drafts, rest, other)
    }

    private fun isPageNoise(line: String) =
        Regex("""^(pag(?:ina)?\.?\s*)?\d{1,3}(\s*(/|di|of)\s*\d{1,3})?$""", RegexOption.IGNORE_CASE).matches(line)

    private val restWords = Regex("""^(riposo|rest|off|recupero completo|riposo completo|libero|-|–|—|x)\.?$""", RegexOption.IGNORE_CASE)

    private fun isRest(text: String): Boolean = text.isEmpty() || restWords.matches(text) ||
        (text.length < 30 && Regex("""\b(riposo|rest day|day off)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) && !hasRunWords(text))

    private val runWords = Regex(
        """\b(corsa|corri|run|running|km|ripetut\w*|interval\w*|lento|lenta|lungo|lunga|progressiv\w*|fartlek|soglia|medio|test|gara|easy|tempo|jog|rpe|riscaldamento)\b|\d\s*[x×]\s*\d""",
        RegexOption.IGNORE_CASE
    )

    private fun hasRunWords(text: String) = runWords.containsMatchIn(text)

    private fun isOtherSport(text: String): Boolean =
        Regex("""\b(palestra|forza|pesi|bici|bike|cyclette|nuoto|swim|yoga|pilates|stretching|mobilit[aà]|core|crossfit|cross training)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text) && !hasRunWords(text)

    // ------------------------------------------------------------------
    // Una seduta
    // ------------------------------------------------------------------

    private val kindRules: List<Pair<Regex, String>> = listOf(
        """\b(test|gara|race|time trial|cronometro)\b""" to "Test",
        """\b(ripetut\w*|interval\w*|fartlek|serie)\b|\d\s*[x×]\s*\(?\d""" to "Intervalli",
        """\bprogressiv\w*|\bprogression\b""" to "Progressivo",
        """\b(soglia|threshold|tempo run)\b""" to "Soglia",
        """\b(medio|ritmo gara|race pace|marathon pace)\b""" to "Medio",
        """\b(lungo|lunga|long run|long)\b""" to "Lungo facile",
        """\b(facile|lento|lenta|easy|rigenerante|recovery|leggera|leggero|jog\w*|fondo)\b""" to "Facile",
        """\b(libera|libero|a sensazione)\b""" to "Libera"
    ).map { (re, k) -> Regex(re, RegexOption.IGNORE_CASE) to k }

    fun kindOf(text: String): String? = kindRules.firstOrNull { it.first.containsMatchIn(text) }?.second

    private val kmRe = Regex("""(\d+(?:[.,]\d+)?)\s*(?:km|k)\b""", RegexOption.IGNORE_CASE)
    private val totalKmRe = Regex("""\b(?:tot(?:ale|\.)?|totali|in tutto|total)\s*:?\s*(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val hoursRe = Regex("""\b(\d)\s*h\s*(?:(\d{1,2})\s*(?:'|′|’|min)?)?""", RegexOption.IGNORE_CASE)
    private val minutesRe = Regex("""(?<![\d:'’′])(\d{1,3})\s*(?:min(?:uti)?\b|'(?!')|′(?!′)|’(?!’))(?!\s*\d)""", RegexOption.IGNORE_CASE)
    private val paceRe = Regex("""(?<![\d:])(\d{1,2})[:'’′](\d{2})(?!\d|:\d)(?:\s*[-–÷/]\s*(\d{1,2})[:'’′](\d{2})(?!\d))?""")
    private val rpeRe = Regex("""\brpe\s*:?\s*(\d{1,2})(?:\s*[-–/]\s*(\d{1,2}))?""", RegexOption.IGNORE_CASE)

    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

    private fun analyze(h: Head, text: String): Draft {
        val kind = kindOf(text)
        val structured = kind == "Intervalli" || Regex("""\d\s*[x×]\s*\(?\d""").containsMatchIn(text)
        // Nelle ripetute i km e i minuti delle singole prove non sono l'obiettivo: vale solo un totale scritto
        val km = totalKmRe.find(text)?.let { num(it.groupValues[1]) }
            ?: if (!structured) kmRe.find(text)?.let { num(it.groupValues[1]) } else null
        val minutes = if (structured) null else hoursRe.find(text)?.let { m ->
            m.groupValues[1].toInt() * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
        } ?: minutesRe.findAll(text).map { it.groupValues[1].toInt() }.filter { it in 1..300 }.toList().let { all ->
            // "10' + 30' lento + 5'" = 45 minuti; nel progressivo il primo numero è già il totale
            val pieces = text.split('+')
            when {
                all.isEmpty() -> null
                // "10' + 5 km al massimo + 5'": un pezzo in km senza minuti, la durata totale non si sa
                pieces.size > 1 && pieces.any { kmRe.containsMatchIn(it) && !minutesRe.containsMatchIn(it) } -> null
                kind != "Progressivo" && pieces.size > 1 -> all.sum()
                else -> all.first()
            }
        }?.takeIf { it in 10..300 }
        val pace = paceRe.findAll(text).firstNotNullOfOrNull { m ->
            val a = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            if (a !in 150..900 || m.groupValues[2].toInt() > 59) return@firstNotNullOfOrNull null
            val first = "${m.groupValues[1]}:${m.groupValues[2]}"
            if (m.groupValues[3].isNotEmpty()) "$first–${m.groupValues[3]}:${m.groupValues[4]}/km" else "$first/km"
        }
        val rpe = rpeRe.find(text)?.let { m -> (m.groupValues[2].toIntOrNull() ?: m.groupValues[1].toInt()) }?.takeIf { it in 1..10 }
        return Draft(h.date, h.week, h.day, kind, km?.takeIf { it in 0.5..100.0 }, minutes, pace, rpe, text)
    }

    /** "6", "5,5": i km di un obiettivo senza decimali inutili. */
    fun shortKm(km: Double): String =
        if (kotlin.math.abs(km - Math.round(km)) < 1e-6) "${Math.round(km)}" else "%.1f".format(io.github.peol13.unicum.i18n.I18n.locale, km)

    /** Il nome breve di una seduta: tipo e obiettivo ("Facile 6 km", "Intervalli"). */
    fun nameOf(kind: String?, km: Double?, minutes: Int?): String {
        val k = kind ?: "Corsa"
        return when {
            km != null -> "$k ${shortKm(km)} km"
            minutes != null -> "$k $minutes′"
            else -> k
        }
    }

    /**
     * Le sedute pronte da salvare: le relative si collocano dal [start] (primo giorno della settimana 1); senza
     * [start] restano senza data. Gli id li assegna [LocalPlan].
     */
    fun toRuns(drafts: List<Draft>, start: LocalDate?): List<PlannedRun> = drafts.map { d ->
        val date = d.date ?: d.offset?.let { off -> start?.plusDays(off.toLong()) }
        PlannedRun(
            id = "",
            name = nameOf(d.kind, d.km, d.minutes),
            date = date?.toString(),
            kind = d.kind,
            status = LocalPlan.STATUS_TODO,
            km = d.km,
            minutes = d.minutes,
            pace = d.pace,
            rpe = d.rpe,
            structure = d.text.takeIf { it.isNotBlank() },
            week = d.week
        )
    }

    /** Il primo giorno proposto per un programma senza date: oggi se è lunedì, altrimenti il prossimo lunedì. */
    fun nextMonday(d: LocalDate): LocalDate = d.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
}
