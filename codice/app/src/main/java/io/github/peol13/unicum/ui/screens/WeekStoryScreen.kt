package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.tr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.app.Activity
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.MetricType
import io.github.peol13.unicum.data.StoryPage
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.data.WeekStory
import io.github.peol13.unicum.ui.components.UnicumGradient
import io.github.peol13.unicum.ui.theme.UnicumType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** La settimana appena finita (quella prima della settimana in corso). */
fun lastCompletedWeek(): LocalDate = DateUtils.weekStart(DateUtils.today()).minusWeeks(1)

/**
 * Racconto della settimana: pagine a scorrimento su fondo teal con un numero grande e una frase ciascuna
 * (deficit, attività, sonno, recupero, peso e un consiglio). Toccando a destra si va avanti, a sinistra indietro.
 */
@Composable
fun WeekStoryScreen(monday: LocalDate, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    LaunchedEffect(monday) { UserSettings.markWeekStorySeen(monday.toString()) }

    // Sul fondo teal le icone della barra di stato vanno chiare; all'uscita torna l'impostazione del tema
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = wasLight }
    }

    val pages by produceState<List<StoryPage>?>(null, monday) {
        val days = UnicumRepository.loadWeekStats(context, monday)
        val span = ChronoUnit.DAYS.between(monday, DateUtils.today()).toInt() + 1
        val weights = UnicumRepository.loadMetricSeries(context, MetricType.WEIGHT_KG, span.coerceAtLeast(7))
            .mapKeys { DateUtils.parse(it.key) }
            .filterKeys { !it.isBefore(monday) && it.isBefore(monday.plusDays(7)) }
        // Con le giornate complete il racconto usa solo quelle per il deficit
        value = WeekStory.build(days.map { if (it.countsForAverages) it else it.copy(deficitKcal = null) }, weights, UserSettings.targetDeficitKcal)
    }

    Box(Modifier.fillMaxSize().background(UnicumGradient)) {
        val list = pages
        if (list != null) {
            val pager = rememberPagerState { list.size }
            val scope = rememberCoroutineScope()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Segmenti in alto, uno per pagina, e chiusura
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        list.indices.forEach { i ->
                            Box(
                                Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = if (i <= pager.currentPage) .9f else .3f))
                            )
                        }
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, tr("Chiudi"), tint = Color.White) }
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                    Box(
                        Modifier.fillMaxSize().clickable {
                            scope.launch { if (index < list.lastIndex) pager.animateScrollToPage(index + 1) else onClose() }
                        }
                    ) { StoryPageContent(list[index]) }
                }
                Text(
                    if (pager.currentPage < list.lastIndex) tr("Tocca o scorri per andare avanti") else tr("Tocca per chiudere"),
                    color = Color.White.copy(alpha = .7f), style = UnicumType.Caption,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun StoryPageContent(page: StoryPage) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)),
            contentAlignment = Alignment.Center
        ) { Icon(iconFor(page.kind), null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(24.dp))
        Text(page.title, color = Color.White.copy(alpha = .85f), style = UnicumType.Heading, fontWeight = FontWeight.Medium)
        if (page.value.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(page.value, color = Color.White, style = UnicumType.Display, fontWeight = FontWeight.SemiBold)
                if (page.unit.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(page.unit, color = Color.White.copy(alpha = .8f), style = UnicumType.Card, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(page.sentence, color = Color.White, style = UnicumType.Heading, lineHeight = 27.sp)
    }
}

private fun iconFor(kind: StoryPage.Kind): ImageVector = when (kind) {
    StoryPage.Kind.INTRO -> Icons.Rounded.AutoStories
    StoryPage.Kind.DEFICIT -> Icons.Rounded.LocalFireDepartment
    StoryPage.Kind.ATTIVITA -> Icons.AutoMirrored.Rounded.DirectionsRun
    StoryPage.Kind.SONNO -> Icons.Rounded.Bedtime
    StoryPage.Kind.RECUPERO -> Icons.Rounded.Bolt
    StoryPage.Kind.PESO -> Icons.Rounded.MonitorWeight
    StoryPage.Kind.PROSSIMA -> Icons.Rounded.TipsAndUpdates
}

/** Invito nella Home, il lunedì e il martedì, finché il racconto della settimana appena finita non è stato aperto. */
@Composable
internal fun WeekStoryTeaser(onOpen: () -> Unit) {
    val monday = lastCompletedWeek()
    val today = DateUtils.today()
    val showDays = today.dayOfWeek.value <= 2
    if (!showDays || UserSettings.weekStorySeen == monday.toString()) return
    // Solo se quella settimana ha qualcosa da raccontare (chi ha appena installato l'app non ha dati)
    val context = LocalContext.current
    val hasData by produceState<Boolean?>(null, monday) {
        value = runCatching {
            UnicumRepository.loadWeekStats(context, monday).any {
                it.eatenKcal > 0 || it.activityMinutes > 0 || it.workouts > 0 || it.sleepMinutes != null
            }
        }.getOrDefault(false)
    }
    if (hasData != true) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(UnicumAccent.copy(alpha = .28f))
            .clickable { onOpen() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AutoStories, null, tint = UnicumPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("La tua settimana è pronta"), color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
            Text(
                tr("%s: deficit, attività, sonno e peso in un colpo d'occhio", DateUtils.weekLabel(monday)),
                color = UnicumDark.copy(alpha = .65f), style = UnicumType.Caption, lineHeight = 17.sp
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = UnicumPrimary)
    }
    Spacer(Modifier.height(12.dp))
}
