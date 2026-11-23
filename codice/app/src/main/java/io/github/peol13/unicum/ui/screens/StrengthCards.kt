package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.Exercise
import io.github.peol13.unicum.data.PlannedRun
import io.github.peol13.unicum.data.RunStats
import io.github.peol13.unicum.data.Sport
import io.github.peol13.unicum.data.StrengthStats
import io.github.peol13.unicum.data.StrengthStructure
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.data.WorkoutDetail
import io.github.peol13.unicum.data.WorkoutFeedback
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumDialog
import io.github.peol13.unicum.ui.components.UnicumSegmented
import io.github.peol13.unicum.ui.components.unicumFieldColors
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import io.github.peol13.unicum.i18n.trp
import androidx.compose.ui.text.drawText

/*
 * Pezzi della sezione Allenamenti per gli sport diversi dalla corsa (pesi e corpo libero): icone, elenco degli
 * esercizi, finestra per correggere quelli fatti e statistiche delle sedute di forza.
 */

internal fun sportIcon(sport: Sport): ImageVector = when (sport) {
    Sport.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
    Sport.WEIGHTS -> Icons.Rounded.FitnessCenter
    Sport.BODYWEIGHT -> Icons.Rounded.SportsGymnastics
}

/** Segno di una seduta del programma: il pallino colorato del tipo per la corsa, l'icona dello sport per gli altri. */
@Composable
internal fun SessionMark(r: PlannedRun) {
    if (r.sportKind == Sport.RUN) KindDot(r.kind)
    else Icon(sportIcon(r.sportKind), null, tint = UnicumPrimary, modifier = Modifier.size(16.dp))
}

/** "4 × 8 · 60 kg" ("–" se non c'è nulla). */
internal fun exerciseLine(e: Exercise): String =
    listOfNotNull(StrengthStructure.volumeText(e), StrengthStructure.kgText(e.kg)).joinToString(" · ").ifEmpty { "–" }

/** Riepilogo di una seduta di forza registrata: durata, calorie, battito medio. */
internal fun strengthSummary(w: WorkoutDetail): String = listOfNotNull(
    WorkoutAnalysis.formatDuration(w.movingSeconds),
    w.kcal?.let { "${io.github.peol13.unicum.i18n.lnum(it.roundToInt())} kcal" },
    w.avgHr?.let { "$it bpm" }
).joinToString(" · ")

/**
 * Gli esercizi uno sotto l'altro: nome, serie × ripetizioni e carico, recupero. Con [planned] (esercizi del
 * programma) sotto quelli fatti diversamente si vede anche cosa prevedeva il programma.
 */
