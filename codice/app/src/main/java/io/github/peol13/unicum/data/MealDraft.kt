package io.github.peol13.unicum.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Un alimento già aggiunto al pasto in inserimento, con la quantità. */
@Serializable
data class DraftItem(val food: Food, val grams: Int)

/** Pasto lasciato a metà: resta salvato finché non lo salvi o lo scarti. */
@Serializable
data class MealDraft(
    val date: String,
    val mealType: String,
    val minute: Int,
    val minuteTouched: Boolean,
    val items: List<DraftItem>
) {
    companion object {
        /** La bozza salvata, se esiste ed è per il giorno [date]. */
        fun load(date: String): MealDraft? =
            runCatching { UserSettings.mealDraftJson?.let { Json.decodeFromString<MealDraft>(it) } }.getOrNull()
                ?.takeIf { it.date == date && it.items.isNotEmpty() }

        fun save(draft: MealDraft) {
            UserSettings.mealDraftJson = if (draft.items.isEmpty()) null else Json.encodeToString(draft)
        }

        fun clear() {
            UserSettings.mealDraftJson = null
        }
    }
}
