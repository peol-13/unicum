package io.github.peol13.unicum.ui.screens

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
    remember { NotionPlan.init(context) }

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
                !ok -> "Non sono riuscito a salvare il file."
                includeNotion -> "Backup salvato, con il token di Notion: non condividere il file e tienilo in un posto sicuro."
                else -> "Backup salvato. Conservalo in un posto sicuro (Drive, PC)."
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
                message = "Il file non è un backup di UNICUM leggibile."
            } else {
                pending = PendingImport(result, UnicumRepository.planImport(context, result))
            }
            busy = false
        }
    }

    AutoBackupCard(onMessage = { message = it })
    Spacer(Modifier.height(14.dp))
    UnicumCard {
        CardHeader(Icons.Rounded.Save, "Esporta")
        Spacer(Modifier.height(6.dp))
        Text(
            "Crea un file con pasti, allenamenti inseriti a mano, alimenti personali, pasti fissi, prodotti salvati, " +
                "profilo e obiettivi, scarpe, sensazioni e correzioni delle corse (ripetute comprese) e promemoria. " +
                "Sonno, passi e allenamenti di Health Connect non servono: tornano da soli con la sincronizzazione.",
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp
        )
        if (NotionPlan.connected) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Includi il collegamento a Notion", color = UnicumDark, style = UnicumType.Detail)
                    Text(
                        if (withNotion) "Il file conterrà il token in chiaro: chi lo trova può leggere le pagine " +
                            "condivise con l'integrazione. Non metterlo in cartelle condivise."
                        else "Spento, il token resta fuori: sul telefono nuovo va incollato di nuovo. " +
                            "Il backup automatico non lo include mai.",
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
            "Esporta i dati", enabled = !busy,
            onClick = { exportLauncher.launch(BackupCodec.nameFor(UnicumRepository.today())) }
        )
    }
    Spacer(Modifier.height(14.dp))
    UnicumCard {
        CardHeader(Icons.Rounded.Save, "Importa")
        Spacer(Modifier.height(6.dp))
        Text(
            "Scegli un file di backup: prima vedi cosa contiene, poi confermi. I dati si aggiungono a quelli " +
                "che hai già, senza duplicare gli stessi pasti.",
            color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp
        )
        Spacer(Modifier.height(12.dp))
        UnicumButton(
            "Scegli il file", enabled = !busy,
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
                        waiting == null -> "Importazione non riuscita."
                        waiting > 0 -> "Importazione completata. Sensazioni e scarpe di $waiting " +
                            "${if (waiting == 1) "corsa" else "corse"} si assegnano dopo la sincronizzazione con Health Connect."
                        else -> "Importazione completata."
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
            onMessage("Quel posto non permette il salvataggio automatico. Scegline un altro (Drive o una cartella del telefono).")
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
        CardHeader(Icons.Rounded.Save, "Backup automatico")
        Spacer(Modifier.height(6.dp))
        if (!state.enabled) {
            Text(
                "Una copia al giorno di tutto quello che c'è nell'app (come l'esportazione manuale), anche ad app chiusa. " +
                    "Quando scegli dove salvarla, nel selettore di Android apri Drive: il file resta nel tuo Google Drive, " +
                    "che tiene anche le versioni precedenti. Va bene anche una cartella del telefono.",
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp
            )
            Spacer(Modifier.height(12.dp))
            UnicumButton("Scegli dove salvarlo", enabled = !busy, onClick = { pickLauncher.launch(AutoBackup.SUGGESTED_NAME) })
        } else {
            Text("Salvato in: ${place ?: "—"}", color = UnicumDark, style = UnicumType.Detail)
            val last = if (state.lastSuccessMillis > 0)
                SimpleDateFormat("d MMM, HH:mm", Locale.ITALY).format(Date(state.lastSuccessMillis)) +
                    " · " + formatSize(state.lastBytes)
            else "ancora nessuno"
            Text(
                "Ultimo backup riuscito: $last",
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
                    "Non c'è un backup riuscito da qualche giorno: apri l'app con la rete accesa o salva ora.",
                    color = UnicumDark, style = UnicumType.Caption, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            if (state.held != null) {
                UnicumButton("Salva comunque", enabled = !busy, onClick = { runNow(overrideHold = true) })
                Spacer(Modifier.height(8.dp))
            }
            UnicumButton(if (busy) "Salvo..." else "Salva ora", enabled = !busy, onClick = { runNow(overrideHold = false) })
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { pickLauncher.launch(AutoBackup.SUGGESTED_NAME) }, modifier = Modifier.weight(1f)) {
                    Text("Cambia posto", color = UnicumPrimary)
                }
                TextButton(onClick = { AutoBackup.disable(context) }, modifier = Modifier.weight(1f)) {
                    Text("Disattiva", color = UnicumPrimary)
                }
            }
        }
    }
}

