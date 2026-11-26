package io.github.peol13.unicum.data

/**
 * Ricerca degli alimenti: tollerante e veloce (gira a ogni lettera digitata).
 *
 * Ogni alimento ha una voce d'indice ([Entry]) calcolata una volta sola: parole del nome già normalizzate
 * (nome mostrato + italiano del catalogo), la loro forma base (singolare/plurale), le parole della categoria
 * e il "livello" (generico, piatto pronto, prodotto di marca). La ricerca prepara le parole digitate una
 * volta ([Query]) e per ogni voce cerca, parola per parola, la corrispondenza migliore:
 *
 * | costo | corrispondenza                                                     | esempio                        |
 * |-------|--------------------------------------------------------------------|--------------------------------|
 * | 0     | stessa parola, anche al singolare/plurale                          | "pomodoro" → "Pomodori"        |
 * | 1     | inizio di una parola (mentre si scrive)                            | "pomod" → "Pomodori"           |
 * | 2     | stessa radice con desinenza diversa                                | "arance" → "Arancia"           |
 * | 2     | sinonimo                                                           | "spaghetti" → "Pasta al …"     |
 * | 0     | parola-gruppo                                                      | "pesce" → "Merluzzo", "Orata"  |
 * | 3     | categoria                                                          | "verdura" → "Zucchine"         |
 * | 4     | dentro una parola                                                  | "berett" → "Gamberetti"        |
 * | 5     | un errore di battitura (due dalle 8 lettere; mai sotto le 5)       | "melanzne" → "Melanzane"       |
 *
 * Articoli e preposizioni non contano. Una voce corrisponde se ogni parola trova posto; l'ordine è: costo
 * totale, poi i cibi generici prima dei piatti pronti e dei prodotti di marca, poi chi inizia con la prima
 * parola cercata, poi i nomi più corti.
 */
internal object FoodSearch {

    private val stopWords = setOf(
        "a", "al", "allo", "alla", "ai", "agli", "alle", "all", "di", "del", "dello", "della", "dei", "degli", "delle",
        "dell", "d", "da", "dal", "dalla", "con", "e", "ed", "in", "il", "lo", "la", "l", "i", "gli", "le", "un", "uno",
        "una", "per", "with", "and", "of", "the", "con", "y", "de", "et", "mit", "und", "com"
    )

    /** Gruppi di parole equivalenti (normalizzate): ognuna trova le altre. */
    private val synonymGroups: List<Set<String>> = listOf(
        setOf("pasta", "spaghetti", "penne", "fusilli", "rigatoni", "maccheroni", "linguine", "bucatini", "paccheri", "tagliatelle", "mezze", "trofie", "orecchiette"),
        setOf("sugo", "salsa"),
        setOf("pane", "panino", "pagnotta", "rosetta", "michetta", "baguette"),
        setOf("manzo", "bovino", "vitellone"),
        setOf("maiale", "suino"),
        setOf("cotoletta", "milanese", "impanata", "impanato"),
        setOf("lesso", "bollito", "lessa", "lessato"),
        setOf("yogurt", "yoghurt", "yogourt"),
        setOf("caffe", "espresso"),
        setOf("evo", "extravergine"),
        setOf("insalata", "lattuga"),
        setOf("patatine", "chips"),
        setOf("arancino", "arancina"),
        setOf("frittata", "omelette"),
        setOf("minestra", "minestrone", "zuppa"),
        setOf("formaggio", "cacio"),
        setOf("gamberi", "gamberetti"),
        setOf("hamburger", "burger"),
        setOf("wurstel", "wuerstel", "wurst", "hotdog"),
        setOf("spremuta", "succo"),
        setOf("te", "the", "tea"),
        setOf("philadelphia", "spalmabile"),
        setOf("muesli", "granola"),
        setOf("porridge", "avena"),
        setOf("acciughe", "alici"),
        setOf("branzino", "spigola"),
        setOf("focaccia", "schiacciata"),
        setOf("cornetto", "croissant"),
        setOf("anguria", "cocomero"),
        setOf("albicocche", "albicocca"),
        setOf("nutella", "nocciole")
    )

