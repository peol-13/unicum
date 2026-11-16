package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.ui.Route

import io.github.peol13.unicum.ui.components.LocalHideCardTitle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.MealTime
import io.github.peol13.unicum.ui.components.AddEntryDialogs
import io.github.peol13.unicum.ui.components.RoundIconButton
import io.github.peol13.unicum.ui.components.rememberAddEntryState
import io.github.peol13.unicum.ui.components.UnicumFab
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.sections.ActivityBigCard
import io.github.peol13.unicum.ui.sections.ActivityNumbersCard
import io.github.peol13.unicum.ui.sections.CalorieBreakdownCard
import io.github.peol13.unicum.ui.sections.CalorieCard
import io.github.peol13.unicum.ui.sections.Gap
import io.github.peol13.unicum.ui.sections.MealSection
import io.github.peol13.unicum.ui.sections.MealsCard
import io.github.peol13.unicum.ui.sections.OtherNutrientsCard
import io.github.peol13.unicum.ui.sections.RecoveryDetailCard
import io.github.peol13.unicum.ui.sections.SleepBigCard
import io.github.peol13.unicum.ui.sections.SectionHeader
import io.github.peol13.unicum.ui.sections.WorkoutsCard
import io.github.peol13.unicum.ui.sections.rememberDayStats
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumType

/** Argomenti che hanno una pagina di dettaglio (le card della Home). */
enum class DetailTopic(val route: String, val title: String) {
    CALORIE("calorie", "Calorie"),
    ATTIVITA("attivita", "Attività"),
    SONNO("sonno", "Sonno"),
    RECUPERO("recupero", "Recupero"),
    PASTI("pasti", "Pasti"),
    PESO("peso", "Peso e composizione")
}

/** Pagina con il dettaglio di un argomento per il giorno selezionato. */
@Composable
fun DetailScreen(topic: DetailTopic, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val data = UnicumAppState.dayData
    val stats = rememberDayStats()
    val weekMinutes = UnicumAppState.week.map { it.activityMinutes }

    val adding = rememberAddEntryState()
    AddEntryDialogs(adding)

    UnicumScreen(
        selectedItem = Route.Home,
        onNavigate = onNavigate,
        floatingActionButton = {
            when (topic) {
                DetailTopic.PASTI -> UnicumFab(onClick = { adding.addMeal() })
                DetailTopic.ATTIVITA -> UnicumFab(onClick = adding::addActivity)
                else -> {}
            }
        }
    ) {
        DetailHeader(topic, onBack)
        Spacer(Modifier.height(16.dp))

        when (topic) {
            DetailTopic.CALORIE -> {
                CalorieCard(stats)
                Gap()
                CalorieBreakdownCard(stats)
                Gap()
                OtherNutrientsCard()
            }

            DetailTopic.ATTIVITA -> {
                ActivityBigCard(stats, weekMinutes)
                Gap()
                ActivityNumbersCard()
                Gap()
                WorkoutsCard(
                    data.activities,
                    limit = Int.MAX_VALUE,
                    onDelete = { UnicumAppState.removeActivity(context, it) }
                )
            }

            DetailTopic.SONNO -> {
                SleepBigCard(UnicumAppState.lastNight)
                // Il recupero di oggi: lo storico delle notti è nelle Statistiche
                SectionHeader(if (UnicumAppState.isToday) "Recupero di oggi" else "Recupero del giorno")
                CompositionLocalProvider(LocalHideCardTitle provides true) { RecoveryDetailCard() }
            }

            DetailTopic.RECUPERO -> {
                RecoveryDetailCard()
            }

            DetailTopic.PESO -> BodyCompositionCard()

            DetailTopic.PASTI -> {
                MealsCard(data.meals)
                Gap()
                // Colazione, pranzo, cena e ogni pasto "Altro" col suo nome (es. due spuntini), in ordine di orario
                MealTime.groups(data.meals, withEmptyOther = true).forEach { (type, meals) ->
                    MealSection(
                        title = type,
                        meals = meals,
                        onAdd = { adding.addMeal(type) },
                        onDelete = { UnicumAppState.removeMeal(context, it) }
                    )
                    Gap()
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(topic: DetailTopic, onBack: () -> Unit) {
    val day = DateUtils.parse(UnicumAppState.selectedDate)
    PageHeader(
        title = topic.title,
        subtitle = if (topic == DetailTopic.PESO) "Andamento nel tempo, da Health Connect"
        else DateUtils.relativeLabel(day) + " · " + DateUtils.fullLabel(day),
        onBack = onBack
    )
}

/** Intestazione delle pagine che si aprono dalla Home: freccia indietro, titolo e sottotitolo. */
@Composable
internal fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onBack)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = UnicumDark, style = UnicumType.Page, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail)
        }
    }
}
