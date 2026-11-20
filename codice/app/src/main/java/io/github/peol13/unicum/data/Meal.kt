package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.i18n.tk

import kotlinx.serialization.Serializable

@Serializable
data class Meal(
    val name: String,
    val calories: Int,
    val mealType: String = "Altro",
    val quantityGrams: Int = 0,
    val protein: Float = 0f,
    val carbohydrates: Float = 0f,
    val fats: Float = 0f,
    val id: Long = 0,
    /** Orario del pasto (minuti dalla mezzanotte, ora locale); null = adesso. */
    val minuteOfDay: Int? = null,
    /** Fibre, zuccheri e sale in grammi; null se l'alimento non ha il dato. */
    val fiber: Float? = null,
    val sugars: Float? = null,
    val salt: Float? = null
) {
    /**
     * Lo stesso alimento con un'altra quantità: kcal e macro in proporzione. Se si conosce l'alimento [food]
     * e la voce viene da lì (stesse kcal per la quantità attuale), si riparte dai suoi valori per 100 g: così
     * cambiare più volte i grammi non accumula arrotondamenti (100 g → 30 g → 100 g torna alle kcal di prima).
     */
    fun withGrams(grams: Int, food: Food? = null): Meal {
        if (quantityGrams <= 0 || grams <= 0) return this
        if (grams == quantityGrams) return this
        if (food != null && kotlin.math.abs(food.portion(quantityGrams).calories - calories) <= 1) {
            return food.portion(grams, mealType, minuteOfDay).copy(id = id)
        }
        val f = grams / quantityGrams.toFloat()
        return copy(
            quantityGrams = grams,
            calories = Math.round(calories * f),
            protein = protein * f,
            carbohydrates = carbohydrates * f,
            fats = fats * f,
            fiber = fiber?.times(f),
            sugars = sugars?.times(f),
            salt = salt?.times(f)
        )
    }

    /** "13:05", oppure null se l'orario non è noto. */
    val timeText: String? get() = minuteOfDay?.let { MealTime.format(it) }
}

/** Orari dei pasti: minuti dalla mezzanotte <-> testo e istanti del giorno. */
object MealTime {
    /** I tipi di pasto, nell'ordine della giornata ("Altro" = spuntino). */
    val TYPES = listOf(tk("Colazione"), tk("Pranzo"), tk("Cena"), tk("Altro"))
    const val OTHER = "Altro"
    private val MAIN = listOf("Colazione", "Pranzo", "Cena")

    /**
     * Un pasto "Altro" può avere un nome proprio (es. "Spuntino mattina"), salvato al posto di "Altro"
     * in `mealType`: così due spuntini dello stesso giorno restano due pasti separati.
     */
    fun isOther(type: String): Boolean = MAIN.none { it.equals(type.trim(), ignoreCase = true) }

    /** Il nome da mostrare: i quattro tipi tradotti nella lingua dell'app, i nomi propri così come sono. */
    fun label(type: String): String = TYPES.firstOrNull { it.equals(type.trim(), ignoreCase = true) }?.let { tr(it) } ?: type

    /** Il tipo da mostrare nel selettore: i pasti con un nome proprio sono "Altro". */
    fun baseType(type: String): String = MAIN.firstOrNull { it.equals(type.trim(), ignoreCase = true) } ?: OTHER

    /** Il nome proprio di un pasto "Altro", oppure null. */
    fun customName(type: String): String? =
        type.trim().takeIf { isOther(it) && !it.equals(OTHER, ignoreCase = true) && it.isNotEmpty() }

    /** Il tipo da salvare: "Altro" con un nome diventa il nome (iniziale maiuscola), senza nome resta "Altro". */
    fun compose(base: String, name: String): String {
        if (base != OTHER) return base
        val n = name.trim().replace(Regex("\\s+"), " ")
        return if (n.isEmpty()) OTHER else n.replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * I pasti del giorno divisi per tipo: Colazione, Pranzo e Cena sempre (anche vuoti), poi ogni pasto
     * "Altro" col suo nome, messo tra gli altri secondo l'orario. [withEmptyOther]: un "Altro" vuoto in fondo,
     * per aggiungere un nuovo spuntino.
     */
    fun groups(meals: List<Meal>, withEmptyOther: Boolean): List<Pair<String, List<Meal>>> {
        val main = MAIN.map { t -> t to meals.filter { it.mealType.trim().equals(t, ignoreCase = true) } }
        val others = meals.filter { isOther(it.mealType) }
            .groupBy { it.mealType.trim().lowercase() }
            .values.map { list -> list.first().mealType.trim() to list }
        fun key(g: Pair<String, List<Meal>>): Int =
            if (isOther(g.first)) g.second.mapNotNull { it.minuteOfDay }.minOrNull() ?: typical(g.first)
            else typical(g.first)
        val all = (main + others).sortedBy(::key)
        val hasPlainOther = others.any { it.first.equals(OTHER, ignoreCase = true) }
        return if (withEmptyOther && !hasPlainOther) all + (OTHER to emptyList()) else all
    }

    fun format(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    fun now(): Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }

    /** Il pasto più probabile a quest'ora (proposto quando si apre la registrazione dalla Home). */
    fun suggestType(minuteOfDay: Int = now()): String = when {
        minuteOfDay < 11 * 60 -> "Colazione"
        minuteOfDay < 15 * 60 + 30 -> "Pranzo"
        minuteOfDay < 18 * 60 + 30 -> "Altro"
        else -> "Cena"
    }

    /** Orario tipico di ogni pasto, usato per i giorni passati. */
    fun typical(mealType: String): Int = when (baseType(mealType)) {
        "Colazione" -> 8 * 60
        "Pranzo" -> 13 * 60
        "Cena" -> 20 * 60
        else -> 16 * 60 + 30
    }

    /** Epoch millis del giorno [date] (yyyy-MM-dd) all'orario dato, nel fuso del telefono. */
    fun toMillis(date: String, minuteOfDay: Int): Long =
        java.time.LocalDate.parse(date)
            .atTime(minuteOfDay / 60, minuteOfDay % 60)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    fun fromMillis(millis: Long): Int =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            .let { it.hour * 60 + it.minute }
}
