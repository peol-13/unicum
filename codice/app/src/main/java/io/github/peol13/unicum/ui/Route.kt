package io.github.peol13.unicum.ui

import androidx.compose.runtime.saveable.Saver
import io.github.peol13.unicum.ui.screens.DetailTopic
import java.time.LocalDate

/**
 * Le schermate dell'app. Prima erano stringhe con prefissi ("Allenamento/<id>", "Dettaglio/sonno"…) sparse nei file:
 * un refuso compilava lo stesso e portava alla Home. Ora ogni destinazione è un tipo e il compilatore le controlla.
 *
 * [key] è la forma salvata (rotazione dello schermo, cambio tema del telefono) e coincide con le stringhe di prima:
 * [decode] la rilegge. Una chiave sconosciuta dà null (si torna alla Home).
 */
sealed interface Route {
    val key: String

    /** Le cinque voci della barra in basso. */
    sealed class Tab(final override val key: String) : Route

    data object Home : Tab("Home")
    data object Allenamenti : Tab("Allenamenti")
    data object Statistiche : Tab("Statistiche")
    data object Diario : Tab("Diario")
    data object Profilo : Tab("Profilo")

    data object Consigli : Route { override val key = "Consigli" }
    data object HomeEditor : Route { override val key = "PersonalizzaHome" }
    data object WorkoutList : Route { override val key = "Allenamenti/elenco" }
    data object Notion : Route { override val key = "Allenamenti/notion" }
    data object Shoes : Route { override val key = "Allenamenti/scarpe" }

    /** La pagina di un allenamento ([id] = sessione di Health Connect). */
    data class Workout(val id: String) : Route { override val key get() = WORKOUT + id }

    data class Shoe(val id: String) : Route { override val key get() = SHOE + id }

    /** Il racconto della settimana che inizia il lunedì [monday]. */
    data class WeekStory(val monday: LocalDate) : Route { override val key get() = WEEK + monday }

    data class Detail(val topic: DetailTopic) : Route { override val key get() = DETAIL + topic.route }

    companion object {
        private const val WORKOUT = "Allenamento/"
        private const val SHOE = "Scarpa/"
        private const val WEEK = "Settimana/"
        private const val DETAIL = "Dettaglio/"

        private val fixed: List<Route> =
            listOf(Home, Allenamenti, Statistiche, Diario, Profilo, Consigli, HomeEditor, WorkoutList, Notion, Shoes)

        fun decode(key: String): Route? {
            fixed.firstOrNull { it.key == key }?.let { return it }
            return when {
                key.startsWith(WORKOUT) -> key.removePrefix(WORKOUT).takeIf { it.isNotEmpty() }?.let(::Workout)
                key.startsWith(SHOE) -> key.removePrefix(SHOE).takeIf { it.isNotEmpty() }?.let(::Shoe)
                key.startsWith(WEEK) -> runCatching { LocalDate.parse(key.removePrefix(WEEK)) }.getOrNull()?.let(::WeekStory)
                key.startsWith(DETAIL) -> DetailTopic.entries.firstOrNull { it.route == key.removePrefix(DETAIL) }?.let(::Detail)
                else -> null
            }
        }

        /** Per `rememberSaveable`: si salva la chiave, si rilegge con [decode] (Home se non la riconosce). */
        val Saver: Saver<Route, String> = Saver(save = { it.key }, restore = { decode(it) ?: Home })
    }
}
