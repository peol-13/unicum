package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.I18n
import kotlinx.serialization.Serializable

/**
 * Un esercizio di una seduta di forza: dal programma (letto con [StrengthStructure.parse]) o come è stato fatto
 * davvero (scritto nella pagina dell'allenamento, in [WorkoutFeedback.exercises]).
 * [reps] è un testo perché può essere un intervallo o "max" ("8", "8-10", "max"); [seconds] per gli esercizi a
 * tempo (plank); [kg] null = corpo libero; [restS] = recupero tra le serie.
 */
@Serializable
data class Exercise(
    val name: String,
    val sets: Int? = null,
    val reps: String? = null,
    val seconds: Int? = null,
    val kg: Double? = null,
    val restS: Int? = null
)

/**
 * Legge gli esercizi dal testo di una seduta di forza ("Struttura" di Notion, testo del PDF, seduta scritta a mano).
 * Logica pura, con test. Riconosce, anche più esercizi sulla stessa riga:
 * - serie × ripetizioni: `Panca piana 4x8 @ 60 kg`, `Trazioni 3×max`, `Rematore 3x8-10 40kg`, `Squat 3 serie da 12`;
 * - esercizi a tempo: `Plank 3x45''`, `Wall sit 2x1'`;
 * - recupero: `rec 90''`, `recupero 2'`, `riposo 1'30`;
 * - circuiti: `Circuito 3 giri: 10 piegamenti, 15 squat, 30'' plank` (i giri diventano le serie).
 * Quello che non si riconosce resta nel testo della seduta, che l'app mostra comunque.
 */
object StrengthStructure {

    private const val REPS = """(\d{1,3}(?:\s*[-–/]\s*\d{1,3})?|max|amrap|cedimento)"""
    private const val UNIT = """("|s\b|sec(?:ondi)?\b|'|min(?:uti)?\b)"""
    private const val REP_WORD = """(?:\s*(?:ripetizioni|rip\.?|reps?)(?![\p{L}]))?"""

    private val setsReps = Regex("""(?<![\d.,])(\d{1,2})\s*x\s*$REPS\s*$UNIT?$REP_WORD""", RegexOption.IGNORE_CASE)
    private val setsWord = Regex("""(?<![\d.,])(\d{1,2})\s*(?:serie|sets?)\s*(?:da|di|of|x)?\s*$REPS\s*$UNIT?$REP_WORD""", RegexOption.IGNORE_CASE)
    private val kgAfter = Regex("""^\s*[@,]?\s*(?:a|con|with|x)?\s*(\d{1,3}(?:[.,]\d{1,2})?)\s*kg\b""", RegexOption.IGNORE_CASE)
    private val kgAny = Regex("""@?\s*(\d{1,3}(?:[.,]\d{1,2})?)\s*kg\b""", RegexOption.IGNORE_CASE)
    private val restAfter = Regex(
        """^\s*[,(]?\s*(?:rec(?:upero)?|riposo|rest|pausa)\.?\s*:?\s*(\d{1,3})\s*('|"|s\b|sec\w*|min\w*)?(?:\s*(\d{1,2})\s*"?)?\s*\)?""",
        RegexOption.IGNORE_CASE
    )
    private val rounds = Regex("""(?<![\d.,])(\d{1,2})\s*(?:giri|round|rounds|volte)\b\s*(?:di|da|of)?\s*:?""", RegexOption.IGNORE_CASE)
    /** "10 piegamenti", "30" plank": quantità davanti al nome (nei circuiti). */
    private val leading = Regex("""^(\d{1,3})\s*$UNIT?\s+(\p{L}.*)$""", RegexOption.IGNORE_CASE)
    /** "Plank 45"", "Burpees 10": quantità dopo il nome (solo nei circuiti, dove le serie sono i giri). */
    private val trailing = Regex("""^(\p{L}.*?)\s+(\d{1,3})\s*$UNIT?$""", RegexOption.IGNORE_CASE)
    private val separators = Regex(""";|•|·|\s\+\s|,\s+|\s[-–]\s""")
    private val numbering = Regex("""^\s*(?:\d{1,2}[.)]|[a-z]\d?[.)]|[-*•])\s+""", RegexOption.IGNORE_CASE)

