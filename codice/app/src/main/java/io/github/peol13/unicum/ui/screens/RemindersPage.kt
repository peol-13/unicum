package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.tr

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.MealReminders
import io.github.peol13.unicum.data.RecoveryAlertNotifier
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.foundation.layout.width
import io.github.peol13.unicum.data.MealTime
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.MealTimePill
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType

/** Un promemoria per tipo di pasto: interruttore e orario. Niente notifica se il pasto è già registrato. */
@Composable
fun RemindersPage() {
    val context = LocalContext.current
    // minuto del giorno per ogni tipo (null = spento); riletto dopo ogni modifica
    val minutes = remember { mutableStateOf(MealReminders.types.associateWith { MealReminders.minuteOf(context, it) }) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun change(type: String, minute: Int?) {
        MealReminders.set(context, type, minute)
        minutes.value = minutes.value + (type to minute)
        if (minute != null && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var alertsOn by remember { mutableStateOf(RecoveryAlertNotifier.isEnabled(context)) }
    UnicumCard {
        CardHeader(Icons.Rounded.Bolt, tr("Avvisi sul recupero"))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Una notifica quando l'HRV resta sotto la tua media del 20% per due notti o la temperatura notturna sale di mezzo grado: possibili segni di stress o di un inizio di malattia. Al massimo una al giorno."),
                    
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = alertsOn,
                onCheckedChange = { on ->
                    alertsOn = on
                    RecoveryAlertNotifier.setEnabled(context, on)
                    if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = UnicumOnPrimary, checkedTrackColor = UnicumPrimary,
                    uncheckedTrackColor = UnicumAccent.copy(alpha = .3f)
                )
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    var weeklyOn by remember { mutableStateOf(io.github.peol13.unicum.data.WeeklySummary.isEnabled(context)) }
    UnicumCard {
        CardHeader(Icons.Rounded.Notifications, tr("Riepilogo della settimana"))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Il lunedì una notifica con deficit medio, attività e sonno della settimana appena finita. Toccandola si apre il racconto della settimana."),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = weeklyOn,
                onCheckedChange = { on ->
                    weeklyOn = on
                    io.github.peol13.unicum.data.WeeklySummary.setEnabled(context, on)
                    if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = UnicumOnPrimary, checkedTrackColor = UnicumPrimary,
                    uncheckedTrackColor = UnicumAccent.copy(alpha = .3f)
                )
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    UnicumCard {
        CardHeader(Icons.Rounded.Notifications, tr("Promemoria"))
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Ricevi una notifica all'orario del pasto. Se l'hai già registrato, non compare."),
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        MealReminders.types.forEach { type ->
            val minute = minutes.value[type]
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    MealReminders.label(type), color = UnicumDark, style = UnicumType.Emphasis,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                if (minute != null) MealTimePill(minute, { change(type, it) })
                Switch(
                    checked = minute != null,
                    onCheckedChange = { on -> change(type, if (on) MealTime.typical(type) else null) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = UnicumOnPrimary, checkedTrackColor = UnicumPrimary,
                        uncheckedTrackColor = UnicumAccent.copy(alpha = .3f)
                    )
                )
            }
        }
    }
}
