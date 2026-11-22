package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.lfmt

import io.github.peol13.unicum.i18n.tk

import io.github.peol13.unicum.i18n.tr
import io.github.peol13.unicum.i18n.trp

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
import io.github.peol13.unicum.data.Exercise
import io.github.peol13.unicum.data.Sport
import io.github.peol13.unicum.data.StrengthStats
import io.github.peol13.unicum.data.TrainingPlan
import io.github.peol13.unicum.data.runKindLabel
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

/**
 * Gli allenamenti seguiti dalla sezione: [runs] = corse (con le correzioni a mano), [strength] = pesi e corpo libero,
 * [all] = tutti, dal più recente. [feedback] = sensazioni di ognuno; [exercises] = esercizi delle sedute di forza
 * (quelli corretti a mano, altrimenti quelli del programma).
 */
internal class RunList(
    val runs: List<WorkoutDetail>,
    val feedback: Map<String, WorkoutFeedback>,
    val strength: List<WorkoutDetail> = emptyList(),
    val exercises: Map<String, List<Exercise>> = emptyMap()
) {
    val all: List<WorkoutDetail> = (runs + strength).sortedByDescending { it.startMillis }

    /** Allenamento registrato per giorno e famiglia di sport ([NotionPlan.doneKey]), per segnare le sedute fatte. */
    val doneByKey: Map<String, WorkoutDetail> =
        all.groupBy { NotionPlan.doneKey(it.date, Sport.ofWorkout(it.type)!!.family) }.mapValues { it.value.first() }
}

/**
 * Allenamenti salvati (corse e forza), senza doppioni; le corse con le correzioni a mano (durata e distanza senza
 * riscaldamento o cammino). Tipo, RPE e nota arrivano dal Registro di Notion quando ci sono; ripetute ed esercizi
 * dal programma (vedi [RunFeedback]).
 */
@Composable
internal fun rememberRunList(): RunList? {
    val context = LocalContext.current
    val data by produceState<RunList?>(null, UnicumAppState.health, NotionPlan.log, NotionPlan.runs, LocalPlan.data) {
        // Doppioni, registro di Notion e ripetute dal programma: conti da non fare sul thread dell'interfaccia
        val (followed, effective) = withContext(Dispatchers.Default) {
            val followed = WorkoutAnalysis.dedupe(UnicumRepository.loadWorkoutSummaries(context)).filter { Sport.ofWorkout(it.type) != null }
            followed to RunFeedback.load(context, followed)
        }
        val feedback = effective.mapValues { it.value.feedback }
        val (runs, strength) = followed.partition { Sport.ofWorkout(it.type) == Sport.RUN }
        // Le corse nuove vanno alla scarpa predefinita (se non ne è stata scelta un'altra)
        Shoes.autoAssign(runs)
        value = RunList(
            runs.map { it.corrected(feedback[it.externalId]) }, feedback, strength,
            strength.associate { w -> w.externalId to (feedback[w.externalId]?.exercises ?: effective[w.externalId]?.planned?.exercises.orEmpty()) }
        )
    }
    return data
}

/** Scheda aperta (0 = Fatti, 1 = Programma): resta la stessa tornando da un allenamento o dall'elenco. */
private val tabState = mutableStateOf(0)

