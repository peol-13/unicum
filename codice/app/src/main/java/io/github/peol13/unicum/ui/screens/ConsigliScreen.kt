package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.ui.Route

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.AdviceCategory
import io.github.peol13.unicum.data.AdviceEngine
import io.github.peol13.unicum.data.AdviceInput
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.MealTime
import io.github.peol13.unicum.data.MetricType
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.sections.DayStats
import io.github.peol13.unicum.ui.sections.Gap
import io.github.peol13.unicum.ui.sections.RecoveryAlertBox
import io.github.peol13.unicum.ui.sections.StatDivider
import io.github.peol13.unicum.ui.sections.rememberDayStats
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumType

/** I dati del giorno mostrato per [AdviceEngine]; oggi anche l'ora attuale, per i consigli su come chiudere la giornata. */
internal fun adviceInput(stats: DayStats): AdviceInput {
    val selected = UnicumAppState.selectedDate
    return AdviceInput(
        lastNight = UnicumAppState.lastNight,
        recentNights = UnicumAppState.health.sleep.filter { it.date <= selected }.takeLast(7),
        hrv = UnicumAppState.latestMetric(MetricType.HRV),
        hrvBaseline = UnicumAppState.metricBaseline(MetricType.HRV),
        weekMinutes = UnicumAppState.week.map { it.activityMinutes },
        steps = UnicumAppState.steps,
        eatenKcal = stats.eaten,
        remainingKcal = stats.remaining,
        proteinEaten = stats.protein,
        proteinGoal = UserSettings.proteinGoal,
        isToday = UnicumAppState.isToday,
        minuteOfDay = MealTime.now(),
        mealTypes = UnicumAppState.dayData.meals.map { MealTime.baseType(it.mealType) }.toSet(),
        activityKcal = stats.activityKcal,
        weightKg = UserSettings.weightKg
    )
}

/** Consigli su attività, sonno e alimentazione, calcolati dai dati del giorno mostrato. */
@Composable
fun ConsigliScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val stats = rememberDayStats()
    val selected = UnicumAppState.selectedDate
    val advice = AdviceEngine.generate(adviceInput(stats))
    val day = DateUtils.parse(selected)

    UnicumScreen(selectedItem = Route.Home, onNavigate = onNavigate) {
        PageHeader(
            title = "Consigli",
            subtitle = DateUtils.relativeLabel(day) + " · " + DateUtils.fullLabel(day),
            onBack = onBack
        )
        Spacer(Modifier.height(14.dp))

        UnicumAppState.recoveryAlert()?.let {
            RecoveryAlertBox(it)
            Gap()
        }

        AdviceCategory.entries.forEach { category ->
            val items = advice.filter { it.category == category }
            if (items.isNotEmpty()) {
                UnicumCard {
                    CardHeader(
                        when (category) {
                            AdviceCategory.GIORNATA -> Icons.Rounded.WbSunny
                            AdviceCategory.ATTIVITA -> Icons.AutoMirrored.Rounded.DirectionsRun
                            AdviceCategory.SONNO -> Icons.Rounded.Bedtime
                            AdviceCategory.ALIMENTAZIONE -> Icons.Rounded.Restaurant
                        },
                        category.label
                    )
                    items.forEachIndexed { i, a ->
                        Spacer(Modifier.height(if (i == 0) 8.dp else 0.dp))
                        if (i > 0) StatDivider()
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(a.title, color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text(a.text, color = UnicumDark.copy(alpha = .8f), style = UnicumType.Detail, lineHeight = 18.sp)
                        }
                    }
                }
                Gap()
            }
        }
    }
}
