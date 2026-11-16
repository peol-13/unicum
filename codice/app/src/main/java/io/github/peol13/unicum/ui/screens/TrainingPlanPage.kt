package io.github.peol13.unicum.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.LocalPlan
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.PdfText
import io.github.peol13.unicum.data.PlanPdfParser
import io.github.peol13.unicum.data.PlannedRun
import io.github.peol13.unicum.ui.Route
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.DeleteRed
import io.github.peol13.unicum.ui.components.ExpandRow
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.ui.components.UnicumDatePickerDialog
import io.github.peol13.unicum.ui.components.UnicumDialog
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/** Pagina a sé (rotta [Route.Notion]) aperta dalla sezione Allenamenti; Indietro torna lì. */
@Composable
fun TrainingPlanScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    UnicumScreen(selectedItem = Route.Allenamenti, onNavigate = onNavigate) {
        PageHeader("Programma di allenamento", "Da Notion, da un PDF o scritto a mano", onBack)
        Spacer(Modifier.height(14.dp))
        TrainingPlanPage()
    }
}

/**
 * Da dove arriva il programma: Notion ([NotionPage]) oppure tenuto nell'app ([LocalPlanPage]: importato da PDF
 * e/o scritto a mano). Se Notion è collegato vince Notion.
 */
@Composable
fun TrainingPlanPage() {
    val context = LocalContext.current
    NotionPlan.init(context)
    LocalPlan.init(context)
    var mode by rememberSaveable { mutableStateOf(if (NotionPlan.connected) "notion" else "app") }

    UnicumCard {
        CardHeader(Icons.Rounded.CalendarMonth, "Da dove arriva il programma")
        Spacer(Modifier.height(4.dp))
        Text(
            "Le sedute compaiono in Allenamenti → Programma e le corse fatte si riconoscono dal giorno.",
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UnicumChip("PDF o a mano", mode == "app", { mode = "app" })
            UnicumChip("Notion", mode == "notion", { mode = "notion" })
        }
    }
    Spacer(Modifier.height(14.dp))
    if (mode == "notion") {
        NotionPage()
    } else {
        if (NotionPlan.connected) {
            UnicumCard {
                Text(
                    "Ora il programma arriva da Notion. Per usare quello dell'app scollega Notion " +
                        "(il programma dell'app resta salvato).",
                    color = UnicumDark.copy(alpha = .75f), style = UnicumType.Detail, lineHeight = 18.sp
                )
                TextButton(onClick = { NotionPlan.disconnect() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Scollega Notion", color = DeleteRed, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        LocalPlanPage()
    }
}

/** Un PDF letto e da controllare prima di salvarlo. */
private data class ImportPreview(
    val sourceName: String?,
    val result: PlanPdfParser.Result,
    /** Sedute pronte (null finché non si sceglie il primo giorno, per i programmi senza date). */
    val runs: List<PlannedRun>?
)

@Composable
internal fun LocalPlanPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = DateUtils.today()
    var reading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var editing by remember { mutableStateOf<PlannedRun?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            reading = true
            error = null
            when (val o = PdfText.read(context, uri)) {
                is PdfText.Outcome.Error -> error = o.message
                is PdfText.Outcome.Ok -> runCatching {
                    val r = PlanPdfParser.parse(o.lines, today)
                    val runs = if (r.needsStart) null else PlanPdfParser.toRuns(r.drafts, null).map { it.copy(id = LocalPlan.newId()) }
                    preview = ImportPreview(o.name, r, runs)
                }.onFailure { error = "Il testo del PDF non si riesce a leggere come programma: aggiungi le sedute a mano." }
            }
            reading = false
        }
    }

    preview?.let { p ->
        ImportPreviewSection(p, onChange = { preview = it }, onDone = { preview = null })
        return
    }

    UnicumCard {
        CardHeader(Icons.Rounded.EventNote, if (LocalPlan.isEmpty) "Nessun programma" else "Programma nell'app")
        Spacer(Modifier.height(6.dp))
        Text(
            if (LocalPlan.isEmpty) "Importa il programma da un PDF (con il testo, non una foto) oppure scrivi le sedute a mano."
            else listOfNotNull(
                "${LocalPlan.runs.size} sedute",
                LocalPlan.data.sourceName?.let { "da «$it»" },
                LocalPlan.data.importedAt.takeIf { it > 0 }?.let { "importato il ${SimpleDateFormat("d MMM", Locale.ITALY).format(Date(it))}" }
            ).joinToString(" · "),
            color = UnicumDark.copy(alpha = .7f), style = UnicumType.Detail, lineHeight = 18.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Tipo, RPE e nota di ogni corsa fatta si scrivono nella sua pagina; se mancano, il tipo è quello della seduta.",
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
        )
        Spacer(Modifier.height(14.dp))
        if (reading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = UnicumPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Lettura del PDF…", color = UnicumDark.copy(alpha = .7f), style = UnicumType.Detail)
            }
        } else {
            UnicumButton("Importa da PDF", onClick = { picker.launch(arrayOf("application/pdf")) })
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DeleteRed, style = UnicumType.Caption, lineHeight = 16.sp)
        }
        TextButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Aggiungi una seduta a mano", color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
        }
    }

    if (!LocalPlan.isEmpty) {
        val upcoming = LocalPlan.runs.filter { r -> r.localDate?.let { !it.isBefore(today) } ?: true }
        val past = LocalPlan.runs.filter { r -> r.localDate?.isBefore(today) == true }.reversed()
        Spacer(Modifier.height(14.dp))
        UnicumCard {
            CardHeader(Icons.Rounded.CalendarMonth, "Prossime sedute")
            if (upcoming.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Nessuna seduta da oggi in poi.", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
            }
            upcoming.forEach { r -> PlanRunRow(r, today) { editing = r } }
            if (past.isNotEmpty()) {
                var open by rememberSaveable { mutableStateOf(false) }
                ExpandRow(open, "Sedute passate (${past.size})", { open = !open })
                if (open) past.forEach { r -> PlanRunRow(r, today) { editing = r } }
            }
        }
        TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Cancella il programma", color = DeleteRed, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
        }
    }

    if (adding) PlannedRunDialog(null, onDismiss = { adding = false }, onSave = { LocalPlan.upsert(it); adding = false })
    editing?.let { r ->
        PlannedRunDialog(
            r, onDismiss = { editing = null },
            onSave = { LocalPlan.upsert(it); editing = null },
            onDelete = { LocalPlan.delete(r.id); editing = null }
        )
    }
    if (confirmClear) {
        UnicumDialog({ confirmClear = false }) {
            Text("Cancellare il programma?", color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
            Text(
                "Le ${LocalPlan.runs.size} sedute dell'app vengono tolte. Le corse fatte e le loro sensazioni restano.",
                color = UnicumDark.copy(alpha = .7f), style = UnicumType.Detail, lineHeight = 18.sp
            )
            UnicumButton("Cancella", onClick = { LocalPlan.clear(); confirmClear = false })
            TextButton(onClick = { confirmClear = false }, modifier = Modifier.fillMaxWidth()) { Text("Annulla", color = UnicumPrimary) }
        }
    }
}

