package io.github.peol13.unicum.data

import kotlinx.serialization.Serializable
import java.text.Normalizer

@Serializable
data class Food(
    val name: String,
    val caloriesPer100g: Int,
    val proteinPer100g: Float = 0f,
    val carbsPer100g: Float = 0f,
    val fatsPer100g: Float = 0f,
    val category: String = "Generico",
    /** Marca, per i prodotti confezionati (Open Food Facts). */
    val brand: String? = null,
    /** Codice a barre, per i prodotti confezionati; serve a riconoscerli e a salvarli in locale. */
    val barcode: String? = null,
    /** Fibre, zuccheri e sale per 100 g: null se il dato non c'è (il database di base non li ha). */
    val fiberPer100g: Float? = null,
    val sugarsPer100g: Float? = null,
    val saltPer100g: Float? = null
) {
    /** [grams] grammi di questo alimento come voce di un pasto: kcal e macro in proporzione ai 100 g. */
    fun portion(grams: Int, mealType: String = "Altro", minuteOfDay: Int? = null): Meal {
        val f = grams / 100f
        return Meal(
            name = name,
            calories = Math.round(caloriesPer100g * f),
            mealType = mealType,
            quantityGrams = grams,
            protein = proteinPer100g * f,
            carbohydrates = carbsPer100g * f,
            fats = fatsPer100g * f,
            minuteOfDay = minuteOfDay,
            fiber = fiberPer100g?.times(f),
            sugars = sugarsPer100g?.times(f),
            salt = saltPer100g?.times(f)
        )
    }
}

object FoodCatalog {
    val defaultFoods: List<Food> get() = FoodDatabase.foods

    /** Categorie mostrate come filtro, nell'ordine in cui compaiono nel database. */
    val categories: List<String> by lazy { defaultFoods.map { it.category }.distinct() }

    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD).replace(diacritics, "")

    /**
     * Cerca tra catalogo e alimenti personalizzati, online o già registrati ([extra], che vincono sui nomi
     * uguali del catalogo). Regole e ordine in [FoodSearch]: in breve, corrispondenze più precise prima,
     * a parità i cibi generici prima dei piatti pronti e delle marche, poi i nomi più corti.
     */
    fun search(
        query: String,
        category: String? = null,
        extra: List<Food> = emptyList()
    ): List<Food> = searchResult(query, category, extra).foods

    /**
     * Risultati di una ricerca, dal più pertinente. [strength] (stessa posizione di [foods]): 0 = corrispondenza
     * buona (parole intere, singolare/plurale, inizio di parola, sinonimi, gruppi), 1 = debole (dentro una parola,
     * errori di battitura), 2 = parziale. [partial] = nessun alimento ha tutte le parole, questi ne hanno almeno una.
     */
    class Result(val foods: List<Food>, val strength: List<Int>, val partial: Boolean)

    /** Come [search]; se nessun alimento ha tutte le parole (e sono più d'una) restituisce quelli che ne hanno di più. */
    fun searchResult(
        query: String,
        category: String? = null,
        extra: List<Food> = emptyList()
    ): Result {
        val lang = io.github.peol13.unicum.i18n.I18n.language
        val entries = ArrayList<FoodSearch.Entry>(extra.size + defaultFoods.size)
        val seen = HashSet<String>(extra.size * 2)
        for (f in extra) if (seen.add(f.name)) entries += entryFor(f, lang)
        for (e in catalogIndex(lang)) if (e.food.name !in seen) entries += e
        val pool = if (category == null) entries else entries.filter { it.food.category == category }
        val q = FoodSearch.Query(normalize(query))
        if (q.isEmpty) return pool.map { it.food }.let { Result(it, List(it.size) { 0 }, false) }
        val full = rank(q, pool, partial = false)
        if (full.isNotEmpty() || q.words.size < 2) return result(q, full, false)
        return result(q, rank(q, pool, partial = true).take(30), true)
    }

    private class Hit(val e: FoodSearch.Entry, val cost: Int, val first: Boolean)

    private fun result(q: FoodSearch.Query, hits: List<Hit>, partial: Boolean) = Result(
        hits.map { it.e.food },
        hits.map { h -> if (h.cost >= FoodSearch.MISSING) 2 else if (h.cost <= 2 * q.words.size) 0 else 1 },
        partial
    )

    private fun rank(q: FoodSearch.Query, pool: List<FoodSearch.Entry>, partial: Boolean): List<Hit> {
        val hits = ArrayList<Hit>()
        for (e in pool) {
            val cost = FoodSearch.score(q, e, partial)
            if (cost >= 0) hits += Hit(e, cost, FoodSearch.startsWithFirstWord(q, e))
        }
        hits.sortWith(
            compareBy<Hit> { it.cost }.thenBy { it.e.tier }.thenBy { !it.first }.thenBy { it.e.length }
        )
        return hits
    }

    /**
     * Le stesse regole di [search] su un elenco qualsiasi (pasti salvati, prodotti salvati in locale): tiene
     * chi corrisponde, dal più pertinente; ricerca vuota = elenco com'è.
     */
    fun <T> filter(query: String, items: List<T>, name: (T) -> String): List<T> {
        val q = FoodSearch.Query(normalize(query))
        if (q.isEmpty) return items
        return items
            .mapNotNull { item ->
                val n = name(item)
                val e = FoodSearch.Entry(Food(n, 0), normalize(n), n.length)
                FoodSearch.score(q, e).takeIf { it >= 0 }?.let { Triple(item, it, n.length) }
            }
            .sortedWith(compareBy<Triple<T, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    /** La parola più lunga della ricerca ridotta alla sua forma base: per prefiltrare in SQL senza perdere i plurali. */
    fun sqlKeyword(query: String): String? =
        FoodSearch.queryWords(normalize(query)).maxByOrNull { it.length }
            ?.let { w -> FoodSearch.form(w).base.takeIf { it.length >= 3 } ?: w }

    // Indice del catalogo per lingua (nome mostrato + italiano): si costruisce una volta sola
    private val catalogByLanguage = java.util.concurrent.ConcurrentHashMap<io.github.peol13.unicum.i18n.AppLanguage, List<FoodSearch.Entry>>()

    private fun catalogIndex(lang: io.github.peol13.unicum.i18n.AppLanguage): List<FoodSearch.Entry> =
        catalogByLanguage.getOrPut(lang) { defaultFoods.map { buildEntry(it, lang) } }

    // Voci degli alimenti personali, online o già registrati: la ricerca gira a ogni lettera sugli stessi cibi
    private val otherEntries = java.util.concurrent.ConcurrentHashMap<Pair<io.github.peol13.unicum.i18n.AppLanguage, Food>, FoodSearch.Entry>()

    private fun entryFor(f: Food, lang: io.github.peol13.unicum.i18n.AppLanguage): FoodSearch.Entry {
        if (otherEntries.size > 5000) otherEntries.clear()
        return otherEntries.getOrPut(lang to f) { buildEntry(f, lang) }
    }

    /** Si cerca nel nome mostrato (lingua dell'app), in quello italiano del catalogo e nella marca: "chicken", "pollo", "barilla". */
    private fun buildEntry(f: Food, lang: io.github.peol13.unicum.i18n.AppLanguage): FoodSearch.Entry {
        val shown = FoodNames.label(f.name, lang)
        val text = buildString {
            append(shown)
            if (shown != f.name) append(" | ").append(f.name)
            if (!f.brand.isNullOrBlank()) append(" | ").append(f.brand)
        }
        return FoodSearch.Entry(f, normalize(text), shown.length)
    }
}
