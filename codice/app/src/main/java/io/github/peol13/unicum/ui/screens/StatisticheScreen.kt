package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.ui.Route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.DayEnergy
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.data.WeekDay
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.DateStepper
import io.github.peol13.unicum.ui.components.DeficitGreen
import io.github.peol13.unicum.ui.components.DeficitRed
import io.github.peol13.unicum.ui.components.DeficitYellow
import io.github.peol13.unicum.ui.components.ScreenTitle
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.components.WeekBar
import io.github.peol13.unicum.ui.components.WeekChart
import io.github.peol13.unicum.ui.sections.Gap
import io.github.peol13.unicum.ui.sections.formatDuration
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSecondary
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.peol13.unicum.data.SleepRegularity
import io.github.peol13.unicum.data.Insight
import io.github.peol13.unicum.data.InsightEngine
import io.github.peol13.unicum.ui.sections.StatDivider
import androidx.compose.material.icons.rounded.Insights
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumTrack
import io.github.peol13.unicum.ui.sections.tempText
import kotlin.math.abs
import kotlin.math.roundToInt
import io.github.peol13.unicum.ui.theme.UnicumType

/**
 * Statistiche della settimana (lunedì–domenica): deficit calorico, attività e sonno giorno per giorno.
 * Le frecce in alto cambiano settimana e le tre sezioni si aggiornano insieme.
 */
