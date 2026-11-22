package io.github.peol13.unicum.ui.components

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.ui.Route

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import io.github.peol13.unicum.ui.theme.UnicumTrack
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumSurface
import io.github.peol13.unicum.ui.theme.BrandPrimary
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSecondary
import io.github.peol13.unicum.ui.theme.UnicumType

/*
 * Componenti condivisi: tutte le schermate li usano così card, titoli, pulsanti e
 * indicatori hanno lo stesso aspetto (card panna/bianche arrotondate, bordo discreto,
 * icone lineari arrotondate, teal come unico colore d'azione).
 */

val UnicumCardShape = RoundedCornerShape(22.dp)

// Colori della linea del deficit calorico
val DeficitGreen = Color(0xFF27C978)
val DeficitYellow = Color(0xFFF2C94C)
val DeficitRed = Color(0xFFE85D5D)

/** Rosso tenue per eliminare e per gli errori nei moduli. */
val DeleteRed = Color(0xFFC25555)

val UnicumGradient: Brush
    get() = Brush.linearGradient(listOf(BrandPrimary, Color(0xFF0A7C86)))

// ------------------------------------------------------------------
// Vibrazione
// ------------------------------------------------------------------

/**
 * Vibrazione leggera: [tick] per i piccoli passi (− / +, segmenti, cambio giorno), [confirm] quando
 * si salva qualcosa. Usa il feedback di sistema, che rispetta l'impostazione "vibrazione al tocco".
 */
class Haptics(private val view: View) {
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

// ------------------------------------------------------------------
// Struttura schermata
// ------------------------------------------------------------------

/** Scaffold comune: barra di navigazione, FAB opzionale, contenuto scrollabile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnicumScreen(
    selectedItem: Route,
    onNavigate: (Route) -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    /** Se presente, tirando verso il basso compare la rotella di aggiornamento e si chiama questa funzione. */
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    /** Se presente, uno swipe orizzontale cambia giorno: -1 = precedente, +1 = successivo; false se non si può. */
    onSwipeDay: ((Int) -> Boolean)? = null,
    /** Posizione dello scorrimento: chi mostra più pagine nella stessa schermata ne passa una per pagina. */
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val haptics = rememberHaptics()
    Scaffold(
        containerColor = UnicumBackground,
        bottomBar = { BottomNavigationBar(selectedItem, onNavigate) },
        floatingActionButton = floatingActionButton
    ) { innerPadding ->
        val body: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UnicumBackground)
                    .then(if (onSwipeDay != null) Modifier.daySwipe(onSwipeDay) else Modifier)
                    .verticalScroll(scrollState)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp)
            ) {
                // Le card possono aprire altre schermate (es. il Profilo dagli stati vuoti)
                CompositionLocalProvider(LocalNavigate provides onNavigate) { content() }
            }
        }
        if (onRefresh != null) {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { haptics.confirm(); onRefresh() },
                state = pullState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                // Rotella nei colori dell'app (di default sarebbe lilla)
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = UnicumSurface,
                        color = UnicumPrimary
                    )
                },
                content = { body() }
            )
        } else {
            Box(Modifier.fillMaxSize().padding(innerPadding)) { body() }
        }
    }
}

/**
 * Come [UnicumScreen] ma per elenchi lunghi: il contenuto è una lista lazy (si disegnano solo le righe a schermo).
 * Stessi margini e stessa barra in basso.
 */
@Composable
fun UnicumLazyScreen(
    selectedItem: Route,
    onNavigate: (Route) -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        containerColor = UnicumBackground,
        bottomBar = { BottomNavigationBar(selectedItem, onNavigate) }
    ) { innerPadding ->
        CompositionLocalProvider(LocalNavigate provides onNavigate) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).background(UnicumBackground),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
                content = content
            )
        }
    }
}

/**
 * Swipe tra i giorni: il contenuto segue un po' il dito e, oltre una soglia, si passa al giorno prima
 * (verso destra) o dopo (verso sinistra). I grafici toccabili (ipnogramma, peso) hanno la precedenza.
 */
