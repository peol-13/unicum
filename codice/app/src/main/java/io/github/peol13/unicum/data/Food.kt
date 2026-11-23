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
     * Cerca tra catalogo e alimenti personalizzati. Ignora maiuscole e accenti; ogni parola
     * digitata deve comparire nel nome. I nomi che iniziano con la ricerca vengono prima.
     */
    fun search(
        query: String,
        category: String? = null,
        extra: List<Food> = emptyList()
    ): List<Food> {
        val all = (extra + defaultFoods).distinctBy { it.name }
        val pool = if (category == null) all else all.filter { it.category == category }
        val q = normalize(query)
        val words = q.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return pool
        // Ogni nome si normalizza una volta sola (la ricerca gira a ogni lettera digitata)
        // Si cerca nel nome mostrato (lingua dell'app) e in quello italiano del catalogo: "chicken" e "pollo"
        return pool
            .map { it to normalizedName(it.name) }
            .filter { (_, n) -> words.all { n.contains(it) } }
            .sortedWith(compareBy<Pair<Food, String>> { !it.second.startsWith(q) }.thenBy { FoodNames.label(it.first.name).length })
            .map { it.first }
    }

    // Nomi del catalogo già normalizzati (nome mostrato + italiano), per lingua; personali e online al volo
    private val catalogNamesByLanguage = java.util.concurrent.ConcurrentHashMap<io.github.peol13.unicum.i18n.AppLanguage, Map<String, String>>()
    private val catalogNames: Map<String, String>
        get() = io.github.peol13.unicum.i18n.I18n.language.let { lang ->
            catalogNamesByLanguage.getOrPut(lang) {
                defaultFoods.associate { f ->
                    val shown = FoodNames.label(f.name, lang)
                    f.name to normalize(if (shown == f.name) f.name else "$shown | ${f.name}")
                }
            }
        }

    // Anche i nomi personali, online o già registrati: la ricerca gira a ogni lettera sugli stessi nomi
    private val otherNames = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun normalizedName(name: String): String = catalogNames[name] ?: run {
        if (otherNames.size > 5000) otherNames.clear()
        otherNames.getOrPut(name) { normalize(name) }
    }
}