@Composable
fun StatisticheScreen(onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val currentMonday = DateUtils.weekStart(DateUtils.today())
    var monday by remember { mutableStateOf(currentMonday) }
    val isCurrentWeek = monday == currentMonday

    // Si ricarica al cambio di settimana, dopo una sincronizzazione o se cambiano pasti/allenamenti
    val days by produceState(
        emptyList<WeekDay>(), monday, UnicumAppState.health,
        UnicumAppState.dayData.meals.size, UnicumAppState.dayData.activities.size
    ) {
        value = UnicumRepository.loadWeekStats(context, monday)
    }

    UnicumScreen(
        selectedItem = Route.Statistiche,
        onNavigate = onNavigate,
        onSwipeDay = { dir ->
            when {
                dir < 0 -> { monday = monday.minusWeeks(1); true }
                !isCurrentWeek -> { monday = monday.plusWeeks(1); true }
                else -> false
            }
        }
    ) {
        ScreenTitle(tr("Statistiche"), tr("Settimana per settimana"))
        Spacer(Modifier.height(8.dp))

        DateStepper(
            label = DateUtils.weekLabel(monday),
            subtitle = if (isCurrentWeek) tr("Settimana in corso") else monday.year.toString(),
            onPrevious = { monday = monday.minusWeeks(1) },
            onNext = { if (!isCurrentWeek) monday = monday.plusWeeks(1) },
            canGoNext = !isCurrentWeek
        )
        if (!isCurrentWeek) {
            Spacer(Modifier.height(4.dp))
            UnicumChip(
                tr("Racconto della settimana"), selected = false,
                onClick = { onNavigate(Route.WeekStory(monday)) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Spacer(Modifier.height(8.dp))

        if (days.size == 7) {
            DeficitSection(days)
            Gap()
            ActivitySection(days)
            Gap()
            SleepSection(days)
            Gap()
            SleepTimesSection(days)
            Gap()
            RecoverySection(days)
        }
        Gap()
        InsightsSection()
    }
}

// ------------------------------------------------------------------
// Sezioni
// ------------------------------------------------------------------

@Composable
private fun DeficitSection(days: List<WeekDay>) {
    val goal = UserSettings.targetDeficitKcal
    val values = days.mapNotNull { it.deficitKcal }
    val bars = days.map { d ->
        val v = d.deficitKcal
        WeekBar(
            label = DateUtils.weekdayInitial(d.date),
            value = v?.toFloat(),
            valueText = v?.toString() ?: "–",
            // stessi colori della linea del deficit: verde = obiettivo raggiunto, giallo = in deficit, rosso = surplus
            color = when {
                v == null -> DeficitYellow
                v >= goal && v >= 0 -> DeficitGreen
                v >= 0 -> DeficitYellow
                else -> DeficitRed
            },
            highlighted = d.isToday
        )
    }
    StatSection(
        icon = Icons.Rounded.LocalFireDepartment,
        title = tr("Deficit calorico"),
        tiles = listOf(
            tr("Medio al giorno") to if (values.isEmpty()) "–" else deficitText(values.average().roundToInt()),
            tr("Totale settimana") to if (values.isEmpty()) "–" else deficitText(values.sum()),
            tr("Peso stimato") to if (values.isEmpty()) "–" else DayEnergy.weightChangeText(DayEnergy.weightChangeKg(values.sum()))
        ),
        footnote = buildString {
            append(tr("Obiettivo: %,d kcal al giorno.", goal) + " ")
            append(tr("Peso stimato: 7.700 kcal di deficit ≈ 1 kg di grasso; la bilancia varia anche per acqua e digestione."))
            if (days.any { it.isToday && it.deficitKcal != null }) {
                append(" " + tr("Il valore di oggi è parziale: il basale matura nell'arco della giornata."))
            }
        },
        chart = { WeekChart(bars) }
    )
}

@Composable
private fun ActivitySection(days: List<WeekDay>) {
    val counted = days.filter { !it.isFuture }
    val bars = days.map { d ->
        val shown = !d.isFuture
        WeekBar(
            label = DateUtils.weekdayInitial(d.date),
            value = if (shown) d.activityMinutes.toFloat() else null,
            valueText = if (shown) d.activityMinutes.toString() else "–",
            color = UnicumPrimary,
            highlighted = d.isToday
        )
    }
    val total = counted.sumOf { it.activityMinutes }
    StatSection(
        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
        title = tr("Attività"),
        tiles = listOf(
            tr("Totale") to "$total min",
            tr("Media al giorno") to if (counted.isEmpty()) "–" else "${(total / counted.size.toFloat()).roundToInt()} min",
            tr("Allenamenti") to counted.sumOf { it.workouts }.toString()
        ),
        footnote = if (days.any { it.isToday }) tr("Il valore di oggi è parziale: la giornata non è ancora finita.") else null,
        chart = { WeekChart(bars) }
    )
}

@Composable
private fun SleepSection(days: List<WeekDay>) {
    val nights = days.mapNotNull { it.sleepMinutes }
    val bars = days.map { d ->
        val m = d.sleepMinutes
        WeekBar(
            label = DateUtils.weekdayInitial(d.date),
            value = m?.toFloat(),
            valueText = m?.let { "${it / 60}h${"%02d".format(it % 60)}" } ?: "–",
            color = UnicumSecondary,
            highlighted = d.isToday
        )
    }
    StatSection(
        icon = Icons.Rounded.Bedtime,
        title = tr("Sonno"),
        tiles = listOf(
            tr("Media") to if (nights.isEmpty()) "–" else formatDuration(nights.average().roundToInt()),
            tr("Notte migliore") to (nights.maxOrNull()?.let { formatDuration(it) } ?: "–"),
            tr("Notte più breve") to (nights.minOrNull()?.let { formatDuration(it) } ?: "–")
        ),
        footnote = if (nights.isEmpty()) tr("Nessuna notte registrata: collega Health Connect dal Profilo.") else null,
        chart = { WeekChart(bars) }
    )
}

/**
 * Orari del sonno: per ogni notte una barra dall'addormentamento al risveglio, sopra la fascia consigliata
 * (8 ore prima della sveglia media, più 15 minuti per addormentarsi).
 */
@Composable
private fun SleepTimesSection(days: List<WeekDay>) {
    val nights = days.mapNotNull { d -> d.sleepStartMillis?.let { s -> d.sleepEndMillis?.let { e -> s to e } } }
    val summary = SleepRegularity.summarize(nights)
    StatSection(
        icon = Icons.Rounded.Schedule,
        title = tr("Orari del sonno"),
        tiles = listOf(
            tr("A letto in media") to (summary?.let { SleepRegularity.clock(it.avgBed) } ?: "–"),
            tr("Sveglia in media") to (summary?.let { SleepRegularity.clock(it.avgWake) } ?: "–"),
            tr("Variazione") to (summary?.let { "± ${it.bedSpread} min" } ?: "–")
        ),
        footnote = when {
            summary == null -> tr("Nessuna notte registrata: collega Health Connect dal Profilo.")
            summary.bedSpread > 60 -> tr("Orari molto variabili: andare a letto alla stessa ora, anche nel weekend, aiuta il recupero.")
            else -> tr("In chiaro la fascia consigliata: 8 ore prima della tua sveglia media, più 15 minuti per addormentarti.")
        },
        chart = { if (summary != null) SleepTimesChart(days, summary) }
    )
}

@Composable
private fun SleepTimesChart(days: List<WeekDay>, summary: SleepRegularity.Summary) {
    // Asse da 21:00 a 11:00 del mattino dopo, in minuti della notte
    val axisStart = 21 * 60
    val axisEnd = 35 * 60
    val band = UnicumAccent.copy(alpha = .35f)
    val bar = UnicumPrimary
    val grid = UnicumTrack
    val label = UnicumDark
    Row(Modifier.fillMaxWidth()) {
        // Una riga di 20 dp per notte: iniziale del giorno a sinistra, barra centrata sulla stessa riga
        Column(Modifier.width(18.dp)) {
            days.forEach { d ->
                Box(Modifier.height(20.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        DateUtils.weekdayInitial(d.date), style = UnicumType.Note,
                        color = if (d.isToday) UnicumPrimary else label.copy(alpha = .55f),
                        fontWeight = if (d.isToday) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        Canvas(Modifier.weight(1f).height((days.size * 20).dp)) {
            fun x(m: Int) = (m - axisStart).coerceIn(0, axisEnd - axisStart) / (axisEnd - axisStart).toFloat() * size.width
            drawRoundRect(
                band, Offset(x(summary.recommendedBed), 0f),
                Size(x(summary.recommendedWake) - x(summary.recommendedBed), size.height), CornerRadius(6.dp.toPx())
            )
            listOf(23, 25, 27, 29, 31, 33).forEach { h ->
                drawLine(grid, Offset(x(h * 60), 0f), Offset(x(h * 60), size.height), strokeWidth = 1.dp.toPx())
            }
            days.forEachIndexed { i, d ->
                val s = d.sleepStartMillis ?: return@forEachIndexed
                val e = d.sleepEndMillis ?: return@forEachIndexed
                val top = i * 20.dp.toPx() + 4.dp.toPx()
                drawRoundRect(
                    bar.copy(alpha = if (d.isToday) 1f else .8f),
                    Offset(x(SleepRegularity.nightMinute(s)), top),
                    Size(x(SleepRegularity.nightMinute(e)) - x(SleepRegularity.nightMinute(s)), 12.dp.toPx()),
                    CornerRadius(6.dp.toPx())
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth().padding(start = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("21", "23", "01", "03", "05", "07", "09", "11").forEach {
            Text(it, style = UnicumType.Micro, color = label.copy(alpha = .55f))
        }
    }
}

@Composable
private fun RecoverySection(days: List<WeekDay>) {
    val hrvs = days.mapNotNull { it.hrv }
    val temps = days.filter { it.nightTemp != null }
    val deltaOnly = temps.isNotEmpty() && temps.all { it.tempIsDelta }
    val avgTemp = temps.map { it.nightTemp!! }.average()
    val hrvBars = days.map { d ->
        WeekBar(
            label = DateUtils.weekdayInitial(d.date),
            value = d.hrv?.toFloat(),
            valueText = d.hrv?.roundToInt()?.toString() ?: "–",
            color = UnicumPrimary,
            highlighted = d.isToday
        )
    }
    // Le colonne mostrano lo scarto dalla media della settimana, così le variazioni si vedono
    val tempBars = days.map { d ->
        val t = d.nightTemp
        WeekBar(
            label = DateUtils.weekdayInitial(d.date),
            value = t?.let { (it - avgTemp).toFloat() },
            valueText = t?.let { String.format(I18n.locale, if (d.tempIsDelta) "%+.1f" else "%.1f", it) } ?: "–",
            color = UnicumSecondary,
            highlighted = d.isToday
        )
    }
    StatSection(
        icon = Icons.Rounded.Bolt,
        title = tr("Recupero"),
        tiles = listOf(
            tr("HRV media") to if (hrvs.isEmpty()) "–" else "${hrvs.average().roundToInt()} ms",
            tr("HRV migliore") to (hrvs.maxOrNull()?.let { "${it.roundToInt()} ms" } ?: "–"),
            tr("Temp. media") to if (temps.isEmpty()) "–" else tempText(avgTemp, deltaOnly)
        ),
        footnote = if (hrvs.isEmpty() && temps.isEmpty()) tr("Nessun dato di recupero: collega Health Connect dal Profilo.")
        else tr("HRV notturna (ms) sopra, temperatura notturna sotto: le colonne mostrano lo scarto dalla media della settimana."),
        chart = {
            Text("HRV", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
            Spacer(Modifier.height(6.dp))
            WeekChart(hrvBars)
            if (temps.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(tr("Temperatura notturna (°C)"), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
                Spacer(Modifier.height(6.dp))
                WeekChart(tempBars)
            } else {
                Spacer(Modifier.height(10.dp))
                Text(tr("Temperatura notturna: nessun dato dalle tue fonti."), color = UnicumDark.copy(alpha = .5f), style = UnicumType.Caption)
            }
        }
    )
}

/**
 * Correlazioni personali degli ultimi 60 giorni (indipendenti dalla settimana scelta): come cambiano HRV e
 * sonno dopo allenamenti, passi, notti lunghe o deficit forti. Sempre con il numero di giorni e l'avvertenza.
 */
@Composable
private fun InsightsSection() {
    val context = LocalContext.current
    val insights by produceState<List<Insight>?>(null, UnicumAppState.health, UnicumAppState.dayData.meals.size) {
        value = InsightEngine.compute(UnicumRepository.loadInsightSamples(context))
    }
    UnicumCard {
        CardHeader(Icons.Rounded.Insights, tr("Cosa emerge dai tuoi dati"))
        Spacer(Modifier.height(2.dp))
        Text(tr("Ultimi 60 giorni"), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
        Spacer(Modifier.height(6.dp))
        val list = insights
        when {
            list == null -> Text(tr("Calcolo…"), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail)
            list.isEmpty() -> Text(
                tr("Ancora nessuna relazione chiara. Servono almeno %d giorni per ogni gruppo (es. con e senza allenamento) con sonno e HRV registrati.", InsightEngine.MIN_GROUP),
                    
                color = UnicumDark.copy(alpha = .65f), style = UnicumType.Detail, lineHeight = 18.sp
            )
            else -> list.forEachIndexed { i, insight ->
                if (i > 0) StatDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(insight.title, color = UnicumPrimary, style = UnicumType.Caption, fontWeight = FontWeight.SemiBold)
                    Text(insight.text, color = UnicumDark, style = UnicumType.Body, lineHeight = 20.sp)
                    Text(
                        tr("Su %d giorni contro %d", insight.daysWith, insight.daysWithout),
                        color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Sono correlazioni, non cause: due cose che capitano insieme possono dipendere da altro (stress, orari, malattia)."),
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp
        )
    }
}

// ------------------------------------------------------------------

@Composable
private fun StatSection(
    icon: ImageVector,
    title: String,
    tiles: List<Pair<String, String>>,
    footnote: String?,
    chart: @Composable () -> Unit
) {
    UnicumCard {
        CardHeader(icon, title)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            tiles.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(value, color = UnicumDark, style = UnicumType.Card, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(label, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        chart()
        if (footnote != null) {
            Spacer(Modifier.height(10.dp))
            Text(footnote, color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp)
        }
    }
}

/** "650 kcal" per un deficit, "surplus 120 kcal" se si è mangiato più di quanto si è bruciato. */
private fun deficitText(kcal: Int): String =
    if (kcal >= 0) "$kcal kcal" else tr("surplus %,d kcal", abs(kcal))
