package io.github.peol13.unicum.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.i18n.tr
import io.github.peol13.unicum.ui.components.DeficitLine
import io.github.peol13.unicum.ui.components.IconBadge
import io.github.peol13.unicum.ui.components.RingGauge
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSurface
import io.github.peol13.unicum.ui.theme.UnicumType
import kotlinx.coroutines.launch

/** Una scheda della guida: anteprima in alto, titolo e punti brevi. */
private class TourPage(
    val icon: ImageVector,
    val title: String,
    val points: List<String>,
    val preview: (@Composable () -> Unit)? = null
)

/**
 * Guida rapida alle funzioni principali, a schede da scorrere: Home e calorie, registrazione dei pasti (il + che
 * aggiunge l'ultima quantità, il tocco che apre la scelta), sonno e recupero, allenamenti, Diario e Statistiche.
 * Compare una volta dopo il primo avvio e si riapre da Profilo → Aiuto → Guida rapida.
 */
@Composable
fun TourScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pages = listOf(
        TourPage(
            Icons.Rounded.Home, tr("La tua giornata"),
            listOf(
                tr("L'anello mostra le calorie mangiate rispetto all'obiettivo del giorno."),
                tr("La linea colorata è il deficit: verde obiettivo raggiunto, giallo in deficit, rosso surplus. Il consumo a riposo cresce durante il giorno, così il conto è giusto a ogni ora."),
                tr("Tocca una card per i dettagli; tienila premuta per riordinare la Home.")
            ),
            preview = { CaloriesPreview() }
        ),
        TourPage(
            Icons.Rounded.Restaurant, tr("Registrare un pasto"),
            listOf(
                tr("Tocca il + in basso a destra, poi Aggiungi pasto. In cima trovi quello che mangi di solito a quel pasto."),
                tr("Il + di un alimento lo aggiunge subito con l'ultima quantità che hai usato (o una porzione media)."),
                tr("Tocca il nome per scegliere porzioni o grammi."),
                tr("Il pasto sta nella barra in basso: toccala per correggere, Salva per registrare. Il codice a barre è accanto alla ricerca.")
            ),
            preview = { MealPreview() }
        ),
        TourPage(
            Icons.Rounded.Bedtime, tr("Sonno e recupero"),
            listOf(
                tr("La card Sonno mostra durata, orari e fasi della notte: tocca il grafico per leggere ogni fase."),
                tr("Il recupero confronta l'HRV della notte con la tua media; in Allenamenti c'è la stima di quando sei di nuovo pronto."),
                tr("Arrivano da anello e orologio tramite Health Connect: senza, queste card restano vuote.")
            )
        ),
        TourPage(
            Icons.AutoMirrored.Rounded.DirectionsRun, tr("Allenamenti"),
            listOf(
                tr("Corse, pesi e corpo libero arrivano dall'orologio, con mappa, parziali e battito."),
                tr("Nella scheda Programma metti il tuo piano (PDF, Notion o a mano): le sedute fatte si segnano da sole."),
                tr("Per i pesi gli esercizi vengono dal programma: se hai fatto altro, li correggi nella pagina della seduta.")
            )
        ),
        TourPage(
            Icons.Rounded.EditNote, tr("Diario"),
            listOf(
                tr("Tutta la giornata nel dettaglio: calorie, pasti, attività, sonno e recupero."),
                tr("Tocca un alimento per cambiare quantità o orario; copia un pasto in un altro giorno o ripeti quello di ieri."),
                tr("Segna la giornata come completa quando hai registrato tutto. Scorri o usa le frecce per gli altri giorni.")
            )
        ),
        TourPage(
            Icons.Rounded.BarChart, tr("Statistiche"),
            listOf(
                tr("La settimana da lunedì a domenica: deficit medio e peso stimato, attività, sonno e recupero."),
                tr("Le frecce portano alle settimane precedenti; il lunedì trovi il racconto della settimana appena finita."),
                tr("Le correlazioni ti dicono, per esempio, come dormi dopo gli allenamenti.")
            )
        )
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(
        Modifier.fillMaxSize().background(UnicumBackground).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Guida rapida"), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            TextButton(onClick = onClose) { Text(tr("Salta"), color = UnicumDark.copy(alpha = .6f)) }
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f)) { i ->
            val page = pages[i]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    if (page.preview != null) page.preview.invoke()
                    else IconBadge(page.icon, size = 72.dp)
                    Spacer(Modifier.height(22.dp))
                    Text(page.title, color = UnicumDark, style = UnicumType.Screen, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    page.points.forEach { p ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 8.dp).size(7.dp).clip(CircleShape).background(UnicumPrimary))
                            Spacer(Modifier.width(12.dp))
                            Text(p, color = UnicumDark.copy(alpha = .85f), style = UnicumType.Body, lineHeight = 22.sp)
                        }
                    }
                }
            }
        }
        // Pallini delle schede e tasto avanti
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 9.dp else 7.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) UnicumPrimary else UnicumAccent.copy(alpha = .6f))
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                UnicumButton(
                    if (last) tr("Inizia a usare UNICUM") else tr("Avanti"),
                    onClick = { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                )
            }
        }
    }
}

/** Anteprima della card Calorie: anello e linea del deficit con valori d'esempio. */
@Composable
private fun CaloriesPreview() {
    UnicumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(progress = .62f, size = 110.dp, stroke = 11.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(io.github.peol13.unicum.i18n.lnum(1120), color = UnicumDark, style = UnicumType.Card, fontWeight = FontWeight.SemiBold)
                    Text("/ ${io.github.peol13.unicum.i18n.lnum(1800)} kcal", color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("420 kcal", color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
                Text(tr("deficit"), color = UnicumPrimary, style = UnicumType.Detail)
                Spacer(Modifier.height(10.dp))
                DeficitLine(deficit = 420, goalDeficit = 500)
            }
        }
    }
}

/**
 * Anteprima della riga di un alimento nel foglio del pasto: il + pulsa ("1 tocco = ultima quantità"), il nome dice
 * "tocca per scegliere"; sotto, la barra del pasto con Salva.
 */
@Composable
private fun MealPreview() {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(UnicumSurface)
                .border(1.dp, UnicumAccent.copy(alpha = .5f), RoundedCornerShape(20.dp))
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(Icons.Rounded.Spa, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(io.github.peol13.unicum.data.FoodNames.label("Mela"), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
                Text(tr("Tocca il nome: porzioni o grammi"), color = UnicumPrimary, style = UnicumType.Note, fontWeight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("52", color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
                Text("kcal", color = UnicumDark.copy(alpha = .5f), style = UnicumType.Micro)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(34.dp).scale(pulse).clip(CircleShape).background(UnicumPrimary),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Add, null, tint = UnicumOnPrimary, modifier = Modifier.size(20.dp)) }
        }
        Text(
            "+ = " + tr("aggiunge l'ultima quantità che hai usato"),
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, modifier = Modifier.align(Alignment.End)
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(UnicumPrimary)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tr("2 alimenti · 310 kcal"), color = UnicumOnPrimary, style = UnicumType.Body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandLess, null, tint = UnicumOnPrimary)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(UnicumOnPrimary).padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text(tr("Salva"), color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold) }
        }
    }
}