    /** Apostrofi e virgolette tipografici, `''` per i secondi e × come x. */
    private fun normalize(text: String): String = text
        .replace('’', '\'').replace('′', '\'').replace('‘', '\'')
        .replace('″', '"').replace('“', '"').replace('”', '"').replace("''", "\"")
        .replace('×', 'x').replace(' ', ' ')

    fun parse(text: String?): List<Exercise> {
        if (text.isNullOrBlank()) return emptyList()
        val out = mutableListOf<Exercise>()
        for (rawLine in normalize(text).lines()) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue
            // "Circuito 3 giri: ..." = le serie degli esercizi di questa riga
            // (quello che c'è prima, es. "Circuito", è solo il titolo)
            val roundSets = rounds.find(line)?.let { m ->
                line = line.substring(m.range.last + 1).trim()
                m.groupValues[1].toInt()
            }
            val start = out.size
            for (item in line.split(separators)) parseItem(item.trim(), roundSets != null, out)
            if (roundSets != null) for (i in start until out.size) if (out[i].sets == null) out[i] = out[i].copy(sets = roundSets)
        }
        return out
    }

    private fun parseItem(item: String, inCircuit: Boolean, out: MutableList<Exercise>) {
        if (item.isEmpty()) return
        // Solo il recupero (es. dopo una virgola): vale per l'esercizio di prima
        restAfter.find(item)?.takeIf { it.range.first == 0 && it.range.last >= item.trimEnd().length - 1 }?.let { m ->
            if (out.isNotEmpty()) out[out.size - 1] = out.last().copy(restS = restSeconds(m))
            return
        }
        // Solo il carico ("Squat 5x5, 70 kg"): vale per l'esercizio di prima
        kgAfter.find(item)?.takeIf { it.range.last >= item.trimEnd().length - 1 }?.let { m ->
            if (out.isNotEmpty() && out.last().kg == null) out[out.size - 1] = out.last().copy(kg = kgValue(m.groupValues[1]))
            return
        }
        val anchors = (setsReps.findAll(item) + setsWord.findAll(item)).sortedBy { it.range.first }
            .fold(mutableListOf<MatchResult>()) { acc, m -> if (acc.isEmpty() || m.range.first > acc.last().range.last) acc += m; acc }
        if (anchors.isEmpty()) {
            simpleItem(item, inCircuit)?.let(out::add)
            return
        }
        var prevEnd = 0
        for (a in anchors) {
            var nameText = item.substring(prevEnd, a.range.first)
            var end = a.range.last + 1
            var kg = kgAfter.find(item.substring(end))?.takeIf { it.range.first == 0 }?.let { m ->
                end += m.range.last + 1
                kgValue(m.groupValues[1])
            }
            val rest = restAfter.find(item.substring(end))?.takeIf { it.range.first == 0 }?.let { m ->
                end += m.range.last + 1
                restSeconds(m)
            }
            // "Panca 60 kg 4x8": il carico scritto prima delle serie
            if (kg == null) kgAny.find(nameText)?.let { m -> kg = kgValue(m.groupValues[1]); nameText = nameText.removeRange(m.range) }
            val name = cleanName(nameText) ?: out.lastOrNull()?.name ?: return
            val (reps, seconds) = amountOf(a.groupValues[2], a.groupValues[3])
            out += Exercise(name, a.groupValues[1].toInt().takeIf { it in 1..20 }, reps, seconds, kg, rest)
            prevEnd = end
        }
    }

    /** Un esercizio senza "serie × ripetizioni": "10 piegamenti", "30" plank" e, nei circuiti, "Plank 45"". */
    private fun simpleItem(item: String, inCircuit: Boolean): Exercise? {
        val text = numbering.replace(item, "").substringAfterLast(':').trim().ifEmpty { item }
        leading.find(text)?.let { m ->
            val name = cleanName(m.groupValues[3]) ?: return null
            val (reps, seconds) = amountOf(m.groupValues[1], m.groupValues[2])
            return Exercise(name, null, reps, seconds)
        }
        if (inCircuit) trailing.find(text)?.let { m ->
            val name = cleanName(m.groupValues[1]) ?: return null
            val (reps, seconds) = amountOf(m.groupValues[2], m.groupValues[3])
            return Exercise(name, null, reps, seconds)
        }
        return null
    }

    /** Il nome tra un esercizio e l'altro: senza numerazione, intestazioni ("Palestra: Panca") e punteggiatura. */
    private fun cleanName(raw: String): String? {
        var s = numbering.replace(raw.trim(), "")
        val colon = s.lastIndexOf(':')
        if (colon >= 0) s = s.substring(colon + 1).ifBlank { s.substring(0, colon) }
        s = s.trim().trim(',', '.', '-', '–', '@', '(', ')', '+', '|', ':').trim()
        return s.takeIf { it.any(Char::isLetter) }?.replaceFirstChar { it.uppercase() }
    }

    /** Ripetizioni ("8", "8-10", "max") oppure secondi se c'è un'unità di tempo. */
    private fun amountOf(value: String, unit: String): Pair<String?, Int?> {
        val n = value.trim().toIntOrNull()
        return when {
            n != null && unit.isNotEmpty() -> null to if (unit.startsWith("'") || unit.startsWith("m", true)) n * 60 else n
            else -> value.lowercase().replace(Regex("""\s*[-–/]\s*"""), "-").let { if (it == "amrap" || it == "cedimento") "max" else it } to null
        }
    }

    private fun kgValue(s: String): Double? = s.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 500 }

    private fun restSeconds(m: MatchResult): Int? {
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val unit = m.groupValues[2]
        val extra = m.groupValues[3].toIntOrNull() ?: 0
        return when {
            unit.startsWith("'") || unit.startsWith("m", true) -> n * 60 + extra
            unit.isNotEmpty() -> n
            // Senza unità: "rec 2" sono minuti, "rec 90" secondi
            n <= 10 -> n * 60
            else -> n
        }.takeIf { it in 1..1200 }
    }

    // ------------------------------------------------------------------
    // Testi da mostrare (senza parole da tradurre)
    // ------------------------------------------------------------------

    /** "45″", "1′", "1′30″". */
    fun durationText(s: Int): String = when {
        s < 60 -> "$s″"
        s % 60 == 0 -> "${s / 60}′"
        else -> "${s / 60}′${s % 60}″"
    }

    /** "4 × 8", "3 × 45″", "3 × max", "12" (null se non c'è nulla). */
    fun volumeText(e: Exercise): String? {
        val amount = e.reps ?: e.seconds?.let(::durationText)
        return when {
            e.sets != null && amount != null -> "${e.sets} × $amount"
            amount != null -> amount
            e.sets != null -> "${e.sets} ×"
            else -> null
        }
    }

    /** "60 kg", "62,5 kg" (null = corpo libero). */
    fun kgText(kg: Double?): String? = kg?.let {
        if (kotlin.math.abs(it - Math.round(it)) < 1e-6) "${Math.round(it)} kg" else "%.1f kg".format(I18n.locale, it)
    }

    /** Il testo di ripetizioni o tempo per un campo da modificare ("8", "8-10", "45″"). */
    fun amountText(e: Exercise): String = e.reps ?: e.seconds?.let(::durationText).orEmpty()

    /** Il contrario di [amountText]: "45"" o "1'30" = secondi, il resto ripetizioni. */
    fun parseAmount(text: String): Pair<String?, Int?> {
        val t = normalize(text).trim()
        if (t.isEmpty()) return null to null
        Regex("""^(\d{1,3})\s*("|s|sec)$""", RegexOption.IGNORE_CASE).find(t)?.let { return null to it.groupValues[1].toInt() }
        Regex("""^(\d{1,2})\s*(?:'|min)\s*(?:(\d{1,2})\s*"?)?$""", RegexOption.IGNORE_CASE).find(t)?.let {
            return null to it.groupValues[1].toInt() * 60 + (it.groupValues[2].toIntOrNull() ?: 0)
        }
        return amountOf(t, "")
    }

    /** Il carico scritto in un campo ("62,5"): null se vuoto o non valido. */
    fun parseKg(text: String): Double? = kgValue(text.trim().removeSuffix("kg").trim())
}