    private val pesce = listOf("merluzzo", "nasello", "orata", "branzino", "spigola", "sogliola", "trota", "salmone", "tonno", "sgombro", "sardine", "alici", "acciughe", "baccala", "gamberi", "calamari", "polpo", "cozze", "vongole", "surimi")
    private val carne = listOf("manzo", "vitello", "maiale", "pollo", "tacchino", "agnello", "coniglio", "bistecca", "filetto", "macinato", "salsiccia", "hamburger", "spezzatino", "brasato", "cotoletta", "scaloppine", "fettina", "lonza")
    private val salumi = listOf("prosciutto", "bresaola", "speck", "mortadella", "salame", "pancetta", "coppa", "capocollo", "soppressata", "wurstel")
    private val formaggi = listOf("parmigiano", "grana", "pecorino", "gorgonzola", "fontina", "provolone", "emmental", "asiago", "scamorza", "feta", "stracchino", "mozzarella", "burrata", "ricotta", "mascarpone", "caciocavallo", "crescenza", "taleggio", "brie", "camembert", "robiola", "edam", "cheddar", "groviera", "caciotta", "formaggino", "spalmabile", "philadelphia", "fiocchi")
    private val frutta = listOf("mela", "pera", "banana", "arancia", "mandarino", "clementine", "pompelmo", "fragole", "mirtilli", "lamponi", "more", "ciliegie", "albicocche", "pesca", "nettarine", "prugne", "uva", "anguria", "melone", "kiwi", "ananas", "mango", "papaya", "fichi", "cachi", "melograno", "avocado")
    private val verdura = listOf("insalata", "lattuga", "rucola", "spinaci", "bietole", "cavolo", "cavolfiore", "broccoli", "zucchine", "melanzane", "peperoni", "pomodori", "carote", "cipolle", "finocchi", "sedano", "asparagi", "carciofi", "fagiolini", "funghi", "zucca", "radicchio", "cetrioli", "verdure", "barbabietola", "ravanelli")
    private val legumi = listOf("fagioli", "ceci", "lenticchie", "piselli", "fave", "edamame", "soia", "hummus")
    private val dolci = listOf("torta", "crostata", "tiramisu", "gelato", "budino", "biscotti", "cornetto", "brioche", "merendina", "cannolo", "pastiera", "panettone", "torrone", "castagnaccio", "plumcake", "cioccolato", "savoiardi", "wafer")
    private val primi = listOf("pasta", "riso", "risotto", "gnocchi", "ravioli", "tortellini", "lasagne", "polenta", "minestrone", "zuppa", "couscous")
    private val colazione = listOf("cornetto", "brioche", "biscotti", "fette", "cereali", "muesli", "latte", "yogurt", "cappuccino", "marmellata", "avena", "corn")
    private val bibite = listOf("cola", "aranciata", "isotonica", "succo", "spremuta", "acqua", "te")
    private val alcolici = listOf("birra", "vino", "spritz", "superalcolici")

    /** Parole-gruppo (anche nelle altre lingue dell'app): trovano i cibi del gruppo, in un solo verso. */
    private val groups: Map<String, List<String>> = HashMap<String, List<String>>().apply {
        fun group(words: String, list: List<String>) = words.split(' ').forEach { this[it] = list }
        group("pesce pesci fish pescado poisson fisch peixe", pesce)
        group("carne carni meat viande fleisch", carne)
        group("salumi salume affettati affettato insaccati charcuterie embutidos wurstwaren frios", salumi)
        group("formaggi formaggio cheese queso fromage kase queijo", formaggi)
        group("frutta fruit fruta fruits obst", frutta)
        group("verdura verdure ortaggi vegetables vegetable verduras legumes gemuse", verdura)
        group("legumi legume beans pulses legumbres legumineuses hulsenfruchte leguminosas", legumi)
        group("dolce dolci dessert desserts sweets postre postres dulces nachtisch sobremesa doces", dolci)
        group("primo primi", primi)
        group("colazione breakfast desayuno fruhstuck", colazione)
        group("bibita bibite bevanda bevande drink drinks bebida boisson getrank", bibite)
        group("alcolici alcol alcohol alcool alkohol", alcolici)
    }

    private val synonyms: Map<String, List<String>> =
        synonymGroups.flatMap { g -> g.map { it to (g - it).toList() } }.toMap()

    private val separators = charArrayOf(' ', '\'', ',', '-', '(', ')', '|', '/', '.', '%')

    fun tokens(normalized: String): List<String> = normalized.split(*separators).filter { it.isNotEmpty() }

    /** Parole utili della ricerca (senza articoli e preposizioni; se restano solo quelle, tutte). */
    fun queryWords(normalizedQuery: String): List<String> {
        val all = tokens(normalizedQuery)
        return all.filter { it !in stopWords }.ifEmpty { all }
    }

