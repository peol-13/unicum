package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.ui.Route

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.LocalPlan
import io.github.peol13.unicum.data.RunFeedback
import io.github.peol13.unicum.data.RunProgress
import io.github.peol13.unicum.data.Shoes
import io.github.peol13.unicum.data.SourceInfo
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.data.WorkoutDetail
import io.github.peol13.unicum.data.WorkoutFeedback
import io.github.peol13.unicum.ui.components.HealthEmptyState
import io.github.peol13.unicum.ui.components.IconBadge
import io.github.peol13.unicum.ui.components.LinkCard
import io.github.peol13.unicum.ui.components.ScreenTitle
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumLazyScreen
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.components.UnicumSegmented
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** La sezione mostra solo le corse (gli altri sport restano in Attività e nel Diario). */
private const val RUN = "Corsa"

internal class RunList(val runs: List<WorkoutDetail>, val feedback: Map<String, WorkoutFeedback>)

/**
 * Corse salvate, senza doppioni e con le correzioni a mano (durata e distanza senza riscaldamento o cammino).
 * Tipo, RPE e nota arrivano dal Registro corse di Notion quando ci sono; le ripetute dal programma (vedi [RunFeedback]).
 */
@Composable
internal fun rememberRunList(): RunList? {
    val context = LocalContext.current
    val data by produceState<RunList?>(null, UnicumAppState.health, NotionPlan.log, NotionPlan.runs, LocalPlan.data) {
        // Doppioni, registro di Notion e ripetute dal programma: conti da non fare sul thread dell'interfaccia
        val (runs, feedback) = withContext(Dispatchers.Default) {
            val runs = WorkoutAnalysis.dedupe(UnicumRepository.loadWorkoutSummaries(context)).filter { it.type == RUN }
            runs to RunFeedback.load(context, runs).mapValues { it.value.feedback }
        }
        // Le corse nuove vanno alla scarpa predefinita (se non ne è stata scelta un'altra)
        Shoes.autoAssign(runs)
        value = RunList(runs.map { it.corrected(feedback[it.externalId]) }, feedback)
    }
    return data
}

/** Scheda aperta (0 = Corse fatte, 1 = Programma): resta la stessa tornando da una corsa o dall'elenco. */
private val tabState = mutableStateOf(0)

/**
 * Sezione Allenamenti (solo corsa) in due schede.
 * - **Corse fatte**: ultima corsa, statistiche per settimana / mese / da sempre con i confronti,
 *   corse a confronto, km per settimana divisi per tipo, scarpe ([ShoesScreen]), elenco completo ([WorkoutListScreen]).
 * - **Programma**: le sedute del programma (Notion, PDF o scritte a mano) e le stime di 5 e 7 km.
 */
@Composable
fun AllenamentiScreen(onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    var tab by tabState
    val data = rememberRunList()
    val runs = data?.runs.orEmpty()
    val progress = remember(runs, data?.feedback) { RunProgress.runs(runs, data?.feedback.orEmpty()) }
    val recovery = rememberRecovery()
    LaunchedEffect(Unit) { NotionPlan.sync(context, force = false) }

    UnicumScreen(
        selectedItem = Route.Allenamenti,
        onNavigate = onNavigate,
        onRefresh = { UnicumAppState.refresh(context) },
        isRefreshing = UnicumAppState.refreshing
    ) {
        ScreenTitle("Allenamenti", if (tab == 0) "Le tue corse" else "Cosa ti aspetta")
        Spacer(Modifier.height(12.dp))
        // Recupero dopo corse e allenamenti avviati a mano, visibile in entrambe le schede
        recovery?.let {
            RecoveryCard(it)
            Spacer(Modifier.height(14.dp))
        }
        UnicumSegmented(listOf("Corse fatte", "Programma"), tab, { tab = it }, height = 38.dp)
        Spacer(Modifier.height(18.dp))

        if (tab == 1) {
            // Una corsa per giorno (la più recente), per segnare come fatte le sedute del programma
            val runsByDate = remember(runs) { runs.groupBy { it.date }.mapValues { it.value.first() } }
            SectionTitle("Corse programmate")
            PlannedRunsSection(runsByDate, onNavigate)
            if (data == null || runs.isEmpty()) return@UnicumScreen
            Spacer(Modifier.height(24.dp))
            SectionTitle("Stime")
            EstimateCard(progress)
            Spacer(Modifier.height(24.dp))
            SectionTitle("Obiettivi")
            GoalsSection(progress)
            return@UnicumScreen
        }

        if (data == null) return@UnicumScreen
        if (runs.isEmpty()) {
            HealthEmptyState(
                "Nessuna corsa",
                "Le corse registrate dall'orologio (Samsung Health, Garmin, Health Sync…) arrivano da Health Connect: sincronizza dal Profilo."
            )
            return@UnicumScreen
        }

        // 1. L'ultima uscita, da aprire subito
        SectionTitle("Ultima corsa")
        val last = runs.first()
        WorkoutRow(last, data.feedback[last.externalId], onClick = { onNavigate(Route.Workout(last.externalId)) })

        // 2. I numeri: settimana, mese, da sempre
        Spacer(Modifier.height(24.dp))
        SectionTitle("Statistiche")
        RunStatsCard(progress, onOpenRun = { onNavigate(Route.Workout(it)) })

        // 3. Confronti tra corse e volume per tipo
        Spacer(Modifier.height(24.dp))
        SectionTitle("Confronti")
        CompareCard(progress)
        Spacer(Modifier.height(14.dp))
        KindVolumeCard(progress)

        // 4. Le scarpe: km verso il limite
        Spacer(Modifier.height(24.dp))
        SectionTitle("Scarpe")
        ShoesSummaryCard(data, onNavigate)

        // 5. L'elenco completo, in una pagina a parte
        Spacer(Modifier.height(24.dp))
        LinkCard(
            Icons.AutoMirrored.Rounded.List,
            "Tutte le corse",
            "${runs.size} corse · dal ${DateUtils.shortLabel(LocalDate.parse(runs.last().date)).substringAfter(' ')}",
            onClick = { onNavigate(Route.WorkoutList) }
        )
    }
}

