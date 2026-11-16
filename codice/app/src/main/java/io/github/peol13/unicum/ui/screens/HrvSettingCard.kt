package io.github.peol13.unicum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumChip
import io.github.peol13.unicum.ui.components.UnicumDatePickerDialog
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun hrvDayLabel(d: LocalDate) = "${d.dayOfMonth}/${d.monthValue}/${d.year}"

/**
 * Da quando l'HRV è confrontabile: automatico (salto di scala trovato nella serie) oppure una data scelta a mano.
 * Serve quando l'anello o l'orologio cambia algoritmo, o quando si cambia dispositivo.
 */
@Composable
fun HrvSettingCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val manual = UserSettings.hrvFromManual
    val auto = UserSettings.hrvFromAuto

    fun apply(date: LocalDate?) {
        UserSettings.chooseHrvFrom(date)
        scope.launch { UnicumAppState.reloadFromDatabase(context) }
    }

    UnicumCard {
        CardHeader(Icons.Rounded.MonitorHeart, "HRV confrontabile dal")
        Spacer(Modifier.height(4.dp))
        Text(
            "Se l'anello o l'orologio cambia algoritmo (o cambi dispositivo) l'HRV fa un salto e le medie mescolerebbero " +
                "due scale diverse: l'app usa solo i valori da questa data.",
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UnicumChip("Automatico", manual == null, { if (manual != null) apply(null) })
            UnicumChip("Scelgo io", manual != null, { picking = true })
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                manual != null -> "Dal ${hrvDayLabel(manual)} (scelto da te)."
                auto != null -> "Dal ${hrvDayLabel(auto)}: lì i valori cambiano di scala."
                else -> "Tutta la serie: nessun salto di scala trovato."
            },
            color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium
        )
        if (manual != null) {
            TextButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Cambia data", color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (picking) {
        val today = DateUtils.today()
        UnicumDatePickerDialog(
            initial = manual ?: auto ?: today,
            onDismiss = { picking = false },
            onPick = { apply(it) },
            max = today
        )
    }
}