/**
 * Sezione Allenamenti (corsa, pesi e corpo libero, vedi [Sport]) in due schede.
 * - **Fatti**: ultimo allenamento; per la corsa statistiche per settimana / mese / da sempre con i confronti,
 *   km per settimana divisi per tipo e scarpe ([ShoesScreen]); per la forza sedute, tempo e costanza
 *   ([StrengthStatsCard]); elenco completo ([WorkoutListScreen]).
 * - **Programma**: tutte le sedute del programma (Notion, PDF o scritte a mano) e le stime di 5 e 7 km.
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
        ScreenTitle(tr("Allenamenti"), if (tab == 0) tr("I tuoi allenamenti") else tr("Cosa ti aspetta"))
        Spacer(Modifier.height(12.dp))
        // Recupero dopo corse e allenamenti avviati a mano, visibile in entrambe le schede
        recovery?.let {
            RecoveryCard(it)
            Spacer(Modifier.height(14.dp))
        }
        UnicumSegmented(listOf(tr("Fatti"), tr("Programma")), tab, { tab = it }, height = 38.dp)
        Spacer(Modifier.height(18.dp))

        if (tab == 1) {
            SectionTitle(tr("Sedute in programma"))
            PlannedRunsSection(data?.doneByKey.orEmpty(), onNavigate)
            if (data == null || runs.isEmpty()) return@UnicumScreen
            Spacer(Modifier.height(24.dp))
            SectionTitle(tr("Stime"))
            EstimateCard(progress)
            Spacer(Modifier.height(24.dp))
            SectionTitle(tr("Obiettivi"))
            GoalsSection(progress)
            return@UnicumScreen
        }

        if (data == null) return@UnicumScreen
        val all = data.all
        if (all.isEmpty()) {
            HealthEmptyState(
                tr("Nessun allenamento"),
                tr("Gli allenamenti registrati dall'orologio (Samsung Health, Garmin, Health Sync…) arrivano da Health Connect: sincronizza dal Profilo.")
            )
            return@UnicumScreen
        }

        // 1. L'ultimo allenamento, da aprire subito
        SectionTitle(tr("Ultimo allenamento"))
        val last = all.first()
        WorkoutRow(last, data.feedback[last.externalId], data.exercises[last.externalId], onClick = { onNavigate(Route.Workout(last.externalId)) })

        // 2. Corsa: i numeri (settimana, mese, da sempre), confronti e volume per tipo, scarpe
        if (runs.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionTitle(tr("Corsa"))
            RunStatsCard(progress, onOpenRun = { onNavigate(Route.Workout(it)) })
            Spacer(Modifier.height(14.dp))
            CompareCard(progress)
            Spacer(Modifier.height(14.dp))
            KindVolumeCard(progress)
            Spacer(Modifier.height(24.dp))
            SectionTitle(tr("Scarpe"))
            ShoesSummaryCard(data, onNavigate)
        }

        // 3. Pesi e corpo libero: sedute, tempo e costanza rispetto al programma
        if (data.strength.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionTitle(tr("Forza"))
            val adherence = remember(data, TrainingPlan.runs) {
                StrengthStats.adherence(TrainingPlan.runs, data.doneByKey.keys, DateUtils.today())
            }
            StrengthStatsCard(data.strength, adherence)
        }

        // 4. L'elenco completo, in una pagina a parte
        Spacer(Modifier.height(24.dp))
        LinkCard(
            Icons.AutoMirrored.Rounded.List,
            tr("Tutti gli allenamenti"),
            tr("%d allenamenti · dal %s", all.size, DateUtils.shortLabel(LocalDate.parse(all.last().date)).substringAfter(' ')),
            onClick = { onNavigate(Route.WorkoutList) }
        )
    }
}

/** Filtro dell'elenco completo (null = tutti): resta lo stesso tornando da un allenamento. */
private val listFilter = mutableStateOf<Sport.Family?>(null)

