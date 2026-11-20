package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.lfmt

import io.github.peol13.unicum.i18n.tr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.TrainingPlan
import io.github.peol13.unicum.data.RunProgress
import io.github.peol13.unicum.data.RunProgress.Metric
import io.github.peol13.unicum.data.RunGoal
import io.github.peol13.unicum.data.RunGoalStore
import io.github.peol13.unicum.data.GoalProgress
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.data.WorkoutFeedback
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.ExpandRow
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.ui.components.UnicumSegmented
import io.github.peol13.unicum.ui.components.animatedFraction
import io.github.peol13.unicum.ui.sections.SeriesChart
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSecondary
import io.github.peol13.unicum.ui.theme.UnicumSurface
import io.github.peol13.unicum.ui.theme.UnicumTrack
import io.github.peol13.unicum.ui.theme.UnicumType
import io.github.peol13.unicum.ui.theme.runKindColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

// Le card della dashboard "Progressi corsa" nella sezione Allenamenti: stima dei 5 km, confronto tra corse
// dello stesso tipo, km per settimana divisi per tipo. I conti sono in [RunProgress].

internal fun mmss(seconds: Int): String = WorkoutAnalysis.formatDuration(abs(seconds))
internal fun pace(seconds: Int?): String = WorkoutAnalysis.formatPace(seconds)
internal fun dayLabel(d: LocalDate): String = DateUtils.shortLabel(d).substringAfter(' ')
private fun thousands(v: Int): String = lfmt("%,d", v)

/** Secondi con segno: "−12 s", "+1:05". */
internal fun signedSeconds(d: Int): String = when {
    d == 0 -> "±0 s"
    abs(d) < 60 -> (if (d < 0) "−" else "+") + "${abs(d)} s"
    else -> (if (d < 0) "−" else "+") + mmss(d)
}

// ------------------------------------------------------------------
// Stime 5 / 7 / 10 km
// ------------------------------------------------------------------

internal fun kmLabel(km: Double): String =
    if (abs(km - km.roundToInt()) < 1e-6) "${km.roundToInt()}" else lfmt("%.1f", km)

/** Secondi senza segno: "41 s", "1:05". */
internal fun gapText(d: Int): String = if (abs(d) < 60) "${abs(d)} s" else mmss(d)

/** Distanza scelta nella card delle stime: resta la stessa tornando nella sezione. */
private val estimateKmState = mutableStateOf(0)