    // ---- Forme delle parole (singolare/plurale) ----

    /**
     * Forma base di una parola italiana: senza vocale finale e senza la "h" di -che/-chi/-ghe/-ghi;
     * -cia/-gia/-cie/-gie perdono anche la "i" ("arancia" e "arance" → "aranc"). [soft] dice se c/g finale
     * è dolce ("pesce" → dolce, "pesca"/"pesche" → duro): così "pesce" non trova "pesca". Sotto le 4 lettere
     * la parola resta com'è.
     */
    class Form(val base: String, val ending: Char?, val soft: Boolean?)

    fun form(word: String): Form {
        if (word.length < 4) return Form(word, null, null)
        var w = word
        if (w.endsWith("s") && w.length > 4 && w[w.length - 2] !in "aeiou") w = w.dropLast(1) // inglese: eggs → egg
        if (w.length >= 5 && (w.endsWith("cia") || w.endsWith("gia") || w.endsWith("cie") || w.endsWith("gie"))) {
            return Form(w.dropLast(2), w.last(), true)
        }
        val last = w.last()
        if (last !in "aeiou") return Form(w, null, null)
        var base = w.dropLast(1)
        var hard = false
        if (base.length >= 3 && (base.endsWith("ch") || base.endsWith("gh"))) { base = base.dropLast(1); hard = true }
        val soft = if (base.last() in "cg") !hard && last in "ei" else null
        return Form(base, last, soft)
    }

    /** Plurali irregolari in -a (uovo → uova). */
    private val pluralInA = setOf("uov", "pai")

    /** Desinenze di singolare e plurale della stessa parola: o/i, a/e, e/i (e uguali). */
    private fun compatibleEndings(a: Char?, b: Char?, base: String): Boolean =
        a == b || a == null || b == null || setOf(a, b).let {
            it == setOf('o', 'i') || it == setOf('a', 'e') || it == setOf('e', 'i') || (it == setOf('o', 'a') && base in pluralInA)
        }

    private fun sameWord(q: Form, t: Form): Boolean =
        q.base.length >= 3 && q.base == t.base && compatibleEndings(q.ending, t.ending, q.base) &&
            (q.soft == null || t.soft == null || q.soft == t.soft)

    /** La parola [token] continua [base] con un suono compatibile ("pesc" dolce: "pesce" sì, "pesca" no). */
    private fun continuesBase(token: String, base: String, soft: Boolean?): Boolean {
        if (base.length < 3 || !token.startsWith(base)) return false
        val next = token.getOrNull(base.length) ?: return true
        return soft != true || next in "ei"
    }

    // ---- Indice ----

    /** Una voce dell'indice: tutto ciò che serve a confrontarla, calcolato una volta. */
    class Entry(val food: Food, normalized: String, shownLength: Int) {
        val tokens: Array<String> = tokens(normalized).toTypedArray()
        val forms: Array<Form> = Array(tokens.size) { form(tokens[it]) }
        val categoryForms: Array<Form> = tokens(FoodCatalog.normalize(food.category)).map { form(it) }.toTypedArray()
        /** 0 = cibo generico (o personale), 1 = piatto pronto, 2 = prodotto di marca. */
        val tier: Int = when {
            food.brand != null || food.barcode != null || food.category == "Marche" -> 2
            food.category == "Piatti pronti" -> 1
            else -> 0
        }
        val length = shownLength
    }

    /** La ricerca preparata una volta: parole, forme, sinonimi, gruppi, tolleranza agli errori. */
    class Query(normalizedQuery: String) {
        val text = normalizedQuery
        val words: Array<String> = queryWords(normalizedQuery).toTypedArray()
        val forms: Array<Form> = Array(words.size) { form(words[it]) }
        val synonyms: Array<TermSet?> = Array(words.size) { i ->
            val b = forms[i].base
            (FoodSearch.synonyms[words[i]] ?: FoodSearch.synonyms[b + "i"] ?: FoodSearch.synonyms[b + "o"]
                ?: FoodSearch.synonyms[b + "a"] ?: FoodSearch.synonyms[b + "e"])?.let { termSet(it) }
        }
        val groups: Array<TermSet?> = Array(words.size) { i -> FoodSearch.groups[words[i]]?.let { termSet(it) } }
        val maxTypos: IntArray = IntArray(words.size) { i -> words[i].length.let { if (it >= 8) 2 else if (it >= 5) 1 else 0 } }
        val isEmpty get() = words.isEmpty()
    }