/** Elenco completo degli allenamenti, diviso per mese (filtrabile per sport); ogni riga apre la sua pagina. */
@Composable
fun WorkoutListScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val data = rememberRunList()
    var filter by listFilter
    val both = data != null && data.runs.isNotEmpty() && data.strength.isNotEmpty()
    val shown = when {
        data == null -> emptyList()
        !both || filter == null -> data.all
        filter == Sport.Family.RUN -> data.runs
        else -> data.strength
    }
    val months = remember(shown) { shown.groupBy { YearMonth.from(LocalDate.parse(it.date)) }.toList() }
    // Elenco lazy: si disegnano solo le righe a schermo, anche con centinaia di allenamenti
    UnicumLazyScreen(selectedItem = Route.Allenamenti, onNavigate = onNavigate) {
        item(key = "header") {
            Column {
                PageHeader(tr("Tutti gli allenamenti"), if (data == null) "" else trp(shown.size, "%d allenamento", "%d allenamenti"), onBack)
                if (both) {
                    Spacer(Modifier.height(14.dp))
                    val options = listOf(null, Sport.Family.RUN, Sport.Family.STRENGTH)
                    UnicumSegmented(
                        listOf(tr("Tutti"), tr("Corsa"), tr("Forza")), options.indexOf(filter), { filter = options[it] }, height = 36.dp
                    )
                }
            }
        }
        if (data == null) return@UnicumLazyScreen
        months.forEach { (month, list) ->
            item(key = "month-$month") {
                Column {
                    Spacer(Modifier.height(20.dp))
                    val km = list.sumOf { it.distanceM ?: 0.0 }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            month.month.getDisplayName(TextStyle.FULL_STANDALONE, I18n.locale).replaceFirstChar { it.uppercase() } +
                                if (month.year != DateUtils.today().year) " ${month.year}" else "",
                            color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                        )
                        Text(
                            listOfNotNull("${list.size}", km.takeIf { it > 0 }?.let { "${WorkoutAnalysis.formatKm(it)} km" }).joinToString(" · "),
                            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            items(list, key = { it.externalId }) { w ->
                Column {
                    WorkoutRow(w, data.feedback[w.externalId], data.exercises[w.externalId], onClick = { onNavigate(Route.Workout(w.externalId)) })
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
private fun WorkoutRow(w: WorkoutDetail, feedback: WorkoutFeedback?, exercises: List<Exercise>?, onClick: () -> Unit) {
    val date = LocalDate.parse(w.date)
    val time = java.time.Instant.ofEpochMilli(w.startMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    val isRun = Sport.ofWorkout(w.type) == Sport.RUN
    UnicumCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(workoutIcon(w.type), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(tr(w.type), feedback?.kind?.let(::runKindLabel)).joinToString(" · "),
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
        if (!isRun) {
            // Forza: dall'orologio durata, calorie e battito; gli esercizi dal programma o corretti a mano
            Row(Modifier.fillMaxWidth()) {
                SmallStat(WorkoutAnalysis.formatDuration(w.movingSeconds), tr("durata"), Modifier.weight(1f))
                SmallStat(w.kcal?.roundToInt()?.toString() ?: "–", "kcal", Modifier.weight(1f))
                SmallStat(w.avgHr?.toString() ?: "–", "bpm", Modifier.weight(1f))
                SmallStat(exercises?.size?.takeIf { it > 0 }?.toString() ?: "–", tr("esercizi"), Modifier.weight(1f))
            }
            val extra = listOfNotNull(
                exercises?.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name },
                feedback?.rpe?.let { "RPE $it/10" }
            )
            if (extra.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(extra.joinToString(" · "), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, maxLines = 2)
            }
            return@UnicumCard
        }
        Row(Modifier.fillMaxWidth()) {
            SmallStat(if (w.distanceM != null) WorkoutAnalysis.formatKm(w.distanceM) else "–", "km", Modifier.weight(1f))
            SmallStat(WorkoutAnalysis.formatDuration(w.movingSeconds), tr("durata"), Modifier.weight(1f))
            SmallStat(WorkoutAnalysis.formatPace(WorkoutAnalysis.averagePace(w)), tr("ritmo"), Modifier.weight(1f))
            SmallStat(w.avgHr?.toString() ?: "–", "bpm", Modifier.weight(1f))
        }
        val pace = WorkoutAnalysis.averagePace(w)
        val extra = listOfNotNull(
            w.maxHr?.let { tr("FC max %d", it) },
            if (w.avgHr != null && pace != null) tr("%s battiti/km", lfmt("%,d", (w.avgHr * pace / 60.0).roundToInt())) else null,
            WorkoutAnalysis.averageCadence(w)?.let { tr("cadenza %d spm", it) },
            w.vo2max?.let { "VO₂ max ${lfmt("%.1f", it)}" },
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

internal fun workoutIcon(type: String): ImageVector = Sport.ofWorkout(type)?.let(::sportIcon) ?: when (type) {
    tk("Ciclismo") -> Icons.AutoMirrored.Rounded.DirectionsBike
    tk("Nuoto") -> Icons.Rounded.Pool
    tk("Escursionismo") -> Icons.Rounded.Hiking
    else -> Icons.Rounded.FitnessCenter
}