private fun outcomeText(r: AutoBackup.Outcome): String = when (r) {
    is AutoBackup.Outcome.Saved -> "Backup salvato (${formatSize(r.bytes)})."
    is AutoBackup.Outcome.Held -> r.message
    is AutoBackup.Outcome.Failed -> r.message
    AutoBackup.Outcome.NotConfigured -> "Scegli prima dove salvare il backup."
    AutoBackup.Outcome.NotDue -> "Il backup di oggi è già fatto."
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) String.format(Locale.ITALY, "%.1f MB", bytes / 1_000_000.0) else "${maxOf(1L, bytes / 1000)} kB"

@Composable
private fun ImportConfirmDialog(p: PendingImport, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val plan = p.plan
    UnicumDialog(onCancel, spacing = 10.dp) {
        Text("Importare il backup?", color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        if (plan.isEmpty) {
            Text(
                "Tutto quello che c'è nel file è già nell'app: non c'è niente da aggiungere.",
                color = UnicumDark.copy(alpha = .75f), style = UnicumType.Body, lineHeight = 20.sp
            )
        } else {
            Text(
                "Verranno aggiunti: " + listOfNotNull(
                    plan.newMeals.size.takeIf { it > 0 }?.let { if (it == 1) "1 alimento nei pasti" else "$it alimenti nei pasti" },
                    plan.newActivities.size.takeIf { it > 0 }?.let { if (it == 1) "1 allenamento" else "$it allenamenti" },
                    plan.newFoods.size.takeIf { it > 0 }?.let { if (it == 1) "1 prodotto salvato" else "$it prodotti salvati" },
                    plan.newRunFeedback.takeIf { it > 0 }?.let { if (it == 1) "le sensazioni di 1 corsa" else "le sensazioni di $it corse" }
                ).ifEmpty { listOf("solo le impostazioni") }.joinToString(", ") +
                    (if (plan.days > 0) ", su ${plan.days} ${if (plan.days == 1) "giorno" else "giorni"}." else "."),
                color = UnicumDark, style = UnicumType.Body, lineHeight = 20.sp
            )
            val skipped = plan.skippedMeals + plan.skippedActivities
            if (skipped > 0) {
                Text(
                    "$skipped voci erano già presenti e non vengono ripetute.",
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail
                )
            }
            if (plan.hasSettings) {
                Text(
                    "Il profilo, gli obiettivi e le preferenze del file sostituiranno quelli attuali; " +
                        "gli alimenti personali si uniscono.",
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
            if (plan.hasGoals) {
                Text(
                    "Gli obiettivi di corsa del file sostituiranno quelli attuali.",
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
            if (plan.hasPlan) {
                Text(
                    "Il programma di allenamento del file sostituirà quello tenuto nell'app.",
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
            if (plan.hasNotion) {
                Text(
                    "Il file contiene il collegamento a Notion: l'app si ricollega senza reinserire il token.",
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
            if (plan.hasReminders) {
                Text(
                    "Anche gli orari dei promemoria vengono dal file. Su un telefono nuovo apri poi Profilo → Promemoria " +
                        "per consentire le notifiche.",
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 18.sp
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (!plan.isEmpty) UnicumButton("Importa", onClick = onConfirm)
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(if (plan.isEmpty) "Chiudi" else "Annulla", color = UnicumPrimary)
        }
    }
}
