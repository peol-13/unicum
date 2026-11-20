package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.GoalProgress
import io.github.peol13.unicum.data.GoalProgress.Status
import io.github.peol13.unicum.data.RunGoal
import io.github.peol13.unicum.data.RunGoalStore
import io.github.peol13.unicum.data.RunProgress
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.ui.components.UnicumDialog
import io.github.peol13.unicum.ui.components.UnicumDatePickerDialog
import io.github.peol13.unicum.ui.components.animatedFraction
import io.github.peol13.unicum.ui.components.unicumFieldColors
import io.github.peol13.unicum.ui.theme.LocalUnicumColors
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSecondary
import io.github.peol13.unicum.ui.theme.UnicumTrack
import io.github.peol13.unicum.ui.theme.UnicumType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

// Obiettivi di corsa a breve e lungo termine (distanza, ritmo, scadenza), modificabili dall'app e inclusi nel
// backup. Per ognuno: stima di oggi, tendenza, dove si arriva alla scadenza continuando così. Conti in [GoalProgress].

/** Sezione "Obiettivi" della scheda Programma: le due card e il dialog di modifica. */
@Composable
internal fun GoalsSection(runs: List<RunProgress.Run>) {
    val goals = RunGoalStore.goals
    val history = remember(runs) { RunProgress.estimateHistory(runs) }
    var editing by remember { mutableStateOf<Boolean?>(null) }
    GoalCard(tr("Obiettivo a breve termine"), Icons.Rounded.Flag, goals.short, runs, history) { editing = true }
    Spacer(Modifier.height(14.dp))
    GoalCard(tr("Obiettivo a lungo termine"), Icons.Rounded.EmojiEvents, goals.long, runs, history) { editing = false }
    editing?.let { short ->
        GoalDialog(
            title = if (short) tr("Obiettivo a breve termine") else tr("Obiettivo a lungo termine"),
            initial = if (short) goals.short else goals.long,
            estimateAt = { km -> history.lastOrNull()?.let { RunProgress.atDistance(it.seconds5k, km) } },
            onDismiss = { editing = null },
            onSave = { RunGoalStore.set(short, it); editing = null }
        )
    }
}

/** Colore e nome dello stato: teal = ci arrivi, sabbia = quasi, rosso tenue = così non ci arrivi. */
@Composable
private fun statusLook(s: Status): Pair<Color, String> {
    val dark = LocalUnicumColors.current.isDark
    val sand = Color(0xFFC9A66B)
    val red = if (dark) Color(0xFFD08F82) else Color(0xFFC27A6B)
    val muted = UnicumDark.copy(alpha = .45f)
    return when (s) {
        Status.REACHED -> UnicumPrimary to tr("Obiettivo alla portata")
        Status.AHEAD -> UnicumPrimary to tr("Sopra l'obiettivo")
        Status.ON_TRACK -> UnicumSecondary to tr("In linea")
        Status.CLOSE -> sand to tr("Quasi in linea")
        Status.BEHIND -> red to tr("Sotto l'obiettivo")
        Status.NO_TREND -> muted to tr("Tendenza ancora incerta")
        Status.NO_DATA -> muted to tr("Servono più dati")
        Status.MISSED -> red to tr("Scaduto")
    }
}

/** Aggiunge l'anno se la data non è di quest'anno (gli obiettivi a lungo termine possono andare oltre). */
private fun withYear(label: String, d: LocalDate): String = if (d.year != DateUtils.today().year) "$label ${d.year}" else label

private fun daysText(days: Int): String = when {
    days < 0 -> tr("scaduto")
    days == 0 -> tr("oggi")
    days == 1 -> tr("domani")
    days <= 60 -> tr("tra %d giorni", days)
    else -> tr("tra %d settimane", (days / 7.0).roundToInt())
}

