package io.github.peol13.unicum.data

import kotlinx.serialization.Serializable

/**
 * Pasto fisso salvato (es. "Colazione"): alimenti e grammi da ricaricare con un tocco nel dialog
 * del pasto, dove restano modificabili prima di salvare. Il nome è la chiave (senza maiuscole).
 */
@Serializable
data class SavedMeal(
    val name: String,
    val items: List<DraftItem>
) {
    val calories: Int get() = items.sumOf { it.food.portion(it.grams).calories }

    companion object {
        /** Aggiunge [meal] in cima; uno con lo stesso nome viene sostituito. */
        fun upsert(list: List<SavedMeal>, meal: SavedMeal): List<SavedMeal> =
            listOf(meal) + list.filterNot { it.name.equals(meal.name, true) }

        fun remove(list: List<SavedMeal>, name: String): List<SavedMeal> =
            list.filterNot { it.name.equals(name, true) }

        /** Backup: quelli già presenti (stesso nome) restano. */
        fun merge(current: List<SavedMeal>, incoming: List<SavedMeal>): List<SavedMeal> =
            current + incoming.filter { m -> current.none { it.name.equals(m.name, true) } }

        /** Pasti che corrispondono alla ricerca, con le regole degli alimenti (plurali, sinonimi, errori; vuota = tutti). */
        fun search(list: List<SavedMeal>, query: String): List<SavedMeal> = FoodCatalog.filter(query, list) { it.name }
    }
}
