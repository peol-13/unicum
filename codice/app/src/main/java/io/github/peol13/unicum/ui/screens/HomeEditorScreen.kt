package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.ui.Route

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.peol13.unicum.data.HomeCard
import io.github.peol13.unicum.data.HomeLayout
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.components.rememberHaptics
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSurface
import io.github.peol13.unicum.ui.theme.UnicumType

private val RowHeight = 64.dp
private val RowGap = 8.dp

/** Icona di una card della Home, la stessa delle card vere. */
internal fun homeCardIcon(card: HomeCard): ImageVector = when (card) {
    HomeCard.BANNER -> Icons.Rounded.ViewAgenda
    HomeCard.CALORIE, HomeCard.CALORIE_COMPATTA -> Icons.Rounded.LocalFireDepartment
    HomeCard.ATTIVITA, HomeCard.ATTIVITA_SONNO -> Icons.AutoMirrored.Rounded.DirectionsRun
    HomeCard.SONNO -> Icons.Rounded.Bedtime
    HomeCard.RECUPERO_SONNO, HomeCard.RECUPERO_ATTIVITA -> Icons.Rounded.Bolt
    HomeCard.PASTI -> Icons.Rounded.Restaurant
    HomeCard.ALLENAMENTI -> Icons.Rounded.FitnessCenter
    HomeCard.PESO -> Icons.Rounded.MonitorWeight
}

/**
 * Ordine delle card della Home: si trascinano dalla maniglia, si tolgono con − e si aggiungono con +.
 * Ogni modifica si salva subito; "Ripristina" torna all'ordine della modalità scelta.
 */
@Composable
fun HomeEditorScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    var items by remember { mutableStateOf(UserSettings.homeCards) }
    var dragging by remember { mutableStateOf<HomeCard?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val stepPx = with(LocalDensity.current) { (RowHeight + RowGap).toPx() }
    val haptics = rememberHaptics()

    fun update(cards: List<HomeCard>) {
        items = cards
        UserSettings.saveHomeLayout(cards)
    }

    UnicumScreen(selectedItem = Route.Home, onNavigate = onNavigate) {
        PageHeader("Personalizza la Home", "Trascina dalla maniglia per cambiare l'ordine", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        UnicumCard {
            CardHeader(Icons.Rounded.Tune, "Nella Home")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
                items.forEach { card ->
                    key(card) {
                        val isDragging = dragging == card
                        CardRow(
                            card,
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                                .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(16.dp)),
                            handle = {
                                Icon(
                                    Icons.Rounded.DragIndicator, "Trascina per spostare",
                                    tint = UnicumDark.copy(alpha = .45f),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(8.dp)
                                        .pointerInput(card) {
                                            detectDragGestures(
                                                onDragStart = { dragging = card; dragOffset = 0f; haptics.tick() },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragOffset += amount.y
                                                    val i = items.indexOf(card)
                                                    // Oltre metà riga si scambia con la vicina e lo spostamento riparte da lì
                                                    if (dragOffset > stepPx / 2 && i < items.lastIndex) {
                                                        items = HomeLayout.move(items, i, i + 1); dragOffset -= stepPx; haptics.tick()
                                                    } else if (dragOffset < -stepPx / 2 && i > 0) {
                                                        items = HomeLayout.move(items, i, i - 1); dragOffset += stepPx; haptics.tick()
                                                    }
                                                },
                                                onDragEnd = { dragging = null; dragOffset = 0f; update(items) },
                                                onDragCancel = { dragging = null; dragOffset = 0f; update(items) }
                                            )
                                        }
                                )
                            },
                            action = if (items.size > 1) ({ RoundAction(add = false) { update(items - card) } }) else null
                        )
                    }
                }
            }
        }

        val hidden = HomeLayout.hidden(items)
        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            UnicumCard {
                CardHeader(Icons.Rounded.Add, "Da aggiungere")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    hidden.forEach { card ->
                        CardRow(card, action = { RoundAction(add = true) { update(items + card) } })
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { UserSettings.saveHomeLayout(null); items = UserSettings.homeCards },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                "Ripristina l'ordine della modalità ${UserSettings.dashboardMode.label}",
                color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CardRow(
    card: HomeCard,
    modifier: Modifier = Modifier,
    handle: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(shape)
            .background(UnicumSurface)
            .border(1.dp, UnicumAccent.copy(alpha = .45f), shape)
            .padding(start = if (handle != null) 2.dp else 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        handle?.invoke()
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(UnicumAccent.copy(alpha = .35f)),
            contentAlignment = Alignment.Center
        ) { Icon(homeCardIcon(card), null, tint = UnicumPrimary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(card.label, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.description, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        action?.invoke()
    }
}

@Composable
private fun RoundAction(add: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (add) UnicumPrimary.copy(alpha = .12f) else UnicumAccent.copy(alpha = .3f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (add) Icons.Rounded.Add else Icons.Rounded.Remove,
            if (add) "Aggiungi alla Home" else "Togli dalla Home",
            tint = UnicumPrimary, modifier = Modifier.size(18.dp)
        )
    }
}
