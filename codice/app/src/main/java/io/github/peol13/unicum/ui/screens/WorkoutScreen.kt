package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.lfmt

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.ui.Route

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.IntervalPart
import io.github.peol13.unicum.data.StepKind
import io.github.peol13.unicum.data.IntervalPlan
import io.github.peol13.unicum.data.LoggedRun
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.LocalPlan
import io.github.peol13.unicum.data.TrainingPlan
import io.github.peol13.unicum.data.PlanStructure
import io.github.peol13.unicum.data.PlannedRun
import io.github.peol13.unicum.data.RunFeedback
import io.github.peol13.unicum.data.Sport
import io.github.peol13.unicum.data.runKindLabel
import io.github.peol13.unicum.ui.components.UnicumSegmented
import io.github.peol13.unicum.data.RoutePoint
import io.github.peol13.unicum.data.RouteStatus
import io.github.peol13.unicum.data.SourceInfo
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.data.TrainingLoad
import io.github.peol13.unicum.data.RecoveryEstimate
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.data.WorkoutDetail
import io.github.peol13.unicum.data.WorkoutFeedback
import io.github.peol13.unicum.data.WorkoutSample
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.EmptyState
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.components.rememberHaptics
import io.github.peol13.unicum.ui.components.unicumFieldColors
import io.github.peol13.unicum.ui.components.unicumFieldTextStyle
import io.github.peol13.unicum.ui.sections.HrZoneBars
import io.github.peol13.unicum.ui.sections.RouteMap
import io.github.peol13.unicum.ui.sections.SeriesChart
import io.github.peol13.unicum.ui.theme.MacroFatsColor
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSecondary
import io.github.peol13.unicum.ui.theme.UnicumTrack
import io.github.peol13.unicum.ui.theme.UnicumSurface
import io.github.peol13.unicum.ui.theme.UnicumType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Pagina di un allenamento: mappa del percorso (se la fonte la condivide), numeri principali, parziali
 * per km, grafici di ritmo, battito (con le zone), cadenza e altitudine, e le sensazioni dell'utente
 * (sforzo percepito, tipo di seduta, nota). Toccando un grafico gli altri e la mappa mostrano lo stesso istante.
 * Per pesi e corpo libero ([Sport.isStrength]) niente percorso, ritmo e parziali: durata, calorie e battito
 * dall'orologio e la card degli esercizi (dal programma, correggibili: Health Connect non ha carichi né ripetizioni).
 */
