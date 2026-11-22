package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.tr
import io.github.peol13.unicum.i18n.trp

import io.github.peol13.unicum.ui.Route

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.LocalPlan
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.TrainingPlan
import io.github.peol13.unicum.data.PlannedRun
import io.github.peol13.unicum.data.PlanPdfParser
import io.github.peol13.unicum.data.Sport
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.data.WorkoutDetail
import io.github.peol13.unicum.data.runKindLabel
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.ExpandRow
import io.github.peol13.unicum.ui.components.LinkCard
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType
import io.github.peol13.unicum.ui.theme.runKindColor
import java.time.LocalDate

/**
 * Le sedute programmate ([TrainingPlan]: Notion, oppure importate da PDF / scritte nell'app), di ogni sport: la
 * prossima in evidenza (con struttura o esercizi), poi le altre e, a richiesta, le ultime due settimane piano contro
 * fatto. [doneByKey] = allenamenti registrati per giorno e sport ([NotionPlan.doneKey]), per segnare le sedute fatte.
 */
@Composable
fun PlannedRunsSection(doneByKey: Map<String, WorkoutDetail>, onNavigate: (Route) -> Unit) {
    if (TrainingPlan.source == TrainingPlan.Source.NONE) {
        LinkCard(
            Icons.Rounded.CalendarMonth, tr("Aggiungi il programma di allenamento"),
            tr("Da un PDF, scritto a mano o da Notion"),
            onClick = { onNavigate(Route.Notion) }
        )
        return
    }
    val today = DateUtils.today()
    val plan = TrainingPlan.runs
    val upcoming = NotionPlan.upcoming(plan, today)
    val recent = NotionPlan.recent(plan, today)
    fun doneFor(r: PlannedRun) = doneByKey[NotionPlan.doneKey(r.date, r.family)]

    if (upcoming.isEmpty()) {
        UnicumCard(onClick = { onNavigate(Route.Notion) }) {
            CardHeader(Icons.Rounded.CalendarMonth, tr("Nessuna seduta in programma"))
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    TrainingPlan.isLocal -> tr("Aggiungi le prossime sedute o importa un nuovo PDF.")
                    NotionPlan.runs.isEmpty() && NotionPlan.lastSyncMillis == 0L -> tr("Il programma non è ancora stato letto.")
                    else -> tr("Aggiungi le prossime sedute nel database del programma su Notion.")
                },
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption
            )
        }
    } else {
        NextSessionCard(upcoming.first(), doneFor(upcoming.first()), today, onNavigate)
        upcoming.drop(1).forEach { r ->
            Spacer(Modifier.height(10.dp))
            PlannedRow(r, today)
        }
    }
    if (recent.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        RecentPlanCard(recent, doneByKey, onNavigate)
    }
    Spacer(Modifier.height(6.dp))
    SyncNote(onNavigate)
}