    /** Costo di una parola che non trova posto, quando si accettano corrispondenze parziali. */
    const val MISSING = 10

    /**
     * Costo della voce per la ricerca (più basso = più pertinente), oppure -1 se non corrisponde.
     * Con [partial] una parola può mancare (costa [MISSING]), purché almeno una trovi posto.
     */
    fun score(q: Query, e: Entry, partial: Boolean = false): Int {
        var total = 0
        var found = 0
        for (i in q.words.indices) {
            val c = wordCost(q, i, e)
            if (c < 0) { if (!partial) return -1; total += MISSING } else { total += c; found++ }
        }
        return if (found == 0) -1 else total
    }

    private fun wordCost(q: Query, i: Int, e: Entry): Int {
        val w = q.words[i]
        val f = q.forms[i]
        var best = Int.MAX_VALUE
        val tokens = e.tokens
        for (j in tokens.indices) {
            val t = tokens[j]
            val c = when {
                t == w || sameWord(f, e.forms[j]) -> return 0
                t.startsWith(w) -> 1
                f.base != w && continuesBase(t, f.base, f.soft) -> 2
                w.length >= 3 && t.contains(w) -> 4
                else -> continue
            }
            if (c < best) best = c
        }
        if (best <= 2) return best
        if (matchesAny(q.synonyms[i], e)) return 2
        if (matchesAny(q.groups[i], e)) return 0 // "formaggi" → i formaggi stessi, prima dei piatti che li contengono
        if (e.categoryForms.any { it.base == w || sameWord(f, it) }) return 3
        if (best != Int.MAX_VALUE) return best
        val max = q.maxTypos[i]
        if (max > 0) for (t in tokens) if (typo(w, t, max)) return 5
        return -1
    }

    /** Parole di un gruppo o di sinonimi, indicizzate per parola e per forma base: il confronto è una ricerca per chiave. */
    class TermSet(val exact: Set<String>, val byBase: Map<String, List<Form>>)

    private val termSets = java.util.concurrent.ConcurrentHashMap<List<String>, TermSet>()

    private fun termSet(words: List<String>): TermSet =
        termSets.getOrPut(words) { TermSet(words.toHashSet(), words.map { form(it) }.groupBy { it.base }) }

    /** Una delle parole di [set] (sinonimi o gruppo) è una parola del nome, anche al singolare/plurale. */
    private fun matchesAny(set: TermSet?, e: Entry): Boolean {
        if (set == null) return false
        for (j in e.tokens.indices) {
            if (e.tokens[j] in set.exact) return true
            val tf = e.forms[j]
            if (set.byBase[tf.base]?.any { sameWord(it, tf) } == true) return true
        }
        return false
    }

    private fun typo(word: String, token: String, max: Int): Boolean {
        if (token.length + max < word.length || token.length < 3) return false
        // Quasi sempre la prima o la seconda lettera è giusta: scarta subito il resto (la parte più lenta)
        if (token[0] != word[0] && token[1] != word[1]) return false
        if (bounded(word, token, max) <= max) return true
        return token.length > word.length && bounded(word, token.substring(0, word.length), max) <= max
    }

    // Righe riusate dalla distanza (la ricerca gira su un thread alla volta: la UI)
    private var prev2 = IntArray(32)
    private var prev = IntArray(32)
    private var cur = IntArray(32)

    /**
     * Distanza di Damerau-Levenshtein (con lo scambio di due lettere vicine) fermandosi appena supera [max]:
     * restituisce max + 1 se la distanza è maggiore.
     */
    @Synchronized
    internal fun bounded(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        val n = b.length
        if (cur.size <= n) { prev2 = IntArray(n + 1); prev = IntArray(n + 1); cur = IntArray(n + 1) }
        for (j in 0..n) prev[j] = j
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[n]
    }

    /** Distanza piena (per i test). */
    internal fun distance(a: String, b: String): Int = bounded(a, b, maxOf(a.length, b.length))

    /** Vero se la prima parola cercata è (o inizia) la prima parola del nome mostrato. */
    fun startsWithFirstWord(q: Query, e: Entry): Boolean {
        val t = e.tokens.firstOrNull() ?: return false
        val w = q.words.firstOrNull() ?: return false
        return t.startsWith(w) || sameWord(q.forms[0], e.forms[0])
    }
}