@Composable
internal fun EstimateCard(runs: List<RunProgress.Run>) {
    val today = DateUtils.today()
    var idx by estimateKmState
    val km = RunProgress.DISTANCES[idx.coerceIn(0, RunProgress.DISTANCES.lastIndex)]
    val history = remember(runs) { RunProgress.estimateHistory(runs) }
    val e = remember(runs, km) { RunProgress.estimate(runs, km) }
    val real = remember(runs, km) { RunProgress.realAt(runs, km) }
    val goal = RunGoalStore.goals.forKm(km, today)
    UnicumCard {
        CardHeader(Icons.Rounded.Flag, tr("Stima"))
        Spacer(Modifier.height(12.dp))
        UnicumSegmented(RunProgress.DISTANCES.map { "${kmLabel(it)} km" }, idx, { idx = it }, height = 36.dp)
        Spacer(Modifier.height(14.dp))
        if (e == null) {
            Text(
                tr("Per la stima servono almeno 3 uscite Facili (o Lungo facile) da 2 km in su con RPE fino a 6, e uno sforzo con RPE 7 o più: segnali nelle Sensazioni di ogni corsa."),
                    
                color = UnicumDark.copy(alpha = .65f), style = UnicumType.Caption, lineHeight = 17.sp
            )
            return@UnicumCard
        }
        Text(tr("Con l'allenamento attuale correresti i %s km in", kmLabel(km)), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(mmss(e.seconds), color = UnicumDark, style = UnicumType.Hero, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("${pace(e.pace)}/km", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Body, modifier = Modifier.padding(bottom = 6.dp))
        }
        val vsGoal = goal?.let { e.seconds - it.targetSeconds }
        val parts = listOfNotNull(
            goal?.let { g ->
                tr("Obiettivo %s entro il %s:", mmss(g.targetSeconds), dayLabel(g.deadlineDate)) + " " + when {
                    vsGoal == 0 -> tr("ci sei")
                    vsGoal!! < 0 -> tr("%s meglio", gapText(vsGoal))
                    else -> tr("mancano %s", gapText(vsGoal))
                }
            },
            e.vsPrevious?.let { tr("%s sulla stima precedente", signedSeconds(it)) }
        )
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                color = if (vsGoal != null && vsGoal <= 0) UnicumPrimary else UnicumDark.copy(alpha = .75f),
                style = UnicumType.Caption, fontWeight = FontWeight.Medium, lineHeight = 17.sp
            )
        }
        if (km > 5.0 && e.longestKm < km * 0.98) {
            Spacer(Modifier.height(4.dp))
            Text(
                tr("È il tuo potenziale: finora hai corso al massimo %s km di fila.", WorkoutAnalysis.formatKm(e.longestKm * 1000)),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        EstimateChart(km, history, real, goal)

        // In secondo piano: forchetta, FC necessaria, scenari e formula
        var open by rememberSaveable { mutableStateOf(false) }
        ExpandRow(open, tr("Scenari e come è calcolata"), { open = !open })
        if (open) {
            Spacer(Modifier.height(12.dp))
            val hrNeeded = goal?.let { RunProgress.hrForPace(e.beatsPerKm, it.paceSec) }
            if (km > 5.0 || hrNeeded != null) {
                Row(Modifier.fillMaxWidth()) {
                    if (km > 5.0) BigStat("${mmss(e.low)}–${mmss(e.high)}", tr("Forchetta %s km", kmLabel(km)), Modifier.weight(1f))
                    if (hrNeeded != null) BigStat("$hrNeeded bpm", tr("FC per %s/km", pace(goal.paceSec)), Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
            if (hrNeeded != null) {
                val margin = e.hardHr - hrNeeded
                Text(
                    if (margin >= 0) tr("Negli sforzi duri tieni %d bpm: %d bpm di margine sul ritmo dell'obiettivo.", e.hardHr, margin)
                    else tr("Negli sforzi duri tieni %d bpm: mancano %d bpm per il ritmo dell'obiettivo.", e.hardHr, -margin),
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note, lineHeight = 15.sp
                )
                Spacer(Modifier.height(12.dp))
            }
            ScenarioTable(RunProgress.scenarios(runs, km), km)
            Spacer(Modifier.height(10.dp))
            val last = history.last()
            Text(
                tr("Come è calcolata: la stima dei 5 km è la media di due stime. Dal battito: battiti per km (FC media × ritmo) delle uscite facili, mediana delle ultime %d (oggi %s), diviso la FC degli sforzi con RPE ≥ 7 degli ultimi 90 giorni (%d bpm su %d; nelle ripetute conta la FC dei tratti veloci) = %s.",
                        last.easyCount, thousands(e.beatsPerKm), e.hardHr, e.hardCount, mmss(last.hrSeconds5k)) + " " +
                    
                    
                    
                    (last.perfRun?.let { r ->
                        tr("Dalla prova reale: il miglior sforzo continuo degli ultimi 90 giorni (%s, %s km in %s) portato a 5 km = %s.",
                                                        dayLabel(r.date), WorkoutAnalysis.formatKm((r.km ?: 0.0) * 1000), mmss(r.seconds), mmss(last.perfSeconds5k!!))
                    } ?: tr("Nessuno sforzo continuo duro negli ultimi 90 giorni: conta solo il battito.")) + " " +
                    tr("Si scartano le uscite facili con RPE oltre 6, sotto i 2 km o con battiti per km fuori scala (±15%). I 7 e i 10 km vengono dai 5 con la formula di Riegel (esponente 1,08; forchetta 1,06–1,10): valgono se riesci a tenere il ritmo per tutta la distanza."),
                    
                    
                color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
    }
}

/** Passo delle etichette dei tempi: il più piccolo che lascia al massimo 6 righe. */
private fun timeStep(range: Int): Int = listOf(60, 120, 300, 600, 900, 1800).firstOrNull { range / it <= 6 } ?: 3600

/**
 * Stima dopo ogni corsa sulla distanza [km] (linea), corse vere su quella distanza (anelli), obiettivo
 * (tratteggio fino alla scadenza) e, con [projection], dove si arriva alla scadenza continuando così
 * (tratteggio dalla stima di oggi).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EstimateChart(
    km: Double,
    history: List<RunProgress.EstimatePoint>,
    real: List<Pair<RunProgress.Run, Int>>,
    goal: RunGoal?,
    projection: Int? = null
) {
    if (history.isEmpty()) return
    val points = remember(history, km) { history.map { RunProgress.atDistance(it.seconds5k, km) } }
    // Punto toccato: una stima (pallino) oppure una corsa vera (anello)
    var selected by remember(km) { mutableStateOf<Int?>(null) }
    var selectedReal by remember(km) { mutableStateOf<Int?>(null) }
    val today = DateUtils.today()
    val goalSec = goal?.targetSeconds
    val lastDate = history.last().date
    // La proiezione parte dalla stima di oggi e arriva alla scadenza
    val proj = if (goal != null && projection != null && goal.deadlineDate.isAfter(lastDate)) projection else null
    val all = points + real.map { it.second } + listOfNotNull(goalSec, proj)
    // Almeno 30 s di margine sopra e sotto: nessun punto sul bordo
    val margin = maxOf(30, (all.max() - all.min()) / 12)
    val rawLo = all.min() - margin
    val rawHi = all.max() + margin
    val yStep = timeStep(rawHi - rawLo)
    val lo = (rawLo / yStep) * yStep
    val hi = ((rawHi + yStep - 1) / yStep) * yStep
    val first = (history.map { it.date } + real.map { it.first.date }).min()
    val end = listOfNotNull(today, lastDate, goal?.deadlineDate?.takeIf { !it.isBefore(first) }).max()
    val x0 = first.minusDays(4).toEpochDay().toFloat()
    val x1 = end.plusDays(if (goal == null) 6 else 3).toEpochDay().toFloat()
    val leftDp = if (hi >= 3600) 48.dp else 40.dp
    val line = UnicumPrimary
    val ring = UnicumSecondary
    val muted = UnicumDark.copy(alpha = .45f)
    val grid = UnicumDark.copy(alpha = .08f)
    val surface = UnicumSurface
    val measurer = rememberTextMeasurer()
    val labelStyle = UnicumType.Micro.copy(color = muted)
    val sel = selected?.let { history.getOrNull(it) }
    val selReal = selectedReal?.let { real.getOrNull(it) }
    val kmL = kmLabel(km)

    Text(
        when {
            selReal != null -> selReal.let { (r, t) ->
                val rk = r.km ?: 0.0
                if (abs(rk - km) / km < 0.02) tr("%s: hai corso %s km in %s (%s/km)", dayLabel(r.date), kmL, mmss(r.seconds), pace(r.paceSec))
                else tr("%s: hai corso %s km di fila in %s, cioè %s km in %s (%s/km)", dayLabel(r.date), WorkoutAnalysis.formatKm(rk * 1000), mmss(r.seconds),
                                        kmL, mmss(t), pace((t / km).roundToInt()))
            }
            sel != null -> {
                val t = points[selected!!]
                tr("Dopo il %s: %s (%s/km)", dayLabel(sel.date), mmss(t), pace((t / km).roundToInt())) + " · " +
                    if (km > 5.0) tr("dai 5 km in %s", mmss(sel.seconds5k))
                    else sel.perfSeconds5k?.let { tr("battito %s, prova reale %s", mmss(sel.hrSeconds5k), mmss(it)) }
                        ?: tr("%s battiti/km ÷ %d bpm", thousands(sel.beatsPerKm), sel.hardHr)
            }
            real.isNotEmpty() -> tr("Tocca un punto per i dettagli. Gli anelli sono i %s km corsi davvero, per confrontarli con la stima.", kmL)
            else -> tr("Tocca un punto per i dettagli")
        },
        color = if (sel != null || selReal != null) (if (selReal != null) ring else line) else UnicumDark.copy(alpha = .55f),
        style = UnicumType.Caption, lineHeight = 17.sp,
        fontWeight = if (sel != null || selReal != null) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2
    )
    Spacer(Modifier.height(8.dp))
    Canvas(
        Modifier.fillMaxWidth().height(200.dp).pointerInput(history, real, km, x1) {
            detectTapGestures { p ->
                // Il segno più vicino al dito, pallino o anello (stessa geometria del disegno qui sotto)
                val left = leftDp.toPx()
                val top = 8.dp.toPx()
                val plotH = size.height - top - 22.dp.toPx()
                fun px(d: LocalDate) = left + (d.toEpochDay() - x0) / (x1 - x0) * (size.width - left)
                fun py(s: Int) = top + plotH - (s - lo).toFloat() / (hi - lo) * plotH
                fun dist(d: LocalDate, s: Int) = kotlin.math.hypot(px(d) - p.x, py(s) - p.y)
                val bestEst = history.indices.minByOrNull { dist(history[it].date, points[it]) }
                val bestReal = real.indices.minByOrNull { dist(real[it].first.date, real[it].second) }
                val dEst = bestEst?.let { dist(history[it].date, points[it]) } ?: Float.MAX_VALUE
                val dReal = bestReal?.let { dist(real[it].first.date, real[it].second) } ?: Float.MAX_VALUE
                if (dReal < dEst) { selectedReal = bestReal; selected = null } else { selected = bestEst; selectedReal = null }
            }
        }
    ) {
        // Margine a sinistra per i tempi, in basso per i mesi: le etichette non coprono i punti
        val left = leftDp.toPx()
        val top = 8.dp.toPx()
        val plotH = size.height - top - 22.dp.toPx()
        val plotW = size.width - left
        fun px(d: LocalDate) = left + (d.toEpochDay() - x0) / (x1 - x0) * plotW
        fun py(s: Int) = top + plotH - (s - lo).toFloat() / (hi - lo) * plotH
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))

        for (s in lo..hi step yStep) {
            drawLine(grid, Offset(left, py(s)), Offset(size.width, py(s)), 1.dp.toPx())
            val t = measurer.measure(mmss(s), labelStyle)
            drawText(t, topLeft = Offset(left - 6.dp.toPx() - t.size.width, py(s) - t.size.height / 2f))
        }
        // Mesi sull'asse orizzontale (uno ogni 2-3 se l'arco è lungo) e la linea di oggi
        val bottomY = top + plotH + 6.dp.toPx()
        val todayX = px(today).takeIf { !today.isBefore(first) && !today.isAfter(end) }
        val firstLabel = measurer.measure(dayLabel(first), labelStyle)
        val firstX = maxOf(left, px(first) - firstLabel.size.width / 2f)
        drawText(firstLabel, topLeft = Offset(firstX, bottomY))
        val firstEnd = firstX + firstLabel.size.width
        val months = ChronoUnit.MONTHS.between(first.withDayOfMonth(1), end.withDayOfMonth(1)).toInt()
        val monthStep = maxOf(1, (months + 6) / 7)
        var m = first.withDayOfMonth(1).plusMonths(1)
        var n = 0
        while (!m.isAfter(end)) {
            if (n % monthStep == 0) {
                val x = px(m)
                drawLine(grid, Offset(x, top), Offset(x, top + plotH), 1.dp.toPx())
                if ((todayX == null || abs(x - todayX) > 30.dp.toPx()) && x - 14.dp.toPx() > firstEnd) {
                    val t = measurer.measure(m.month.getDisplayName(java.time.format.TextStyle.SHORT, I18n.locale), labelStyle)
                    drawText(t, topLeft = Offset(x - t.size.width / 2f, bottomY))
                }
            }
            m = m.plusMonths(1)
            n++
        }
        if (todayX != null) {
            drawLine(muted, Offset(todayX, top), Offset(todayX, top + plotH), 1.dp.toPx(), pathEffect = dash)
            val t = measurer.measure(tr("oggi"), labelStyle)
            drawText(t, topLeft = Offset(todayX - t.size.width / 2f, bottomY))
        }
        // Obiettivo: tratteggio fino alla scadenza, con il valore accanto all'anello
        if (goal != null && goalSec != null) {
            val inRange = !goal.deadlineDate.isBefore(first)
            val gx = if (inRange) px(goal.deadlineDate) else size.width
            drawLine(muted, Offset(left, py(goalSec)), Offset(gx, py(goalSec)), 1.5.dp.toPx(), pathEffect = dash)
            if (inRange) {
                drawCircle(surface, 6.dp.toPx(), Offset(gx, py(goalSec)))
                drawCircle(muted, 6.dp.toPx(), Offset(gx, py(goalSec)), style = Stroke(2.dp.toPx()))
                val gt = measurer.measure(mmss(goalSec), labelStyle)
                val above = proj == null || proj >= goalSec
                val gy = if (above) py(goalSec) - 9.dp.toPx() - gt.size.height else py(goalSec) + 9.dp.toPx()
                drawText(gt, topLeft = Offset(minOf(gx - gt.size.width / 2f, size.width - gt.size.width), gy))
            }
        }
        // Continuando così: dalla stima di oggi alla scadenza
        // (curva: i miglioramenti rallentano, come nel calcolo di GoalProgress)
        if (proj != null && goal != null) {
            val cur = points.last()
            val total = ChronoUnit.DAYS.between(lastDate, goal.deadlineDate).toInt()
            val full = GoalProgress.effectiveDays(total)
            val curve = Path()
            for (d in 0..total) {
                val v = cur + (proj - cur) * (if (full > 0) GoalProgress.effectiveDays(d) / full else 1.0)
                val x = px(lastDate.plusDays(d.toLong()))
                val y = top + plotH - (v - lo).toFloat() / (hi - lo) * plotH
                if (d == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
            }
            drawPath(curve, line.copy(alpha = .7f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash))
            drawCircle(line.copy(alpha = .7f), 3.5.dp.toPx(), Offset(px(goal.deadlineDate), py(proj)))
        }

        real.forEachIndexed { i, (r, t) ->
            val c = Offset(px(r.date), py(t))
            if (i == selectedReal) drawCircle(ring.copy(alpha = .35f), 7.dp.toPx(), c)
            drawCircle(ring, 7.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
        if (history.size > 1) {
            val path = Path()
            history.forEachIndexed { i, h -> if (i == 0) path.moveTo(px(h.date), py(points[i])) else path.lineTo(px(h.date), py(points[i])) }
            drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        history.forEachIndexed { i, h ->
            val c = Offset(px(h.date), py(points[i]))
            val on = i == selected || (selected == null && selectedReal == null && i == history.lastIndex)
            drawCircle(surface, (if (on) 6.5f else 5f).dp.toPx(), c)
            drawCircle(line, (if (on) 5f else 3.5f).dp.toPx(), c)
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendDot(line, tr("Stima %s km", kmL))
        if (real.isNotEmpty()) LegendDot(ring, tr("Corsi davvero"), ring = true)
        if (goal != null) LegendDot(muted, tr("Obiettivo"), ring = true)
        if (proj != null) LegendDash(line.copy(alpha = .7f), tr("Se continui così"))
    }
}

@Composable
private fun LegendDot(color: Color, label: String, ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .then(if (ring) Modifier.border(2.dp, color, CircleShape) else Modifier.background(color))
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Micro)
    }
}

@Composable
private fun LegendDash(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(16.dp).height(10.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(label, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Micro)
    }
}

@Composable
private fun ScenarioTable(scenarios: List<RunProgress.Scenario>, km: Double) {
    if (scenarios.isEmpty()) return
    Text(tr("SE IN GARA LA FC FOSSE…"), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Note, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf(tr("Scenario") to 1.4f, tr("FC") to 1f, tr("Ritmo") to 1f, "${kmLabel(km)} km" to 1.2f).forEachIndexed { i, (h, w) ->
            Text(h.uppercase(), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Micro, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(w), textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
        }
    }
    scenarios.forEach { s ->
        val strong = s.name == "Centrale"
        val weight = if (strong) FontWeight.SemiBold else FontWeight.Normal
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(tr(s.name), color = UnicumDark, style = UnicumType.Caption, fontWeight = weight, modifier = Modifier.weight(1.4f))
            listOf("${s.hr}" to 1f, pace(s.pace) to 1f, mmss(s.seconds) to 1.2f).forEach { (v, w) ->
                Text(v, color = UnicumDark, style = UnicumType.Caption, fontWeight = weight, modifier = Modifier.weight(w), textAlign = TextAlign.End)
            }
        }
    }
}

// ------------------------------------------------------------------
// Confronto tra corse dello stesso tipo
// ------------------------------------------------------------------

private fun Metric.short(): String = when (this) {
    Metric.PACE -> tr("Ritmo")
    Metric.KM -> tr("Distanza")
    Metric.DURATION -> tr("Durata")
    Metric.HR -> tr("FC media")
    Metric.HR_MAX -> tr("FC max")
    Metric.BPK -> tr("Battiti/km")
    Metric.CADENCE -> tr("Cadenza")
    Metric.RPE -> "RPE"
    Metric.VO2 -> "VO₂ max"
}

private fun Metric.unit(): String = when (this) {
    Metric.PACE -> "/km"
    Metric.KM -> " km"
    Metric.HR, Metric.HR_MAX -> " bpm"
    Metric.CADENCE -> " spm"
    Metric.RPE -> "/10"
    else -> ""
}

private fun Metric.fmt(v: Double): String = when (this) {
    Metric.PACE -> pace(v.roundToInt())
    Metric.KM -> lfmt("%.2f", v)
    Metric.DURATION -> mmss(v.roundToInt())
    Metric.BPK -> thousands(v.roundToInt())
    Metric.VO2 -> lfmt("%.1f", v)
    Metric.RPE -> if (v % 1.0 == 0.0) v.toInt().toString() else lfmt("%.1f", v)
    else -> v.roundToInt().toString()
}

private fun Metric.fmtDelta(d: Double): String {
    val zero = when (this) {
        Metric.KM -> abs(d) < .005
        Metric.VO2, Metric.RPE -> abs(d) < .05
        else -> abs(d) < .5
    }
    if (zero) return "±0"
    val sign = if (d < 0) "−" else "+"
    return sign + when (this) {
        Metric.PACE -> "${abs(d).roundToInt()} s"
        else -> fmt(abs(d))
    }
}

/**
 * Corse dello stesso tipo a confronto, come nella dashboard: scegli il tipo, poi per ogni misura vedi due corse
 * (di solito l'ultima e la precedente, ma si possono scegliere a mano), la differenza, la media e la migliore
 * del tipo; sopra, il grafico di una misura corsa per corsa con la media tratteggiata.
 */
/** Ordine dei tipi di seduta: quelli di Notion (come sono ora), poi i predefiniti, infine "Senza tipo". */
internal fun runKindOrder(): List<String> =
    (TrainingPlan.kindNames + WorkoutFeedback.RUN_KINDS).distinct() + RunProgress.NO_KIND

@Composable
internal fun CompareCard(runs: List<RunProgress.Run>) {
    val order = runKindOrder()
    // "Tutte" per ultima: l'andamento di tutte le corse, qualunque tipo (senza il confronto a due)
    val kinds = runs.map { it.kind }.distinct().sortedBy { order.indexOf(it).let { i -> if (i < 0) 99 else i } } + ALL_KINDS
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    val current = kind?.takeIf { it in kinds } ?: kinds.firstOrNull { it == "Facile" } ?: kinds.first()
    val same = if (current == ALL_KINDS) runs else runs.filter { it.kind == current }
    var aId by rememberSaveable(current) { mutableStateOf<String?>(null) }
    var bId by rememberSaveable(current) { mutableStateOf<String?>(null) }
    var metric by rememberSaveable { mutableStateOf(Metric.PACE) }
    var selected by rememberSaveable(current, metric) { mutableStateOf<Float?>(null) }
    val a = same.firstOrNull { it.id == aId }
    val b = same.firstOrNull { it.id == bId }
    val rows = remember(same, a, b) { RunProgress.compare(same, current, a, b) }

    UnicumCard {
        CardHeader(Icons.AutoMirrored.Rounded.CompareArrows, tr("Corse a confronto"))
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(kinds.size) { i ->
                val k = kinds[i]
                KindChip(k, if (k == ALL_KINDS) runs.size else runs.count { it.kind == k }, k == current) { kind = k }
            }
        }
        if (current == RunProgress.NO_KIND) {
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Queste corse non hanno ancora il tipo: aprile e scegli il tipo di seduta nelle Sensazioni."),
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
        Spacer(Modifier.height(14.dp))

        // Grafico di una misura, corsa per corsa
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Metric.values().size) { i ->
                val m = Metric.values()[i]
                UnicumChip(m.short(), m == metric, { metric = m }, compact = true)
            }
        }
        Spacer(Modifier.height(10.dp))
        val points = same.mapNotNull { r -> metric.get(r)?.let { r to it } }
        if (points.size >= 2) {
            val mean = points.map { it.second }.average()
            SeriesChart(
                xs = points.indices.map { it.toFloat() },
                ys = points.map { it.second.toFloat() },
                color = runKindColor(current).takeIf { current != RunProgress.NO_KIND && current != ALL_KINDS } ?: UnicumPrimary,
                xLabel = { x -> dayLabel(points[x.roundToInt().coerceIn(points.indices)].first.date) },
                yLabel = { metric.fmt(it.toDouble()) + metric.unit() },
                summary = tr("%d corse · media %s (tratteggio)", points.size, metric.fmt(mean) + metric.unit()),
                invert = metric == Metric.PACE,
                selectedX = selected, onSelect = { selected = it },
                dots = true, reference = mean.toFloat(), height = 120.dp
            )
        } else {
            Text(
                if (points.isEmpty()) tr("Nessun dato di questa misura per il tipo scelto.") else tr("Una sola corsa con questo dato: il grafico compare dalla seconda."),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
            )
        }
        // In secondo piano: due corse dello stesso tipo, scelte a mano, misura per misura
        if (current == ALL_KINDS || same.size < 2) return@UnicumCard
        var open by rememberSaveable { mutableStateOf(false) }
        ExpandRow(open, tr("Confronta due corse") + " · " + kindText(current), { open = !open })
        if (!open) return@UnicumCard
        Spacer(Modifier.height(12.dp))
        val defaultA = rows.first().a
        val defaultB = rows.first().b
        Row(verticalAlignment = Alignment.CenterVertically) {
            RunPicker(a ?: defaultA, same) { aId = it.id }
            Spacer(Modifier.width(6.dp))
            Text(tr("con"), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
            Spacer(Modifier.width(6.dp))
            RunPicker(b ?: defaultB, same) { bId = it.id }
        }
        Spacer(Modifier.height(10.dp))
        CompareTable(rows, a ?: defaultA, b ?: defaultB)
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Differenza = prima corsa − seconda; verde se è un miglioramento. Nelle uscite facili il ritmo non ha un \"meglio\"; nelle ripetute nemmeno distanza, durata e battiti per km."),
                
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp
        )
    }
}

/** Voce dei tipi di seduta che mostra tutte le corse insieme. */
private const val ALL_KINDS = "Tutte"

/** Nome di un tipo da mostrare: "Tutte" e "Senza tipo" tradotti, i tipi di seduta come li dà il programma. */
private fun kindText(kind: String): String = when (kind) {
    ALL_KINDS -> tr("Tutte")
    RunProgress.NO_KIND -> tr("Senza tipo")
    else -> io.github.peol13.unicum.data.runKindLabel(kind)
}

@Composable
private fun KindChip(kind: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val dot = when {
        kind != ALL_KINDS -> runKindColor(kind)
        selected -> io.github.peol13.unicum.ui.theme.UnicumOnPrimary
        else -> UnicumPrimary
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) UnicumPrimary else UnicumSurface)
            .border(1.dp, if (selected) UnicumPrimary else UnicumAccent.copy(alpha = .6f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(kindText(kind), color = if (selected) io.github.peol13.unicum.ui.theme.UnicumOnPrimary else UnicumDark, style = UnicumType.Caption,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        Spacer(Modifier.width(6.dp))
        Text("$count", color = (if (selected) io.github.peol13.unicum.ui.theme.UnicumOnPrimary else UnicumDark).copy(alpha = .6f), style = UnicumType.Micro)
    }
}

/** Pillola con la data della corsa scelta; toccandola si sceglie un'altra corsa dello stesso tipo. */
@Composable
private fun RunPicker(current: RunProgress.Run?, options: List<RunProgress.Run>, onPick: (RunProgress.Run) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(UnicumAccent.copy(alpha = .25f))
                .clickable(enabled = options.size > 1) { open = true }
                .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(current?.let { dayLabel(it.date) } ?: "–", color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ExpandMore, tr("Scegli la corsa"), tint = UnicumDark.copy(alpha = .6f), modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = UnicumSurface) {
            options.reversed().forEach { r ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${DateUtils.shortLabel(r.date)} · ${r.km?.let { WorkoutAnalysis.formatKm(it * 1000) + " km" } ?: "–"} · ${pace(r.paceSec)}",
                            color = UnicumDark, style = UnicumType.Caption,
                            fontWeight = if (r == current) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = { open = false; onPick(r) }
                )
            }
        }
    }
}

@Composable
private fun CompareTable(rows: List<RunProgress.Comparison>, a: RunProgress.Run?, b: RunProgress.Run?) {
    val good = UnicumPrimary
    val bad = Color(0xFFC9A66B)
    val headColor = UnicumDark.copy(alpha = .45f)
    Row(Modifier.fillMaxWidth()) {
        listOf(
            tr("Misura") to 1.5f, (a?.let { dayLabel(it.date) } ?: tr("Prima")) to 1f, (b?.let { dayLabel(it.date) } ?: tr("Seconda")) to 1f,
            tr("Diff.") to 1f, tr("Media") to 1f, tr("Migliore") to 1.1f
        ).forEachIndexed { i, (t, w) ->
            Text(t.uppercase(), color = headColor, style = UnicumType.Micro, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.weight(w), textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
        }
    }
    Spacer(Modifier.height(2.dp))
    rows.forEach { c ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
            Text(c.metric.short(), color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.5f))
            Cell(c.aValue?.let { c.metric.fmt(it) }, Modifier.weight(1f), strong = true)
            Cell(c.bValue?.let { c.metric.fmt(it) }, Modifier.weight(1f))
            Cell(
                c.delta?.let { c.metric.fmtDelta(it) }, Modifier.weight(1f),
                color = when (c.deltaGood) { true -> good; false -> bad; null -> UnicumDark.copy(alpha = .55f) }
            )
            Cell(c.mean?.let { c.metric.fmt(it) }, Modifier.weight(1f), color = UnicumDark.copy(alpha = .55f))
            Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                Text(c.bestValue?.let { c.metric.fmt(it) } ?: "–", color = UnicumDark.copy(alpha = .75f), style = UnicumType.Caption, maxLines = 1)
                c.best?.let { Text(dayLabel(it.date), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Micro, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Cell(text: String?, modifier: Modifier, strong: Boolean = false, color: Color = UnicumDark) {
    Text(
        text ?: "–", color = color, style = UnicumType.Caption, maxLines = 1,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier, textAlign = TextAlign.End
    )
}

// ------------------------------------------------------------------
// Km per settimana divisi per tipo
// ------------------------------------------------------------------

@Composable
internal fun KindVolumeCard(runs: List<RunProgress.Run>) {
    val weeks = remember(runs) { RunProgress.weeklyByKind(runs, 12, DateUtils.today()) }
    val order = runKindOrder()
    val present = (order + weeks.flatMap { it.km.keys }).distinct().filter { k -> weeks.any { (it.km[k] ?: 0.0) > 0 } }
    val colors = present.associateWith { runKindColor(it) }
    val active = weeks.filter { it.count > 0 }
    var selected by remember { mutableStateOf<Int?>(null) }
    val track = UnicumTrack
    val highlight = UnicumDark.copy(alpha = .06f)
    val grow = animatedFraction(1f, durationMillis = 700)
    UnicumCard {
        CardHeader(Icons.AutoMirrored.Rounded.TrendingUp, tr("Km per settimana"))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            BigStat("${weeks.sumOf { it.total }.roundToInt()} km", tr("12 settimane"), Modifier.weight(1f))
            BigStat(weeks.sumOf { it.count }.toString(), tr("Uscite"), Modifier.weight(1f))
            BigStat(
                if (active.isEmpty()) "–" else lfmt("%.1f km", weeks.sumOf { it.total } / weeks.size),
                tr("Media a settimana"), Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        val sel = selected?.let { weeks.getOrNull(it) }
        Text(
            sel?.let { w ->
                tr("Dal %s: %s km · %d min · %d uscite", dayLabel(w.monday), lfmt("%.2f", w.total), w.seconds / 60, w.count) +
                    w.km.entries.sortedBy { order.indexOf(it.key) }.joinToString("") { " · ${kindText(it.key)} ${lfmt("%.1f", it.value)}" }
            } ?: tr("Tocca una colonna per il dettaglio"),
            color = if (sel != null) UnicumDark else UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 17.sp, maxLines = 3
        )
        Spacer(Modifier.height(8.dp))
        val top = (weeks.maxOf { it.total }).coerceAtLeast(2.0)
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).pointerInput(weeks) {
                detectTapGestures { p -> selected = (p.x / (size.width / weeks.size)).toInt().coerceIn(weeks.indices) }
            }
        ) {
            val slot = size.width / weeks.size
            val bw = minOf(slot * .55f, 22.dp.toPx())
            weeks.forEachIndexed { i, w ->
                val x = slot * i + (slot - bw) / 2
                if (i == selected) drawRect(highlight, Offset(slot * i, 0f), Size(slot, size.height))
                if (w.count == 0) {
                    drawRoundRect(track, Offset(x, size.height - 4.dp.toPx()), Size(bw, 4.dp.toPx()), CornerRadius(3.dp.toPx()))
                    return@forEachIndexed
                }
                var base = size.height
                present.forEach { k ->
                    val km = w.km[k] ?: 0.0
                    if (km <= 0) return@forEach
                    val h = (km / top * size.height * grow).toFloat()
                    drawRect(colors.getValue(k), Offset(x, base - h), Size(bw, h))
                    base -= h
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            weeks.forEach { w ->
                Text(w.monday.dayOfMonth.toString(), color = UnicumDark.copy(alpha = .5f), style = UnicumType.Micro,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(present.size) { i -> LegendDot(colors.getValue(present[i]), present[i]) }
        }
    }
}