@Composable
private fun NextSessionCard(r: PlannedRun, done: WorkoutDetail?, today: LocalDate, onNavigate: (Route) -> Unit) {
    var open by rememberSaveable(r.id) { mutableStateOf(false) }
    var editing by rememberSaveable(r.id) { mutableStateOf(false) }
    if (editing) PlanExercisesDialog(r, onDismiss = { editing = false })
    val strength = r.sportKind.isStrength
    val exercises = r.exercises
    UnicumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(dayLabel(r, today), r.week?.let { tr("settimana %d", it) }).joinToString(" · "),
                    color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
                )
                Text(r.name, color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
            }
            when {
                r.kind != null -> KindPill(r.kind)
                strength -> KindPill(r.sportKind.label)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            if (strength) BigStat(exercises.size.takeIf { it > 0 }?.toString() ?: "–", tr("Esercizi"), Modifier.weight(1f))
            else BigStat(r.km?.let { "${PlanPdfParser.shortKm(it)} km" } ?: "–", tr("Obiettivo"), Modifier.weight(1f))
            BigStat(r.minutes?.let { "$it′" } ?: "–", tr("Durata"), Modifier.weight(1f))
            BigStat(r.rpe?.let { "$it/10" } ?: "–", "RPE", Modifier.weight(1f))
        }
        r.pace?.let {
            Spacer(Modifier.height(10.dp))
            Text(tr("Ritmo: %s", it), color = UnicumDark.copy(alpha = .75f), style = UnicumType.Detail, lineHeight = 18.sp)
        }
        // Gli esercizi sono il contenuto della seduta: sempre in vista
        if (exercises.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            ExerciseList(exercises)
        }
        if (done != null) {
            Spacer(Modifier.height(12.dp))
            DoneRow(done, onClick = { onNavigate(Route.Workout(done.externalId)) })
        } else if (strength && r.status == LocalPlan.STATUS_DONE) {
            Spacer(Modifier.height(12.dp))
            PlanExercisesLink { editing = true }
        }
        // Il testo intero resta da aprire: per la corsa la struttura, per la forza quello che non è un esercizio
        if (r.structure != null || r.focus != null) {
            ExpandRow(open, if (strength) tr("Testo della seduta") else tr("Struttura della seduta"), { open = !open })
            if (open) {
                r.structure?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = UnicumDark, style = UnicumType.Detail, lineHeight = 19.sp)
                }
                r.focus?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Focus"), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, fontWeight = FontWeight.SemiBold)
                    Text(it, color = UnicumDark.copy(alpha = .8f), style = UnicumType.Detail, lineHeight = 19.sp)
                }
                r.url?.let { OpenInNotion(it) }
            }
        }
    }
}

/** Allenamento già registrato nel giorno della seduta: tocco = sua pagina. */
@Composable
private fun DoneRow(w: WorkoutDetail, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(UnicumPrimary.copy(alpha = .10f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            tr("Fatta: %s", doneSummary(w)),
            color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.ChevronRight, null, tint = UnicumDark.copy(alpha = .45f), modifier = Modifier.size(18.dp))
    }
}

/** Seduta di forza fatta senza orologio: si apre la finestra degli esercizi fatti. */
@Composable
private fun PlanExercisesLink(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(UnicumPrimary.copy(alpha = .10f))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(tr("Fatta · esercizi fatti"), color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = UnicumDark.copy(alpha = .45f), modifier = Modifier.size(18.dp))
    }
}

private fun doneSummary(w: WorkoutDetail): String = if (Sport.ofWorkout(w.type)?.isStrength == true) strengthSummary(w) else listOfNotNull(
    w.distanceM?.let { "${WorkoutAnalysis.formatKm(it)} km" },
    WorkoutAnalysis.formatDuration(w.movingSeconds),
    WorkoutAnalysis.averagePace(w)?.let { "${WorkoutAnalysis.formatPace(it)}/km" }
).joinToString(" · ")

/** Una seduta successiva alla prossima: giorno, nome, tipo o sport, obiettivo o numero di esercizi. */
@Composable
private fun PlannedRow(r: PlannedRun, today: LocalDate) {
    var open by rememberSaveable(r.id) { mutableStateOf(false) }
    UnicumCard(onClick = { open = !open }, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SessionMark(r)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    listOfNotNull(dayLabel(r, today), sessionLabel(r), planSummary(r).takeIf { it != "–" && it != r.kind }).joinToString(" · "),
                    color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, maxLines = 1
                )
            }
        }
        if (open) {
            r.pace?.let {
                Spacer(Modifier.height(10.dp))
                Text(tr("Ritmo: %s", it), color = UnicumDark.copy(alpha = .75f), style = UnicumType.Detail, lineHeight = 18.sp)
            }
            val exercises = r.exercises
            if (exercises.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ExerciseList(exercises)
            } else r.structure?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = UnicumDark, style = UnicumType.Detail, lineHeight = 19.sp)
            }
            r.url?.let { OpenInNotion(it) }
        }
    }
}

/** Il tipo della seduta oppure, per gli sport senza tipo, il nome dello sport. */
private fun sessionLabel(r: PlannedRun): String? = r.kind?.let(::runKindLabel) ?: r.sportKind.takeIf { it.isStrength }?.label

