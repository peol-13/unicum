package io.github.peol13.unicum.ui.components

import io.github.peol13.unicum.i18n.tr

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Calendario per scegliere un giorno, nei colori dell'app (chiaro e scuro). [min]/[max] = giorni selezionabili
 * (null = nessun limite). [onPick] riceve il giorno scelto quando si preme Ok.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnicumDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    min: LocalDate? = null,
    max: LocalDate? = null
) {
    val utc = ZoneOffset.UTC
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(utc).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = Instant.ofEpochMilli(utcTimeMillis).atZone(utc).toLocalDate()
                return (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
            }
            override fun isSelectableYear(year: Int): Boolean =
                (min == null || year >= min.year) && (max == null || year <= max.year)
        }
    )
    val colors = DatePickerDefaults.colors(
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
        colors = colors,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(utc).toLocalDate()) }
                onDismiss()
            }) { Text(tr("Ok"), color = UnicumPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f)) } }
    ) { DatePicker(state = state, showModeToggle = false, colors = colors, title = null) }
}
