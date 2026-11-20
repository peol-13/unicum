package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.lfmt

import io.github.peol13.unicum.i18n.trp

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.ui.Route

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.RunProgress
import io.github.peol13.unicum.data.Shoe
import io.github.peol13.unicum.data.ShoeStats
import io.github.peol13.unicum.data.Shoes
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.data.WorkoutDetail
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.DeleteRed
import io.github.peol13.unicum.ui.components.LinkCard
import io.github.peol13.unicum.ui.components.ThinProgress
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.ui.components.UnicumDialog
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.components.rememberHaptics
import io.github.peol13.unicum.ui.components.unicumFieldColors
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType
import io.github.peol13.unicum.ui.theme.runKindColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Tono "da cambiare" della barra dei km (sabbia, come i peggioramenti nei confronti). */
private val WornColor = Color(0xFFC9A66B)

private fun km1(km: Double): String = lfmt("%.1f", km)
private fun kmInt(km: Double): String = lfmt("%,d", km.roundToInt())

/** Statistiche di tutte le scarpe sulle corse [data] (null = ancora in caricamento). */
@Composable
private fun rememberShoeStats(data: RunList?): List<ShoeStats.Stats> {
    val shoes = Shoes.data
    return remember(data, shoes) {
        val runs = data?.runs.orEmpty()
        val kinds = data?.feedback.orEmpty().mapValues { it.value.kind }
        shoes.shoes.map { ShoeStats.of(it, runs, shoes, kinds, DateUtils.today()) }
    }
}

// ------------------------------------------------------------------
// Nella pagina della corsa
// ------------------------------------------------------------------

/** Scelta della scarpa usata in una corsa, con i km fatti finora da quella scarpa. */
@Composable
internal fun ShoePickerCard(run: WorkoutDetail, onManage: () -> Unit, onOpenShoe: (String) -> Unit) {
    val data = Shoes.data
    val current = data.shoeOf(run.externalId)
    val haptics = rememberHaptics()
    UnicumCard {
        CardHeader(Icons.AutoMirrored.Rounded.DirectionsRun, tr("Scarpe"), action = tr("Gestisci"), onAction = onManage)
        Spacer(Modifier.height(12.dp))
        if (data.shoes.isEmpty()) {
            Text(
                tr("Registra le tue scarpe per sapere quanti km hanno fatto e quando cambiarle."),
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            UnicumChip(tr("Aggiungi scarpa"), false, onManage, compact = true)
            return@UnicumCard
        }
        // Le scarpe in uso; una ritirata resta visibile se è quella di questa corsa
        val options = data.shoes.filter { !it.retired || it.id == current?.id }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options.size) { i ->
                val s = options[i]
                UnicumChip(s.name, current?.id == s.id, {
                    haptics.tick()
                    Shoes.assign(run.externalId, if (current?.id == s.id) null else s.id)
                }, compact = true)
            }
            item { UnicumChip(tr("Nessuna"), current == null, { Shoes.assign(run.externalId, null) }, compact = true) }
        }
        if (current != null) {
            val km = rememberShoeStats(rememberRunList()).firstOrNull { it.shoe.id == current.id }?.km
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onOpenShoe(current.id) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    current.name + (km?.let { ": " + tr("%s km su %d", km1(it), current.limitKm) } ?: ""),
                    color = UnicumPrimary, style = UnicumType.Caption, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.ChevronRight, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
            }
        } else if (data.default == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Imposta una scarpa predefinita: le prossime corse le verranno assegnate da sole."),
                color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp
            )
        }
    }
}

// ------------------------------------------------------------------
// Nella sezione Allenamenti
// ------------------------------------------------------------------

