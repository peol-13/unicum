package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.AppLanguage

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.tr

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
import androidx.compose.material.icons.rounded.BarChart
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.Language
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
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.automirrored.rounded.OpenInNew

@Composable
fun ProfiloScreen(onNavigate: (Route) -> Unit) {
    // null = menu principale, altrimenti la sotto-pagina aperta
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var whatsNew by remember { mutableStateOf(false) }
    if (whatsNew) WhatsNewDialog(onDismiss = { whatsNew = false })
    BackHandler(enabled = page != null) { page = null }
    LaunchedEffect(page) { page?.let { io.github.peol13.unicum.data.UsageStats.screen("Profilo/$it") } }
    // Ogni sotto-pagina parte dall'alto (prima ereditava lo scorrimento del menu: "Aspetto" si apriva già sul Tema);
    // il menu ricorda dov'era, così tornando indietro si ritrova la voce toccata
    val menuScroll = rememberScrollState()
    val pageScroll = remember(page) { ScrollState(0) }

    UnicumScreen(selectedItem = Route.Profilo, onNavigate = onNavigate, scrollState = if (page == null) menuScroll else pageScroll) {
        when (page) {
            "dati" -> {
                PageHeader(tr("Profilo e alimentazione"), tr("Dati personali, obiettivo calorico e macro"), onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                PersonalPage()
            }
            "programma" -> {
                PageHeader(tr("Programma di allenamento"), tr("Da Notion, da un PDF o scritto a mano"), onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                TrainingPlanPage()
            }
            "recupero" -> {
                PageHeader(tr("Recupero e HRV"), tr("Da quando l'HRV è confrontabile"), onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                HrvSettingCard()
            }
            "notifiche" -> {
                PageHeader(tr("Notifiche"), tr("Promemoria dei pasti e avviso sul recupero"), onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                RemindersPage()
            }
            "aspetto" -> {
                PageHeader(tr("Aspetto"), tr("Tema, lingua e schermata principale"), onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                AppearancePage(onEditHome = { onNavigate(Route.HomeEditor) })
            }
            "backup" -> {
                PageHeader(tr("Backup dei dati"), tr("Automatico, esporta e importa"), onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                BackupPage()
            }
            "parere" -> {
                PageHeader(tr("Invia un parere"), tr("Problemi, idee, com'è andata"), onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                FeedbackPage()
            }
            "info" -> {
                PageHeader(tr("Informazioni"), tr("Versione, privacy, licenza e crediti"), onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                AboutPage(onWhatsNew = { whatsNew = true })
            }
            "statistiche" -> {
                PageHeader(tr("Statistiche anonime"), tr("Facoltative: cosa viene inviato"), onBack = { page = null })
                Spacer(Modifier.height(14.dp))
                UsageStatsPage()
            }
            else -> {
                ScreenTitle(tr("Profilo"), tr("Fonti dati, obiettivi e preferenze"))
                Spacer(Modifier.height(16.dp))
                // Fonti dati: Health Connect in evidenza (stato e "Sincronizza ora"), poi il programma
                HealthConnectCard()

                MenuSection(tr("Tu"))
                ProfileMenuRow(
                    Icons.Rounded.Person, tr("Profilo e alimentazione"),
                    tr("Peso, altezza, metabolismo, obiettivo calorico e macro")
                ) { page = "dati" }

                MenuSection(tr("Allenamento"))
                ProfileMenuRow(
                    Icons.Rounded.CalendarMonth, tr("Programma di allenamento"),
                    when (TrainingPlan.source) {
                        TrainingPlan.Source.NOTION -> tr("Da Notion: programma e registro delle corse")
                        TrainingPlan.Source.LOCAL -> tr("Nell'app: %d sedute", LocalPlan.runs.size) + (LocalPlan.data.sourceName?.let { " " + tr("da «%s»", it) } ?: "")
                        TrainingPlan.Source.NONE -> tr("Importa un PDF, scrivi le sedute a mano o collega Notion")
                    }
                ) { page = "programma" }
                Spacer(Modifier.height(10.dp))
                ProfileMenuRow(
                    Icons.Rounded.MonitorHeart, tr("Recupero e HRV"),
                    UserSettings.hrvReliableFrom?.let {
                        tr("HRV confrontabile dal %s", "${it.dayOfMonth}/${it.monthValue}/${it.year}") +
                            " " + if (UserSettings.hrvFromManual != null) tr("(scelto da te)") else tr("(trovato dall'app)")
                    } ?: tr("HRV: tutta la serie è confrontabile")
                ) { page = "recupero" }

                MenuSection(tr("App"))
                ProfileMenuRow(
                    Icons.Rounded.Notifications, tr("Notifiche"),
                    tr("Promemoria agli orari dei pasti e avviso quando il recupero è più basso del solito")
                ) { page = "notifiche" }
                Spacer(Modifier.height(10.dp))
                ProfileMenuRow(
                    Icons.Rounded.Tune, tr("Aspetto"),
                    tr("Tema, lingua e schermata principale: %s", "${UserSettings.themeMode.label} · ${I18n.language.nativeName} · ${UserSettings.dashboardMode.label}")
                ) { page = "aspetto" }
                Spacer(Modifier.height(10.dp))
                ProfileMenuRow(
                    Icons.Rounded.Save, tr("Backup dei dati"), backupSummary()
                ) { page = "backup" }
                if (io.github.peol13.unicum.data.UsageStats.available) {
                    Spacer(Modifier.height(10.dp))
                    ProfileMenuRow(
                        Icons.Rounded.BarChart, tr("Statistiche anonime"), usageStatsSummary
                    ) { page = "statistiche" }
                }
                MenuSection(tr("Aiuto"))
                ProfileMenuRow(
                    Icons.Rounded.Forum, tr("Invia un parere"), tr("Un problema, un'idea o com'è andata: in privato o su GitHub")
                ) { page = "parere" }
                Spacer(Modifier.height(10.dp))
                ProfileMenuRow(
                    Icons.Rounded.Info, tr("Informazioni"), tr("Novità, privacy, licenza e crediti")
                ) { page = "info" }
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
private fun LanguageCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    UnicumCard {
        CardHeader(Icons.Rounded.Language, tr("Lingua"))
        Spacer(Modifier.height(4.dp))
        Text(
            tr("«Come il telefono» usa la lingua del sistema; se l'app non la conosce, è in inglese."),
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        val choice = I18n.choice.value
        val options = listOf("" to tr("Come il telefono")) + AppLanguage.entries.map { it.code to it.nativeName }
        options.forEach { (code, name) ->
            val selected = choice == code
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { if (!selected) I18n.choose(context, code) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name, color = if (selected) UnicumPrimary else UnicumDark, style = UnicumType.Body,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f)
                )
                if (selected) Icon(Icons.Rounded.Check, tr("Selezionata"), tint = UnicumPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AppearancePage(onEditHome: () -> Unit) {
    LanguageCard()
    Spacer(Modifier.height(14.dp))
    UnicumCard {
        CardHeader(Icons.Rounded.Straighten, tr("Unità di misura"))
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Per peso, altezza e carichi della forza. Le distanze delle corse restano in km."),
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            io.github.peol13.unicum.data.UnitSystem.entries.forEach { s ->
                UnicumChip(s.label, UserSettings.unitSystem == s, { UserSettings.chooseUnitSystem(s) })
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    UnicumCard {
        CardHeader(Icons.Rounded.DarkMode, tr("Tema"))
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Il tema scuro può seguire quello del telefono."),
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
        CardHeader(Icons.Rounded.Tune, tr("Schermata principale"))
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Scegli cosa vedere per prima nella Home."),
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
            tr("La modalità è un punto di partenza: puoi riordinare, togliere e aggiungere card."),
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
        )
        TextButton(onClick = onEditHome, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Personalizza le card della Home"), color = UnicumPrimary, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PersonalPage() {
    val form = remember { PersonalFormState() }
    var saved by remember { mutableStateOf(false) }

    // DATI PERSONALI E METABOLISMO BASALE
    UnicumCard {
        CardHeader(Icons.Rounded.Person, tr("Dati personali"))
        Spacer(Modifier.height(12.dp))
        PersonalDataFields(form)
    }

    Spacer(Modifier.height(14.dp))

    // OBIETTIVO CALORICO
    UnicumCard {
        CardHeader(Icons.Rounded.LocalFireDepartment, tr("Obiettivo calorico"))
        Spacer(Modifier.height(12.dp))
        GoalFields(form)

        Spacer(Modifier.height(18.dp))
        // Qualsiasi modifica riabilita il pulsante di salvataggio
        LaunchedEffect(form.snapshot) { saved = false }
        UnicumButton(
            text = if (saved) tr("Salvato") else tr("Salva"),
            enabled = form.isValid && !saved,
            onClick = {
                form.save()
                saved = true
            }
        )
        if (!form.isValid) {
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Controlla i dati: età 14–100, peso 30–250 kg, altezza 120–230 cm, metabolismo 800–4500 kcal, obiettivo 800–6000 kcal, macro al 100%."),
                    
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
            Icon(Icons.Rounded.Check, tr("Selezionata"), tint = UnicumPrimary, modifier = Modifier.size(22.dp))
        }
    }
}


/** Una riga che dice se il backup automatico è a posto, fermo o non ancora attivo. */
@Composable
private fun backupSummary(): String {
    val s = AutoBackup.state
    val last = if (s.lastSuccessMillis > 0)
        SimpleDateFormat(tr("d MMM, HH:mm"), I18n.locale).format(Date(s.lastSuccessMillis)) else null
    return when (AutoBackup.health()) {
        AutoBackupPolicy.Health.OFF -> tr("Nessun backup automatico: scegli dove salvarlo, o esporta a mano")
        AutoBackupPolicy.Health.OK -> tr("Automatico · ultimo backup: %s", last ?: tr("in corso"))
        AutoBackupPolicy.Health.STALE -> tr("Attenzione: nessun backup riuscito da giorni (ultimo: %s)", last ?: tr("mai"))
        AutoBackupPolicy.Health.HELD -> tr("Attenzione: un backup è fermo e aspetta la tua conferma")
        AutoBackupPolicy.Health.ERROR -> tr("Attenzione: l'ultimo backup non è riuscito")
    }
}

/** Collegamento a Health Connect: permessi, sincronizzazione manuale e stato (Profilo e primo avvio). */
@Composable
internal fun HealthConnectCard() {
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
            null -> Text(tr("Controllo Health Connect..."), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail)

            HealthConnectSource.Status.Unavailable,
            HealthConnectSource.Status.UpdateRequired -> {
                Text(
                    if (s == HealthConnectSource.Status.UpdateRequired)
                        tr("Health Connect va aggiornato per poter essere usato.")
                    else tr("Health Connect non è installato su questo telefono."),
                    color = UnicumDark, style = UnicumType.Detail
                )
                Spacer(Modifier.height(10.dp))
                UnicumButton(tr("Apri Play Store"), onClick = {
                    val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                })
            }

            is HealthConnectSource.Status.Available -> {
                if (!s.anyGranted) {
                    Text(tr("Non ancora collegato."), color = UnicumDark, style = UnicumType.Detail)
                    Spacer(Modifier.height(10.dp))
                    UnicumButton(tr("Collega Health Connect"), onClick = {
                        permissionLauncher.launch(s.wanted)
                    })
                } else {
                    val busy = UnicumAppState.syncing || NotionPlan.syncing
                    UnicumButton(
                        text = if (busy) tr("Sincronizzo...") else tr("Sincronizza ora"),
                        enabled = !busy,
                        onClick = { UnicumAppState.sync(context, days = 30) }
                    )
                    if (NotionPlan.connected) Text(
                        tr("Health Connect e Notion insieme"),
                        color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    val last = UserSettings.lastSyncMillis
                    Text(
                        if (last > 0) tr("Ultima sincronizzazione: %s",
                                                        SimpleDateFormat(tr("d MMM, HH:mm"), I18n.locale).format(Date(last)))
                        else tr("Mai sincronizzato"),
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
                            !s.backgroundSupported -> tr("Su questo telefono Health Connect non legge ad app chiusa: i dati si aggiornano quando apri UNICUM.")
                            !s.backgroundGranted -> tr("Per aggiornarsi anche ad app chiusa serve il permesso \"in background\" di Health Connect.")
                            else -> tr("Si aggiorna da sola anche ad app chiusa, circa ogni 3 ore") +
                                (BackgroundSync.lastRunText(context)?.let { " " + tr("(ultima volta: %s)", it) } ?: "") + "."
                        },
                        color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (!s.allGranted) {
                        TextButton(
                            onClick = { permissionLauncher.launch(s.wanted) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(tr("Concedi i permessi mancanti"), color = UnicumPrimary) }
                    }
                }
            }
        }
        // I dati arrivano solo se anche le altre app (orologio, anello, bilancia) scrivono in Health Connect
        if (status is HealthConnectSource.Status.Available) OtherAppsHint()
    }
}

/**
 * Promemoria sotto la card di Health Connect: UNICUM legge solo quello che le app di orologio, anello e bilancia
 * scrivono in Health Connect, quindi anche loro devono avere il permesso. Il tasto apre le impostazioni di Health
 * Connect (in Android 14+ sono nelle impostazioni di sistema), dove si vedono i permessi di ogni app.
 */
@Composable
private fun OtherAppsHint() {
    val context = LocalContext.current
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(UnicumAccent.copy(alpha = .35f)))
    Spacer(Modifier.height(10.dp))
    Text(
        tr("Controlla anche le altre app: l'app dell'orologio, dell'anello o della bilancia (Samsung Health, Garmin Connect, Oura, Health Sync…) deve avere il permesso di scrivere in Health Connect, altrimenti qui non arriva niente."),
        color = UnicumDark.copy(alpha = .7f), style = UnicumType.Caption, lineHeight = 17.sp
    )
    TextButton(onClick = { openHealthConnectSettings(context) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(tr("Apri le impostazioni di Health Connect"), color = UnicumPrimary, fontWeight = FontWeight.SemiBold)
    }
}

/** Le impostazioni di Health Connect (permessi delle app); se il telefono non le apre, i dati gestiti o il Play Store. */
internal fun openHealthConnectSettings(context: android.content.Context) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val attempts = listOf(
        { Intent(androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS) },
        { androidx.health.connect.client.HealthConnectClient.getHealthConnectManageDataIntent(context) },
        { Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")) }
    )
    for (make in attempts) {
        if (runCatching { context.startActivity(make().addFlags(flags)) }.isSuccess) return
    }
}

private fun syncSummary(r: SyncResult): String? = when {
    r.error != null -> tr("Sincronizzazione non riuscita: %s", r.error)
    r.isEmpty -> tr("Nessun dato nuovo trovato. Controlla che le app dell'orologio, dell'anello o della bilancia condividano i dati con Health Connect.") +
        (if (r.skipped.isNotEmpty()) " " + tr("Permessi non concessi: %s.", r.skipped.joinToString(", ")) else "")
    r.skipped.isNotEmpty() -> tr("Permessi mancanti: %s.", r.skipped.joinToString(", "))
    else -> null
}
