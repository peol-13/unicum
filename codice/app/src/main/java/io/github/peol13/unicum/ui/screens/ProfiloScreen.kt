package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.ui.Route

import io.github.peol13.unicum.ui.components.GoalFields
import io.github.peol13.unicum.ui.theme.UnicumSurface
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import io.github.peol13.unicum.ui.components.DeleteRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.CalendarMonth
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.LocalPlan
import io.github.peol13.unicum.data.TrainingPlan
import kotlinx.coroutines.launch
import io.github.peol13.unicum.data.SyncResult
import io.github.peol13.unicum.data.HealthConnectSource
import io.github.peol13.unicum.data.BackgroundSync
import io.github.peol13.unicum.data.AutoBackup
import io.github.peol13.unicum.data.AutoBackupPolicy
import io.github.peol13.unicum.UnicumAppState
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.DashboardMode
import io.github.peol13.unicum.data.ThemeMode
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.components.UnicumChip
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Notifications
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.PersonalDataFields
import io.github.peol13.unicum.ui.components.PersonalFormState
import io.github.peol13.unicum.ui.components.ScreenTitle
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType

@Composable
fun ProfiloScreen(onNavigate: (Route) -> Unit) {
    // null = menu principale, altrimenti la sotto-pagina aperta
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) { page = null }

    UnicumScreen(selectedItem = Route.Profilo, onNavigate = onNavigate) {
        when (page) {
            "dati" -> {
                PageHeader("Profilo e alimentazione", "Dati personali, obiettivo calorico e macro", onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                PersonalPage()
            }
            "programma" -> {
                PageHeader("Programma di allenamento", "Da Notion, da un PDF o scritto a mano", onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                TrainingPlanPage()
            }
            "recupero" -> {
                PageHeader("Recupero e HRV", "Da quando l'HRV è confrontabile", onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                HrvSettingCard()
            }
            "notifiche" -> {
                PageHeader("Notifiche", "Promemoria dei pasti e avviso sul recupero", onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                RemindersPage()
            }
            "aspetto" -> {
                PageHeader("Aspetto", "Tema e schermata principale", onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                AppearancePage(onEditHome = { onNavigate(Route.HomeEditor) })
            }
            "backup" -> {
                PageHeader("Backup dei dati", "Automatico, esporta e importa", onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                BackupPage()
            }
            else -> {
                ScreenTitle("Profilo", "Fonti dati, obiettivi e preferenze")
                Spacer(Modifier.height(16.dp))
                // Fonti dati: Health Connect in evidenza (stato e "Sincronizza ora"), poi il programma
                SyncCard()

                MenuSection("Tu")
                ProfileMenuRow(
                    Icons.Rounded.Person, "Profilo e alimentazione",
                    "Peso, altezza, metabolismo, obiettivo calorico e macro"
                ) { page = "dati" }

                MenuSection("Allenamento")
                ProfileMenuRow(
                    Icons.Rounded.CalendarMonth, "Programma di allenamento",
                    when (TrainingPlan.source) {
                        TrainingPlan.Source.NOTION -> "Da Notion: programma e registro delle corse"
                        TrainingPlan.Source.LOCAL -> "Nell'app: ${LocalPlan.runs.size} sedute" + (LocalPlan.data.sourceName?.let { " da «$it»" } ?: "")
                        TrainingPlan.Source.NONE -> "Importa un PDF, scrivi le sedute a mano o collega Notion"
                    }
                ) { page = "programma" }
                Spacer(Modifier.height(10.dp))
                ProfileMenuRow(
                    Icons.Rounded.MonitorHeart, "Recupero e HRV",
                    UserSettings.hrvReliableFrom?.let {
                        "HRV confrontabile dal ${it.dayOfMonth}/${it.monthValue}/${it.year}" +
                            if (UserSettings.hrvFromManual != null) " (scelto da te)" else " (trovato dall'app)"
                    } ?: "HRV: tutta la serie è confrontabile"
                ) { page = "recupero" }

                MenuSection("App")
                ProfileMenuRow(
                    Icons.Rounded.Notifications, "Notifiche",
                    "Promemoria agli orari dei pasti e avviso quando il recupero è più basso del solito"
                ) { page = "notifiche" }
                Spacer(Modifier.height(10.dp))
                ProfileMenuRow(
                    Icons.Rounded.Tune, "Aspetto",
                    "Tema e schermata principale: ${UserSettings.themeMode.label} · ${UserSettings.dashboardMode.label}"
                ) { page = "aspetto" }
                Spacer(Modifier.height(10.dp))
                ProfileMenuRow(
                    Icons.Rounded.Save, "Backup dei dati", backupSummary()
                ) { page = "backup" }
                Spacer(Modifier.height(18.dp))
                Text(
                    "UNICUM ${io.github.peol13.unicum.BuildConfig.VERSION_NAME} · Your health. Unified.",
                    color = UnicumDark.copy(alpha = .4f), style = UnicumType.Note,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
        }
    }
}

/** Titoletto di un gruppo di voci del Profilo. */
@Composable
private fun MenuSection(title: String) {
    Text(
        title, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 4.dp)
    )
}

@Composable
private fun ProfileMenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    UnicumCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(UnicumAccent.copy(alpha = .35f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = UnicumPrimary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = UnicumDark.copy(alpha = .4f))
        }
    }
}

@Composable
private fun AppearancePage(onEditHome: () -> Unit) {
    UnicumCard {
        CardHeader(Icons.Rounded.DarkMode, "Tema")
        Spacer(Modifier.height(4.dp))
        Text(
            "Il tema scuro può seguire quello del telefono.",
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m ->
                UnicumChip(m.label, UserSettings.themeMode == m, { UserSettings.chooseThemeMode(m) })
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    UnicumCard {
        CardHeader(Icons.Rounded.Tune, "Schermata principale")
        Spacer(Modifier.height(4.dp))
        Text(
            "Scegli cosa vedere per prima nella Home.",
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
        )
        Spacer(Modifier.height(12.dp))
        DashboardMode.entries.forEach { mode ->
            ModeOption(
                mode = mode,
                selected = UserSettings.dashboardMode == mode,
                onClick = { UserSettings.chooseDashboardMode(mode) }
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "La modalità è un punto di partenza: puoi riordinare, togliere e aggiungere card.",
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
        )
        TextButton(onClick = onEditHome, modifier = Modifier.fillMaxWidth()) {
            Text("Personalizza le card della Home", color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PersonalPage() {
    val form = remember { PersonalFormState() }
    var saved by remember { mutableStateOf(false) }

    // DATI PERSONALI E METABOLISMO BASALE
    UnicumCard {
        CardHeader(Icons.Rounded.Person, "Dati personali")
        Spacer(Modifier.height(12.dp))
        PersonalDataFields(form)
    }

    Spacer(Modifier.height(14.dp))

    // OBIETTIVO CALORICO
    UnicumCard {
        CardHeader(Icons.Rounded.LocalFireDepartment, "Obiettivo calorico")
        Spacer(Modifier.height(12.dp))
        GoalFields(form)

        Spacer(Modifier.height(18.dp))
        // Qualsiasi modifica riabilita il pulsante di salvataggio
        LaunchedEffect(form.snapshot) { saved = false }
        UnicumButton(
            text = if (saved) "Salvato" else "Salva",
            enabled = form.isValid && !saved,
            onClick = {
                form.save()
                saved = true
            }
        )
        if (!form.isValid) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Controlla i dati: età 14–100, peso 30–250 kg, altezza 120–230 cm, metabolismo 800–4500 kcal, " +
                    "obiettivo 800–6000 kcal, macro al 100%.",
                color = DeleteRed, style = UnicumType.Caption
            )
        }
    }
}

/** Una delle tre schermate principali tra cui scegliere (Profilo e primo avvio). */
@Composable
internal fun ModeOption(mode: DashboardMode, selected: Boolean, onClick: () -> Unit) {
    val icon = when (mode) {
        DashboardMode.NUTRIZIONE -> Icons.Rounded.Restaurant
        DashboardMode.ATTIVITA -> Icons.AutoMirrored.Rounded.DirectionsRun
        DashboardMode.RECUPERO -> Icons.Rounded.Bedtime
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) UnicumPrimary.copy(alpha = .08f) else UnicumSurface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) UnicumPrimary else UnicumAccent.copy(alpha = .5f), shape)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(UnicumAccent.copy(alpha = .35f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = UnicumPrimary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.label, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
            Text(mode.description, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
        }
        if (selected) {
            Icon(Icons.Rounded.Check, "Selezionata", tint = UnicumPrimary, modifier = Modifier.size(22.dp))
        }
    }
}


/** Una riga che dice se il backup automatico è a posto, fermo o non ancora attivo. */
@Composable
private fun backupSummary(): String {
    val s = AutoBackup.state
    val last = if (s.lastSuccessMillis > 0)
        SimpleDateFormat("d MMM, HH:mm", Locale.ITALY).format(Date(s.lastSuccessMillis)) else null
    return when (AutoBackup.health()) {
        AutoBackupPolicy.Health.OFF -> "Nessun backup automatico: scegli dove salvarlo, o esporta a mano"
        AutoBackupPolicy.Health.OK -> "Automatico · ultimo backup: ${last ?: "in corso"}"
        AutoBackupPolicy.Health.STALE -> "Attenzione: nessun backup riuscito da giorni (ultimo: ${last ?: "mai"})"
        AutoBackupPolicy.Health.HELD -> "Attenzione: un backup è fermo e aspetta la tua conferma"
        AutoBackupPolicy.Health.ERROR -> "Attenzione: l'ultimo backup non è riuscito"
    }
}

/** Collegamento a Health Connect: permessi, sincronizzazione manuale e stato. */
@Composable
private fun SyncCard() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<HealthConnectSource.Status?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch { status = HealthConnectSource.status(context) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        refresh()
        if (granted.isNotEmpty()) UnicumAppState.sync(context, days = 30)
    }

    LaunchedEffect(Unit) { refresh() }

    UnicumCard {
        CardHeader(Icons.Rounded.Sync, "Health Connect")
        Spacer(Modifier.height(10.dp))

        when (val s = status) {
            null -> Text("Controllo Health Connect...", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail)

            HealthConnectSource.Status.Unavailable,
            HealthConnectSource.Status.UpdateRequired -> {
                Text(
                    if (s == HealthConnectSource.Status.UpdateRequired)
                        "Health Connect va aggiornato per poter essere usato."
                    else "Health Connect non è installato su questo telefono.",
                    color = UnicumDark, style = UnicumType.Detail
                )
                Spacer(Modifier.height(10.dp))
                UnicumButton("Apri Play Store", onClick = {
                    val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                })
            }

            is HealthConnectSource.Status.Available -> {
                if (!s.anyGranted) {
                    Text("Non ancora collegato.", color = UnicumDark, style = UnicumType.Detail)
                    Spacer(Modifier.height(10.dp))
                    UnicumButton("Collega Health Connect", onClick = {
                        permissionLauncher.launch(s.wanted)
                    })
                } else {
                    val busy = UnicumAppState.syncing || NotionPlan.syncing
                    UnicumButton(
                        text = if (busy) "Sincronizzo..." else "Sincronizza ora",
                        enabled = !busy,
                        onClick = { UnicumAppState.sync(context, days = 30) }
                    )
                    if (NotionPlan.connected) Text(
                        "Health Connect e Notion insieme",
                        color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    val last = UserSettings.lastSyncMillis
                    Text(
                        if (last > 0) "Ultima sincronizzazione: " +
                            SimpleDateFormat("d MMM, HH:mm", Locale.ITALY).format(Date(last))
                        else "Mai sincronizzato",
                        color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    UnicumAppState.lastSyncResult?.let { r ->
                        syncSummary(r)?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = UnicumDark.copy(alpha = .75f), style = UnicumType.Caption, lineHeight = 17.sp)
                        }
                    }
                    Text(
                        when {
                            !s.backgroundSupported -> "Su questo telefono Health Connect non legge ad app chiusa: i dati si aggiornano quando apri UNICUM."
                            !s.backgroundGranted -> "Per aggiornarsi anche ad app chiusa serve il permesso \"in background\" di Health Connect."
                            else -> "Si aggiorna da sola anche ad app chiusa, circa ogni 3 ore" +
                                (BackgroundSync.lastRunText(context)?.let { " (ultima volta: $it)" } ?: "") + "."
                        },
                        color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (!s.allGranted) {
                        TextButton(
                            onClick = { permissionLauncher.launch(s.wanted) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Concedi i permessi mancanti", color = UnicumPrimary) }
                    }
                }
            }
        }
    }
}

private fun syncSummary(r: SyncResult): String? = when {
    r.error != null -> "Sincronizzazione non riuscita: ${r.error}"
    r.isEmpty -> "Nessun dato nuovo trovato. Controlla che le app dell'orologio, dell'anello o della bilancia condividano i dati con Health Connect." +
        (if (r.skipped.isNotEmpty()) " Permessi non concessi: ${r.skipped.joinToString(", ")}." else "")
    r.skipped.isNotEmpty() -> "Permessi mancanti: ${r.skipped.joinToString(", ")}."
    else -> null
}