/** Le sedute delle ultime due settimane: fatta (con i numeri reali), saltata o non registrata. */
@Composable
private fun RecentPlanCard(recent: List<PlannedRun>, doneByKey: Map<String, WorkoutDetail>, onNavigate: (Route) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    recent.firstOrNull { it.id == editing }?.let { PlanExercisesDialog(it, onDismiss = { editing = null }) }
    val done = recent.count { NotionPlan.isDone(it, doneByKey.keys) }
    UnicumCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, null, tint = UnicumPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                tr("Ultime 2 settimane: %d di %d sedute fatte", done, recent.size),
                color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium
            )
        }
        ExpandRow(open, tr("Piano e allenamenti fatti"), { open = !open })
        if (open) {
            recent.forEach { r ->
                val workout = doneByKey[NotionPlan.doneKey(r.date, r.family)]
                // Forza fatta senza orologio: si possono scrivere gli esercizi fatti
                val canEdit = workout == null && r.sportKind.isStrength && r.status == LocalPlan.STATUS_DONE
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().let { m ->
                        when {
                            workout != null -> m.clickable { onNavigate(Route.Workout(workout.externalId)) }
                            canEdit -> m.clickable { editing = r.id }
                            else -> m
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SessionMark(r)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${DateUtils.shortLabel(r.localDate!!)} · ${r.name}",
                            color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium, maxLines = 1
                        )
                        Text(
                            when {
                                workout != null -> tr("Piano %s → fatta %s", planSummary(r), doneSummary(workout))
                                r.status == "Saltata" -> tr("Saltata")
                                canEdit -> tr("Segnata come fatta · tocca per gli esercizi fatti")
                                r.status == "Completata" -> if (TrainingPlan.isLocal) tr("Segnata come fatta (allenamento non registrato)")
                                    else tr("Completata in Notion, allenamento non trovato nell'app")
                                else -> tr("Nessun allenamento registrato")
                            },
                            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

internal fun planSummary(r: PlannedRun): String {
    val exercises = r.exercises.size.takeIf { it > 0 }?.let { trp(it, "%d esercizio", "%d esercizi") }
    return listOfNotNull(r.km?.let { "${PlanPdfParser.shortKm(it)} km" }, exercises, r.minutes?.let { "$it′" })
        .joinToString(" · ").ifEmpty { r.kind ?: "–" }
}

@Composable
private fun SyncNote(onNavigate: (Route) -> Unit) {
    val text = when {
        TrainingPlan.isLocal -> (LocalPlan.data.sourceName?.let { tr("Dal PDF «%s»", it) } ?: tr("Programma scritto nell'app")) + " · " + tr("tocca per modificarlo")
        NotionPlan.syncing -> tr("Lettura da Notion…")
        NotionPlan.error != null -> NotionPlan.error!!
        NotionPlan.lastSyncMillis > 0 -> tr("Da Notion · letto %s", syncTime(NotionPlan.lastSyncMillis))
        else -> tr("Da Notion")
    }
    Text(
        text,
        color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note,
        modifier = Modifier.clickable { onNavigate(Route.Notion) }.padding(vertical = 4.dp)
    )
}

internal fun syncTime(millis: Long): String {
    val t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
    val day = DateUtils.relativeLabel(t.toLocalDate()).lowercase()
    return tr("%s alle %s", day, "%02d:%02d".format(t.hour, t.minute))
}

@Composable
private fun OpenInNotion(url: String) {
    val context = LocalContext.current
    Spacer(Modifier.height(10.dp))
    Text(
        tr("Apri in Notion"),
        color = UnicumPrimary, style = UnicumType.Caption, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            .padding(vertical = 4.dp)
    )
}

@Composable
internal fun KindPill(kind: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(runKindColor(kind).copy(alpha = .25f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(kind, color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun KindDot(kind: String?) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(runKindColor(kind ?: "")))
}

/** "Oggi", "Domani" oppure "Mar 10 Nov". */
internal fun dayLabel(r: PlannedRun, today: LocalDate): String? {
    val d = r.localDate ?: return null
    return when (d) {
        today -> tr("Oggi")
        today.plusDays(1) -> tr("Domani")
        else -> DateUtils.shortLabel(d)
    }
}