@Composable
private fun Modifier.daySwipe(onSwipe: (Int) -> Boolean): Modifier {
    val threshold = with(LocalDensity.current) { 90.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    return this
        .graphicsLayer {
            translationX = offset.value
            // Dissolvenza leggera mentre scorre
            alpha = 1f - (abs(offset.value) / size.width).coerceIn(0f, .35f)
        }
        .pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    val dir = if (total < 0) 1 else -1
                    val width = size.width.toFloat()
                    scope.launch {
                        if (abs(total) > threshold && currentOnSwipe(dir)) {
                            haptics.tick()
                            // Il vecchio giorno esce, il nuovo entra dal lato opposto
                            offset.animateTo(-dir * width * .35f, tween(110, easing = FastOutLinearInEasing))
                            offset.snapTo(dir * width * .35f)
                            offset.animateTo(0f, tween(260, easing = LinearOutSlowInEasing))
                        } else {
                            offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
                onHorizontalDrag = { change, amount ->
                    total += amount
                    change.consume()
                    scope.launch { offset.snapTo(total * .35f) }
                }
            )
        }
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = UnicumDark, style = UnicumType.Screen, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Body)
        }
        trailing()
    }
}

// ------------------------------------------------------------------
// Card e intestazioni
// ------------------------------------------------------------------

/** Se presente, tenendo premuta una card o un banner si chiama questa funzione (nella Home: modifica delle card). */
val LocalCardLongClick = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Tocco e pressione lunga di card e banner: la pressione lunga vibra e c'è solo dove [LocalCardLongClick] è fornito. */
@Composable
private fun Modifier.cardClicks(onClick: (() -> Unit)?): Modifier {
    val onLong = LocalCardLongClick.current
    val haptics = rememberHaptics()
    return when {
        onLong != null -> combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = if (onClick != null) LocalIndication.current else null,
            onClick = { onClick?.invoke() },
            onLongClick = { haptics.confirm(); onLong() }
        )
        onClick != null -> clickable { onClick() }
        else -> this
    }
}

@Composable
fun UnicumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .clip(UnicumCardShape)
        .background(UnicumSurface.copy(alpha = .92f))
        .cardClicks(onClick)
    Surface(
        modifier = base,
        color = Color.Transparent,
        shape = UnicumCardShape,
        border = BorderStroke(1.dp, UnicumAccent.copy(alpha = .35f))
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** Bottone tondo con un'icona (calendario, indietro...), sulla superficie delle card. */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(UnicumSurface.copy(alpha = .92f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = UnicumDark, modifier = Modifier.size(22.dp))
    }
}

/**
 * Finestra di dialogo dell'app: fondo crema arrotondato, largo quasi quanto lo schermo,
 * contenuto in colonna che scorre se non ci sta.
 */
@Composable
fun UnicumDialog(
    onDismiss: () -> Unit,
    spacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(28.dp), color = UnicumBackground) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content
            )
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(UnicumAccent.copy(alpha = .35f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = UnicumPrimary, modifier = Modifier.size(size * .58f))
    }
}

/** Se true, le card senza azione non mostrano la propria intestazione (il titolo è già sopra, in una sezione). */
val LocalHideCardTitle = compositionLocalOf { false }

