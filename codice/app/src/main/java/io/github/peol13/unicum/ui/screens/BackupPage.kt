package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.trp

import io.github.peol13.unicum.i18n.tr

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.AutoBackup
import io.github.peol13.unicum.data.AutoBackupPolicy
import io.github.peol13.unicum.data.BackupCodec
import io.github.peol13.unicum.data.BackupFile
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumDialog
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.peol13.unicum.ui.theme.UnicumType

private class PendingImport(val file: BackupFile, val plan: BackupCodec.Plan)

/** Esporta/importa i dati creati nell'app, per non perderli cambiando telefono. */
@Composable
fun BackupPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingImport?>(null) }
    // Spento ogni volta che si apre la pagina: il token entra nel file solo per scelta esplicita
    var withNotion by remember { mutableStateOf(false) }
    // Già fatto in MainActivity; ripetuto per sicurezza (non fa niente se già inizializzato)
    NotionPlan.init(context)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        val includeNotion = withNotion && NotionPlan.connected
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = UnicumRepository.exportBackup(context, includeNotion)
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            busy = false
            message = when {
                !ok -> tr("Non sono riuscito a salvare il file.")
                includeNotion -> tr("Backup salvato, con il token di Notion: non condividere il file e tienilo in un posto sicuro.")
                else -> tr("Backup salvato. Conservalo in un posto sicuro (Drive, PC).")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                    BackupCodec.decode(text)
                }.getOrNull()
            }
            if (result == null) {
                message = tr("Il file non è un backup di UNICUM leggibile.")
            } else {
                pending = PendingImport(result, UnicumRepository.planImport(context, result))
            }
            busy = false
        }
    }

    AutoBackupCard(onMessage = { message = it })
    Spacer(Modifier.height(14.dp))
    UnicumCard {
        CardHeader(Icons.Rounded.Save, tr("Esporta"))
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Crea un file con pasti, allenamenti inseriti a mano, alimenti personali, pasti fissi, prodotti salvati, profilo e obiettivi, scarpe, sensazioni e correzioni delle corse (ripetute comprese) e promemoria. Sonno, passi e allenamenti di Health Connect non servono: tornano da soli con la sincronizzazione."),
                
                
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp
        )
        if (NotionPlan.connected) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Includi il collegamento a Notion"), color = UnicumDark, style = UnicumType.Detail)
                    Text(
                        if (withNotion) tr("Il file conterrà il token in chiaro: chi lo trova può leggere le pagine condivise con l'integrazione. Non metterlo in cartelle condivise.")
                            
                        else tr("Spento, il token resta fuori da questo file: sul telefono nuovo va incollato di nuovo (il backup automatico lo include, se l'hai lasciato acceso)."),
                            
                        color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = withNotion,
                    onCheckedChange = { withNotion = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = UnicumOnPrimary, checkedTrackColor = UnicumPrimary,
                        uncheckedTrackColor = UnicumAccent.copy(alpha = .3f)
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        UnicumButton(
            tr("Esporta i dati"), enabled = !busy,
            onClick = { exportLauncher.launch(BackupCodec.nameFor(UnicumRepository.today())) }
        )
    }
    Spacer(Modifier.height(14.dp))
    UnicumCard {
        CardHeader(Icons.Rounded.Save, tr("Importa"))
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Scegli un file di backup: prima vedi cosa contiene, poi confermi. I dati si aggiungono a quelli che hai già, senza duplicare gli stessi pasti."),
                
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp
        )
        Spacer(Modifier.height(12.dp))
        UnicumButton(
            tr("Scegli il file"), enabled = !busy,
            onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) }
        )
    }
    message?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = UnicumDark.copy(alpha = .8f), style = UnicumType.Detail, lineHeight = 18.sp)
    }

    pending?.let { p ->
        ImportConfirmDialog(
            p,
            onCancel = { pending = null },
            onConfirm = {
                pending = null
                busy = true
                scope.launch {
                    val waiting = runCatching {
                        UnicumRepository.applyImport(context, p.file, p.plan)
                        UnicumAppState.afterImport(context)
                        UnicumRepository.pendingBackupRuns(context)
                    }.getOrNull()
                    busy = false
                    message = when {
                        waiting == null -> tr("Importazione non riuscita.")
                        waiting > 0 -> trp(waiting, "Importazione completata. Sensazioni e scarpe di %d corsa si assegnano dopo la sincronizzazione con Health Connect.", "Importazione completata. Sensazioni e scarpe di %d corse si assegnano dopo la sincronizzazione con Health Connect.")
                            
                        else -> tr("Importazione completata.")
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/** Backup automatico: una copia al giorno in un posto scelto una volta (anche Google Drive). */
@Composable
private fun AutoBackupCard(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val state = AutoBackup.state
    val place = remember(state.uri) { AutoBackup.placeLabel(context) }
    val health = AutoBackup.health()

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!AutoBackup.setTarget(context, uri)) {
            onMessage(tr("Quel posto non permette il salvataggio automatico. Scegline un altro (Drive o una cartella del telefono)."))
            return@rememberLauncherForActivityResult
        }
        busy = true
        scope.launch {
            val r = AutoBackup.run(context, force = true)
            busy = false
            onMessage(outcomeText(r))
        }
    }

    fun runNow(overrideHold: Boolean) {
        busy = true
        scope.launch {
            val r = AutoBackup.run(context, force = true, overrideHold = overrideHold)
            busy = false
            onMessage(outcomeText(r))
        }
    }

    UnicumCard {
        CardHeader(Icons.Rounded.Save, tr("Backup automatico"))
        Spacer(Modifier.height(6.dp))
        if (!state.enabled) {
            Text(
                tr("Una copia al giorno di tutto quello che c'è nell'app (come l'esportazione manuale), anche ad app chiusa. Quando scegli dove salvarla, nel selettore di Android apri Drive: il file resta nel tuo Google Drive, che tiene anche le versioni precedenti. Va bene anche una cartella del telefono."),
                    
                    
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp
            )
            Spacer(Modifier.height(12.dp))
            UnicumButton(tr("Scegli dove salvarlo"), enabled = !busy, onClick = { pickLauncher.launch(AutoBackup.SUGGESTED_NAME) })
        } else {
            Text(tr("Salvato in: %s", place ?: "—"), color = UnicumDark, style = UnicumType.Detail)
            val last = if (state.lastSuccessMillis > 0)
                SimpleDateFormat(tr("d MMM, HH:mm"), I18n.locale).format(Date(state.lastSuccessMillis)) +
                    " · " + formatSize(state.lastBytes)
            else tr("ancora nessuno")
            Text(
                tr("Ultimo backup riuscito: %s", last),
                color = UnicumDark.copy(alpha = .7f), style = UnicumType.Caption, modifier = Modifier.padding(top = 4.dp)
            )
            val problem = state.held ?: state.lastError
            if (problem != null && health != AutoBackupPolicy.Health.OK) {
                Text(
                    problem, color = UnicumDark, style = UnicumType.Caption, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (health == AutoBackupPolicy.Health.STALE) {
                Text(
                    tr("Non c'è un backup riuscito da qualche giorno: apri l'app con la rete accesa o salva ora."),
                    color = UnicumDark, style = UnicumType.Caption, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            if (state.held != null) {
                UnicumButton(tr("Salva comunque"), enabled = !busy, onClick = { runNow(overrideHold = true) })
                Spacer(Modifier.height(8.dp))
            }
            // Collegamento a Notion nel file: su un telefono nuovo l'app si ricollega da sola
            if (NotionPlan.connected) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Includi il collegamento a Notion"), color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
                        Text(
                            tr("Token e database nel file: su un telefono nuovo l'app si ricollega da sola. Il token legge solo le pagine condivise con l'integrazione: tieni il file in un posto solo tuo."),
                            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.material3.Switch(
                        checked = state.includeNotion,
                        onCheckedChange = { AutoBackup.setIncludeNotion(context, it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = io.github.peol13.unicum.ui.theme.UnicumOnPrimary, checkedTrackColor = UnicumPrimary,
                            uncheckedTrackColor = io.github.peol13.unicum.ui.theme.UnicumAccent.copy(alpha = .3f)
                        )
                    )
                }
            }
            UnicumButton(if (busy) tr("Salvo...") else tr("Salva ora"), enabled = !busy, onClick = { runNow(overrideHold = false) })
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { pickLauncher.launch(AutoBackup.SUGGESTED_NAME) }, modifier = Modifier.weight(1f)) {
                    Text(tr("Cambia posto"), color = UnicumPrimary)
                }
                TextButton(onClick = { AutoBackup.disable(context) }, modifier = Modifier.weight(1f)) {
                    Text(tr("Disattiva"), color = UnicumPrimary)
                }
            }
        }
    }
}

private fun outcomeText(r: AutoBackup.Outcome): String = when (r) {
    is AutoBackup.Outcome.Saved -> tr("Backup salvato (%s).", formatSize(r.bytes))
    is AutoBackup.Outcome.Held -> r.message
    is AutoBackup.Outcome.Failed -> r.message
    AutoBackup.Outcome.NotConfigured -> tr("Scegli prima dove salvare il backup.")
    AutoBackup.Outcome.NotDue -> tr("Il backup di oggi è già fatto.")
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) String.format(I18n.locale, "%.1f MB", bytes / 1_000_000.0) else "${maxOf(1L, bytes / 1000)} kB"

@Composable
private fun ImportConfirmDialog(p: PendingImport, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val plan = p.plan
    UnicumDialog(onCancel, spacing = 10.dp) {
        Text(tr("Importare il backup?"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        if (plan.isEmpty) {
            Text(
                tr("Tutto quello che c'è nel file è già nell'app: non c'è niente da aggiungere."),
                color = UnicumDark.copy(alpha = .75f), style = UnicumType.Body, lineHeight = 20.sp
            )
        } else {
            Text(
                tr("Verranno aggiunti: %s", listOfNotNull(
                    plan.newMeals.size.takeIf { it > 0 }?.let { trp(it, "%d alimento nei pasti", "%d alimenti nei pasti") },
                    plan.newActivities.size.takeIf { it > 0 }?.let { trp(it, "%d allenamento", "%d allenamenti") },
                    plan.newFoods.size.takeIf { it > 0 }?.let { trp(it, "%d prodotto salvato", "%d prodotti salvati") },
                    plan.newRunFeedback.takeIf { it > 0 }?.let { trp(it, "le sensazioni di %d allenamento", "le sensazioni di %d allenamenti") }
                ).ifEmpty { listOf(tr("solo le impostazioni")) }.joinToString(", ")) +
                    (if (plan.days > 0) ", " + trp(plan.days, "su %d giorno", "su %d giorni") + "." else "."),
                color = UnicumDark, style = UnicumType.Body, lineHeight = 20.sp
            )
            val skipped = plan.skippedMeals + plan.skippedActivities
            if (skipped > 0) {
                Text(
                    tr("%d voci erano già presenti e non vengono ripetute.", skipped),
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail
                )
            }
            if (plan.hasSettings) {
                Text(
                    tr("Il profilo, gli obiettivi e le preferenze del file sostituiranno quelli attuali; gli alimenti personali si uniscono."),
                        
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
            if (plan.hasGoals) {
                Text(
                    tr("Gli obiettivi di corsa del file sostituiranno quelli attuali."),
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
            if (plan.hasPlan) {
                Text(
                    tr("Il programma di allenamento del file sostituirà quello tenuto nell'app."),
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
            if (plan.hasNotion) {
                Text(
                    tr("Il file contiene il collegamento a Notion: l'app si ricollega senza reinserire il token."),
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
            if (plan.hasReminders) {
                Text(
                    tr("Anche gli orari dei promemoria vengono dal file. Su un telefono nuovo apri poi Profilo → Notifiche per consentire le notifiche."),
                        
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (!plan.isEmpty) UnicumButton(tr("Importa"), onClick = onConfirm)
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(if (plan.isEmpty) tr("Chiudi") else tr("Annulla"), color = UnicumPrimary)
        }
    }
}
