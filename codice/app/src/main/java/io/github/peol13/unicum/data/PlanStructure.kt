package io.github.peol13.unicum.data

/**
 * Legge la "Struttura" di una seduta del programma Notion e ne ricava i pezzi in ordine, per dividere la
 * corsa senza scrivere le ripetute a mano. Riconosce le due forme usate nel programma:
 * - ripetute: `10' di corsa facile + 3×(4' a 6:45 + 2' lento) + 5' defaticamento` (riscaldamento e
 *   defaticamento facoltativi; durate in `'` minuti, `''` secondi, `km` o `m`);
 * - progressivo: `15' progressivi: 5' a 7:10–6:50, 5' a 6:50–6:25, 5' a 6:25–6:05 min/km`.
 * Tutto il resto (corsa continua, test) restituisce null: la corsa resta divisa per km.
 */
object PlanStructure {

    /** Tipi di seduta per cui la struttura del programma divide la corsa. */
    val STRUCTURED_KINDS = setOf("Intervalli", "Progressivo")

    private val amount = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(''|"|″|'|′|’|km\b|m\b)""")
    private val repeat = Regex("""^\s*(\d+)\s*[×xX]\s*\((.*)\)\s*$""")

    fun parse(structure: String?): List<PlanStep>? {
        if (structure.isNullOrBlank()) return null
        val main = mainPart(structure)
        return progressive(main) ?: repeats(main)
    }

    /** La sequenza è la prima frase, prima di ", RPE …" o delle note. */
    private fun mainPart(text: String): String {
        var s = text.replace('\n', ' ')
        // Un'etichetta senza numeri davanti ("Ripetute: 10' …", tipica dei PDF) non fa parte della sequenza
        val colon = s.indexOf(':')
        if (colon > 0 && s.substring(0, colon).none { it.isDigit() }) s = s.substring(colon + 1).trim()
        s = Regex("""\.\s""").find(s)?.let { s.substring(0, it.range.first) } ?: s
        s = s.substringBefore(", RPE").substringBefore(" RPE ")
        return s.trim().trimEnd('.', ',')
    }

    /** Una durata all'inizio del testo: secondi oppure metri. */
    fun part(token: String): IntervalPart? {
        val m = amount.find(token) ?: return null
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return when (m.groupValues[2]) {
            "''", "\"", "″" -> IntervalPart(seconds = n.toInt())
            "'", "′", "’" -> IntervalPart(seconds = (n * 60).toInt())
            "km" -> IntervalPart(meters = n * 1000)
            "m" -> IntervalPart(meters = n)
            else -> null
        }?.takeIf { it.isSet }
    }

    /** Divide su " + " fuori dalle parentesi. */
    private fun splitPlus(s: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> depth--
                '+' -> if (depth == 0) { out += s.substring(start, i); start = i + 1 }
            }
            i++
        }
        out += s.substring(start)
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun repeats(main: String): List<PlanStep>? {
        val tokens = splitPlus(main)
        val idx = tokens.indexOfFirst { repeat.matches(it) }
        if (idx < 0) return null
        val steps = mutableListOf<PlanStep>()
        tokens.forEachIndexed { i, tok ->
            val rm = repeat.matchEntire(tok)
            if (rm != null) {
                val n = rm.groupValues[1].toInt()
                val inner = splitPlus(rm.groupValues[2])
                val work = inner.getOrNull(0)?.let(::part) ?: return null
                val rest = inner.getOrNull(1)?.let(::part)
                if (n !in 1..50) return null
                for (r in 1..n) {
                    steps += PlanStep(StepKind.WORK, r, work)
                    if (rest != null) steps += PlanStep(StepKind.REST, r, rest)
                }
            } else {
                val p = part(tok) ?: return null
                // Prima delle ripetute è riscaldamento, dopo è defaticamento
                steps += PlanStep(if (i < idx) StepKind.WARM_UP else StepKind.COOL_DOWN, null, p)
            }
        }
        return steps
    }

    private fun progressive(main: String): List<PlanStep>? {
        if (!main.contains("progressiv", ignoreCase = true) || !main.contains(':')) return null
        val blocks = main.substringAfter(':').split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val parts = blocks.map { part(it) ?: return null }
        if (parts.size < 2) return null
        return parts.mapIndexed { i, p -> PlanStep(StepKind.WORK, i + 1, p) }
    }
}