/** Le scarpe in uso con i km verso il limite; tocco = pagina della scarpa. */
@Composable
internal fun ShoesSummaryCard(data: RunList?, onNavigate: (Route) -> Unit) {
    val stats = rememberShoeStats(data).filter { !it.shoe.retired }
    if (stats.isEmpty()) {
        LinkCard(
            Icons.AutoMirrored.Rounded.DirectionsRun, tr("Le tue scarpe"),
            if (Shoes.data.shoes.isEmpty()) tr("Aggiungi le scarpe e scegli quale usi in ogni corsa") else tr("Nessuna scarpa in uso"),
            onClick = { onNavigate(Route.Shoes) }
        )
        return
    }
    UnicumCard {
        stats.forEachIndexed { i, s ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            ShoeLine(s, Shoes.data.defaultId == s.shoe.id, onClick = { onNavigate(Route.Shoe(s.shoe.id)) })
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clickable { onNavigate(Route.Shoes) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tr("Gestisci scarpe"), color = UnicumPrimary, style = UnicumType.Caption, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Nome, km fatti / limite con la barra, corse e ultima uscita. */
@Composable
private fun ShoeLine(s: ShoeStats.Stats, isDefault: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                s.shoe.name, color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            Text("${km1(s.km)} km", color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
            Text(" / ${s.shoe.limitKm}", color = UnicumDark.copy(alpha = .5f), style = UnicumType.Caption)
        }
        Spacer(Modifier.height(6.dp))
        ThinProgress(s.fraction, color = if (s.fraction >= .9f) WornColor else UnicumPrimary)
        Spacer(Modifier.height(5.dp))
        Text(
            listOfNotNull(
                tr("predefinita").takeIf { isDefault },
                trp(s.count, "%d corsa", "%d corse"),
                s.last?.let { tr("ultima %s", DateUtils.relativeLabel(it).lowercase(I18n.locale)) },
                s.shoe.retired.takeIf { it }?.let { tr("ritirata") }
            ).joinToString(" · "),
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
        )
    }
}

// ------------------------------------------------------------------
// Pagina di tutte le scarpe
// ------------------------------------------------------------------

@Composable
fun ShoesScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val data = rememberRunList()
    val stats = rememberShoeStats(data)
    var adding by remember { mutableStateOf(false) }
    if (adding) ShoeDialog(null, Shoes.data.shoes.isEmpty(), onDismiss = { adding = false }) { shoe, def ->
        adding = false
        Shoes.upsert(shoe, def)
        data?.runs?.let { Shoes.autoAssign(it) }
    }
    UnicumScreen(selectedItem = Route.Allenamenti, onNavigate = onNavigate) {
        PageHeader(tr("Scarpe"), if (stats.isEmpty()) tr("Km e usura delle tue scarpe") else trp(stats.size, "%d paio", "%d paia"), onBack)
        Spacer(Modifier.height(18.dp))
        val active = stats.filter { !it.shoe.retired }
        val retired = stats.filter { it.shoe.retired }
        if (stats.isEmpty()) {
            Text(
                tr("Aggiungi le scarpe che usi per correre. Nella pagina di ogni corsa scegli quale hai usato; la scarpa predefinita viene assegnata da sola alle nuove corse."),
                    
                color = UnicumDark.copy(alpha = .65f), style = UnicumType.Body, lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
        }
        if (active.isNotEmpty()) {
            UnicumCard {
                active.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    ShoeLine(s, Shoes.data.defaultId == s.shoe.id, onClick = { onNavigate(Route.Shoe(s.shoe.id)) })
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        UnicumButton(tr("Aggiungi scarpa"), onClick = { adding = true })
        if (retired.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(tr("Ritirate"), color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            UnicumCard {
                retired.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    ShoeLine(s, false, onClick = { onNavigate(Route.Shoe(s.shoe.id)) })
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Pagina di una scarpa
// ------------------------------------------------------------------

@Composable
fun ShoeScreen(id: String, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val data = rememberRunList()
    val s = rememberShoeStats(data).firstOrNull { it.shoe.id == id }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val isDefault = Shoes.data.defaultId == id

    if (s != null && editing) ShoeDialog(s.shoe, isDefault, onDismiss = { editing = false }) { shoe, def ->
        editing = false
        Shoes.upsert(shoe, def)
        data?.runs?.let { Shoes.autoAssign(it) }
    }
    if (s != null && deleting) UnicumDialog(onDismiss = { deleting = false }) {
        Text(tr("Eliminare “%s”?", s.shoe.name), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        Text(
            tr("Le %d corse fatte con questa scarpa resteranno senza scarpa. Se vuoi solo smettere di usarla, ritirala.", s.count),
            color = UnicumDark.copy(alpha = .7f), style = UnicumType.Body, lineHeight = 20.sp
        )
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .border(1.dp, UnicumDark.copy(alpha = .12f), RoundedCornerShape(16.dp))
                .clickable { deleting = false; Shoes.remove(id); onBack() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text(tr("Elimina"), color = DeleteRed, style = UnicumType.Body, fontWeight = FontWeight.Medium) }
        TextButton(onClick = { deleting = false }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f))
        }
    }

    UnicumScreen(selectedItem = Route.Allenamenti, onNavigate = onNavigate) {
        if (s == null) {
            PageHeader(tr("Scarpa"), "", onBack)
            return@UnicumScreen
        }
        val since = runCatching { LocalDate.parse(s.shoe.since) }.getOrNull()
        PageHeader(
            s.shoe.name,
            listOfNotNull(
                since?.let { tr("In uso dal %s", DateUtils.shortLabel(it).substringAfter(' ')) },
                tr("predefinita").takeIf { isDefault }, tr("ritirata").takeIf { s.shoe.retired }
            ).joinToString(" · "),
            onBack
        )
        Spacer(Modifier.height(16.dp))

        // ---------- km verso il limite
        UnicumCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(km1(s.km), color = UnicumDark, style = UnicumType.Page, fontWeight = FontWeight.SemiBold)
                Text(" km", color = UnicumDark, style = UnicumType.Body, modifier = Modifier.padding(bottom = 3.dp))
                Spacer(Modifier.weight(1f))
                Text(tr("su %d km", s.shoe.limitKm), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(10.dp))
            ThinProgress(s.fraction, height = 8.dp, color = if (s.fraction >= .9f) WornColor else UnicumPrimary)
            Spacer(Modifier.height(10.dp))
            val weeks = s.weeksLeft
            Text(
                when {
                    s.km >= s.shoe.limitKm -> tr("Hai superato i %d km: l'ammortizzazione è probabilmente calata, valuta di cambiarla.", s.shoe.limitKm)
                    s.shoe.retired -> tr("Ritirata con %s km.", kmInt(s.km))
                    weeks != null && s.weeklyKm != null ->
                        tr("Ne restano %s km: a %s km a settimana, circa %s", kmInt(s.remainingKm), km1(s.weeklyKm),
                                                        if (weeks >= 78) tr("%d anni.", (weeks / 52.0).roundToInt()) else if (weeks >= 9) tr("%d mesi.", (weeks / 4.3).roundToInt()) else if (weeks <= 1) tr("1 settimana.") else tr("%d settimane.", weeks))
                    else -> tr("Ne restano %s km.", kmInt(s.remainingKm))
                } + if (s.shoe.startKm > 0) " " + tr("Compresi %s km fatti prima dell'app.", kmInt(s.shoe.startKm)) else "",
                color = UnicumDark.copy(alpha = .65f), style = UnicumType.Detail, lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(14.dp))

        // ---------- i numeri
        UnicumCard {
            Row(Modifier.fillMaxWidth()) {
                BigStat("${s.count}", if (s.count == 1) tr("corsa") else tr("corse"), Modifier.weight(1f))
                BigStat(WorkoutAnalysis.formatDuration(s.seconds).let { if (s.seconds >= 3600) it.substringBeforeLast(':') + " h" else it }, tr("tempo"), Modifier.weight(1f))
                BigStat(WorkoutAnalysis.formatPace(s.paceSec), tr("ritmo medio"), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                BigStat(s.avgHr?.toString() ?: "–", tr("FC media"), Modifier.weight(1f))
                BigStat(s.beatsPerKm?.let { lfmt("%,d", it) } ?: "–", tr("battiti/km"), Modifier.weight(1f))
                BigStat(s.longestKm?.let { km1(it) } ?: "–", tr("km più lunga"), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                BigStat(km1(s.last30Km), tr("km in 30 giorni"), Modifier.weight(1f))
                BigStat(s.weeklyKm?.let { km1(it) } ?: "–", tr("km a settimana"), Modifier.weight(1f))
                BigStat(s.last?.let { DateUtils.relativeLabel(it) } ?: "–", tr("ultima corsa"), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------- km per tipo di seduta
        if (s.byKind.isNotEmpty()) {
            UnicumCard {
                Text(tr("Per tipo di seduta"), color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                val max = s.byKind.maxOf { it.second }.coerceAtLeast(0.1)
                s.byKind.forEach { (kind, km) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(
                            if (kind == RunProgress.NO_KIND) UnicumDark.copy(alpha = .2f) else runKindColor(kind)
                        ))
                        Spacer(Modifier.width(8.dp))
                        Text(if (kind == RunProgress.NO_KIND) tr("Senza tipo") else io.github.peol13.unicum.data.runKindLabel(kind), color = UnicumDark, style = UnicumType.Caption, modifier = Modifier.width(96.dp), maxLines = 1)
                        ThinProgress((km / max).toFloat(), Modifier.weight(1f),
                            color = if (kind == RunProgress.NO_KIND) UnicumDark.copy(alpha = .35f) else runKindColor(kind))
                        Spacer(Modifier.width(8.dp))
                        Text("${km1(km)} km", color = UnicumDark.copy(alpha = .7f), style = UnicumType.Caption, modifier = Modifier.width(58.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---------- azioni
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { UnicumChip(tr("Modifica"), false, { editing = true }, compact = true) }
            if (!s.shoe.retired) item {
                UnicumChip(if (isDefault) tr("Predefinita") else tr("Rendi predefinita"), isDefault, {
                    Shoes.upsert(s.shoe, !isDefault)
                    if (!isDefault) data?.runs?.let { Shoes.autoAssign(it) }
                }, compact = true)
            }
            item {
                UnicumChip(if (s.shoe.retired) tr("Rimetti in uso") else tr("Ritira"), false, {
                    Shoes.upsert(s.shoe.copy(retired = !s.shoe.retired), makeDefault = false)
                }, compact = true)
            }
            item { UnicumChip(tr("Elimina"), false, { deleting = true }, compact = true) }
        }

        // ---------- le corse
        if (s.runs.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(tr("Corse con questa scarpa"), color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            UnicumCard(padding = 6.dp) {
                s.runs.forEach { w -> ShoeRunRow(w, data?.feedback?.get(w.externalId)?.kind) { onNavigate(Route.Workout(w.externalId)) } }
            }
        }
    }
}

@Composable
private fun ShoeRunRow(w: WorkoutDetail, kind: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (kind == null) UnicumDark.copy(alpha = .2f) else runKindColor(kind)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(DateUtils.relativeLabel(LocalDate.parse(w.date)), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(kind?.let { io.github.peol13.unicum.data.runKindLabel(it) } ?: tr("Senza tipo"), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, maxLines = 1)
        }
        Text(
            "${WorkoutAnalysis.formatKm(w.distanceM)} km · ${WorkoutAnalysis.formatPace(WorkoutAnalysis.averagePace(w))}",
            color = UnicumDark, style = UnicumType.Caption
        )
        Icon(Icons.Rounded.ChevronRight, null, tint = UnicumDark.copy(alpha = .35f), modifier = Modifier.size(18.dp))
    }
}

// ------------------------------------------------------------------
// Aggiunta e modifica
// ------------------------------------------------------------------

/** Nome, km già fatti, limite dei km, data di inizio e predefinita. [initial] = null: nuova scarpa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoeDialog(initial: Shoe?, initialDefault: Boolean, onDismiss: () -> Unit, onSave: (Shoe, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var startKm by remember { mutableStateOf(initial?.startKm?.takeIf { it > 0 }?.let { km1(it) }.orEmpty()) }
    var limit by remember { mutableStateOf((initial?.limitKm ?: Shoes.DEFAULT_LIMIT_KM).toString()) }
    var since by remember { mutableStateOf(initial?.since ?: DateUtils.today().toString()) }
    var isDefault by remember { mutableStateOf(initialDefault) }
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        val utc = ZoneOffset.UTC
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(since).atStartOfDay(utc).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(utc).toLocalDate().isAfter(LocalDate.now())
                override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
            }
        )
        val pickerColors = DatePickerDefaults.colors(
            containerColor = UnicumBackground,
            titleContentColor = UnicumDark.copy(alpha = .6f),
            headlineContentColor = UnicumDark,
            weekdayContentColor = UnicumDark.copy(alpha = .6f),
            navigationContentColor = UnicumDark,
            yearContentColor = UnicumDark,
            selectedYearContainerColor = UnicumPrimary,
            selectedYearContentColor = UnicumOnPrimary,
            dayContentColor = UnicumDark,
            selectedDayContainerColor = UnicumPrimary,
            selectedDayContentColor = UnicumOnPrimary,
            todayContentColor = UnicumPrimary,
            todayDateBorderColor = UnicumPrimary,
            dividerColor = UnicumDark.copy(alpha = .12f)
        )
        DatePickerDialog(
            colors = pickerColors,
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { since = Instant.ofEpochMilli(it).atZone(utc).toLocalDate().toString() }
                    picking = false
                }) { Text(tr("Ok"), color = UnicumPrimary) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f)) } }
        ) { DatePicker(state = state, showModeToggle = false, colors = pickerColors, title = null) }
    }

    UnicumDialog(onDismiss, spacing = 12.dp) {
        Text(if (initial == null) tr("Nuova scarpa") else tr("Modifica scarpa"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) },
            label = { Text(tr("Nome (es. Pegasus 41)")) }, singleLine = true,
            colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = startKm, onValueChange = { startKm = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                label = { Text(tr("Km già fatti")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = limit, onValueChange = { limit = it.filter(Char::isDigit).take(4) },
                label = { Text(tr("Da cambiare a")) }, suffix = { Text("km") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
            )
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .border(1.dp, UnicumDark.copy(alpha = .15f), RoundedCornerShape(16.dp))
                .clickable { picking = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = UnicumPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("In uso dal"), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note)
                Text(DateUtils.fullLabel(LocalDate.parse(since)), color = UnicumDark, style = UnicumType.Body)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Scarpa predefinita"), color = UnicumDark, style = UnicumType.Body)
                Text(
                    tr("Le corse da quel giorno senza una scarpa scelta vanno a questa."),
                    color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            UnicumChip(if (isDefault) tr("Sì") else tr("No"), isDefault, { isDefault = !isDefault }, compact = true)
        }
        Spacer(Modifier.height(2.dp))
        UnicumButton(if (initial == null) tr("Aggiungi") else tr("Salva"), enabled = name.isNotBlank(), onClick = {
            val shoe = (initial ?: Shoe(Shoes.newId(), "", since)).copy(
                name = name.trim(),
                since = since,
                startKm = startKm.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                limitKm = limit.toIntOrNull()?.takeIf { it > 0 } ?: Shoes.DEFAULT_LIMIT_KM
            )
            onSave(shoe, isDefault)
        })
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f))
        }
    }
}
