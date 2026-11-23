package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.tk

import io.github.peol13.unicum.i18n.tr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.BodyTrend
import io.github.peol13.unicum.data.DateUtils
import androidx.compose.ui.graphics.PathEffect
import io.github.peol13.unicum.data.MetricType
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.HealthEmptyState
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSecondary
import io.github.peol13.unicum.ui.theme.UnicumTrack
import androidx.compose.ui.graphics.Color
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import io.github.peol13.unicum.ui.theme.UnicumType
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

/** Periodi del grafico (giorni) quando la card è espansa. */
private val RANGES get() = listOf(30 to tr("30 giorni"), 90 to tr("3 mesi"), 365 to tr("1 anno"))

private enum class BodyMetric(private val labelIt: String, private val isWeight: Boolean) {
    PESO(tk("Peso"), true),
    MASSA_GRASSA(tk("Massa grassa"), true),
    GRASSO(tk("Grasso corporeo"), false);

    val label: String get() = tr(labelIt)
    /** kg o lb (scelta in Profilo → Aspetto), % per il grasso. */
    val unit: String get() = if (isWeight) io.github.peol13.unicum.data.Units.weightUnit() else "%"
    /** Dal valore salvato (kg o %) a quello mostrato. */
    fun shown(v: Double): Double = if (isWeight) io.github.peol13.unicum.data.Units.weightValue(v) else v
}

/**
 * Peso e grasso corporeo (da Health Connect). Nella Home ([onDetails] presente): ultimo valore in grande e sotto
 * minimo, massimo e media degli ultimi 30 giorni, con il tasto "Dettagli". Nella pagina di dettaglio
 * ([onDetails] nullo): in più il grafico toccabile con periodo a scelta (30 giorni, 3 mesi, 1 anno).
 */
@Composable
internal fun BodyCompositionCard(onDetails: (() -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    var metric by remember { mutableStateOf(BodyMetric.PESO) }
    val expanded = onDetails == null
    var range by remember { mutableStateOf(RANGES.first().first) }
    val days = if (expanded) range else 30

    // Si ricarica dopo ogni sincronizzazione
    val series by produceState(
        Pair(emptyMap<String, Double>(), emptyMap<String, Double>()),
        UnicumAppState.health, UserSettings.lastSyncMillis, days
    ) {
        value = UnicumRepository.loadMetricSeries(context, MetricType.WEIGHT_KG, days) to
            UnicumRepository.loadMetricSeries(context, MetricType.BODY_FAT_PCT, days)
    }
    val (weights, fats) = series

    // Deficit di ogni giorno del periodo, per il peso previsto (solo nel dettaglio del peso). Oggi è escluso:
    // la giornata non è finita e il suo deficit è parziale.
    val deficits by produceState(emptyMap<LocalDate, Int?>(), expanded, days, UnicumAppState.health, UnicumAppState.dayData.meals.size) {
        value = if (!expanded) emptyMap() else UnicumRepository.loadDays(context, DateUtils.lastDays(days).map(DateUtils::parse))
            .associate { it.date to if (it.isToday) null else it.deficitKcal }
    }

    // data -> valore della grandezza scelta; la massa grassa si ricava da peso e percentuale dello stesso giorno
    val raw: Map<String, Double> = when (metric) {
        BodyMetric.PESO -> weights
        BodyMetric.GRASSO -> fats
        BodyMetric.MASSA_GRASSA -> weights.mapNotNull { (d, w) -> fats[d]?.let { d to w * it / 100.0 } }.toMap()
    }
    // Valori nell'unità scelta (kg o lb); la previsione dal deficit si calcola in kg e poi si converte
    val values: Map<String, Double> = raw.mapValues { metric.shown(it.value) }

    UnicumCard(onClick = onDetails) {
        CardHeader(
            Icons.Rounded.MonitorWeight, tr("Peso e composizione"),
            action = if (onDetails != null) tr("Dettagli") else null, onAction = { onDetails?.invoke() }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyMetric.entries.forEach { m ->
                UnicumChip(m.label, metric == m, { metric = m })
            }
        }
        Spacer(Modifier.height(14.dp))

        if (values.isEmpty()) {
            HealthEmptyState(
                tr("Nessuna misura"),
                tr("Pesati con una bilancia che scrive in Health Connect (anche tramite la sua app) e concedi i permessi di peso e grasso corporeo.")
            )
        } else {
            val all = values.values
            val sorted = values.entries.sortedBy { it.key }
            val latest = sorted.last()
            val previous = sorted.getOrNull(sorted.size - 2)
            val day = DateUtils.parse(latest.key)
            Text(
                tr("Ultima misura") + " · " + day.format(DAY_MONTH),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmt1(latest.value), color = UnicumDark, style = UnicumType.Hero, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(metric.unit, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Body, modifier = Modifier.padding(bottom = 7.dp))
                if (previous != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        diffText(latest.value - previous.value),
                        color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
            }
            Text(
                tr("Min %s  ·  Max %s  ·  Media %s", fmt1(all.min()), fmt1(all.max()), fmt1(all.average())) + " ${metric.unit}" +
                    "  (${RANGES.first { it.first == days }.second})",
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption
            )
            // Tendenza (media mobile di 7 giorni): dice se stai davvero scendendo, al netto dell'acqua
            BodyTrend.trendChange(values.mapKeys { DateUtils.parse(it.key) }, trendWindow(days))?.let { change ->
                Text(
                    tr("Tendenza: %s in %s", "${signed(change)} ${metric.unit}", RANGES.first { it.first == days }.second),
                    color = UnicumPrimary, style = UnicumType.Caption, fontWeight = FontWeight.Medium
                )
            }
            if (expanded) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RANGES.forEach { (d, label) -> UnicumChip(label, range == d, { range = d }) }
                }
                Spacer(Modifier.height(14.dp))
                val projection = if (metric == BodyMetric.PESO) projectionFrom(raw, deficits, trendWindow(days))?.inUnits() else null
                LineChart(values, metric.unit, days, projection?.line)
                projection?.let { ProjectionSummary(it) }
            }
        }
    }
}