@Composable
internal fun ExerciseList(exercises: List<Exercise>, planned: List<Exercise> = emptyList()) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        exercises.forEachIndexed { i, e ->
            val plan = planned.firstOrNull { it.name.equals(e.name, ignoreCase = true) }?.takeIf { it != e }
            Row(verticalAlignment = Alignment.Top) {
                Text("${i + 1}", color = UnicumDark.copy(alpha = .4f), style = UnicumType.Caption, modifier = Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium)
                    val sub = listOfNotNull(
                        e.restS?.let { tr("recupero %s", StrengthStructure.durationText(it)) },
                        plan?.let { tr("programma: %s", exerciseLine(it)) }
                    )
                    if (sub.isNotEmpty()) {
                        Text(sub.joinToString(" · "), color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(exerciseLine(e), color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Un esercizio mentre lo si modifica: i campi come testo, convertiti solo al salvataggio. */
private class ExerciseInput(e: Exercise) {
    var name by mutableStateOf(e.name)
    var sets by mutableStateOf(e.sets?.toString().orEmpty())
    var amount by mutableStateOf(StrengthStructure.amountText(e))
    var kg by mutableStateOf(StrengthStructure.kgText(e.kg)?.removeSuffix(" kg").orEmpty())
    val restS = e.restS

    fun toExercise(): Exercise? {
        val n = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val (reps, seconds) = StrengthStructure.parseAmount(amount)
        return Exercise(n, sets.toIntOrNull()?.takeIf { it in 1..50 }, reps, seconds, StrengthStructure.parseKg(kg), restS)
    }
}

/**
 * Correggi gli esercizi fatti: si parte da [current] (quelli già corretti o quelli del programma). [onSave] riceve la
 * lista nuova, oppure null per tornare a quelli del programma ([planned]), che poi si seguono se il programma cambia.
 */
@Composable
internal fun ExercisesDialog(
    current: List<Exercise>,
    planned: List<Exercise>,
    edited: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<Exercise>?) -> Unit
) {
    val inputs = remember { mutableStateListOf<ExerciseInput>().apply { addAll(current.map(::ExerciseInput)) } }
    UnicumDialog(onDismiss, spacing = 12.dp) {
        Text(tr("Esercizi fatti"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        Text(
            tr("Cambia quello che hai fatto in modo diverso dal programma. Ripetizioni o tempo: 8, 8-10, max, 45''. Kg vuoto = corpo libero."),
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note, lineHeight = 15.sp
        )
        inputs.toList().forEach { input ->
            key(input) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input.name, onValueChange = { input.name = it.take(50) },
                            label = { Text(tr("Esercizio")) }, singleLine = true,
                            colors = unicumFieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { inputs.remove(input) }) {
                            Icon(Icons.Rounded.Close, tr("Togli"), tint = UnicumDark.copy(alpha = .5f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = input.sets, onValueChange = { input.sets = it.filter(Char::isDigit).take(2) },
                            label = { Text(tr("Serie")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = unicumFieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(.8f)
                        )
                        OutlinedTextField(
                            value = input.amount, onValueChange = { input.amount = it.take(8) },
                            label = { Text(tr("Rip.")) }, singleLine = true,
                            colors = unicumFieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = input.kg, onValueChange = { input.kg = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                            label = { Text("Kg") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = unicumFieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        TextButton(onClick = { inputs.add(ExerciseInput(Exercise(""))) }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Aggiungi esercizio"), color = UnicumPrimary, fontWeight = FontWeight.SemiBold)
        }
        UnicumButton(tr("Salva"), onClick = { onSave(inputs.mapNotNull { it.toExercise() }) })
        if (edited && planned.isNotEmpty()) {
            TextButton(onClick = { onSave(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Torna agli esercizi del programma"), color = UnicumPrimary)
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f)) }
    }
}

/**
 * Esercizi fatti di una seduta del programma senza allenamento registrato (fatta senza orologio): si salvano
 * sulla seduta ([WorkoutFeedback.planKey]).
 */
@Composable
internal fun PlanExercisesDialog(r: PlannedRun, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = WorkoutFeedback.planKey(r.id)
    var feedback by remember { mutableStateOf<WorkoutFeedback?>(null) }
    LaunchedEffect(key) { feedback = UnicumRepository.loadWorkoutFeedback(context)[key] ?: WorkoutFeedback() }
    val f = feedback ?: return
    ExercisesDialog(f.exercises ?: r.exercises, r.exercises, f.exercises != null, onDismiss) { list ->
        scope.launch { UnicumRepository.saveWorkoutFeedback(context, key, f.copy(exercises = list)) }
        onDismiss()
    }
}

/**
 * Le sedute di forza registrate: quante, quanto tempo, calorie e battito per settimana / mese / sempre, e la
 * costanza rispetto al programma ([adherence] = fatte, previste nelle ultime 4 settimane).
 */
@Composable
internal fun StrengthStatsCard(workouts: List<WorkoutDetail>, adherence: Pair<Int, Int>?) {
    var period by rememberSaveable { mutableStateOf(RunStats.Period.WEEK) }
    val today = DateUtils.today()
    val t = remember(workouts, period) { StrengthStats.totals(StrengthStats.inPeriod(workouts, period, today)) }
    UnicumCard {
        UnicumSegmented(RunStats.Period.entries.map { it.label }, period.ordinal, { period = RunStats.Period.entries[it] }, height = 36.dp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            BigStat(t.count.toString(), tr("Sedute"), Modifier.weight(1f))
            BigStat(if (t.count > 0) WorkoutAnalysis.formatDuration(t.seconds) else "–", tr("Tempo"), Modifier.weight(1f))
            BigStat(t.kcal?.let { "$it" } ?: "–", "kcal", Modifier.weight(1f))
        }
        t.avgHr?.let {
            Spacer(Modifier.height(8.dp))
            Text(tr("FC media %d bpm", it), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
        }
        adherence?.let { (done, total) ->
            Spacer(Modifier.height(10.dp))
            Text(
                tr("Programma: %d di %d sedute fatte nelle ultime 4 settimane", done, total),
                color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Serie, ripetizioni e carichi non arrivano dall'orologio: li trovi nella pagina di ogni seduta."),
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp
        )
    }
}

/**
 * Progressione dei carichi: si sceglie un esercizio e si vedono i kg seduta per seduta (il più alto di ogni
 * seduta), con il primo, l'ultimo e la differenza. Gli esercizi sono quelli scritti nella pagina di ogni seduta
 * (o, se non li hai cambiati, quelli del programma).
 */
@Composable
internal fun LoadProgressCard(loads: List<StrengthStats.ExerciseLoads>) {
    if (loads.isEmpty()) return
    var selected by rememberSaveable { mutableStateOf(0) }
    val e = loads[selected.coerceIn(loads.indices)]
    UnicumCard {
        Text(tr("Carichi per esercizio"), color = UnicumDark, style = UnicumType.Card, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            items(loads.size) { i ->
                io.github.peol13.unicum.ui.components.UnicumChip(loads[i].name, i == selected, { selected = i }, compact = true)
            }
        }
        Spacer(Modifier.height(14.dp))
        val diff = e.last - e.first
        Row(Modifier.fillMaxWidth()) {
            BigStat(StrengthStructure.kgText(e.last) ?: "–", tr("Ultima"), Modifier.weight(1f))
            BigStat(StrengthStructure.kgText(e.best) ?: "–", tr("Migliore"), Modifier.weight(1f))
            BigStat(
                when {
                    kotlin.math.abs(diff) < 1e-6 -> "="
                    diff > 0 -> "+" + StrengthStructure.kgText(diff)
                    else -> "−" + StrengthStructure.kgText(-diff)
                },
                tr("Dalla prima"), Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        LoadChart(e.points)
        Spacer(Modifier.height(6.dp))
        Text(
            trp(e.points.size, "%d seduta", "%d sedute") + " · " + tr("dal %s", DateUtils.shortLabel(e.points.first().date)),
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
        )
    }
}

/** Linea dei carichi nel tempo (punti alla data vera), con i kg minimo e massimo a sinistra. */
@Composable
private fun LoadChart(points: List<StrengthStats.LoadPoint>) {
    val line = UnicumPrimary
    val track = io.github.peol13.unicum.ui.theme.UnicumTrack
    val label = UnicumDark.copy(alpha = .5f)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = UnicumType.Micro.copy(color = label)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val minKg = points.minOf { it.kg }
        val maxKg = points.maxOf { it.kg }
        val span = (maxKg - minKg).takeIf { it > 0 } ?: 1.0
        val t0 = points.first().date.toEpochDay()
        val days = (points.last().date.toEpochDay() - t0).coerceAtLeast(1)
        val left = 44.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        fun x(p: StrengthStats.LoadPoint) = left + (size.width - left - 8.dp.toPx()) * (p.date.toEpochDay() - t0) / days.toFloat()
        fun y(kg: Double) = (bottom - (bottom - top) * ((kg - minKg) / span)).toFloat()
        listOf(maxKg, minKg).distinct().forEach { kg ->
            drawLine(track, androidx.compose.ui.geometry.Offset(left, y(kg)), androidx.compose.ui.geometry.Offset(size.width, y(kg)), 1.dp.toPx())
            val t = measurer.measure(StrengthStructure.kgText(kg) ?: "", style)
            drawText(t, topLeft = androidx.compose.ui.geometry.Offset(0f, y(kg) - t.size.height / 2f))
        }
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p), y(p.kg)) else path.lineTo(x(p), y(p.kg)) }
        drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        points.forEach { p -> drawCircle(line, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(x(p), y(p.kg))) }
    }
}