/** Intestazione di una card: icona + titolo, con azione facoltativa a destra ("Dettagli ›"). */
@Composable
fun CardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    if (LocalHideCardTitle.current && action == null) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(10.dp))
            Text(title, color = UnicumDark, style = UnicumType.Card, fontWeight = FontWeight.SemiBold)
        }
        if (action != null) {
            Row(
                modifier = Modifier.clickable { onAction() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(action, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
                Icon(
                    Icons.Rounded.ChevronRight, null,
                    tint = UnicumDark.copy(alpha = .55f), modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Banner teal con messaggio principale in alto nella dashboard. */
@Composable
fun TealBanner(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(UnicumCardShape)
            .background(UnicumGradient)
            .cardClicks(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text, color = Color.White.copy(alpha = .82f), style = UnicumType.Caption, lineHeight = 17.sp)
        }
        if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

// ------------------------------------------------------------------
// Pulsanti
// ------------------------------------------------------------------

@Composable
fun UnicumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = UnicumPrimary,
            contentColor = UnicumOnPrimary,
            disabledContainerColor = UnicumPrimary.copy(alpha = .30f),
            disabledContentColor = UnicumOnPrimary.copy(alpha = .85f)
        )
    ) {
        Text(text, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Riga "Mostra dettagli ⌄" dentro una card: apre e chiude una parte secondaria (il contenuto va messo
 * sotto con `if (open)`). Sopra c'è una linea sottile che la separa dal resto della card.
 */
@Composable
fun ExpandRow(open: Boolean, label: String, onToggle: () -> Unit, openLabel: String = tr("Nascondi")) {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(UnicumAccent.copy(alpha = .35f)))
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (open) openLabel else label, color = UnicumPrimary, style = UnicumType.Caption,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
        )
        Icon(
            if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null,
            tint = UnicumPrimary, modifier = Modifier.size(20.dp)
        )
    }
}

/** Card-pulsante che porta a un'altra pagina (es. "Tutte le corse"): icona, titolo, sottotitolo, freccia. */
@Composable
fun LinkCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    UnicumCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = UnicumDark.copy(alpha = .45f), modifier = Modifier.size(22.dp))
        }
    }
}

/** Chip selezionabile (filtri, tipo pasto, scelta schermata...); [compact] = più piccolo, per le categorie. */
@Composable
fun UnicumChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) UnicumPrimary else UnicumSurface)
            .border(1.dp, if (selected) UnicumPrimary else UnicumAccent.copy(alpha = .6f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) UnicumOnPrimary else UnicumDark,
            style = if (compact) UnicumType.Caption else UnicumType.Detail,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Selettore a segmenti di uguale larghezza (tipo di pasto, quantità rapide...): occupa tutta la riga
 * e non va mai oltre il bordo, anche su schermi stretti. [selected] = null: nessuno scelto.
 */
@Composable
fun UnicumSegmented(
    options: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    style: androidx.compose.ui.text.TextStyle = UnicumType.Detail
) {
    val shape = RoundedCornerShape(50)
    val haptics = rememberHaptics()
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(UnicumSurface)
            .border(1.dp, UnicumAccent.copy(alpha = .6f), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (on) UnicumPrimary else Color.Transparent)
                    .clickable { if (i != selected) haptics.tick(); onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (on) UnicumOnPrimary else UnicumDark,
                    style = style,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun UnicumFab(onClick: () -> Unit, expanded: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(58.dp),
        shape = CircleShape,
        color = UnicumPrimary,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (expanded) Icons.Rounded.Close else Icons.Rounded.Add,
                null, tint = UnicumOnPrimary, modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun unicumFieldColors() = OutlinedTextFieldDefaults.colors(
    // testo scritto dall'utente: scuro (o chiaro in tema scuro), ben contrastato
    focusedTextColor = UnicumDark,
    unfocusedTextColor = UnicumDark,
    // bordo visibile anche a campo non selezionato
    focusedBorderColor = UnicumPrimary,
    unfocusedBorderColor = UnicumDark.copy(alpha = .35f),
    focusedLabelColor = UnicumPrimary,
    unfocusedLabelColor = UnicumDark.copy(alpha = .75f),
    focusedSuffixColor = UnicumDark.copy(alpha = .8f),
    unfocusedSuffixColor = UnicumDark.copy(alpha = .8f),
    focusedPlaceholderColor = UnicumDark.copy(alpha = .55f),
    unfocusedPlaceholderColor = UnicumDark.copy(alpha = .55f),
    focusedLeadingIconColor = UnicumPrimary,
    unfocusedLeadingIconColor = UnicumPrimary,
    focusedTrailingIconColor = UnicumDark.copy(alpha = .7f),
    unfocusedTrailingIconColor = UnicumDark.copy(alpha = .7f),
    cursorColor = UnicumPrimary,
    focusedContainerColor = UnicumSurface,
    unfocusedContainerColor = UnicumSurface
)

/** Testo dei campi: più grande e un po' più marcato del testo normale (mantiene Montserrat). */
@Composable
fun unicumFieldTextStyle() = UnicumType.Heading.copy(fontWeight = FontWeight.Medium)

// ------------------------------------------------------------------
// Indicatori
// ------------------------------------------------------------------

private val GrowEasing = FastOutSlowInEasing

/**
 * Valore animato: alla prima comparsa parte da 0 e cresce fino a [target], ai cambi successivi si sposta
 * con una transizione breve. Con "Rimuovi animazioni" attivo nel telefono Compose salta l'animazione.
 */
@Composable
fun animatedFraction(target: Float, durationMillis: Int = 900): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateFloatAsState(
        if (started) target else 0f, tween(durationMillis, easing = GrowEasing), label = "fraction"
    )
    return value
}

/** Numero intero che "conta" fino al valore (calorie, minuti) con la stessa regola di [animatedFraction]. */
@Composable
fun animatedCount(target: Int, durationMillis: Int = 900): Int {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateIntAsState(
        if (started) target else 0, tween(durationMillis, easing = GrowEasing), label = "count"
    )
    return value
}

/** Anello di progresso con sfumatura teal; il contenuto va al centro. */
@Composable
fun RingGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    stroke: Dp = 13.dp,
    content: @Composable () -> Unit
) {
    val trackColor = UnicumTrack
    val ringStart = UnicumSecondary
    val ringEnd = UnicumPrimary
    val animated = animatedFraction(progress.coerceIn(0f, 1f))
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val inset = s / 2f
            val arcSize = Size(this.size.width - s, this.size.height - s)
            drawArc(
                trackColor, -90f, 360f, false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(s, cap = StrokeCap.Round)
            )
            val p = animated
            if (p > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to ringStart,
                        p.coerceAtLeast(.05f) to ringEnd,
                        1f to ringEnd
                    ),
                    startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(s, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** Barra sottile di avanzamento con sfumatura. */
@Composable
fun ThinProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = UnicumPrimary
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = .16f))
    ) {
        val target = fraction.coerceIn(0f, 1f)
        val f = animatedFraction(if (target > 0f) target.coerceAtLeast(.03f) else 0f)
        if (f > 0.001f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = .65f))))
            )
        }
    }
}