private fun fmt1(v: Double): String = String.format(I18n.locale, "%.1f", v)

/** Peso previsto dal deficit accanto alla tendenza reale, dalla prima all'ultima misura del periodo. */
private class Projection(
    val line: Map<String, Double>,
    val predictedChange: Double,
    val realChange: Double,
    val daysWithMeals: Int,
    val days: Int
) {
    /** La stessa previsione nell'unità scelta (kg o lb). */
    fun inUnits(): Projection {
        fun c(v: Double) = io.github.peol13.unicum.data.Units.weightValue(v)
        return Projection(line.mapValues { c(it.value) }, c(predictedChange), c(realChange), daysWithMeals, days)
    }
}

/**
 * Il calo previsto parte dalla tendenza alla prima misura e scende di deficit / 7.700 kg al giorno;
 * null se mancano misure o giorni con i pasti registrati.
 */
private fun projectionFrom(values: Map<String, Double>, deficits: Map<LocalDate, Int?>, window: Int): Projection? {
    val byDay = values.mapKeys { DateUtils.parse(it.key) }
    val trend = BodyTrend.movingAverage(byDay, window)
    if (trend.size < 2) return null
    val first = trend.keys.first()
    val last = trend.keys.last()
    val known = deficits.filterKeys { !it.isBefore(first) && it.isBefore(last) }.values.count { it != null }
    if (known == 0) return null
    val line = BodyTrend.projected(trend.getValue(first), first, DateUtils.today(), deficits)
    return Projection(
        line = line.mapKeys { it.key.toString() },
        predictedChange = line.getValue(last) - trend.getValue(first),
        realChange = trend.getValue(last) - trend.getValue(first),
        daysWithMeals = known,
        days = (last.toEpochDay() - first.toEpochDay()).toInt()
    )
}

