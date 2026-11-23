package io.github.peol13.unicum.ui.sections

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.data.SleepStageType
import io.github.peol13.unicum.ui.theme.UnicumTrack
import io.github.peol13.unicum.ui.theme.MacroFatsColor
import io.github.peol13.unicum.ui.theme.MacroCarbsColor
import io.github.peol13.unicum.ui.theme.MacroProteinColor
import io.github.peol13.unicum.data.MacroBalance
import io.github.peol13.unicum.data.DayEnergy
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import io.github.peol13.unicum.data.UnicumRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.Activity
import io.github.peol13.unicum.data.Meal
import io.github.peol13.unicum.data.MealTime
import io.github.peol13.unicum.data.MetricType
import io.github.peol13.unicum.data.percentVs
import io.github.peol13.unicum.data.signedPercent
import io.github.peol13.unicum.ui.components.DeleteRed
import io.github.peol13.unicum.data.SleepNight
import io.github.peol13.unicum.data.SourceInfo
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.components.EditMealDialog
import androidx.compose.material.icons.rounded.Edit
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.DeficitLine
import io.github.peol13.unicum.ui.components.IconBadge
import io.github.peol13.unicum.ui.components.HealthEmptyState
import io.github.peol13.unicum.ui.components.MiniBars
import io.github.peol13.unicum.data.RecoveryAlert
import io.github.peol13.unicum.ui.components.DeficitYellow
import androidx.compose.material.icons.rounded.WarningAmber
import io.github.peol13.unicum.ui.components.fmtGrams
import androidx.compose.material.icons.rounded.Grain
import io.github.peol13.unicum.ui.components.animatedCount
import io.github.peol13.unicum.ui.components.RingGauge
import io.github.peol13.unicum.ui.components.ThinProgress
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSecondary
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.peol13.unicum.ui.theme.UnicumType
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.ContentCopy
import io.github.peol13.unicum.i18n.trp
import io.github.peol13.unicum.data.DateUtils
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.heightIn

/*
 * Sezioni condivise tra Home, pagine di dettaglio e Diario.
 */

internal const val ACTIVITY_GOAL_MINUTES = 60

internal const val NO_DATA = "–"

/** Numeri di una giornata, calcolati una volta e usati da tutte le card. */
internal data class DayStats(
    val eaten: Int,
    val activityKcal: Int,
    val activityMinutes: Int,
    val goal: Int,            // obiettivo calorico giornaliero: FISSO, non dipende dall'attività
    val burnedSoFar: Int,     // consumato fino a ora = metabolismo maturato + movimento e allenamenti
    val deficit: Int,         // bruciate - assunte fino a ora (negativo = surplus)
    val remaining: Int,       // obiettivo - assunte
    val protein: Float,
    val carbs: Float,
    val fats: Float
)

@Composable
internal fun Gap() = Spacer(Modifier.height(12.dp))

/** Due card affiancate della stessa altezza. */
@Composable
internal fun CardPair(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
internal fun CalorieCard(s: DayStats, onClick: (() -> Unit)? = null) {
    UnicumCard(onClick = onClick) {
        CardHeader(
            Icons.Rounded.LocalFireDepartment, tr("Calorie"),
            action = if (onClick != null) tr("Dettagli") else null, onAction = { onClick?.invoke() }
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(progress = s.eaten / s.goal.coerceAtLeast(1).toFloat()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt(animatedCount(s.eaten)), color = UnicumDark, style = UnicumType.Page, fontWeight = FontWeight.SemiBold)
                    Text("/ ${fmt(s.goal)}", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail)
                    Text("kcal", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail)
                }
            }
            Spacer(Modifier.width(16.dp))
            DeficitSummary(s, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        MacroBalanceBlock(s)
    }
}

@Composable
internal fun DeficitSummary(s: DayStats, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "${fmt(animatedCount(abs(s.deficit)))} kcal",
            color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold
        )
        Text(
            if (s.deficit < 0) tr("surplus") else tr("deficit"),
            color = UnicumPrimary, style = UnicumType.Detail
        )
        Spacer(Modifier.height(10.dp))
        DeficitLine(s.deficit, UserSettings.targetDeficitKcal)
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Deficit obiettivo: %,d kcal", UserSettings.targetDeficitKcal),
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note
        )
    }
}

