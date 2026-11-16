package io.github.peol13.unicum.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.LocalPlan
import io.github.peol13.unicum.data.PlanPdfParser
import io.github.peol13.unicum.data.PlannedRun
import io.github.peol13.unicum.data.TrainingPlan
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.ui.components.DeleteRed
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.ui.components.UnicumDatePickerDialog
import io.github.peol13.unicum.ui.components.UnicumDialog
import io.github.peol13.unicum.ui.components.unicumFieldColors
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType
import java.time.LocalDate

private fun kmInput(km: Double?) = km?.let { PlanPdfParser.shortKm(it) }.orEmpty()

/**
 * Crea o modifica una seduta del programma tenuto nell'app. Il nome, se non lo si scrive, si ricava da tipo e
 * obiettivo. [onDelete] = null per una seduta nuova. Non salva da solo: passa la seduta a [onSave].
 */
@Composable
fun PlannedRunDialog(
    initial: PlannedRun?,
    onDismiss: () -> Unit,
    onSave: (PlannedRun) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val today = DateUtils.today()
    var date by remember { mutableStateOf(initial?.localDate ?: today) }
    var noDate by remember { mutableStateOf(initial != null && initial.localDate == null) }
    var kind by remember { mutableStateOf(initial?.kind) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var nameTouched by remember { mutableStateOf(initial != null && initial.name != PlanPdfParser.nameOf(initial.kind, initial.km, initial.minutes)) }
    var kmText by remember { mutableStateOf(kmInput(initial?.km)) }
    var minText by remember { mutableStateOf(initial?.minutes?.toString().orEmpty()) }
    var pace by remember { mutableStateOf(initial?.pace.orEmpty()) }
    var rpe by remember { mutableStateOf(initial?.rpe) }
    var structure by remember { mutableStateOf(initial?.structure.orEmpty()) }
    var status by remember { mutableStateOf(initial?.status ?: LocalPlan.STATUS_TODO) }
    var picking by remember { mutableStateOf(false) }

    val km = kmText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.1..200.0 }
    val minutes = minText.toIntOrNull()?.takeIf { it in 1..600 }
    val autoName = PlanPdfParser.nameOf(kind, km, minutes)
    val kmOk = kmText.isBlank() || km != null
    val minOk = minText.isBlank() || minutes != null

    if (picking) {
        UnicumDatePickerDialog(initial = date, onDismiss = { picking = false }, onPick = { date = it; noDate = false })
    }

    UnicumDialog(onDismiss, spacing = 12.dp) {
        Text(if (initial == null) "Nuova seduta" else "Seduta", color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .border(1.dp, UnicumDark.copy(alpha = .15f), RoundedCornerShape(16.dp))
                .clickable { picking = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = UnicumPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Giorno", color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note)
                Text(
                    if (noDate) "Senza data: tocca per sceglierla" else DateUtils.fullLabel(date) + if (date.year != today.year) " ${date.year}" else "",
                    color = UnicumDark, style = UnicumType.Body
                )
            }
        }

        Text("Tipo", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (TrainingPlan.kindNames + listOfNotNull(kind)).distinct().forEach { k ->
                UnicumChip(k, kind == k, { kind = if (kind == k) null else k }, compact = true)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = kmText, onValueChange = { kmText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                label = { Text("Obiettivo") }, suffix = { Text("km") }, singleLine = true, isError = !kmOk,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minText, onValueChange = { minText = it.filter(Char::isDigit).take(3) },
                label = { Text("Durata") }, suffix = { Text("min") }, singleLine = true, isError = !minOk,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = pace, onValueChange = { pace = it.take(40) },
            label = { Text("Ritmo (es. 6:30/km)") }, singleLine = true,
            colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
        )

        Text("RPE previsto", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..10).forEach { v -> UnicumChip("$v", rpe == v, { rpe = if (rpe == v) null else v }, compact = true) }
        }

        OutlinedTextField(
            value = structure, onValueChange = { structure = it.take(600) },
            label = { Text("Struttura") },
            placeholder = { Text("es. 10' facile + 5×(3' a 5:50 + 2' lento) + 5' defaticamento") },
            minLines = 2, maxLines = 6,
            colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = if (nameTouched) name else autoName, onValueChange = { name = it.take(60); nameTouched = it.isNotBlank() },
            label = { Text("Nome") }, singleLine = true,
            colors = unicumFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
        )
        if (!noDate && !date.isAfter(today)) {
            Text("Com'è andata", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnicumChip("Da fare", status == LocalPlan.STATUS_TODO, { status = LocalPlan.STATUS_TODO }, compact = true)
                UnicumChip("Fatta", status == LocalPlan.STATUS_DONE, { status = LocalPlan.STATUS_DONE }, compact = true)
                UnicumChip("Saltata", status == LocalPlan.STATUS_SKIPPED, { status = LocalPlan.STATUS_SKIPPED }, compact = true)
            }
            Text(
                "Se nel giorno c'è una corsa registrata è già fatta da sola: \"Fatta\" serve per le corse senza orologio.",
                color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note
            )
        }

        UnicumButton(
            "Salva", enabled = kmOk && minOk,
            onClick = {
                onSave(
                    PlannedRun(
                        id = initial?.id ?: LocalPlan.newId(),
                        name = (if (nameTouched) name.trim() else "").ifEmpty { autoName },
                        date = if (noDate) null else date.toString(),
                        kind = kind,
                        // Una seduta spostata nel futuro torna "da fare"
                        status = if (noDate || date.isAfter(today)) LocalPlan.STATUS_TODO else status,
                        km = km, minutes = minutes,
                        pace = pace.trim().ifEmpty { null },
                        rpe = rpe,
                        structure = structure.trim().ifEmpty { null },
                        week = initial?.week
                    )
                )
            }
        )
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Elimina seduta", color = DeleteRed, fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Annulla", color = UnicumPrimary) }
    }
}