@Composable
fun WorkoutScreen(externalId: String, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    var detail by remember { mutableStateOf<WorkoutDetail?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(WorkoutFeedback()) }
    // Registro e programma di Notion collegati a questa corsa (tipo/RPE/nota e ripetute dal programma)
    var notion by remember { mutableStateOf<RunFeedback.Effective?>(null) }
    LaunchedEffect(externalId, NotionPlan.log, NotionPlan.runs, LocalPlan.data) {
        val summary = UnicumRepository.loadWorkoutSummary(context, externalId)
        if (summary != null && Sport.ofWorkout(summary.type) != null) notion = RunFeedback.load(context, listOf(summary))[externalId]
    }
    LaunchedEffect(externalId) {
        val raw = UnicumRepository.loadWorkoutDetail(context, externalId)
        detail = raw
        val f = UnicumRepository.loadWorkoutFeedback(context)[externalId] ?: WorkoutFeedback()
        // Correzioni salvate prima che l'app ricavasse la FC del tratto corso: si completano ora
        var fixed = if (raw != null && f.isCorrected && f.avgHr == null) withSegmentHr(raw, f) else f
        // Ripetute salvate prima che si registrasse la FC dei tratti veloci: si completa ora
        if (raw != null && fixed.intervals != null && fixed.intervals!!.workHr == null) {
            fixed = fixed.copy(intervals = WorkoutAnalysis.withWorkHr(raw, fixed.intervals!!))
        }
        if (fixed != f) UnicumRepository.saveWorkoutFeedback(context, externalId, fixed)
        feedback = fixed
        loaded = true
    }
    val recovery = rememberRecovery()
    // Istante scelto toccando un grafico (secondi dall'inizio), condiviso da grafici e mappa
    var selectedT by rememberSaveable { mutableStateOf<Int?>(null) }

    // Percorso da autorizzare: Health Connect chiede il consenso per questa sessione
    val routeLauncher = rememberLauncherForActivityResult(ExerciseRouteRequestContract()) { route ->
        val d = detail ?: return@rememberLauncherForActivityResult
        if (route != null) {
            val updated = d.copy(
                routeStatus = RouteStatus.DATA,
                route = route.route.map {
                    RoutePoint(((it.time.toEpochMilli() - d.startMillis) / 1000).toInt(), it.latitude, it.longitude, it.altitude?.inMeters?.toFloat())
                }
            )
            detail = updated
            scope.launch { UnicumRepository.saveWorkoutDetails(context, listOf(updated)) }
        }
    }

    var correcting by remember { mutableStateOf(false) }
    var editingIntervals by remember { mutableStateOf(false) }
    var editingExercises by remember { mutableStateOf(false) }
    fun saveFeedback(f: WorkoutFeedback) {
        feedback = f
        scope.launch { UnicumRepository.saveWorkoutFeedback(context, externalId, f) }
    }
    detail?.let { raw ->
        if (correcting) CorrectionDialog(raw, feedback, onDismiss = { correcting = false }) { f -> correcting = false; saveFeedback(f) }
        if (editingIntervals) IntervalsDialog(
            raw, feedback.intervals, preferLaps = RunFeedback.merge(feedback, notion?.logged, notion?.planned).kind !in PlanStructure.STRUCTURED_KINDS, onDismiss = { editingIntervals = false }
        ) { plan ->
            editingIntervals = false
            saveFeedback(feedback.copy(intervals = plan?.let { WorkoutAnalysis.withWorkHr(raw, it) }))
        }
        if (editingExercises) {
            val planned = notion?.planned?.exercises.orEmpty()
            ExercisesDialog(feedback.exercises ?: planned, planned, feedback.exercises != null, onDismiss = { editingExercises = false }) { list ->
                editingExercises = false
                saveFeedback(feedback.copy(exercises = list))
            }
        }
    }

    UnicumScreen(selectedItem = Route.Allenamenti, onNavigate = onNavigate) {
        val raw = detail
        if (raw == null) {
            PageHeader(tr("Allenamento"), "", onBack)
            if (loaded) {
                Spacer(Modifier.height(16.dp))
                EmptyState(tr("Allenamento non trovato"), tr("Forse è stato cancellato dalla fonte: sincronizza di nuovo."))
            }
            return@UnicumScreen
        }
        // Durata e distanza corrette a mano (es. senza il riscaldamento), se l'utente le ha sistemate
        val d = raw.corrected(feedback)
        // Tipo, RPE e nota: quelli del Registro di Notion vincono su quelli scritti qui
        val shown = RunFeedback.merge(feedback, notion?.logged, notion?.planned)
        val start = Instant.ofEpochMilli(d.startMillis).atZone(ZoneId.systemDefault())
        val sport = Sport.ofWorkout(d.type)
        val isRun = sport == Sport.RUN
        val strength = sport?.isStrength == true
        PageHeader(
            listOfNotNull(tr(d.type), shown.kind?.let(::runKindLabel)).joinToString(" · "),
            "${DateUtils.relativeLabel(LocalDate.parse(d.date))} · ${"%02d:%02d".format(start.hour, start.minute)} · ${SourceInfo.label(d.source)}",
            onBack
        )
        Spacer(Modifier.height(16.dp))

        // ---------- mappa (in palestra non c'è un percorso: niente avviso se manca)
        when {
            d.route.size >= 2 -> UnicumCard(padding = 8.dp) { RouteMap(d.route, selectedT) }
            d.routeStatus == RouteStatus.CONSENT -> EmptyState(
                tr("Percorso da autorizzare"), tr("Health Connect chiede il permesso per mostrare il percorso GPS di questo allenamento."),
                action = tr("Mostra percorso"), onAction = { routeLauncher.launch(d.externalId) }
            )
            strength -> Unit
            else -> EmptyState(
                tr("Percorso non disponibile"),
                tr("%s non ha condiviso il percorso GPS con Health Connect. Gli altri dati ci sono.", SourceInfo.label(d.source))
            )
        }
        if (!strength || d.route.size >= 2 || d.routeStatus == RouteStatus.CONSENT) Spacer(Modifier.height(14.dp))

        if (strength) {
            StrengthSummaryCard(d)
            Spacer(Modifier.height(14.dp))
            val planned = notion?.planned
            val exercises = feedback.exercises ?: planned?.exercises.orEmpty()
            // Scritti nel Registro di Notion: si leggono da lì (come tipo, RPE e nota), senza Modifica
            val fromNotion = notion?.logged?.exercises?.isNotEmpty() == true
            ChartCard(
                sportIcon(sport!!), tr("Esercizi"),
                action = if (fromNotion) null else tr("Modifica"), onAction = { editingExercises = true }
            ) {
                when {
                    exercises.isNotEmpty() -> {
                        Text(
                            if (fromNotion) tr("Dal Registro di Notion")
                            else if (feedback.exercises != null) tr("Come li hai fatti (modificati a mano)")
                            else tr("Dal programma («%s»): tocca Modifica se hai fatto qualcosa di diverso.", planned?.name ?: ""),
                            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        ExerciseList(exercises, planned = if (feedback.exercises != null) planned?.exercises.orEmpty() else emptyList())
                    }
                    planned?.structure != null -> {
                        Text(planned.structure, color = UnicumDark, style = UnicumType.Detail, lineHeight = 19.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr("Esercizi non riconosciuti nel testo del programma: tocca Modifica per scriverli."),
                            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp
                        )
                    }
                    else -> Text(
                        tr("Nessuna seduta di forza nel programma per questo giorno: tocca Modifica per scrivere gli esercizi fatti."),
                        color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Con una correzione a mano i parziali (calcolati su tutta la registrazione) non sarebbero affidabili
        val splits = remember(d, feedback.isCorrected) { if (feedback.isCorrected || strength) emptyList() else WorkoutAnalysis.splits(d) }
        if (!strength) {
            SummaryCard(d, splits)
            Spacer(Modifier.height(8.dp))
        }
        // Il tratto corso si cerca una volta sola, non a ogni ridisegno (es. mentre si tocca un grafico)
        val runSegment = remember(raw, d.movingSeconds, d.distanceM, feedback.isCorrected) {
            if (feedback.isCorrected) WorkoutAnalysis.findSegment(raw, d.movingSeconds, d.distanceM ?: 0.0) else null
        }
        if (!strength) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (feedback.isCorrected)
                    tr("Corretta a mano (registrata: %s, %s km).", WorkoutAnalysis.formatDuration(raw.movingSeconds), WorkoutAnalysis.formatKm(raw.distanceM)) +
                        (runSegment?.takeIf { feedback.avgHr != null }?.let {
                            " " + tr("FC dal tratto corso, da %s a %s.", WorkoutAnalysis.formatDuration(it.first), WorkoutAnalysis.formatDuration(it.second))
                        } ?: "") + " " + tr("I grafici mostrano tutta la registrazione.")
                else tr("L'orologio ha registrato anche riscaldamento o cammino?"),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            UnicumChip(tr("Correggi"), false, { correcting = true }, compact = true)
        }
        if (!strength) Spacer(Modifier.height(14.dp))

        // Giri o ripetute scritti dall'utente; se mancano, i giri dell'orologio (se la fonte li scrive in Health
        // Connect) o la struttura del programma di Notion: sulla registrazione intera, non su quella corretta
        val watchLaps = remember(raw) { IntervalPlan.fromWatchLaps(raw.laps) }
        val intervalPlan = feedback.intervals ?: watchLaps ?: notion?.autoPlan
        val segments = remember(raw, intervalPlan) { intervalPlan?.let { WorkoutAnalysis.intervals(raw, it) }.orEmpty() }
        if (splits.isNotEmpty() || segments.isNotEmpty() || isRun) {
            SplitsCard(
                splits, segments, canEditIntervals = isRun, suggestIntervals = shown.kind in PlanStructure.STRUCTURED_KINDS,
                fromPlan = intervalPlan?.fromPlan.takeIf { feedback.intervals == null },
                fromWatch = feedback.intervals == null && intervalPlan?.fromWatch == true,
                manual = feedback.intervals != null
            ) { editingIntervals = true }
            Spacer(Modifier.height(14.dp))
        }

        val pace = remember(d) { WorkoutAnalysis.paceSeries(d) }
        if (pace.size >= 2) {
            ChartCard(Icons.Rounded.Speed, tr("Ritmo")) {
                SeriesChart(
                    xs = pace.map { it.t.toFloat() }, ys = pace.map { it.v },
                    color = UnicumPrimary, invert = true,
                    xLabel = { WorkoutAnalysis.formatDuration(it.roundToInt()) },
                    yLabel = { WorkoutAnalysis.formatPace(it.roundToInt()) },
                    summary = tr("Medio %s/km · in alto più veloce", WorkoutAnalysis.formatPace(WorkoutAnalysis.averagePace(d))),
                    selectedX = selectedT?.toFloat(), onSelect = { selectedT = it?.roundToInt() }
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        if (d.hr.size >= 2) {
            val maxHr = maxOf(WorkoutAnalysis.estimatedMaxHr(UserSettings.age), d.maxHr ?: 0)
            ChartCard(Icons.Rounded.Favorite, tr("Frequenza cardiaca")) {
                SeriesChart(
                    xs = d.hr.map { it.t.toFloat() }, ys = d.hr.map { it.v },
                    color = MacroFatsColor,
                    xLabel = { WorkoutAnalysis.formatDuration(it.roundToInt()) },
                    yLabel = { "${it.roundToInt()} bpm" },
                    summary = tr("Media %s · max %s · min %s bpm", d.avgHr ?: "–", d.maxHr ?: "–", d.minHr ?: "–"),
                    selectedX = selectedT?.toFloat(), onSelect = { selectedT = it?.roundToInt() }
                )
                Spacer(Modifier.height(16.dp))
                Text(tr("ZONE"), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Note, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                HrZoneBars(remember(d, maxHr) { WorkoutAnalysis.hrZones(d.hr, maxHr) })
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Zone in %% della FC massima (%d bpm: stimata dall'età, o la più alta registrata).", maxHr),
                    color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        if (d.cadence.size >= 2) {
            SeriesCard(Icons.Rounded.Straighten, tr("Cadenza"), remember(d) { WorkoutAnalysis.smooth(d.cadence.filter { it.v > 0 }, 15) }, UnicumSecondary, "spm",
                tr("Media %s passi/min", WorkoutAnalysis.averageCadence(d) ?: "–"), selectedT) { selectedT = it }
            Spacer(Modifier.height(14.dp))
        }

        val altitude = remember(d) { WorkoutAnalysis.altitudeSeries(d) }
        if (altitude.size >= 2) {
            SeriesCard(Icons.Rounded.Terrain, tr("Altitudine"), altitude, UnicumSecondary, "m",
                tr("Dislivello positivo %s m", d.elevationGainM?.roundToInt() ?: "–"), selectedT) { selectedT = it }
            Spacer(Modifier.height(14.dp))
        }

        // Fatica misurata dal battito (sulla registrazione intera) e recupero stimato con il fattore personale
        // Pesi e corpo libero: con l'RPE il carico (e quindi il recupero) non resta quello del solo battito
        val strengthRpe = (notion?.logged?.rpe ?: feedback.rpe).takeIf { strength }
        val effort = remember(raw, recovery?.maxHr, strengthRpe) {
            recovery?.let { TrainingLoad.withSessionRpe(TrainingLoad.effort(raw, it.maxHr), strengthRpe, raw.movingSeconds / 60.0) }
        }
        val counts = remember(raw) { TrainingLoad.countsForRecovery(raw) }
        val recoveryHours = effort?.takeIf { counts }?.let { RecoveryEstimate.modelHours(it) * (recovery?.result?.personalFactor ?: 1.0) }
        FeedbackCard(feedback, notion?.logged, notion?.planned, isRun = isRun, effort = effort,
            recoveryHours = recoveryHours, countsForRecovery = counts, onChange = ::saveFeedback)
        if (isRun) {
            Spacer(Modifier.height(14.dp))
            ShoePickerCard(d, onManage = { onNavigate(Route.Shoes) }, onOpenShoe = { onNavigate(Route.Shoe(it)) })
        }
        Spacer(Modifier.height(14.dp))

        val missing = if (strength) emptyList() else listOfNotNull(
            tr("percorso GPS").takeIf { d.route.size < 2 },
            tr("cadenza").takeIf { d.cadence.isEmpty() && d.steps == null },
            tr("dislivello").takeIf { d.elevationGainM == null && altitude.isEmpty() }
        )
        if (missing.isNotEmpty()) {
            Text(
                tr("Non arrivati da Health Connect per questa sessione: %s.", missing.joinToString(", ")) +
                    (d.device?.let { " " + tr("Dispositivo: %s.", it) } ?: ""),
                color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
    }
}

// ------------------------------------------------------------------

@Composable
private fun SummaryCard(d: WorkoutDetail, splits: List<WorkoutAnalysis.Split>) {
    val best = WorkoutAnalysis.bestSplit(splits)
    val cadence = WorkoutAnalysis.averageCadence(d)
    UnicumCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                if (d.distanceM != null) WorkoutAnalysis.formatKm(d.distanceM) else "–",
                color = UnicumDark, style = UnicumType.Hero, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text("km", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Body, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        val tiles = listOf(
            WorkoutAnalysis.formatDuration(d.movingSeconds) to tr("Durata"),
            (WorkoutAnalysis.formatPace(WorkoutAnalysis.averagePace(d)) + "/km") to tr("Ritmo medio"),
            (d.avgHr?.let { "$it bpm" } ?: "–") to tr("FC media"),
            (d.maxHr?.let { "$it bpm" } ?: "–") to tr("FC max"),
            (cadence?.let { "$it spm" } ?: "–") to (if (d.cadence.isEmpty() && cadence != null) tr("Cadenza (dai passi)") else tr("Cadenza")),
            (d.vo2max?.let { lfmt("%.1f", it) } ?: "–") to (if (d.vo2maxFromSession || d.vo2max == null) "VO₂ max" else tr("VO₂ max (ultimo)")),
            (d.kcal?.let { "${it.roundToInt()} kcal" } ?: "–") to tr("Calorie"),
            (best?.let { WorkoutAnalysis.formatPace(it.paceSec) + "/km" } ?: "–") to tr("Km più veloce"),
            (d.maxSpeedMs?.let { lfmt("%.1f km/h", it * 3.6) } ?: "–") to tr("Velocità max")
        ) + listOfNotNull(
            // Costo per km della dashboard: FC media × minuti al km
            WorkoutAnalysis.averagePace(d)?.let { p -> d.avgHr?.let { lfmt("%,d", (it * p / 60.0).roundToInt()) to tr("Battiti per km") } },
            // Solo se la fonte li dà: non sono nel registro corse e spesso mancano
            d.elevationGainM?.let { "${it.roundToInt()} m" to tr("Dislivello") },
            d.steps?.let { lfmt("%,d", it) to tr("Passi") },
            (WorkoutAnalysis.formatDuration(d.durationSeconds) to tr("Tempo totale")).takeIf { d.durationSeconds - d.movingSeconds >= 30 }
        )
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                row.forEach { (v, l) -> BigStat(v, l, Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Parziali della corsa, come in Samsung Health: i giri o le ripetute (scritti dall'utente, dell'orologio o dal
 * programma) e i km. Con entrambi, due schede "Giri | 1 km" o "Ripetute | 1 km".
 */
@Composable
private fun SplitsCard(
    splits: List<WorkoutAnalysis.Split>,
    segments: List<WorkoutAnalysis.IntervalSegment>,
    canEditIntervals: Boolean,
    suggestIntervals: Boolean,
    fromPlan: String?,
    fromWatch: Boolean,
    manual: Boolean,
    onEditIntervals: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val laps = segments.any { it.lap }
    UnicumCard {
        CardHeader(
            Icons.Rounded.Straighten, tr("Parziali"),
            action = if (!canEditIntervals) null else if (manual) tr("Modifica") else tr("Giri"),
            onAction = onEditIntervals
        )
        Spacer(Modifier.height(12.dp))
        if (segments.isNotEmpty() && splits.isNotEmpty()) {
            UnicumSegmented(listOf(if (laps) tr("Giri") else tr("Ripetute"), "1 km"), tab, { tab = it }, height = 36.dp)
            Spacer(Modifier.height(12.dp))
        }
        when {
            segments.isNotEmpty() && (tab == 0 || splits.isEmpty()) -> IntervalsTable(segments)
            splits.isNotEmpty() -> KmSplits(splits)
            else -> Text(
                tr("Parziali per km non disponibili per questa registrazione."),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
            )
        }
        if (fromPlan != null && segments.isNotEmpty() && (tab == 0 || splits.isEmpty())) {
            Spacer(Modifier.height(10.dp))
            Text(
                tr("Pezzi presi dal programma (\"%s\"), collocati dove la corsa li segue meglio. Se hai fatto altro, scrivi i giri con \"Giri\".", fromPlan),
                    
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
        if (fromWatch && segments.isNotEmpty() && (tab == 0 || splits.isEmpty())) {
            Spacer(Modifier.height(10.dp))
            Text(
                tr("Giri registrati dall'orologio. Se non tornano, scrivili tu con \"Giri\"."),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
        if (segments.isEmpty() && canEditIntervals && suggestIntervals) {
            Spacer(Modifier.height(10.dp))
            Text(
                tr("I giri dell'orologio non arrivano in Health Connect: tocca \"Giri\" e scrivi la durata di ogni giro, oppure la struttura delle ripetute (es. 10' di riscaldamento, 3 × 4' veloce e 2' di recupero)."),
                    
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun IntervalsTable(segments: List<WorkoutAnalysis.IntervalSegment>) {
    val work = segments.filter { it.kind == WorkoutAnalysis.IntervalKind.WORK }
    val laps = segments.any { it.lap }
    // Senza recuperi (progressivo) i tratti sono blocchi, non ripetute veloci
    val blocks = !laps && segments.none { it.kind == WorkoutAnalysis.IntervalKind.REST }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Header("", Modifier.weight(1f))
        Header(tr("Durata"), Modifier.width(52.dp), TextAlign.End)
        Header("Km", Modifier.width(44.dp), TextAlign.End)
        Header(tr("Ritmo"), Modifier.width(52.dp), TextAlign.End)
        Header(tr("FC"), Modifier.width(40.dp), TextAlign.End)
    }
    segments.forEach { s ->
        // Giri senza nessuno segnato veloce: tutti uguali
        val strong = s.kind == WorkoutAnalysis.IntervalKind.WORK || (laps && work.isEmpty())
        val color = if (strong) UnicumDark else UnicumDark.copy(alpha = .6f)
        val weight = if (strong) FontWeight.SemiBold else FontWeight.Normal
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(50))
                        .background(if (strong) UnicumPrimary else UnicumAccent.copy(alpha = .5f))
                )
                Spacer(Modifier.width(8.dp))
                Text(segmentName(s, blocks), color = color, style = UnicumType.Caption, fontWeight = weight, maxLines = 1)
            }
            listOf(
                WorkoutAnalysis.formatDuration(s.seconds) to 52.dp,
                kmText(s.meters) to 44.dp,
                WorkoutAnalysis.formatPace(s.paceSec) to 52.dp,
                (s.avgHr?.toString() ?: "–") to 40.dp
            ).forEach { (v, w) ->
                Text(v, color = color, style = UnicumType.Caption, fontWeight = weight, modifier = Modifier.width(w), textAlign = TextAlign.End, maxLines = 1)
            }
        }
    }
    if (work.size >= 2) {
        val seconds = work.sumOf { it.seconds }
        val meters = work.sumOf { it.meters }
        val hr = work.mapNotNull { it.avgHr }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val fastest = work.filter { it.paceSec != null }.minByOrNull { it.paceSec!! }
        Spacer(Modifier.height(8.dp))
        Text(
            tr("%s: %s km in %s, ritmo medio %s/km", if (blocks) tr("Blocchi") else tr("Veloci"), kmText(meters), WorkoutAnalysis.formatDuration(seconds),
                                WorkoutAnalysis.formatPace(WorkoutAnalysis.paceSecPerKm(meters, seconds))) +
                (hr?.let { " · " + tr("FC %d", it) } ?: "") + (fastest?.let { " · " + when { laps -> tr("il più veloce il giro %d", it.rep); blocks -> tr("il più veloce il %d°", it.rep); else -> tr("la più veloce la %dª", it.rep) } } ?: ""),
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note, lineHeight = 15.sp
        )
    }
}

/** "Giro 3", "Veloce 2", "Blocco 1", "Recupero", "Riscaldamento"… */
private fun segmentName(s: WorkoutAnalysis.IntervalSegment, blocks: Boolean): String = when {
    s.lap -> tr("Giro %d", s.rep ?: 0)
    s.kind == WorkoutAnalysis.IntervalKind.WORK -> (if (blocks) tr("Blocco") else s.kind.label) + (s.rep?.let { " $it" } ?: "")
    else -> s.kind.label
}

@Composable
private fun KmSplits(splits: List<WorkoutAnalysis.Split>) {
    val fastest = splits.minOf { it.paceSec }
    val slowest = splits.maxOf { it.paceSec }
    val track = UnicumAccent.copy(alpha = .3f)
    val bar = UnicumPrimary
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Header("Km", Modifier.width(34.dp))
            Header(tr("Ritmo"), Modifier.weight(1f))
            Header(tr("Tempo"), Modifier.width(58.dp), TextAlign.End)
            Header(tr("FC"), Modifier.width(44.dp), TextAlign.End)
        }
        splits.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (s.meters >= 990) s.index.toString() else lfmt("%.2f", s.meters / 1000),
                    color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.Medium, modifier = Modifier.width(34.dp)
                )
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // Barra più lunga = più veloce
                    val f = if (slowest == fastest) 1f else 0.35f + 0.65f * (slowest - s.paceSec) / (slowest - fastest).toFloat()
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(track)) {
                        Box(
                            Modifier.fillMaxWidth(f).height(10.dp).clip(RoundedCornerShape(50))
                                .background(if (s.paceSec == fastest) bar else bar.copy(alpha = .6f))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(WorkoutAnalysis.formatPace(s.paceSec), color = UnicumDark, style = UnicumType.Caption, modifier = Modifier.width(44.dp))
                }
                Text(WorkoutAnalysis.formatDuration(s.seconds), color = UnicumDark.copy(alpha = .7f), style = UnicumType.Caption,
                    modifier = Modifier.width(58.dp), textAlign = TextAlign.End)
                Text(s.avgHr?.toString() ?: "–", color = UnicumDark.copy(alpha = .7f), style = UnicumType.Caption,
                    modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun Header(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(text.uppercase(), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Micro, fontWeight = FontWeight.SemiBold,
        modifier = modifier, textAlign = align)
}

@Composable
private fun ChartCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    action: String? = null,
    onAction: () -> Unit = {},
    content: @Composable () -> Unit
) {
    UnicumCard {
        CardHeader(icon, title, action = action, onAction = onAction)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Numeri di una seduta di forza dall'orologio: durata in grande, poi calorie e battito. */
@Composable
private fun StrengthSummaryCard(d: WorkoutDetail) {
    UnicumCard {
        Text(WorkoutAnalysis.formatDuration(d.movingSeconds), color = UnicumDark, style = UnicumType.Hero, fontWeight = FontWeight.SemiBold)
        Text(tr("Durata"), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note)
        Spacer(Modifier.height(12.dp))
        val tiles = listOf(
            (d.kcal?.let { "${it.roundToInt()} kcal" } ?: "–") to tr("Calorie"),
            (d.avgHr?.let { "$it bpm" } ?: "–") to tr("FC media"),
            (d.maxHr?.let { "$it bpm" } ?: "–") to tr("FC max")
        ) + listOfNotNull(
            (WorkoutAnalysis.formatDuration(d.durationSeconds) to tr("Tempo totale")).takeIf { d.durationSeconds - d.movingSeconds >= 30 }
        )
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                row.forEach { (v, l) -> BigStat(v, l, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // Il nome del dispositivo è lungo ("Galaxy Watch7"): su una riga sua, non in una casella
        d.device?.let {
            Spacer(Modifier.height(4.dp))
            Text(tr("Dispositivo: %s.", it), color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note)
        }
    }
}

@Composable
private fun SeriesCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    samples: List<WorkoutSample>,
    color: androidx.compose.ui.graphics.Color,
    unit: String,
    summary: String,
    selectedT: Int?,
    onSelect: (Int?) -> Unit
) {
    ChartCard(icon, title) {
        SeriesChart(
            xs = samples.map { it.t.toFloat() }, ys = samples.map { it.v }, color = color,
            xLabel = { WorkoutAnalysis.formatDuration(it.roundToInt()) },
            yLabel = { "${it.roundToInt()} $unit" },
            summary = summary,
            selectedX = selectedT?.toFloat(), onSelect = { onSelect(it?.roundToInt()) }
        )
    }
}

/** "17" → 1020 s, "17:00" / "17.00" / "17'00" → 1020 s, "1:02:10" → 3730 s. */
private fun parseClock(text: String): Int? {
    val parts = text.trim().split(':', '.', '\'', '"', ',').filter { it.isNotBlank() }.map { it.trim().toIntOrNull() ?: return null }
    return when (parts.size) {
        1 -> parts[0] * 60
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }?.takeIf { it > 0 }
}

/** Con durata e distanza corrette, FC media e massima del tratto della registrazione che corrisponde alla corsa. */
private fun withSegmentHr(raw: WorkoutDetail, f: WorkoutFeedback): WorkoutFeedback {
    val s = f.movingSeconds ?: return f
    val m = f.distanceM ?: return f
    val hr = WorkoutAnalysis.findSegment(raw, s, m)?.let { WorkoutAnalysis.hrIn(raw, it) }
    return f.copy(avgHr = hr?.first, maxHr = hr?.second)
}

private fun kmText(meters: Double): String = lfmt("%.2f", meters / 1000)

/**
 * Correzione a mano di una corsa quando l'orologio ha registrato anche riscaldamento o cammino: durata e
 * distanza (oppure il ritmo, da cui si ricava la distanza). Si salva nelle Sensazioni, non tocca i dati importati.
 */
@Composable
private fun CorrectionDialog(
    raw: WorkoutDetail,
    feedback: WorkoutFeedback,
    onDismiss: () -> Unit,
    onSave: (WorkoutFeedback) -> Unit
) {
    val startSeconds = feedback.movingSeconds ?: raw.movingSeconds
    val startMeters = feedback.distanceM ?: raw.distanceM
    var durationText by remember { mutableStateOf(WorkoutAnalysis.formatDuration(startSeconds)) }
    var kmTextValue by remember { mutableStateOf(startMeters?.let(::kmText).orEmpty()) }
    var paceText by remember {
        mutableStateOf(WorkoutAnalysis.paceSecPerKm(startMeters, startSeconds)?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }.orEmpty())
    }
    // Il campo scritto per ultimo comanda: se è il ritmo, la distanza si ricava esatta (non dai km arrotondati)
    var paceLast by remember { mutableStateOf(false) }
    val seconds = parseClock(durationText)
    val typedMeters = kmTextValue.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.times(1000)
    val meters = if (paceLast) parseClock(paceText)?.let { p -> seconds?.let { it * 1000.0 / p } } ?: typedMeters else typedMeters

    fun updatePace(s: Int?, m: Double?) {
        WorkoutAnalysis.paceSecPerKm(m, s ?: return)?.let { paceText = "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
    }

    io.github.peol13.unicum.ui.components.UnicumDialog(onDismiss) {
        Text(tr("Correggi la corsa"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        Text(
            tr("Registrata dall'orologio: %s e %s km. Scrivi durata e distanza della sola corsa, oppure durata e ritmo medio: la distanza si calcola da sé.", WorkoutAnalysis.formatDuration(raw.movingSeconds), raw.distanceM?.let(::kmText) ?: "–"),
                
            color = UnicumDark.copy(alpha = .65f), style = UnicumType.Caption, lineHeight = 17.sp
        )
        CorrectionField(tr("Durata (min:sec)"), durationText, androidx.compose.ui.text.input.KeyboardType.Text) {
            durationText = it
            val s = parseClock(it)
            val p = parseClock(paceText)
            if (paceLast && s != null && p != null) kmTextValue = kmText(s * 1000.0 / p) else updatePace(s, typedMeters)
        }
        CorrectionField(tr("Distanza (km)"), kmTextValue, androidx.compose.ui.text.input.KeyboardType.Decimal) {
            kmTextValue = it; paceLast = false; updatePace(seconds, it.replace(',', '.').toDoubleOrNull()?.times(1000))
        }
        CorrectionField(tr("Ritmo medio (min:sec al km)"), paceText, androidx.compose.ui.text.input.KeyboardType.Text) {
            paceText = it
            paceLast = true
            val p = parseClock(it) // "6:17" o "6'17" → 377 s al km
            if (p != null && seconds != null) kmTextValue = kmText(seconds * 1000.0 / p)
        }
        io.github.peol13.unicum.ui.components.UnicumButton(
            tr("Salva correzione"),
            enabled = seconds != null && meters != null,
            onClick = { onSave(withSegmentHr(raw, feedback.copy(movingSeconds = seconds, distanceM = meters))) }
        )
        if (feedback.isCorrected) {
            androidx.compose.material3.TextButton(
                onClick = { onSave(feedback.copy(movingSeconds = null, distanceM = null, avgHr = null, maxHr = null)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr("Torna ai dati dell'orologio"), color = UnicumPrimary)
            }
        }
        androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f))
        }
    }
}

@Composable
private fun CorrectionField(label: String, value: String, keyboard: androidx.compose.ui.text.input.KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(10)) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        textStyle = unicumFieldTextStyle(), colors = unicumFieldColors(),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
    )
}

/** Testo e unità (minuti o km) di una parte delle ripetute nel dialog. */
private class PartInput(text: String, km: Boolean) {
    var text by mutableStateOf(text)
    var km by mutableStateOf(km)

    /** A tempo: "4", "4:00", "4'30"; a distanza: "0,64" km, oppure "400" (oltre 20 si intendono metri). */
    fun part(): IntervalPart? = if (km) {
        text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }
            ?.let { IntervalPart(meters = if (it > 20) it else it * 1000) }
    } else parseClock(text)?.let { IntervalPart(seconds = it) }

    companion object {
        fun of(p: IntervalPart?): PartInput = when {
            p?.meters != null -> PartInput(kmText(p.meters), true)
            p?.seconds != null -> PartInput(WorkoutAnalysis.formatDuration(p.seconds), false)
            else -> PartInput("", false)
        }
    }
}

/** Un giro nel dialog: testo (tempo o km, come scelto per tutti i giri) e se è un tratto veloce. */
private class LapInput(text: String, fast: Boolean) {
    var text by mutableStateOf(text)
    var fast by mutableStateOf(fast)
}

/** Testo di un giro a tempo ("6:40") o a distanza ("0,64" km; oltre 20 si intendono metri). */
private fun lapPart(text: String, km: Boolean): IntervalPart? = if (km) {
    text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }?.let { IntervalPart(meters = if (it > 20) it else it * 1000) }
} else parseClock(text)?.let { IntervalPart(seconds = it) }

/**
 * Come era fatta la corsa, scritta dall'utente (i giri dell'orologio di solito non arrivano in Health Connect), in
 * due modi: **Ripetute** (riscaldamento, numero di ripetute, veloce e recupero) oppure **Giri** liberi, uno per
 * riga con la sua durata o distanza (es. 6:40, 2:00, 6:16, 2:00, 25:00), tagliati in ordine dall'inizio della
 * registrazione. Sotto, i pezzi che ne escono, per controllare che tornino con quelli dell'orologio.
 */
@Composable
private fun IntervalsDialog(
    raw: WorkoutDetail,
    current: IntervalPlan?,
    preferLaps: Boolean,
    onDismiss: () -> Unit,
    onSave: (IntervalPlan?) -> Unit
) {
    val watch = remember(raw) { IntervalPlan.fromWatchLaps(raw.laps) }
    var lapsMode by remember { mutableStateOf(current?.laps ?: (watch != null || preferLaps)) }

    // Ripetute
    val ivPlan = current?.takeIf { !it.laps }
    val warmUp = remember { PartInput.of(ivPlan?.warmUp) }
    val work = remember { PartInput.of(ivPlan?.work) }
    val rest = remember { PartInput.of(ivPlan?.rest) }
    var repsText by remember { mutableStateOf(ivPlan?.reps?.toString().orEmpty()) }
    val reps = repsText.trim().toIntOrNull()?.takeIf { it in 1..50 }
    val workPart = work.part()

    // Giri: quelli salvati, altrimenti quelli dell'orologio, altrimenti due righe vuote
    val lapSource = current?.takeIf { it.laps } ?: watch
    var lapsKm by remember { mutableStateOf(lapSource?.steps().orEmpty().let { s -> s.isNotEmpty() && s.all { it.part.meters != null } }) }
    val lapInputs = remember {
        mutableStateListOf<LapInput>().apply {
            lapSource?.steps().orEmpty().forEach { st ->
                val text = st.part.meters?.let { kmText(it) } ?: st.part.seconds?.let { WorkoutAnalysis.formatDuration(it) }.orEmpty()
                add(LapInput(text, st.kind == StepKind.WORK))
            }
            while (size < 2) add(LapInput("", false))
        }
    }
    val lapsComplete = lapInputs.all { it.text.isBlank() || lapPart(it.text, lapsKm) != null }

    val lapParts = if (lapsComplete) lapInputs.mapNotNull { l -> lapPart(l.text, lapsKm)?.let { it to l.fast } } else emptyList()
    // La somma dei giri deve tornare con la corsa registrata (piccolo scarto ammesso)
    val lapsTotal = remember(raw, lapParts) { WorkoutAnalysis.lapsTotal(raw, lapParts.map { it.first }) }
    val plan = if (lapsMode) {
        if (!lapsComplete) null else IntervalPlan.ofLaps(lapParts)
    } else if (reps != null && workPart != null) IntervalPlan(warmUp.part(), reps, workPart, rest.part()) else null
    val preview = remember(plan) { plan?.let { WorkoutAnalysis.intervals(raw, it) }.orEmpty() }

    io.github.peol13.unicum.ui.components.UnicumDialog(onDismiss, spacing = 12.dp) {
        Text(tr("Giri e ripetute"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        UnicumSegmented(listOf(tr("Giri"), tr("Ripetute")), if (lapsMode) 0 else 1, { lapsMode = it == 0 }, height = 36.dp)
        if (lapsMode) {
            Text(
                tr("Scrivi tutti i giri come li vedi nei giri dell'orologio: la corsa viene tagliata in quest'ordine dall'inizio e la somma deve tornare con la sua durata (scarto massimo %d s). Segna \"Veloce\" i tratti di sforzo.", WorkoutAnalysis.LAPS_TOLERANCE_S),
                color = UnicumDark.copy(alpha = .65f), style = UnicumType.Caption, lineHeight = 17.sp
            )
            UnicumSegmented(
                listOf(tr("Durata (min:sec)"), tr("Distanza (km)")), if (lapsKm) 1 else 0, { lapsKm = it == 1 },
                height = 36.dp, style = UnicumType.Caption
            )
            lapInputs.forEachIndexed { i, lap ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}", color = UnicumDark.copy(alpha = .5f), style = UnicumType.Caption,
                        fontWeight = FontWeight.Medium, modifier = Modifier.width(20.dp)
                    )
                    OutlinedTextField(
                        value = lap.text, onValueChange = { lap.text = it.take(8) },
                        placeholder = { Text(if (lapsKm) tr("es. 0,40") else tr("es. 6:40")) }, singleLine = true,
                        isError = lap.text.isNotBlank() && lapPart(lap.text, lapsKm) == null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = if (lapsKm) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text
                        ),
                        textStyle = unicumFieldTextStyle(), colors = unicumFieldColors(),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    UnicumChip(tr("Veloce"), lap.fast, { lap.fast = !lap.fast }, compact = true)
                    androidx.compose.material3.IconButton(
                        onClick = { lapInputs.removeAt(i); if (lapInputs.isEmpty()) lapInputs.add(LapInput("", false)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Rounded.Close, contentDescription = tr("Togli il giro %d", i + 1),
                            tint = UnicumDark.copy(alpha = .5f), modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            androidx.compose.material3.TextButton(onClick = { if (lapInputs.size < 60) lapInputs.add(LapInput("", false)) }) {
                androidx.compose.material3.Icon(Icons.Rounded.Add, contentDescription = null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Aggiungi giro"), color = UnicumPrimary)
            }
            lapsTotal?.let { LapsTotalCheck(it, lapsKm) { text ->
                val empty = lapInputs.lastOrNull()?.takeIf { it.text.isBlank() }
                if (empty != null) empty.text = text else lapInputs.add(LapInput(text, false))
            } }
        } else {
            Text(
                tr("Scrivi come era fatta la seduta. Ogni parte a tempo (min:sec) o a distanza (km). Il resto in fondo diventa defaticamento."),
                color = UnicumDark.copy(alpha = .65f), style = UnicumType.Caption, lineHeight = 17.sp
            )
            PartField(tr("Riscaldamento"), tr("es. 10:00 (se c'è)"), warmUp)
            CorrectionField(tr("Numero di ripetute"), repsText, androidx.compose.ui.text.input.KeyboardType.Number) { repsText = it.filter(Char::isDigit) }
            PartField(tr("Veloce"), tr("es. 4:00"), work)
            PartField(tr("Recupero"), tr("es. 2:00"), rest)
        }

        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(tr("COME VIENE"), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Note, fontWeight = FontWeight.SemiBold)
            val anyFast = preview.any { it.kind == WorkoutAnalysis.IntervalKind.WORK }
            preview.forEach { s ->
                val strong = s.kind == WorkoutAnalysis.IntervalKind.WORK
                Text(
                    "${segmentName(s, blocks = false)} · " +
                        "${WorkoutAnalysis.formatDuration(s.startS)}–${WorkoutAnalysis.formatDuration(s.endS)} · " +
                        "${kmText(s.meters)} km · ${WorkoutAnalysis.formatPace(s.paceSec)}/km" +
                        (s.avgHr?.let { " · " + tr("FC %d", it) } ?: ""),
                    color = if (strong || (s.lap && !anyFast)) UnicumDark else UnicumDark.copy(alpha = .6f), style = UnicumType.Caption,
                    fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
        io.github.peol13.unicum.ui.components.UnicumButton(
            if (lapsMode) tr("Salva giri") else tr("Salva ripetute"),
            enabled = plan != null && preview.isNotEmpty() && (!lapsMode || lapsTotal?.ok == true), onClick = { onSave(plan) }
        )
        if (current != null) {
            androidx.compose.material3.TextButton(onClick = { onSave(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (current.laps) tr("Togli i giri") else tr("Togli le ripetute"), color = UnicumPrimary)
            }
        }
        androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f))
        }
    }
}

/**
 * Somma dei giri contro la corsa: va bene (teal), manca un pezzo (con il bottone che lo aggiunge come ultimo
 * giro) oppure la supera (sabbia). [onAddRest] riceve il testo del pezzo mancante.
 */
@Composable
private fun LapsTotalCheck(t: WorkoutAnalysis.LapsTotal, km: Boolean, onAddRest: (String) -> Unit) {
    fun fmt(v: Double): String = if (t.byKm) kmText(v) + " km" else WorkoutAnalysis.formatDuration(v.roundToInt())
    val sand = Color(0xFFC9A66B)
    val rest = -t.diff
    Text(
        tr("Somma dei giri: %s · corsa: %s", fmt(t.written), fmt(t.recorded)),
        color = UnicumDark.copy(alpha = .75f), style = UnicumType.Caption, fontWeight = FontWeight.Medium
    )
    when {
        t.ok -> Text(tr("Torna con la corsa."), color = UnicumPrimary, style = UnicumType.Caption)
        rest > 0 -> {
            Text(tr("Mancano %s: aggiungi l'ultimo giro.", fmt(rest)), color = sand, style = UnicumType.Caption, lineHeight = 17.sp)
            // Solo se il pezzo mancante si scrive nell'unità scelta per i giri
            if (km == t.byKm) UnicumChip(tr("Aggiungi %s come ultimo giro", fmt(rest)), false, {
                onAddRest(if (t.byKm) kmText(rest) else WorkoutAnalysis.formatDuration(rest.roundToInt()))
            }, compact = true)
        }
        else -> Text(tr("I giri superano la corsa di %s: controllali.", fmt(-rest)), color = sand, style = UnicumType.Caption, lineHeight = 17.sp)
    }
}

@Composable
private fun PartField(label: String, placeholder: String, input: PartInput) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input.text, onValueChange = { input.text = it.take(8) },
            label = { Text(label) }, placeholder = { Text(if (input.km) tr("es. 0,40") else placeholder) }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (input.km) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text
            ),
            textStyle = unicumFieldTextStyle(), colors = unicumFieldColors(),
            shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        UnicumSegmented(
            listOf("min", "km"), if (input.km) 1 else 0, { input.km = it == 1 },
            modifier = Modifier.width(104.dp), height = 40.dp, style = UnicumType.Caption
        )
    }
}

/**
 * Sforzo percepito (RPE 1–10), tipo di seduta e nota: si salvano da soli. Quelli presenti nel Registro corse di
 * Notion ([logged]) vincono: si vedono in sola lettura con "da Notion".
 */
@Composable
private fun FeedbackCard(
    feedback: WorkoutFeedback, logged: LoggedRun?, planned: PlannedRun?, isRun: Boolean,
    effort: TrainingLoad.Effort?, recoveryHours: Double?, countsForRecovery: Boolean, onChange: (WorkoutFeedback) -> Unit
) {
    val rpe = logged?.rpe ?: feedback.rpe
    val kind = logged?.kind ?: feedback.kind ?: planned?.kind
    val rpeLocked = logged?.rpe != null
    val kindLocked = logged?.kind != null
    val kindFromPlan = !kindLocked && feedback.kind == null && planned?.kind != null
    // I tag come sono ora in Notion; un tipo già scelto che in Notion non c'è più resta visibile
    val kinds = (TrainingPlan.kindNamesFor(io.github.peol13.unicum.data.Sport.Family.RUN) + listOfNotNull(kind)).distinct()
    val haptics = rememberHaptics()
    var note by remember(feedback.note) { mutableStateOf(feedback.note.orEmpty()) }
    // La nota si salva poco dopo che smetti di scrivere
    LaunchedEffect(note) {
        if (note != feedback.note.orEmpty()) { delay(600); onChange(feedback.copy(note = note)) }
    }
    ChartCard(Icons.Rounded.SentimentSatisfied, tr("Sensazioni")) {
        FieldLabel(tr("Sforzo percepito (RPE)"), rpeLocked)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..10).forEach { v ->
                val on = rpe == v
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                        .background(if (on) UnicumPrimary else UnicumSurface)
                        .border(1.dp, if (on) UnicumPrimary else UnicumAccent.copy(alpha = .6f), CircleShape)
                        .clickable(enabled = !rpeLocked) { haptics.tick(); onChange(feedback.copy(rpe = if (on) null else v)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$v", color = if (on) UnicumOnPrimary else UnicumDark, style = UnicumType.Caption,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when (rpe) {
                null -> tr("1 = facilissimo, 10 = massimo sforzo")
                in 1..3 -> tr("Leggero")
                in 4..6 -> tr("Moderato")
                in 7..8 -> tr("Duro")
                else -> tr("Massimale")
            },
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note
        )
        if (effort != null) {
            Spacer(Modifier.height(14.dp))
            MeasuredEffort(effort, rpe, recoveryHours, countsForRecovery)
        }
        if (isRun) {
            Spacer(Modifier.height(14.dp))
            FieldLabel(tr("Tipo di seduta"), kindLocked, if (kindFromPlan) tr("dal programma") else null)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(kinds.size) { i ->
                    val k = kinds[i]
                    UnicumChip(k, kind == k, { if (!kindLocked) onChange(feedback.copy(kind = if (feedback.kind == k) null else k)) }, compact = true)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (logged?.note != null) {
            FieldLabel(tr("Nota"), true)
            Spacer(Modifier.height(4.dp))
            Text(logged.note, color = UnicumDark, style = UnicumType.Detail, lineHeight = 19.sp)
            return@ChartCard
        }
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(500) },
            placeholder = { Text(tr("Nota (come ti sentivi, meteo…)")) },
            textStyle = unicumFieldTextStyle(), colors = unicumFieldColors(),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), minLines = 2
        )
    }
}

/**
 * Fatica misurata dal battito ([TrainingLoad]), sulla stessa scala dell'RPE: dieci tacche come i cerchi sopra,
 * piene fino al valore. Sotto: da cosa viene, recupero stimato e confronto con l'RPE.
 */
@Composable
private fun MeasuredEffort(effort: TrainingLoad.Effort, rpe: Int?, recoveryHours: Double?, countsForRecovery: Boolean) {
    val filled = UnicumSecondary
    val track = UnicumTrack
    FieldLabel(tr("Fatica misurata dai dati"), false, tr("automatica"))
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        (1..10).forEach { v ->
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(if (v <= effort.score) filled else track))
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${effort.score}/10", color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text(TrainingLoad.label(effort.score), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, modifier = Modifier.weight(1f))
        recoveryHours?.takeIf { it > 0 }?.let {
            Text(tr("recupero ~%s", hoursText(it)), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
        }
    }
    Text(
        (if (effort.fromSeries) tr("Dal battito:") else tr("Dalla FC media:")) + " " +
            tr("%d' sopra l'80%% della FC max · carico %d", effort.hardMinutes, effort.load.roundToInt()),
        color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note
    )
    if (!countsForRecovery) {
        Text(
            tr("Riconosciuto in automatico dall'orologio (battito a tratti): non conta per il recupero."),
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp
        )
    }
    if (rpe != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Hai sentito %d, i dati dicono %d.", rpe, effort.score) + " " + TrainingLoad.compare(rpe, effort.score),
            color = UnicumDark, style = UnicumType.Detail, lineHeight = 18.sp
        )
    }
}

/** Etichetta di un campo delle Sensazioni; [fromNotion] = valore preso dal Registro corse (non modificabile qui). */
@Composable
private fun FieldLabel(text: String, fromNotion: Boolean, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, modifier = Modifier.weight(1f))
        val tag = if (fromNotion) tr("da Notion") else hint
        if (tag != null) Text(tag, color = UnicumPrimary, style = UnicumType.Note, fontWeight = FontWeight.Medium)
    }
}