/** Mini grafico a barre (es. ultimi 7 giorni). L'ultima barra è evidenziata. */
@Composable
fun MiniBars(
    values: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp
) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            val last = i == values.lastIndex
            val frac = (v.toFloat() / max).coerceIn(0f, 1f)
            // Le barre crescono dal basso, una dopo l'altra
            val h = animatedFraction(if (v == 0) .06f else frac.coerceAtLeast(.12f), durationMillis = 600 + i * 60)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(h.coerceAtLeast(.02f))
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        if (last) UnicumPrimary
                        else UnicumSecondary.copy(alpha = if (v == 0) .25f else .7f)
                    )
            )
        }
    }
}

/**
 * Linea del deficit: verde = deficit pari o superiore all'obiettivo, giallo = in deficit ma
 * sotto l'obiettivo, rosso = surplus. Il triangolo indica a che punto sei.
 *
 * @param deficit calorie bruciate meno calorie assunte (negativo = surplus)
 * @param goalDeficit deficit obiettivo in kcal (positivo)
 */
@Composable
fun DeficitLine(deficit: Int, goalDeficit: Int, modifier: Modifier = Modifier) {
    val balance = -deficit
    val goal = if (goalDeficit > 0) goalDeficit.toFloat() else 500f
    val minimum = -2f * goal
    val maximum = goal
    val normalized = animatedFraction(((balance - minimum) / (maximum - minimum)).coerceIn(0f, 1f))
    val markerColor = UnicumDark
    // zone: [-2g, -g] verde · [-g, 0] giallo · [0, +g] rosso
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(9.dp)) {
            val x = normalized * size.width
            val path = Path().apply {
                moveTo(x, size.height)
                lineTo(x - 5.dp.toPx(), 0f)
                lineTo(x + 5.dp.toPx(), 0f)
                close()
            }
            drawPath(path, markerColor)
        }
        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().background(DeficitGreen))
            Box(Modifier.weight(1f).fillMaxHeight().background(DeficitYellow))
            Box(Modifier.weight(1f).fillMaxHeight().background(DeficitRed))
        }
    }
}