@Composable
internal fun CalorieCompactCard(s: DayStats, onClick: () -> Unit) {
    UnicumCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.LocalFireDepartment, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Calorie"), color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(animatedCount(s.eaten)), color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold)
                    Text(
                        "  / ${fmt(s.goal)} kcal",
                        color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${fmt(abs(s.deficit))} kcal", color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
                Text(if (s.deficit < 0) tr("surplus") else tr("deficit"), color = UnicumPrimary, style = UnicumType.Note)
            }
        }
        Spacer(Modifier.height(10.dp))
        DeficitLine(s.deficit, UserSettings.targetDeficitKcal)
    }
}

@Composable
internal fun ActivityBigCard(s: DayStats, week: List<Int>, onClick: (() -> Unit)? = null) {
    // Confronto con la media dei 6 giorni precedenti
    val previous = week.dropLast(1)
    val avg = if (previous.isNotEmpty()) previous.average() else 0.0
    val delta = if (avg > 0) (s.activityMinutes - avg).roundToInt() else null

    UnicumCard(onClick = onClick) {
        CardHeader(
            Icons.AutoMirrored.Rounded.DirectionsRun, tr("Attività"),
            action = if (onClick != null) tr("Dettagli") else null, onAction = { onClick?.invoke() }
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(progress = s.activityMinutes / ACTIVITY_GOAL_MINUTES.toFloat()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${animatedCount(s.activityMinutes)}", color = UnicumDark, style = UnicumType.Hero, fontWeight = FontWeight.SemiBold)
                    Text("min", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Body)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                SoftPill {
                    Text(
                        if (delta != null) "${if (delta >= 0) "+" else ""}$delta min" else "—",
                        color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold
                    )
                    Text(tr("rispetto alla media"), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
                }
                Spacer(Modifier.height(10.dp))
                ThinProgress(s.activityMinutes / ACTIVITY_GOAL_MINUTES.toFloat())
                Spacer(Modifier.height(4.dp))
                Text(tr("Obiettivo %d min", ACTIVITY_GOAL_MINUTES), color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note)
                UnicumAppState.steps?.let {
                    Text(tr("%s passi", fmt(it)), color = UnicumDark.copy(alpha = .65f), style = UnicumType.Caption, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MiniBars(week)
    }
}

@Composable
internal fun ActivitySmallCard(minutes: Int, week: List<Int>, modifier: Modifier, onClick: () -> Unit) {
    UnicumCard(modifier = modifier, onClick = onClick) {
        CardHeader(Icons.AutoMirrored.Rounded.DirectionsRun, tr("Attività"))
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$minutes", color = UnicumDark, style = UnicumType.Screen, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Text("min", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Body, modifier = Modifier.padding(bottom = 4.dp))
            }
            UnicumAppState.steps?.let {
                Text(tr("%s passi", fmt(it)), color = UnicumDark.copy(alpha = .75f), style = UnicumType.Emphasis, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(10.dp))
        MiniBars(week, height = 36.dp)
    }
}

internal fun workoutDetail(a: Activity): String = buildList {
    a.startMinute?.let { add(MealTime.format(it)) }
    add("${a.durationMinutes} min")
    a.distanceMeters?.takeIf { it > 0 }?.let { add("%.1f km".format(I18n.locale, it / 1000.0)) }
    a.avgHeartRate?.let { add("$it bpm") }
    if (!a.isManual) add(SourceInfo.label(a.source))
}.joinToString(" · ")

@Composable
internal fun WorkoutsCard(
    activities: List<Activity>,
    onAll: (() -> Unit)? = null,
    limit: Int = 4,
    onDelete: ((Activity) -> Unit)? = null
) {
    UnicumCard {
        CardHeader(
            Icons.Rounded.FitnessCenter, tr("Allenamenti"),
            action = if (onAll != null) tr("Vedi tutti") else null, onAction = { onAll?.invoke() }
        )
        Spacer(Modifier.height(8.dp))
        if (activities.isEmpty()) {
            Text(
                tr("Nessun allenamento registrato."),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            activities.takeLast(limit).reversed().forEachIndexed { i, a ->
                if (i > 0) StatDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(activityIcon(a.type))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(a.type), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
                        Text(workoutDetail(a), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
                    }
                    Text("${fmt(a.calories)} kcal", color = UnicumPrimary, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
                    // Solo quelli scritti a mano: gli altri arrivano da Health Connect e tornerebbero alla sincronizzazione
                    if (onDelete != null && a.isManual) {
                        IconButton(onClick = { onDelete(a) }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.Close, tr("Elimina"), tint = DeleteRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

internal fun formatDuration(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"

internal fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", I18n.locale).format(Date(millis))

internal const val SLEEP_GOAL_MINUTES = 8 * 60

@Composable
internal fun SleepBigCard(night: SleepNight?, onClick: (() -> Unit)? = null) {
    UnicumCard(onClick = onClick) {
        CardHeader(Icons.Rounded.Bedtime, tr("Sonno"))
        Spacer(Modifier.height(10.dp))
        if (night == null) {
            HealthEmptyState(
                tr("Nessuna notte registrata"),
                tr("Collega Health Connect e consenti la lettura del sonno: qui vedrai durata, fasi e orari.")
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingGauge(progress = night.durationMinutes / SLEEP_GOAL_MINUTES.toFloat()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatDuration(night.durationMinutes),
                            color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold
                        )
                        Text(tr("di sonno"), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
                    }
                }
                Spacer(Modifier.width(16.dp))
                SoftPill(Modifier.weight(1f)) {
                    Text(
                        "${night.efficiencyPercent}%",
                        color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold
                    )
                    Text(tr("efficienza del sonno"), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatClock(night.startMillis)} → ${formatClock(night.endMillis)}",
                        color = UnicumDark.copy(alpha = .75f), style = UnicumType.Caption
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (night.hasTimeline) {
                SleepHypnogram(night)
            } else if (night.hasStages) {
                StageBar(night)
            } else {
                Text(
                    tr("Questa fonte non fornisce le fasi del sonno."),
                    color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Fonte: %s", SourceInfo.label(night.source)),
                color = UnicumDark.copy(alpha = .45f), style = UnicumType.Note
            )
        }
    }
}

/** Barra proporzionale delle fasi (solo totali): si usa quando la fonte non fornisce gli orari delle fasi. */
@Composable
internal fun StageBar(night: SleepNight) {
    val colors = SleepStageType.entries.associateWith { sleepStageColor(it) }
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))) {
        night.stageTotals.forEach { (type, minutes) ->
            Box(Modifier.weight(minutes.toFloat()).fillMaxHeight().background(colors.getValue(type)))
        }
    }
    Spacer(Modifier.height(6.dp))
    StageLegend(night, colors)
}

@Composable
internal fun SleepSmallCard(night: SleepNight?, weekMinutes: List<Int>, modifier: Modifier, onClick: (() -> Unit)? = null) {
    UnicumCard(modifier = modifier, onClick = onClick) {
        CardHeader(Icons.Rounded.Bedtime, tr("Sonno"))
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (night != null) formatDuration(night.durationMinutes) else NO_DATA,
                color = UnicumDark, style = if (night != null) UnicumType.Section else UnicumType.Screen, fontWeight = FontWeight.SemiBold
            )
            Text(tr("di sonno"), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail)
        }
        Spacer(Modifier.height(10.dp))
        MiniBars(weekMinutes.ifEmpty { List(7) { 0 } }, height = 36.dp)
    }
}

@Composable
internal fun SleepRingSmallCard(night: SleepNight?, modifier: Modifier, onClick: (() -> Unit)? = null) {
    UnicumCard(modifier = modifier, onClick = onClick) {
        CardHeader(Icons.Rounded.Bedtime, tr("Sonno"))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RingGauge(
                progress = (night?.durationMinutes ?: 0) / SLEEP_GOAL_MINUTES.toFloat(),
                size = 104.dp, stroke = 10.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (night != null) formatDuration(night.durationMinutes) else NO_DATA,
                        color = UnicumDark, style = if (night != null) UnicumType.Heading else UnicumType.Page, fontWeight = FontWeight.SemiBold
                    )
                    Text(tr("di sonno"), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note)
                }
            }
        }
    }
}

/** "36,4 °C" oppure, se la fonte dà solo lo scarto dalla base personale, "+0,3 °C". */
internal fun tempText(value: Double, isDelta: Boolean): String =
    String.format(I18n.locale, if (isDelta) "%+.1f °C" else "%.1f °C", value)

/** " (+0,2 vs media)" rispetto alla media recente; vuoto se mancano i dati per il confronto. */
internal fun tempVsAverage(temp: Pair<Double, Boolean>): String {
    val type = if (temp.second) MetricType.NIGHT_TEMP_DELTA else MetricType.NIGHT_TEMP
    val base = UnicumAppState.metricBaseline(type) ?: return ""
    return " " + tr("(%+.1f vs media)", temp.first - base)
}

/** Recupero: HRV notturna rispetto alla propria media, con temperatura notturna. */
@Composable
internal fun RecoveryCard(modifier: Modifier, onClick: (() -> Unit)? = null) {
    val hrv = UnicumAppState.latestMetric(MetricType.HRV)
    val temp = UnicumAppState.latestNightTemp()
    val baseline = UnicumAppState.metricBaseline(MetricType.HRV)
    val progress = if (hrv != null && baseline != null && baseline > 0) (hrv / baseline / 1.5).toFloat() else 0f

    UnicumCard(modifier = modifier, onClick = onClick) {
        CardHeader(Icons.Rounded.Bolt, tr("Recupero"))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RingGauge(progress = progress, size = 104.dp, stroke = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        hrv?.let { "${it.toInt()}" } ?: NO_DATA,
                        color = UnicumDark, style = UnicumType.Page, fontWeight = FontWeight.SemiBold
                    )
                    Text("HRV ms", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append(tr("Temp. notte %s", if (temp != null) tempText(temp.first, temp.second) + tempVsAverage(temp) else "–"))
                percentVs(hrv, baseline)?.let { append(" · " + tr("HRV %s vs media", signedPercent(it))) }
            },
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Note, lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun MealsCard(meals: List<Meal>, onDetails: (() -> Unit)? = null) {
    // Colazione, pranzo e cena sempre; i pasti "Altro" (anche con un nome proprio) solo se contengono qualcosa
    val groups = MealTime.groups(meals, withEmptyOther = false)
    val totals = groups.associate { (t, list) -> t to list.sumOf { it.calories } }
    val max = (totals.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val rows = groups.map { it.first }

    UnicumCard(onClick = onDetails) {
        CardHeader(
            Icons.Rounded.Restaurant, tr("Pasti"),
            action = if (onDetails != null) tr("Dettagli") else null, onAction = { onDetails?.invoke() }
        )
        Spacer(Modifier.height(6.dp))
        rows.forEach { type ->
            val kcal = totals[type] ?: 0
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(MealTime.label(type), color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    ThinProgress(kcal / max.toFloat(), color = UnicumSecondary, modifier = Modifier.padding(end = 16.dp))
                }
                Text(
                    if (kcal > 0) "${fmt(kcal)} kcal" else "–",
                    color = if (kcal > 0) UnicumDark else UnicumDark.copy(alpha = .4f),
                    style = UnicumType.Detail, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun SoftPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(UnicumAccent.copy(alpha = .22f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { content() }
}

internal fun fmt(v: Int): String = io.github.peol13.unicum.i18n.lnum(v)

/** In un pasto vuoto: "Ripeti da ieri" con gli alimenti dello stesso pasto del giorno prima, se ci sono. */
@Composable
private fun RepeatFromYesterday(type: String) {
    val context = LocalContext.current.applicationContext
    val date = UnicumAppState.selectedDate
    val yesterday = remember(date) { DateUtils.parse(date).minusDays(1).toString() }
    val previous by androidx.compose.runtime.produceState(emptyList<Meal>(), yesterday, type, UnicumAppState.dayData) {
        value = runCatching {
            UnicumRepository.loadMeals(context, yesterday).filter { it.mealType.equals(type, ignoreCase = true) }
        }.getOrDefault(emptyList())
    }
    if (previous.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { UnicumAppState.copyMeals(context, previous, date) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.ContentCopy, null, tint = UnicumPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            tr("Ripeti da ieri") + " · " + trp(previous.size, "%d alimento", "%d alimenti") + " · ${fmt(previous.sumOf { it.calories })} kcal",
            color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold
        )
    }
}

/** Dove copiare un pasto: oggi, ieri o un giorno scelto dal calendario (non nel futuro). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CopyMealDialog(type: String, meals: List<Meal>, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val from = UnicumAppState.selectedDate
    val today = DateUtils.today()
    var picking by remember { mutableStateOf(false) }
    fun copyTo(date: java.time.LocalDate) {
        UnicumAppState.copyMeals(context, meals, date.toString())
        onDismiss()
    }
    if (picking) {
        val state = androidx.compose.material3.rememberDatePickerState(
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(today)
            }
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { copyTo(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }
                }) { Text(tr("Copia"), color = UnicumPrimary) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f)) } }
        ) { androidx.compose.material3.DatePicker(state = state, showModeToggle = false) }
        return
    }
    io.github.peol13.unicum.ui.components.UnicumDialog(onDismiss = onDismiss) {
        Text(
            tr("Copia %s", io.github.peol13.unicum.data.MealTime.label(type).lowercase(io.github.peol13.unicum.i18n.I18n.locale)),
            color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold
        )
        Text(
            trp(meals.size, "%d alimento", "%d alimenti") + " · ${fmt(meals.sumOf { it.calories })} kcal · " +
                tr("stesso pasto e stesso orario"),
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail
        )
        val fromDate = DateUtils.parse(from)
        if (fromDate != today) io.github.peol13.unicum.ui.components.UnicumButton(tr("Copia in oggi"), onClick = { copyTo(today) })
        if (fromDate != today.minusDays(1)) {
            io.github.peol13.unicum.ui.components.UnicumButton(
                tr("Copia in ieri"), onClick = { copyTo(today.minusDays(1)) }
            )
        }
        androidx.compose.material3.TextButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Scegli un altro giorno"), color = UnicumPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun MealSection(
    title: String,
    meals: List<Meal>,
    onAdd: () -> Unit,
    onDelete: (Meal) -> Unit
) {
    UnicumCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                CardHeader(
                    icon = Icons.Rounded.Restaurant,
                    title = io.github.peol13.unicum.data.MealTime.label(title).let { t -> if (meals.isEmpty()) t else "$t · ${fmt(meals.sumOf { it.calories })} kcal" }
                )
            }
            // Copia il pasto in un altro giorno (es. la colazione di ieri anche oggi)
            var copying by remember { mutableStateOf(false) }
            if (meals.isNotEmpty()) {
                IconButton(onClick = { copying = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.ContentCopy, tr("Copia in un altro giorno"), tint = UnicumPrimary, modifier = Modifier.size(18.dp))
                }
            }
            if (copying) CopyMealDialog(title, meals, onDismiss = { copying = false })
            // Area di tocco di almeno 48 dp (prima era bassa quanto il testo)
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { onAdd() }.padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
                Text(tr("Aggiungi"), color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
            }
        }

        if (meals.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(tr("Nessun alimento registrato."), color = UnicumDark.copy(alpha = .5f), style = UnicumType.Caption)
            RepeatFromYesterday(title)
        } else {
            // Toccando un alimento si modificano quantità, tipo di pasto e orario
            val context = LocalContext.current.applicationContext
            var editing by remember { mutableStateOf<Meal?>(null) }
            editing?.let { m ->
                EditMealDialog(
                    meal = m,
                    onDismiss = { editing = null },
                    onSave = { UnicumAppState.updateMeal(context, it); editing = null },
                    onDelete = { onDelete(m); editing = null }
                )
            }
            Spacer(Modifier.height(6.dp))
            meals.forEachIndexed { i, meal ->
                if (i > 0) StatDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { editing = meal }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    meal.timeText?.let { t ->
                        Text(
                            t, color = UnicumPrimary, style = UnicumType.Caption, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(44.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(io.github.peol13.unicum.data.FoodNames.label(meal.name), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
                        val grams = if (meal.quantityGrams > 0) "${meal.quantityGrams} g · " else ""
                        Text(
                            "$grams${meal.calories} kcal · ${tr("P")} ${meal.protein.toInt()}  ${tr("C")} ${meal.carbohydrates.toInt()}  ${tr("G")} ${meal.fats.toInt()}",
                            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
                        )
                    }
                    Icon(
                        Icons.Rounded.Edit, tr("Modifica"), tint = UnicumDark.copy(alpha = .35f),
                        modifier = Modifier.size(16.dp)
                    )
                    IconButton(onClick = { onDelete(meal) }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Close, tr("Elimina"), tint = DeleteRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Icona di un tipo di allenamento (valore salvato in italiano, come arriva da Health Connect o dal dialog). */
internal fun activityIcon(type: String): androidx.compose.ui.graphics.vector.ImageVector = when {
    io.github.peol13.unicum.data.Sport.ofWorkout(type)?.isStrength == true -> Icons.Rounded.FitnessCenter
    type.equals("Camminata", true) || type.equals("Escursionismo", true) -> Icons.AutoMirrored.Rounded.DirectionsWalk
    type.equals("Ciclismo", true) -> Icons.AutoMirrored.Rounded.DirectionsBike
    type.equals("Nuoto", true) -> Icons.Rounded.Pool
    type.equals("Yoga", true) -> Icons.Rounded.SelfImprovement
    type.equals("Calcio", true) -> Icons.Rounded.SportsSoccer
    else -> Icons.AutoMirrored.Rounded.DirectionsRun
}

/** Numeri del giorno mostrato. Per oggi il consumo a riposo matura col passare dei minuti. */
@Composable
internal fun rememberDayStats(): DayStats {
    val data = UnicumAppState.dayData
    val context = LocalContext.current.applicationContext
    val tickingResting by produceState(UserSettings.restingBurnSoFar()) {
        while (true) {
            delay(60_000)
            value = UserSettings.restingBurnSoFar()
            UnicumAppState.followToday(context)
        }
    }
    val restingSoFar = if (UnicumAppState.isToday) tickingResting else UserSettings.dailyBurnKcal
    val burnedSoFar = restingSoFar + UnicumAppState.extraBurnKcal
    return DayStats(
        eaten = data.caloriesConsumed,
        activityKcal = data.activityCalories,
        activityMinutes = data.activityMinutes,
        goal = UserSettings.intakeGoalKcal,
        burnedSoFar = burnedSoFar,
        deficit = DayEnergy.deficit(data.caloriesConsumed, restingSoFar, UnicumAppState.extraBurnKcal),
        remaining = DayEnergy.remaining(UserSettings.intakeGoalKcal, data.caloriesConsumed),
        protein = data.meals.sumOf { it.protein.toDouble() }.toFloat(),
        carbs = data.meals.sumOf { it.carbohydrates.toDouble() }.toFloat(),
        fats = data.meals.sumOf { it.fats.toDouble() }.toFloat()
    )
}

@Composable
internal fun StatRow(label: String, value: String, emphasized: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = UnicumDark.copy(alpha = if (emphasized) 1f else .7f), style = UnicumType.Body,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            value, color = if (emphasized) UnicumPrimary else UnicumDark, style = UnicumType.Body,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
internal fun StatDivider() =
    Box(Modifier.fillMaxWidth().height(1.dp).background(UnicumAccent.copy(alpha = .3f)))

/** Da dove vengono le calorie consumate e come si arriva al deficit. */
@Composable
internal fun CalorieBreakdownCard(s: DayStats) {
    val resting = s.burnedSoFar - UnicumAppState.extraBurnKcal
    UnicumCard {
        CardHeader(Icons.Rounded.LocalFireDepartment, tr("Bilancio del giorno"))
        Spacer(Modifier.height(6.dp))
        StatRow(tr("Metabolismo basale"), "${fmt(resting)} kcal")
        StatDivider()
        StatRow(tr("Movimento e allenamenti"), "${fmt(UnicumAppState.extraBurnKcal)} kcal")
        StatDivider()
        StatRow(tr("Totale consumato"), "${fmt(s.burnedSoFar)} kcal", emphasized = true)
        StatDivider()
        StatRow(tr("Calorie assunte"), "${fmt(s.eaten)} kcal")
        StatDivider()
        StatRow(if (s.deficit < 0) tr("Surplus") else tr("Deficit"), "${fmt(abs(s.deficit))} kcal", emphasized = true)
        StatDivider()
        StatRow(tr("Obiettivo calorico (fisso)"), "${fmt(s.goal)} kcal")
        if (UnicumAppState.isToday) {
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Il basale matura nell'arco della giornata: a fine giornata arriverà a %s kcal.", fmt(UserSettings.dailyBurnKcal)),
                color = UnicumDark.copy(alpha = .5f), style = UnicumType.Caption, lineHeight = 17.sp
            )
        }
    }
}

/** Limiti di riferimento per un adulto: fibre almeno 25 g (EFSA), sale non oltre 5 g (OMS). */
private const val FIBER_GOAL_G = 25f
private const val SALT_LIMIT_G = 5f

/**
 * Fibre, zuccheri e sale del giorno. Il database di base non ha questi valori: contano i prodotti con marca
 * (Open Food Facts) e gli alimenti personali in cui li hai scritti, e la card dice per quanti alimenti c'è il dato.
 */
@Composable
internal fun OtherNutrientsCard() {
    val meals = UnicumAppState.dayData.meals
    val withData = meals.count { it.fiber != null || it.sugars != null || it.salt != null }
    fun total(pick: (Meal) -> Float?): Float? =
        meals.mapNotNull(pick).takeIf { it.isNotEmpty() }?.sum()

    val fiber = total { it.fiber }
    val sugars = total { it.sugars }
    val salt = total { it.salt }
    UnicumCard {
        CardHeader(Icons.Rounded.Grain, tr("Fibre, zuccheri e sale"))
        Spacer(Modifier.height(6.dp))
        NutrientRow(tr("Fibre"), fiber, if (fiber != null) fiber / FIBER_GOAL_G else null, tr("obiettivo almeno 25 g"), warn = false)
        StatDivider()
        NutrientRow(tr("Zuccheri"), sugars, null, tr("compresi quelli di frutta e latte"), warn = false)
        StatDivider()
        NutrientRow(tr("Sale"), salt, if (salt != null) salt / SALT_LIMIT_G else null, tr("massimo consigliato 5 g"), warn = (salt ?: 0f) > SALT_LIMIT_G)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                meals.isEmpty() -> tr("Nessun pasto registrato.")
                withData == meals.size -> tr("Dato presente per tutti gli alimenti del giorno.")
                else -> tr("Dato presente per %d alimenti su %d: il database di base non ha questi valori, li hanno i prodotti con marca e gli alimenti personali in cui li inserisci.", withData, meals.size)
                    
            },
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Caption, lineHeight = 17.sp
        )
    }
}

@Composable
private fun NutrientRow(label: String, grams: Float?, fraction: Float?, hint: String, warn: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.Medium)
                Text(hint, color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note)
            }
            Text(
                grams?.let { "${fmtGrams(it)} g" } ?: NO_DATA,
                color = if (warn) DeleteRed else UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold
            )
        }
        if (fraction != null) {
            Spacer(Modifier.height(6.dp))
            ThinProgress(fraction, color = if (warn) DeleteRed else UnicumSecondary)
        }
    }
}

/** Passi, distanza, calorie attive e minuti del giorno. */
@Composable
internal fun ActivityNumbersCard() {
    val day = UnicumAppState.selectedDate
    val distance = UnicumAppState.health.metric(MetricType.DISTANCE_M, day)
    val activeKcal = UnicumAppState.health.metric(MetricType.ACTIVE_KCAL, day)
    UnicumCard {
        CardHeader(Icons.AutoMirrored.Rounded.DirectionsRun, tr("Movimento del giorno"))
        Spacer(Modifier.height(6.dp))
        StatRow(tr("Passi"), UnicumAppState.steps?.let { fmt(it) } ?: NO_DATA)
        StatDivider()
        StatRow(tr("Distanza"), distance?.let { "%.1f km".format(I18n.locale, it / 1000.0) } ?: NO_DATA)
        StatDivider()
        StatRow(tr("Calorie attive"), activeKcal?.let { "${fmt(it.toInt())} kcal" } ?: NO_DATA)
        StatDivider()
        StatRow(tr("Minuti di allenamento"), "${UnicumAppState.dayData.activityMinutes} min")
    }
}

/** Riquadro di un avviso sul recupero: icona, titolo e cosa fare, su un giallo tenue. */
@Composable
internal fun RecoveryAlertBox(alert: RecoveryAlert) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeficitYellow.copy(alpha = .18f))
            .padding(14.dp)
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = UnicumDark.copy(alpha = .75f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(alert.title, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
            Text(alert.text, color = UnicumDark.copy(alpha = .75f), style = UnicumType.Caption, lineHeight = 17.sp)
        }
    }
}

/** HRV e temperatura notturna con il confronto rispetto alla propria media. */
@Composable
internal fun RecoveryDetailCard() {
    val hrv = UnicumAppState.latestMetric(MetricType.HRV)
    val temp = UnicumAppState.latestNightTemp()
    val hrvBase = UnicumAppState.metricBaseline(MetricType.HRV)
    val days = UnicumRepository.lastDays(7, UnicumAppState.selectedDate)
    val hrvWeek = days.map { UnicumAppState.health.metric(MetricType.HRV, it)?.toInt() ?: 0 }

    UnicumCard {
        CardHeader(Icons.Rounded.Bolt, tr("Recupero"))
        Spacer(Modifier.height(6.dp))
        if (hrv == null && temp == null && hrvWeek.all { it == 0 }) {
            HealthEmptyState(
                tr("Nessun dato di recupero"),
                tr("HRV e temperatura notturna arrivano dall'anello o dall'orologio tramite Health Connect.")
            )
            return@UnicumCard
        }
        UnicumAppState.recoveryAlert()?.let {
            RecoveryAlertBox(it)
            Spacer(Modifier.height(6.dp))
        }
        val hrvVs = percentVs(hrv, hrvBase)?.let { " " + tr("(%s vs media)", signedPercent(it)) }.orEmpty()
        StatRow(tr("HRV notturna"), if (hrv != null) "${hrv.toInt()} ms$hrvVs" else NO_DATA, emphasized = true)
        StatDivider()
        StatRow(
            tr("Temperatura notturna"),
            if (temp != null) tempText(temp.first, temp.second) + tempVsAverage(temp) else NO_DATA
        )
        Spacer(Modifier.height(10.dp))
        Text(tr("HRV degli ultimi 7 giorni"), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
        Spacer(Modifier.height(6.dp))
        MiniBars(hrvWeek, height = 50.dp)
        if (hrvBase == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Servono almeno 4 giorni di dati per confrontare con la tua media."),
                color = UnicumDark.copy(alpha = .5f), style = UnicumType.Caption
            )
        }
    }
}

/**
 * Andamento dei macro: la barra "Effettivo" mostra da dove vengono davvero le calorie mangiate,
 * quella sottile "Consigliate" la ripartizione scelta nel Profilo. Sotto, cosa cambiare nel prossimo pasto.
 */
@Composable
internal fun MacroBalanceBlock(s: DayStats) {
    val shares = MacroBalance.shares(
        s.protein, s.carbs, s.fats,
        Triple(UserSettings.proteinPct, UserSettings.carbsPct, UserSettings.fatsPct),
        Triple(UserSettings.proteinGoal, UserSettings.carbsGoal, UserSettings.fatsGoal)
    )
    val colors = listOf(MacroProteinColor, MacroCarbsColor, MacroFatsColor)
    val track = UnicumTrack
    val hasEaten = shares.any { it.effectivePct != null }

    Text(tr("Macronutrienti"), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))

    MacroBarLabel(tr("Effettivo"))
    Spacer(Modifier.height(4.dp))
    if (hasEaten) {
        MacroPercentRow(shares.map { it.effectivePct ?: 0 }, colors)
        Spacer(Modifier.height(3.dp))
        MacroSegments(shares.map { it.effectivePct ?: 0 }, colors, height = 14.dp)
    } else {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(6.dp)).background(track))
        Spacer(Modifier.height(3.dp))
        Text(tr("Nessun pasto registrato"), color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note)
    }

    Spacer(Modifier.height(8.dp))
    MacroSegments(shares.map { it.goalPct }, colors, height = 6.dp, alpha = .6f)
    Spacer(Modifier.height(3.dp))
    MacroPercentRow(shares.map { it.goalPct }, colors, alpha = .6f)
    Spacer(Modifier.height(2.dp))
    MacroBarLabel(tr("Consigliate"))

    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        shares.forEachIndexed { i, share ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i]))
                Spacer(Modifier.width(5.dp))
                Text(
                    share.macro.label + " " + share.eatenGrams.toInt() + "/" + share.goalGrams + " g",
                    color = UnicumDark.copy(alpha = .7f), style = UnicumType.Micro, maxLines = 1
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        MacroBalance.nextMealHint(shares, s.remaining),
        color = UnicumDark, style = UnicumType.Detail, lineHeight = 18.sp, fontWeight = FontWeight.Medium
    )
}

@Composable
private fun MacroBarLabel(text: String) =
    Text(text, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, fontWeight = FontWeight.Medium)

/** Percentuali sopra o sotto le barre, allineate ai segmenti. */
@Composable
private fun MacroPercentRow(pcts: List<Int>, colors: List<Color>, alpha: Float = 1f) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        pcts.forEachIndexed { i, pct ->
            Text(
                "$pct%",
                modifier = Modifier.weight(segmentWeight(pct)),
                color = UnicumDark.copy(alpha = alpha),
                style = UnicumType.Detail, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MacroSegments(pcts: List<Int>, colors: List<Color>, height: androidx.compose.ui.unit.Dp, alpha: Float = 1f) {
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        pcts.forEachIndexed { i, pct ->
            Box(
                Modifier
                    .weight(segmentWeight(pct))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2.2f))
                    .background(colors[i].copy(alpha = alpha))
            )
        }
    }
}

/** Anche un macro allo 0% mantiene una piccola quota, così etichetta e segmento restano visibili. */
private fun segmentWeight(pct: Int): Float = pct.coerceAtLeast(6).toFloat()

/**
 * Intestazione che apre un argomento del Diario: titolo grande, riepilogo a destra e una linea sotto,
 * con più aria sopra così le sezioni risultano chiaramente separate.
 */
@Composable
internal fun SectionHeader(title: String, summary: String? = null, first: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = if (first) 4.dp else 30.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(title, color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            if (summary != null) {
                Text(summary, color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(50)).background(UnicumAccent.copy(alpha = .55f)))
    }
}

/**
 * "Giornata completa": si segna quando si è registrato tutto quello che si è mangiato. Da quando se ne segna
 * una, medie del deficit e peso stimato (Statistiche, racconto della settimana) contano solo i giorni completi.
 */
@Composable
internal fun CompleteDayCard(date: String) {
    val complete = UserSettings.isDayComplete(date)
    val haptics = io.github.peol13.unicum.ui.components.rememberHaptics()
    UnicumCard(onClick = { haptics.tick(); UserSettings.setDayComplete(date, !complete) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (complete) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null, tint = UnicumPrimary, modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Giornata completa"), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
                Text(
                    if (complete) tr("Hai registrato tutto: questo giorno conta nelle medie e nel peso stimato.")
                    else tr("Hai registrato tutto quello che hai mangiato? Segnalo: medie e peso stimato useranno solo i giorni completi."),
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption
                )
            }
        }
    }
}