@Composable
private fun ProjectionSummary(p: Projection) {
    Spacer(Modifier.height(12.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(UnicumAccent.copy(alpha = .2f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(tr("Deficit teorico e peso reale"), color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
        Text(
            tr("Previsto dal deficit: %s · reale (tendenza): %s in %d giorni.",
                signed(p.predictedChange) + " " + io.github.peol13.unicum.data.Units.weightUnit(),
                signed(p.realChange) + " " + io.github.peol13.unicum.data.Units.weightUnit(), p.days),
                
            color = UnicumDark.copy(alpha = .75f), style = UnicumType.Caption, lineHeight = 17.sp
        )
        Text(
            if (p.daysWithMeals * 2 < p.days)
                tr("Pasti registrati solo in %d giorni su %d: nei giorni mancanti la previsione resta ferma, quindi il confronto è parziale.", p.daysWithMeals, p.days)
            else tr("Calcolato su %d giorni con i pasti registrati (7.700 kcal ≈ 1 kg). Le differenze vengono da acqua, stime del consumo e pasti non registrati.", p.daysWithMeals),
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp
        )
    }
}

/** "+0,4" oppure "−0,2". */
private fun signed(diff: Double): String = "${if (diff >= 0) "+" else "−"}${fmt1(abs(diff))}"

/** "+0,4 vs precedente" oppure "−0,2 vs precedente". */
private fun diffText(diff: Double): String = tr("%s vs precedente", signed(diff))

private val DAY_MONTH get() = DateTimeFormatter.ofPattern(tr("d MMM"), I18n.locale)
private val WEEKDAY_DAY_MONTH get() = DateTimeFormatter.ofPattern(tr("EEE d MMM"), I18n.locale)
private val MONTH_YEAR get() = DateTimeFormatter.ofPattern("MMM ''yy", I18n.locale)

/**
 * Grafico del periodo: le misure sono punti tenui, la linea è la tendenza (media mobile di 7 giorni).
 * Toccando o trascinando il dito si sceglie la misura più vicina: sopra il grafico compaiono data,
 * valore e differenza dalla misura precedente.
 */
@Composable
private fun LineChart(values: Map<String, Double>, unit: String, days: Int, projection: Map<String, Double>? = null) {
    val dayList = DateUtils.lastDays(days)
    val points = dayList.mapIndexedNotNull { i, d -> values[d]?.let { i to it } }
    // Su periodi lunghi la media è più larga: con 7 giorni e misure rade la "tendenza" coincideva con le misure
    val window = trendWindow(days)
    val trendByDay = BodyTrend.movingAverage(values.mapKeys { DateUtils.parse(it.key) }, window)
    val trend = dayList.mapIndexedNotNull { i, d -> trendByDay[DateUtils.parse(d)]?.let { i to it } }
    val projected = projection?.let { p -> dayList.mapIndexedNotNull { i, d -> p[d]?.let { i to it } } }.orEmpty()
    // L'asse segue le misure; la previsione può allargarlo al massimo di metà dell'intervallo delle misure
    // (su un anno accumulava tanto deficit da schiacciare le misure in una striscia), il resto si taglia
    val dataMin = points.minOf { it.second }
    val dataMax = points.maxOf { it.second }
    val dataSpan = (dataMax - dataMin).coerceAtLeast(1.0)
    val rawMin = minOf(dataMin, (projected.minOfOrNull { it.second } ?: dataMin).coerceAtLeast(dataMin - dataSpan / 2))
    val rawMax = maxOf(dataMax, (projected.maxOfOrNull { it.second } ?: dataMax).coerceAtMost(dataMax + dataSpan / 2))
    // Margine attorno ai dati e estremi su valori tondi, così l'asse è leggibile
    // Quattro righe a valori tondi (passo 0,5 / 1 / 2 / 2,5 / 5 / 10): l'asse si legge a colpo d'occhio
    val pad = ((rawMax - rawMin) * 0.15).coerceAtLeast(0.3)
    val step = listOf(0.5, 1.0, 2.0, 2.5, 5.0, 10.0, 20.0).firstOrNull { s ->
        floor((rawMin - pad) / s) * s + 3 * s >= rawMax + pad
    } ?: 50.0
    val lo = floor((rawMin - pad) / step) * step
    val hi = lo + 3 * step
    val span = hi - lo

    val grid = UnicumTrack
    val line = UnicumPrimary
    val dot = UnicumSecondary
    // Previsione in un colore diverso dalla tendenza (prima erano due teal quasi uguali)
    val forecast = UnicumDark.copy(alpha = .55f)
    val surface = UnicumSurface
    val cursor = UnicumDark.copy(alpha = .35f)

    var widthPx by remember { mutableIntStateOf(0) }
    // Indice in [points] della misura scelta (null = nessuna)
    var selected by remember(values) { mutableStateOf<Int?>(null) }

    fun select(x: Float) {
        if (widthPx <= 0 || points.isEmpty()) return
        val day = x / widthPx * (days - 1)
        selected = points.indices.minByOrNull { abs(points[it].first - day) }
    }

    // Dettaglio della misura scelta
    val sel = selected?.let { points.getOrNull(it) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (sel != null) UnicumAccent.copy(alpha = .25f) else Color.Transparent)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (sel == null) {
            Text(tr("Tocca il grafico per leggere ogni misura"), color = UnicumDark.copy(alpha = .5f), style = UnicumType.Caption)
        } else {
            val d = DateUtils.parse(dayList[sel.first])
            val prev = selected?.let { points.getOrNull(it - 1) }
            Text(
                d.format(WEEKDAY_DAY_MONTH),
                color = UnicumDark.copy(alpha = .7f), style = UnicumType.Detail
            )
            Spacer(Modifier.width(10.dp))
            Text("${fmt1(sel.second)} $unit", color = UnicumPrimary, style = UnicumType.Card, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (prev != null) {
                Text(diffText(sel.second - prev.second), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    Row(Modifier.fillMaxWidth().height(150.dp)) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .onSizeChanged { widthPx = it.width }
                .pointerInput(values) { detectTapGestures(onPress = { select(it.x) }) }
                .pointerInput(values) {
                    detectHorizontalDragGestures(
                        onDragStart = { select(it.x) },
                        onHorizontalDrag = { change, _ -> select(change.position.x) }
                    )
                }
        ) {
            for (g in 0..3) {
                val y = size.height * g / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            fun x(i: Int) = size.width * i / (days - 1).toFloat()
            fun y(v: Double) = (size.height * (1 - (v - lo) / span)).toFloat()
            sel?.let { drawLine(cursor, Offset(x(it.first), 0f), Offset(x(it.first), size.height), strokeWidth = 1.dp.toPx()) }
            clipRect {
                points.forEach { (i, v) ->
                    drawCircle(dot.copy(alpha = if (days > 60) .6f else .45f), radius = (if (days > 60) 3.0 else 4.5).dp.toPx(), center = Offset(x(i), y(v)))
                }
                // Peso previsto dal deficit, tratteggiato: un percorso unico, così il tratteggio continua anche quando
                // un giorno è largo pochi pixel (disegnato a pezzi ripartiva da capo e sembrava una linea piena)
                if (projected.size >= 2) {
                    val path = Path()
                    projected.forEachIndexed { k, (i, v) -> if (k == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                    drawPath(
                        path, forecast,
                        style = Stroke(
                            width = 2.dp.toPx(), cap = StrokeCap.Butt,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                        )
                    )
                }
                if (trend.size >= 2) {
                    val path = Path()
                    trend.forEachIndexed { k, (i, v) -> if (k == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                    drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            // Misura scelta: punto più grande, con anello
            sel?.let { (i, v) ->
                drawCircle(line, radius = 8.dp.toPx(), center = Offset(x(i), y(v)))
                drawCircle(surface, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
            }
        }
        Column(
            Modifier.fillMaxHeight().widthIn(min = 40.dp).padding(start = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            for (g in 0..3) {
                Text((hi - span * g / 3.0).let { v -> if (step >= 1.0) String.format(I18n.locale, "%.0f", v) else fmt1(v) }, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().padding(end = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(0, 1, 2, 3, 4).map { it * (days - 1) / 4 }.forEach { i ->
            val d = DateUtils.parse(dayList[i])
            Text(
                if (days > 90) d.format(MONTH_YEAR) else "${d.dayOfMonth}/${d.monthValue}",
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ChartLegend(dot.copy(alpha = .6f), tr("misure"), isLine = false)
        ChartLegend(line, tr("tendenza (media di %d giorni)", window), isLine = true)
        if (projected.isNotEmpty()) ChartLegend(forecast, tr("previsto dal deficit"), isLine = true, dashed = true)
    }
}

/** Giorni della media mobile della tendenza: 7 sul mese, 14 su 3 mesi, 30 sull'anno. */
private fun trendWindow(days: Int): Int = when {
    days <= 31 -> 7
    days <= 100 -> 14
    else -> 30
}

/** Voce della legenda del grafico: un punto o un tratto di linea, con il nome. */
@Composable
private fun ChartLegend(color: Color, label: String, isLine: Boolean, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(if (isLine) 18.dp else 8.dp).height(8.dp)) {
            if (isLine) {
                drawLine(
                    color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                    strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null
                )
            } else {
                drawCircle(color, radius = size.height / 2)
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note)
    }
}
