package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.ui.Route

import io.github.peol13.unicum.ui.components.LocalHideCardTitle
import androidx.compose.runtime.CompositionLocalProvider
import io.github.peol13.unicum.ui.sections.formatDuration
import io.github.peol13.unicum.ui.sections.fmt
import io.github.peol13.unicum.ui.sections.SectionHeader
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.MealTime
import io.github.peol13.unicum.ui.components.AddEntryDialogs
import io.github.peol13.unicum.ui.components.AddFabMenu
import io.github.peol13.unicum.ui.components.rememberAddEntryState
import io.github.peol13.unicum.ui.components.DateStepper
import io.github.peol13.unicum.ui.components.ScreenTitle
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
import io.github.peol13.unicum.ui.sections.WorkoutsCard
import io.github.peol13.unicum.ui.sections.rememberDayStats

/**
 * Diario: il recap completo di una giornata, con tutte le sezioni delle card riunite
 * (calorie, pasti, attività, sonno, recupero). Le frecce portano ai giorni precedenti.
 */
@Composable
fun DiarioScreen(onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val data = UnicumAppState.dayData
    val stats = rememberDayStats()
    val weekMinutes = UnicumAppState.week.map { it.activityMinutes }
    val day = DateUtils.parse(UnicumAppState.selectedDate)

    val adding = rememberAddEntryState()
    AddEntryDialogs(adding)

    UnicumScreen(
        selectedItem = Route.Diario,
        onNavigate = onNavigate,
        onSwipeDay = { UnicumAppState.shiftDay(context, it) },
        floatingActionButton = {
            AddFabMenu(onAddMeal = { adding.addMeal() }, onAddActivity = adding::addActivity)
        }
    ) {
        ScreenTitle("Diario", "Il recap della giornata")
        Spacer(Modifier.height(8.dp))

        DateStepper(
            label = DateUtils.relativeLabel(day),
            subtitle = DateUtils.fullLabel(day),
            onPrevious = { UnicumAppState.shiftDay(context, -1) },
            onNext = { UnicumAppState.shiftDay(context, +1) },
            canGoNext = !UnicumAppState.isToday
        )
        Spacer(Modifier.height(8.dp))

        // CALORIE
        SectionHeader("Calorie", "${fmt(stats.eaten)} / ${fmt(stats.goal)} kcal", first = true)
        HideTitle { CalorieCard(stats) }
        Gap()
        CalorieBreakdownCard(stats)
        Gap()
        OtherNutrientsCard()

        // PASTI
        SectionHeader("Pasti", if (data.meals.isEmpty()) "nessuno" else "${fmt(stats.eaten)} kcal")
        HideTitle { MealsCard(data.meals) }
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

        // ATTIVITÀ
        SectionHeader("Attività", "${stats.activityMinutes} min")
        HideTitle { ActivityBigCard(stats, weekMinutes) }
        Gap()
        ActivityNumbersCard()
        Gap()
        WorkoutsCard(
            data.activities,
            limit = Int.MAX_VALUE,
            onDelete = { UnicumAppState.removeActivity(context, it) }
        )

        // SONNO
        SectionHeader("Sonno", UnicumAppState.lastNight?.let { formatDuration(it.durationMinutes) } ?: "nessun dato")
        HideTitle { SleepBigCard(UnicumAppState.lastNight) }

        // RECUPERO
        SectionHeader("Recupero")
        HideTitle { RecoveryDetailCard() }
    }
}

/** Le card dentro non ripetono il titolo: c'è già l'intestazione della sezione. */
@Composable
private fun HideTitle(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalHideCardTitle provides true, content = content)