/** Elenco completo delle corse, diviso per mese; ogni riga apre la pagina della corsa. */
@Composable
fun WorkoutListScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val data = rememberRunList()
    val shown = data?.runs.orEmpty()
    val months = remember(shown) { shown.groupBy { YearMonth.from(LocalDate.parse(it.date)) }.toList() }
    // Elenco lazy: si disegnano solo le righe a schermo, anche con centinaia di corse
    UnicumLazyScreen(selectedItem = Route.Allenamenti, onNavigate = onNavigate) {
        item(key = "header") {
            Column { PageHeader("Tutte le corse", if (data == null) "" else "${shown.size} corse", onBack) }
        }
        if (data == null) return@UnicumLazyScreen
        months.forEach { (month, list) ->
            item(key = "month-$month") {
                Column {
                    Spacer(Modifier.height(20.dp))
                    val km = list.sumOf { it.distanceM ?: 0.0 }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN).replaceFirstChar { it.uppercase() } +
                                if (month.year != DateUtils.today().year) " ${month.year}" else "",
                            color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${list.size} · ${WorkoutAnalysis.formatKm(km)} km",
                            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            items(list, key = { it.externalId }) { w ->
                Column {
                    WorkoutRow(w, data.feedback[w.externalId], onClick = { onNavigate(Route.Workout(w.externalId)) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

// ------------------------------------------------------------------

/** Una riga dell'elenco con le stesse colonne del registro corse: km, durata, ritmo, FC, VO₂ max, RPE. */
@Composable
private fun WorkoutRow(w: WorkoutDetail, feedback: WorkoutFeedback?, onClick: () -> Unit) {
    val date = LocalDate.parse(w.date)
    val time = java.time.Instant.ofEpochMilli(w.startMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    UnicumCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(workoutIcon(w.type), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(w.type, feedback?.kind).joinToString(" · "),
                    color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold, maxLines = 1
                )
                Text(
                    "${DateUtils.relativeLabel(date)} · ${"%02d:%02d".format(time.hour, time.minute)} · ${SourceInfo.label(w.source)}",
                    color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, maxLines = 1
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = UnicumDark.copy(alpha = .4f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            SmallStat(if (w.distanceM != null) WorkoutAnalysis.formatKm(w.distanceM) else "–", "km", Modifier.weight(1f))
            SmallStat(WorkoutAnalysis.formatDuration(w.movingSeconds), "durata", Modifier.weight(1f))
            SmallStat(WorkoutAnalysis.formatPace(WorkoutAnalysis.averagePace(w)), "ritmo", Modifier.weight(1f))
            SmallStat(w.avgHr?.toString() ?: "–", "bpm", Modifier.weight(1f))
        }
        val pace = WorkoutAnalysis.averagePace(w)
        val extra = listOfNotNull(
            w.maxHr?.let { "FC max $it" },
            if (w.avgHr != null && pace != null) "${"%,d".format((w.avgHr * pace / 60.0).roundToInt()).replace(',', '.')} battiti/km" else null,
            WorkoutAnalysis.averageCadence(w)?.let { "cadenza $it spm" },
            w.vo2max?.let { "VO₂ max ${"%.1f".format(it)}" },
            feedback?.rpe?.let { "RPE $it/10" }
        )
        if (extra.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(extra.joinToString(" · "), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note)
        }
    }
}

@Composable
internal fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, maxLines = 1)
    }
}

@Composable
private fun SmallStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = UnicumDark.copy(alpha = .5f), style = UnicumType.Micro, maxLines = 1)
    }
}

internal fun workoutIcon(type: String): ImageVector = when (type) {
    "Corsa" -> Icons.AutoMirrored.Rounded.DirectionsRun
    "Ciclismo" -> Icons.AutoMirrored.Rounded.DirectionsBike
    "Nuoto" -> Icons.Rounded.Pool
    "Escursionismo" -> Icons.Rounded.Hiking
    else -> Icons.Rounded.FitnessCenter
}
