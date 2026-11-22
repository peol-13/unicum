package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.ui.Route

import io.github.peol13.unicum.ui.components.AddFabMenu
import java.time.ZoneOffset
import java.time.LocalDate
import java.time.Instant
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.data.DateUtils
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.AdviceCategory
import io.github.peol13.unicum.data.AdviceEngine
import io.github.peol13.unicum.data.DashboardMode
import io.github.peol13.unicum.data.MetricType
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.components.AddEntryDialogs
import io.github.peol13.unicum.ui.components.LocalCardLongClick
import io.github.peol13.unicum.data.HomeCard
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import io.github.peol13.unicum.ui.components.RoundIconButton
import io.github.peol13.unicum.ui.components.rememberAddEntryState
import io.github.peol13.unicum.ui.components.TealBanner
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.sections.*
import io.github.peol13.unicum.ui.theme.UnicumType

@Composable
fun DashboardScreen(onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val stats = rememberDayStats()
    val weekMinutes = UnicumAppState.week.map { it.activityMinutes }

    val adding = rememberAddEntryState()
    AddEntryDialogs(adding)
    // Dal widget o da un promemoria: la registrazione del pasto si apre da sola
    LaunchedEffect(UnicumAppState.pendingAddMeal) {
        UnicumAppState.pendingAddMeal?.let { type ->
            adding.addMeal(type.ifEmpty { null })
            UnicumAppState.consumeAddMeal()
        }
    }

    UnicumScreen(
        selectedItem = Route.Home,
        onNavigate = onNavigate,
        onRefresh = { UnicumAppState.refresh(context) },
        isRefreshing = UnicumAppState.refreshing || UnicumAppState.syncing,
        onSwipeDay = { UnicumAppState.shiftDay(context, it) },
        floatingActionButton = {
            AddFabMenu(onAddMeal = { adding.addMeal() }, onAddActivity = adding::addActivity)
        }
    ) {
        DashboardHeader()
        Spacer(Modifier.height(14.dp))
        UpdateBanner()
        if (UnicumAppState.isToday) WeekStoryTeaser(onOpen = { onNavigate(Route.WeekStory(lastCompletedWeek())) })

        // Le card nell'ordine scelto (o quello della modalità); tenendone premuta una si apre la modifica
        CompositionLocalProvider(LocalCardLongClick provides { onNavigate(Route.HomeEditor) }) {
            UserSettings.homeCards.forEachIndexed { i, card ->
                if (i > 0) Gap()
                HomeCardContent(card, stats, weekMinutes, onNavigate)
            }
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { onNavigate(Route.HomeEditor) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.Edit, null, tint = UnicumPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("Personalizza la Home"), color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Una card della Home. Il banner cambia in base alla modalità scelta (nutrizione, attività o recupero). */
@Composable
private fun HomeCardContent(card: HomeCard, stats: DayStats, weekMinutes: List<Int>, onNavigate: (Route) -> Unit) {
    val data = UnicumAppState.dayData
    when (card) {
        HomeCard.BANNER -> when (UserSettings.dashboardMode) {
            DashboardMode.NUTRIZIONE -> NutritionBanner(stats, onClick = { onNavigate(Route.Consigli) })
            DashboardMode.ATTIVITA -> ActivityBanner(stats, onClick = { onNavigate(Route.Consigli) })
            DashboardMode.RECUPERO -> RecoveryBanner(onClick = { onNavigate(Route.Consigli) })
        }
        HomeCard.CALORIE -> CalorieCard(stats, onClick = { onNavigate(Route.Detail(DetailTopic.CALORIE)) })
        HomeCard.CALORIE_COMPATTA -> CalorieCompactCard(stats, onClick = { onNavigate(Route.Detail(DetailTopic.CALORIE)) })
        HomeCard.ATTIVITA -> ActivityBigCard(stats, weekMinutes, onClick = { onNavigate(Route.Detail(DetailTopic.ATTIVITA)) })
        HomeCard.SONNO -> SleepBigCard(UnicumAppState.lastNight) { onNavigate(Route.Detail(DetailTopic.SONNO)) }
        HomeCard.ATTIVITA_SONNO -> CardPair {
            ActivitySmallCard(stats.activityMinutes, weekMinutes, Modifier.weight(1f).fillMaxHeight()) {
                onNavigate(Route.Detail(DetailTopic.ATTIVITA))
            }
            SleepSmallCard(UnicumAppState.lastNight, UnicumAppState.sleepWeekMinutes, Modifier.weight(1f).fillMaxHeight()) {
                onNavigate(Route.Detail(DetailTopic.SONNO))
            }
        }
        HomeCard.RECUPERO_SONNO -> CardPair {
            RecoveryCard(Modifier.weight(1f).fillMaxHeight()) { onNavigate(Route.Detail(DetailTopic.RECUPERO)) }
            SleepRingSmallCard(UnicumAppState.lastNight, Modifier.weight(1f).fillMaxHeight()) { onNavigate(Route.Detail(DetailTopic.SONNO)) }
        }
        HomeCard.RECUPERO_ATTIVITA -> CardPair {
            RecoveryCard(Modifier.weight(1f).fillMaxHeight()) { onNavigate(Route.Detail(DetailTopic.RECUPERO)) }
            ActivitySmallCard(stats.activityMinutes, weekMinutes, Modifier.weight(1f).fillMaxHeight()) {
                onNavigate(Route.Detail(DetailTopic.ATTIVITA))
            }
        }
        HomeCard.PASTI -> MealsCard(data.meals, onDetails = { onNavigate(Route.Detail(DetailTopic.PASTI)) })
        HomeCard.ALLENAMENTI -> WorkoutsCard(data.activities, onAll = { onNavigate(Route.Detail(DetailTopic.ATTIVITA)) })
        HomeCard.PESO -> BodyCompositionCard(onDetails = { onNavigate(Route.Detail(DetailTopic.PESO)) })
    }
}

@Composable
private fun DashboardHeader() {
    val context = LocalContext.current
    val day = DateUtils.parse(UnicumAppState.selectedDate)
    var showCalendar by remember { mutableStateOf(false) }

    if (showCalendar) {
        CalendarDialog(
            selected = UnicumAppState.selectedDate,
            onPick = {
                UnicumAppState.selectDay(context, it)
                showCalendar = false
            },
            onDismiss = { showCalendar = false }
        )
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                DateUtils.relativeLabel(day),
                color = UnicumDark, style = UnicumType.Screen, fontWeight = FontWeight.SemiBold
            )
            Text(DateUtils.fullLabel(day), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Body)
            if (!UnicumAppState.isToday) {
                Spacer(Modifier.height(8.dp))
                UnicumChip(tr("Torna a oggi"), selected = false, onClick = { UnicumAppState.backToToday(context) })
            }
        }
        RoundIconButton(Icons.Rounded.CalendarMonth, tr("Calendario")) { showCalendar = true }
    }
}

/** Calendario per scegliere un giorno passato: toccando una data si apre la Home di quel giorno. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDialog(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val utc = ZoneOffset.UTC
    val initial = LocalDate.parse(selected).atStartOfDay(utc).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(utc).toLocalDate().isAfter(LocalDate.now())

            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
        }
    )
    // Un solo tocco su un giorno diverso da quello mostrato apre quella giornata
    LaunchedEffect(state.selectedDateMillis) {
        val ms = state.selectedDateMillis ?: return@LaunchedEffect
        if (ms != initial) onPick(Instant.ofEpochMilli(ms).atZone(utc).toLocalDate().toString())
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Chiudi"), color = UnicumPrimary) } }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@Composable
private fun NutritionBanner(s: DayStats, onClick: () -> Unit) {
    // Oggi, con almeno un pasto, il banner mostra il primo consiglio su come chiudere la giornata
    val today = if (UnicumAppState.isToday && s.eaten > 0)
        AdviceEngine.generate(adviceInput(s)).firstOrNull { it.category == AdviceCategory.GIORNATA } else null
    val (title, text) = when {
        today != null -> today.title to today.text
        s.eaten == 0 && !UnicumAppState.isToday -> tr("Nessun pasto registrato") to
            tr("Puoi aggiungere i pasti di questo giorno con il pulsante +.")
        s.eaten == 0 -> tr("Inizia a registrare i pasti") to
            tr("Oggi hai %s kcal per restare sul tuo obiettivo.", fmt(s.remaining.coerceAtLeast(0)))
        s.remaining < 0 -> tr("Hai superato l'obiettivo") to
            tr("Sei oltre di %s kcal rispetto al tuo obiettivo calorico.", fmt(-s.remaining))
        s.remaining > 0 -> tr("Sei in linea con il tuo obiettivo!") to
            tr("Hai ancora %s kcal disponibili per concludere la giornata.", fmt(s.remaining))
        else -> tr("Obiettivo del giorno raggiunto!") to
            tr("Hai assunto esattamente le calorie previste per oggi.")
    }
    TealBanner(Icons.Rounded.Restaurant, title, text, onClick = onClick)
}

@Composable
private fun ActivityBanner(s: DayStats, onClick: () -> Unit) {
    if (s.activityMinutes > 0) {
        TealBanner(
            Icons.AutoMirrored.Rounded.DirectionsRun,
            tr("Buon movimento oggi!"),
            tr("%d min di attività e %s kcal bruciate.", s.activityMinutes, fmt(s.activityKcal)),
            onClick = onClick
        )
    } else {
        TealBanner(
            Icons.AutoMirrored.Rounded.DirectionsRun,
            tr("Nessun allenamento oggi"),
            tr("Registra un'attività con il pulsante + oppure collega i tuoi dispositivi."),
            onClick = onClick
        )
    }
}

@Composable
private fun RecoveryBanner(onClick: () -> Unit) {
    val night = UnicumAppState.lastNight
    val hrv = UnicumAppState.latestMetric(MetricType.HRV)
    val baseline = UnicumAppState.metricBaseline(MetricType.HRV)
    val alert = UnicumAppState.recoveryAlert()
    val (title, text) = when {
        alert != null -> alert.title to alert.text
        night == null && hrv == null -> tr("Sonno e recupero") to
            tr("Collega Health Connect dal Profilo per vedere sonno, HRV e temperatura notturna.")
        night != null && night.durationMinutes >= 7 * 60 -> tr("Hai dormito bene!") to
            (if (hrv != null && baseline != null && hrv >= baseline) tr("%s di sonno e HRV nella tua norma.", formatDuration(night.durationMinutes))
                                else tr("%s di sonno.", formatDuration(night.durationMinutes)))
        night != null -> tr("Notte un po' corta") to
            tr("%s di sonno: oggi vai con calma se puoi.", formatDuration(night.durationMinutes))
        else -> tr("Dati di recupero") to "HRV ${hrv?.toInt()} ms."
    }
    TealBanner(Icons.Rounded.Bedtime, title, text, onClick = onClick)
}