/** Una seduta nell'elenco: tipo, nome, giorno e obiettivo; tocco = modifica. */
@Composable
private fun PlanRunRow(r: PlannedRun, today: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KindDot(r.kind)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                listOfNotNull(
                    dayLabel(r, today) ?: "Senza data",
                    planSummary(r).takeIf { it != r.kind && it != "–" },
                    r.pace,
                    when (r.status) {
                        LocalPlan.STATUS_SKIPPED -> "Saltata"
                        LocalPlan.STATUS_DONE -> "Fatta"
                        else -> null
                    }
                ).joinToString(" · "),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, maxLines = 1
            )
        }
    }
}

/** Le sedute trovate nel PDF: primo giorno (se servono), controllo e correzione, poi salvataggio. */
@Composable
private fun ImportPreviewSection(p: ImportPreview, onChange: (ImportPreview) -> Unit, onDone: () -> Unit) {
    val today = DateUtils.today()
    var start by rememberSaveable { mutableStateOf(PlanPdfParser.nextMonday(today).toString()) }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlannedRun?>(null) }
    val r = p.result

    UnicumCard {
        CardHeader(Icons.Rounded.Description, p.sourceName?.let { "«$it»" } ?: "PDF")
        Spacer(Modifier.height(6.dp))
        Text(
            if (r.drafts.isEmpty()) "Nessuna seduta trovata."
            else "Trovate ${r.drafts.size} sedute" + listOfNotNull(
                r.restDays.takeIf { it > 0 }?.let { if (it == 1) "1 giorno di riposo" else "$it giorni di riposo" },
                r.otherActivities.takeIf { it > 0 }?.let { if (it == 1) "1 allenamento non di corsa" else "$it allenamenti non di corsa" }
            ).joinToString(" e ").let { if (it.isEmpty()) "." else " (saltati $it)." },
            color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium
        )
        if (r.drafts.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "L'app cerca le sedute dalla data (es. 17/11, lun 17 nov) o dal giorno della settimana sotto \"Settimana 1\", " +
                    "all'inizio di ogni riga. Puoi aggiungere le sedute a mano.",
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 16.sp
            )
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Chiudi", color = UnicumPrimary) }
            return@UnicumCard
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Controlla le sedute: tocca una riga per correggere giorno, tipo, km o struttura.",
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
        )
    }
    if (r.drafts.isEmpty()) return

    if (p.runs == null) {
        Spacer(Modifier.height(14.dp))
        UnicumCard {
            CardHeader(Icons.Rounded.CalendarMonth, "Quando inizia?")
            Spacer(Modifier.height(6.dp))
            Text(
                "Il programma è scritto per settimane e giorni, senza date: scegli il primo giorno della settimana 1.",
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            val d = LocalDate.parse(start)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .border(1.dp, UnicumDark.copy(alpha = .15f), RoundedCornerShape(16.dp))
                    .clickable { picking = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = UnicumPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(DateUtils.fullLabel(d), color = UnicumDark, style = UnicumType.Body)
            }
            Spacer(Modifier.height(12.dp))
            UnicumButton("Continua", onClick = {
                onChange(p.copy(runs = PlanPdfParser.toRuns(r.drafts, d).map { it.copy(id = LocalPlan.newId()) }))
            })
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Annulla", color = UnicumPrimary) }
        }
        if (picking) UnicumDatePickerDialog(LocalDate.parse(start), { picking = false }, { start = it.toString() })
        return
    }

    val runs = p.runs
    Spacer(Modifier.height(14.dp))
    UnicumCard {
        runs.forEach { run -> PlanRunRow(run, today) { editing = run } }
        val undated = runs.count { it.date == null }
        if (undated > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "$undated sedute senza data: non compaiono finché non scegli il giorno.",
                color = DeleteRed, style = UnicumType.Caption, lineHeight = 16.sp
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    if (LocalPlan.isEmpty) {
        UnicumButton("Salva il programma", enabled = runs.isNotEmpty(), onClick = { LocalPlan.import(runs, p.sourceName, replace = true); onDone() })
    } else {
        UnicumButton("Sostituisci il programma attuale", enabled = runs.isNotEmpty(), onClick = { LocalPlan.import(runs, p.sourceName, replace = true); onDone() })
        TextButton(onClick = { LocalPlan.import(runs, p.sourceName, replace = false); onDone() }, modifier = Modifier.fillMaxWidth()) {
            Text("Aggiungi a quello attuale", color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
        }
    }
    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Annulla", color = UnicumDark.copy(alpha = .7f)) }

    editing?.let { e ->
        PlannedRunDialog(
            e, onDismiss = { editing = null },
            onSave = { new -> onChange(p.copy(runs = runs.map { if (it.id == e.id) new else it }.sortedBy { it.date ?: "9999" })); editing = null },
            onDelete = { onChange(p.copy(runs = runs.filter { it.id != e.id })); editing = null }
        )
    }
}