@Composable
private fun GoalCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    goal: RunGoal?,
    runs: List<RunProgress.Run>,
    history: List<RunProgress.EstimatePoint>,
    onEdit: () -> Unit
) {
    UnicumCard {
        CardHeader(icon, title, action = if (goal != null) tr("Modifica") else null, onAction = onEdit)
        Spacer(Modifier.height(12.dp))
        if (goal == null) {
            Text(
                tr("Scegli distanza, ritmo e data: l'app ti dice se, continuando ad allenarti così, ci arrivi in tempo."),
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp
            )
            Spacer(Modifier.height(12.dp))
            UnicumButton(tr("Imposta obiettivo"), onClick = onEdit)
            return@UnicumCard
        }
        val today = DateUtils.today()
        val p = remember(goal, runs, today) { GoalProgress.of(goal, runs, today) }
        val real = remember(runs, goal.km) { RunProgress.realAt(runs, goal.km) }
        val kmL = kmLabel(goal.km)
        val deadline = withYear(DateUtils.shortLabel(goal.deadlineDate), goal.deadlineDate)

        Text(tr("%s km in %s", kmL, mmss(p.target)), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        Text(
            "${pace(goal.paceSec)}/km · " + tr("entro %s", deadline.lowercase(io.github.peol13.unicum.i18n.I18n.locale)) + " · ${daysText(p.daysLeft)}",
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption
        )
        Spacer(Modifier.height(12.dp))

        // Lo stato, chiaro a colpo d'occhio, e la frase che lo spiega
        val (color, label) = statusLook(p.status)
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .16f)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(7.dp))
            Text(label, color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        val atDeadline = tr("il %s", withYear(dayLabel(goal.deadlineDate), goal.deadlineDate))
        val projGap = p.projectedGap
        Text(
            when (p.status) {
                Status.REACHED -> if (p.expired) tr("Ce l'hai fatta: alla scadenza la stima era %s.", mmss(p.current!!))
                else tr("La stima di oggi (%s) è già entro l'obiettivo: ora si tratta di correrlo davvero.", mmss(p.current!!))
                Status.AHEAD -> tr("Se continui così: %s %s, %s meglio dell'obiettivo.", mmss(p.projected!!), atDeadline, gapText(projGap!!))
                Status.ON_TRACK -> tr("Se continui così: %s %s, giusto in tempo.", mmss(p.projected!!), atDeadline)
                Status.CLOSE -> tr("Se continui così: %s %s, %s più lento dell'obiettivo: basta poco in più.", mmss(p.projected!!), atDeadline, gapText(projGap!!))
                Status.BEHIND -> tr("Se continui così: %s %s, %s più lento dell'obiettivo.", mmss(p.projected!!), atDeadline, gapText(projGap!!))
                Status.NO_TREND -> tr("Per la tendenza servono almeno 3 stime in 10 giorni. Oggi sei a %s dall'obiettivo.", gapText(p.gap!!))
                Status.NO_DATA -> tr("La stima arriva con almeno 3 uscite Facili da 2 km e uno sforzo con RPE 7 o più.")
                Status.MISSED -> tr("Alla scadenza la stima era %s, %s più lento. Modificalo per fissarne uno nuovo.", mmss(p.current!!), gapText(p.gap!!))
            },
            color = UnicumDark.copy(alpha = .75f), style = UnicumType.Caption, lineHeight = 17.sp
        )
        if (p.current == null) return@UnicumCard

        // I numeri
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            BigStat(mmss(p.current), tr("Stima di oggi"), Modifier.weight(1f))
            BigStat((if (p.gap!! <= 0) "−" else "+") + gapText(p.gap!!), tr("Dall'obiettivo"), Modifier.weight(1f))
            BigStat(
                p.perWeek?.let { w -> val s = w.roundToInt(); if (s == 0) "±0 s" else (if (s < 0) "−" else "+") + gapText(s) } ?: "–",
                tr("A settimana"), Modifier.weight(1f)
            )
        }
        p.neededPerWeek?.let { need ->
            Spacer(Modifier.height(6.dp))
            val n = abs(need)
            val needText = if (n < 1) tr("meno di 1 s") else gapText(n.roundToInt())
            val now = p.perWeek?.let { w ->
                if (w < -0.5) "; " + tr("nelle ultime settimane: %s in meno a settimana.", gapText(abs(w).roundToInt()))
                else "; " + tr("nelle ultime settimane la stima non è migliorata.")
            } ?: "."
            Text(
                tr("Per arrivarci servono %s in meno a settimana", needText) + now,
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        EstimateChart(goal.km, history, real, goal, projection = p.projected)

        // La distanza: senza averla mai corsa di fila la stima è solo potenziale
        if (p.longestKm < goal.km * 0.98) {
            Spacer(Modifier.height(16.dp))
            Text(
                tr("Distanza continua più lunga: %s km di %s", WorkoutAnalysis.formatKm(p.longestKm * 1000), kmL),
                color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            val grow = animatedFraction((p.longestKm / goal.km).toFloat().coerceIn(0f, 1f), durationMillis = 700)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(UnicumTrack)) {
                Box(Modifier.fillMaxWidth(grow).height(10.dp).clip(RoundedCornerShape(50)).background(UnicumPrimary))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tr("Mancano %s km: la stima vale se arrivi a correrli tutti di fila.", WorkoutAnalysis.formatKm((goal.km - p.longestKm) * 1000)),
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
    }
}

private fun paceInput(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/** Distanza, ritmo e scadenza; [onSave] con null toglie l'obiettivo. [estimateAt]: stima di oggi su una distanza. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDialog(
    title: String,
    initial: RunGoal?,
    estimateAt: (Double) -> Int?,
    onDismiss: () -> Unit,
    onSave: (RunGoal?) -> Unit
) {
    val today = DateUtils.today()
    var kmText by remember { mutableStateOf(initial?.km?.let(::kmLabel) ?: "5") }
    var paceText by remember { mutableStateOf(initial?.paceSec?.let(::paceInput).orEmpty()) }
    var deadline by remember { mutableStateOf(initial?.deadline ?: today.plusMonths(2).toString()) }
    var picking by remember { mutableStateOf(false) }
    val km = RunGoal.parseKm(kmText)
    val paceSec = RunGoal.parsePace(paceText)

    if (picking) {
        UnicumDatePickerDialog(
            initial = LocalDate.parse(deadline),
            onDismiss = { picking = false },
            onPick = { deadline = it.toString() },
            min = today
        )
    }

    UnicumDialog(onDismiss, spacing = 12.dp) {
        Text(title, color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("5", "7", "10", "21,1").forEach { k ->
                UnicumChip("$k km", RunGoal.parseKm(k) == km, { kmText = k }, compact = true)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = kmText, onValueChange = { kmText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                label = { Text(tr("Distanza")) }, suffix = { Text("km") }, singleLine = true,
                isError = kmText.isNotBlank() && km == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = paceText, onValueChange = { paceText = it.filter { c -> c.isDigit() || c in ":.,'" }.take(5) },
                label = { Text(tr("Ritmo")) }, placeholder = { Text("6:30") }, suffix = { Text("/km") }, singleLine = true,
                isError = paceText.isNotBlank() && paceSec == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
            )
        }
        // Il tempo che ne viene e, per riferimento, la stima di oggi su quella distanza
        Text(
            if (km != null && paceSec != null) {
                val target = (km * paceSec).roundToInt()
                tr("Tempo obiettivo: %s sui %s km", mmss(target), kmLabel(km)) +
                    (estimateAt(km)?.let { " · " + tr("stima di oggi %s", mmss(it)) } ?: "")
            } else tr("Ritmo in minuti e secondi al km, per esempio 6:30."),
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .border(1.dp, UnicumDark.copy(alpha = .15f), RoundedCornerShape(16.dp))
                .clickable { picking = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = UnicumPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Entro il"), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note)
                Text(withYear(DateUtils.fullLabel(LocalDate.parse(deadline)), LocalDate.parse(deadline)), color = UnicumDark, style = UnicumType.Body)
            }
        }
        Spacer(Modifier.height(4.dp))
        UnicumButton(
            tr("Salva"), enabled = km != null && paceSec != null,
            onClick = { if (km != null && paceSec != null) onSave(RunGoal(km, paceSec, deadline)) }
        )
        if (initial != null) {
            TextButton(onClick = { onSave(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Togli obiettivo"), color = UnicumDark.copy(alpha = .7f))
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(tr("Annulla"), color = UnicumPrimary) }
    }
}
