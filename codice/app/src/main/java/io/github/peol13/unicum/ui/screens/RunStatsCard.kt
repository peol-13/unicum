package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.lfmt

import io.github.peol13.unicum.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.RunProgress
import io.github.peol13.unicum.data.RunStats
import io.github.peol13.unicum.data.RunStats.Period
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumSegmented
import io.github.peol13.unicum.ui.components.WeekBar
import io.github.peol13.unicum.ui.components.WeekChart
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun km1(v: Double) = lfmt("%.1f", v)
private fun monthName(d: LocalDate, short: Boolean = false) =
    d.month.getDisplayName(if (short) TextStyle.SHORT_STANDALONE else TextStyle.FULL_STANDALONE, I18n.locale)
        .replace(".", "").replaceFirstChar { it.uppercase() }
private fun dayMonth(d: LocalDate) = DateUtils.shortLabel(d).substringAfter(' ')

/**
 * Statistiche delle corse fatte: Settimana / Mese / Sempre. Totali e medie del periodo, confronto con
 * il periodo precedente (o, per Sempre, record e inizio contro adesso) e colonne dei km.
 */
@Composable
fun RunStatsCard(runs: List<RunProgress.Run>, onOpenRun: (String) -> Unit) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    val today = DateUtils.today()
    val window = remember(runs, period) { RunStats.current(period, today, runs) }
    val now = remember(runs, period) { RunStats.totals(RunStats.inWindow(runs, window)) }
    val prevWindow = RunStats.previous(period, today)
    val before = remember(runs, period) { prevWindow?.let { RunStats.totals(RunStats.inWindow(runs, it)) } }
    val buckets = remember(runs, period) { RunStats.buckets(runs, period, today) }

    UnicumCard {
        UnicumSegmented(Period.entries.map { it.label }, period.ordinal, { period = Period.entries[it] }, height = 36.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            when (period) {
                Period.WEEK -> tr("Questa settimana · dal %s", dayMonth(window.start))
                Period.MONTH -> "${monthName(window.start)} ${window.start.year}"
                Period.ALL -> tr("Dal %s", "${dayMonth(window.start)} ${window.start.year}")
            },
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            BigStat("${WorkoutAnalysis.formatKm(now.km * 1000)} km", tr("Distanza"), Modifier.weight(1f))
            BigStat(now.count.toString(), tr("Corse"), Modifier.weight(1f))
            BigStat(WorkoutAnalysis.formatDuration(now.seconds), tr("Tempo"), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            MiniStat(now.paceSec?.let { "${WorkoutAnalysis.formatPace(it)}/km" }, tr("ritmo medio"), Modifier.weight(1f))
            MiniStat(now.avgHr?.let { "$it bpm" }, tr("FC media"), Modifier.weight(1f))
            MiniStat(now.beatsPerKm?.let { thousandsDot(it) }, tr("battiti/km"), Modifier.weight(1f))
            MiniStat(now.avgCadence?.let { "$it spm" }, tr("cadenza"), Modifier.weight(1f))
        }

        if (before != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (period == Period.WEEK) tr("Rispetto alla settimana scorsa") else tr("Rispetto a %s", monthName(prevWindow!!.start).lowercase(I18n.locale)),
                color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            CompareHeader(tr("Prima"), tr("Ora"))
            CompareLine(tr("Distanza"), "${km1(before.km)} km", "${km1(now.km)} km", deltaKm(now.km - before.km, before.count > 0))
            CompareLine(tr("Corse"), before.count.toString(), now.count.toString(), deltaInt(now.count - before.count, before.count > 0))
            CompareLine(
                tr("Tempo"), WorkoutAnalysis.formatDuration(before.seconds), WorkoutAnalysis.formatDuration(now.seconds),
                deltaInt((now.seconds - before.seconds) / 60, before.count > 0)?.let { Delta(it.text + "′", it.good) }
            )
            CompareLine(
                tr("Ritmo"), before.paceSec?.let(WorkoutAnalysis::formatPace), now.paceSec?.let(WorkoutAnalysis::formatPace),
                deltaOf(before.paceSec, now.paceSec) { d -> "${if (d < 0) "−" else "+"}${abs(d)}″" to (d < 0) }
            )
            CompareLine(
                tr("FC media"), before.avgHr?.toString(), now.avgHr?.toString(),
                deltaOf(before.avgHr, now.avgHr) { d -> "${if (d < 0) "−" else "+"}${abs(d)}" to null }
            )
            CompareLine(
                tr("Battiti/km"), before.beatsPerKm?.let(::thousandsDot), now.beatsPerKm?.let(::thousandsDot),
                deltaOf(before.beatsPerKm, now.beatsPerKm) { d -> "${if (d < 0) "−" else "+"}${abs(d)}" to (d < 0) }
            )
            Text(
                tr("Il periodo in corso non è finito: ritmo e battiti si confrontano già, i km si riempiono man mano."),
                color = UnicumDark.copy(alpha = .45f), style = UnicumType.Note, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            AllTimeRecords(runs, now, window.start, today, onOpenRun)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            when (period) {
                Period.WEEK -> tr("Km nelle ultime 12 settimane (sotto, il lunedì)")
                Period.MONTH -> tr("Km negli ultimi 6 mesi")
                Period.ALL -> tr("Km mese per mese")
            },
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
        )
        Spacer(Modifier.height(8.dp))
        WeekChart(
            buckets.map { b ->
                WeekBar(
                    label = if (period == Period.WEEK) b.start.dayOfMonth.toString() else monthName(b.start, short = true).take(3),
                    value = b.totals.km.toFloat().takeIf { b.totals.count > 0 },
                    valueText = if (b.totals.count > 0) b.totals.km.roundToInt().toString() else "",
                    color = UnicumPrimary,
                    highlighted = b === buckets.last()
                )
            },
            height = 90.dp
        )
    }
}

/** Sempre: record e confronto tra le prime e le ultime corse. */
@Composable
private fun AllTimeRecords(
    runs: List<RunProgress.Run>, all: RunStats.Totals, start: LocalDate, today: LocalDate, onOpenRun: (String) -> Unit
) {
    if (runs.isEmpty()) return
    val bestWeek = remember(runs) { RunStats.bestWeek(runs) }
    val bestMonth = remember(runs) { RunStats.bestMonth(runs) }
    val best5k = remember(runs) { RunProgress.realAt(runs, 5.0).minByOrNull { it.second } }
    val progress = remember(runs) { RunStats.firstVsLast(runs) }

    Spacer(Modifier.height(16.dp))
    Text(tr("Record"), color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    all.longest?.let { r ->
        RecordLine(tr("Corsa più lunga"), "${km1(r.km!!)} km", dayMonth(r.date)) { onOpenRun(r.id) }
    }
    all.fastest?.let { r ->
        RecordLine(tr("Ritmo migliore (≥ 2 km)"), "${WorkoutAnalysis.formatPace(r.paceSec)}/km", dayMonth(r.date)) { onOpenRun(r.id) }
    }
    best5k?.let { (r, s) ->
        RecordLine(tr("5 km più veloce"), WorkoutAnalysis.formatDuration(s), dayMonth(r.date)) { onOpenRun(r.id) }
    }
    bestWeek?.let { RecordLine(tr("Settimana con più km"), "${km1(it.totals.km)} km", tr("dal %s", dayMonth(it.start)), null) }
    bestMonth?.let { RecordLine(tr("Mese con più km"), "${km1(it.totals.km)} km", monthName(it.start).lowercase(I18n.locale), null) }
    RecordLine(tr("Media a settimana"), "${km1(RunStats.kmPerWeek(runs, start, today))} km", null, null)

    progress?.let { (first, last) ->
        Spacer(Modifier.height(16.dp))
        Text(tr("Le prime 4 corse e le ultime 4"), color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        CompareHeader(tr("Inizio"), tr("Ora"))
        CompareLine(tr("Distanza media"), "${km1(first.km / first.count)} km", "${km1(last.km / last.count)} km",
            deltaKm(last.km / last.count - first.km / first.count, true))
        CompareLine(
            tr("Ritmo"), first.paceSec?.let(WorkoutAnalysis::formatPace), last.paceSec?.let(WorkoutAnalysis::formatPace),
            deltaOf(first.paceSec, last.paceSec) { d -> "${if (d < 0) "−" else "+"}${abs(d)}″" to (d < 0) }
        )
        CompareLine(
            tr("FC media"), first.avgHr?.toString(), last.avgHr?.toString(),
            deltaOf(first.avgHr, last.avgHr) { d -> "${if (d < 0) "−" else "+"}${abs(d)}" to null }
        )
        CompareLine(
            tr("Battiti/km"), first.beatsPerKm?.let(::thousandsDot), last.beatsPerKm?.let(::thousandsDot),
            deltaOf(first.beatsPerKm, last.beatsPerKm) { d -> "${if (d < 0) "−" else "+"}${abs(d)}" to (d < 0) }
        )
    }
}

private fun thousandsDot(v: Int) = lfmt("%,d", v)

/** Differenza con il colore: true = meglio (teal), false = peggio (sabbia), null = neutra. */
private class Delta(val text: String, val good: Boolean?)

private fun deltaOf(a: Int?, b: Int?, fmt: (Int) -> Pair<String, Boolean?>): Delta? {
    if (a == null || b == null) return null
    val d = b - a
    if (d == 0) return Delta("=", null)
    val (t, g) = fmt(d)
    return Delta(t, g)
}

private fun deltaKm(d: Double, comparable: Boolean): Delta? = when {
    !comparable -> null
    abs(d) < 0.05 -> Delta("=", null)
    else -> Delta("${if (d > 0) "+" else "−"}${km1(abs(d))}", d > 0)
}

private fun deltaInt(d: Int, comparable: Boolean): Delta? = when {
    !comparable -> null
    d == 0 -> Delta("=", null)
    else -> Delta("${if (d > 0) "+" else "−"}${abs(d)}", d > 0)
}

@Composable
private fun CompareHeader(a: String, b: String) {
    val c = UnicumDark.copy(alpha = .45f)
    Row(Modifier.fillMaxWidth()) {
        Text("", modifier = Modifier.weight(1.4f))
        listOf(a, b, tr("Diff.")).forEach {
            Text(it.uppercase(), color = c, style = UnicumType.Micro, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        }
    }
}

@Composable
private fun CompareLine(label: String, before: String?, now: String?, delta: Delta?) {
    val good = UnicumPrimary
    val bad = Color(0xFFC9A66B)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.4f), maxLines = 1)
        Text(before ?: "–", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        Text(now ?: "–", color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        Text(
            delta?.text ?: "–",
            color = when (delta?.good) { true -> good; false -> bad; null -> UnicumDark.copy(alpha = .5f) },
            style = UnicumType.Caption, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1
        )
    }
}

@Composable
private fun RecordLine(label: String, value: String, note: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = UnicumDark.copy(alpha = .75f), style = UnicumType.Caption, modifier = Modifier.weight(1f), maxLines = 1)
        note?.let {
            Text(it, color = if (onClick != null) UnicumPrimary else UnicumDark.copy(alpha = .5f), style = UnicumType.Micro, maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        Text(value, color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun MiniStat(value: String?, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value ?: "–", color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = UnicumDark.copy(alpha = .5f), style = UnicumType.Micro, maxLines = 1)
    }
}
