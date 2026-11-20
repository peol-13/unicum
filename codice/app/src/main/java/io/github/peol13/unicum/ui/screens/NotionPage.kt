package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.ui.Route

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import io.github.peol13.unicum.data.NotionSource
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.DeleteRed
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumScreen
import io.github.peol13.unicum.ui.components.unicumFieldColors
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumType
import kotlinx.coroutines.launch

/**
 * Un database da cui legge l'app: ruolo, nome come è in Notion (o quello previsto finché non è stato letto) e id
 * abbreviato. Toccandolo si apre in Notion.
 */
@Composable
private fun SourceRow(role: String, fallback: String, source: NotionSource) {
    val context = LocalContext.current
    val url = source.url ?: "https://www.notion.so/${source.id}"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(role, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
            Text(source.title ?: fallback, color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium)
            Text("id ${source.id.take(8)}…", color = UnicumDark.copy(alpha = .45f), style = UnicumType.Note)
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = tr("Apri in Notion"), tint = UnicumPrimary, modifier = Modifier.size(18.dp))
    }
}

/** Collegamento al database "Programma corse": token dell'integrazione, database, stato dell'ultima lettura. */
@Composable
fun NotionPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    NotionPlan.init(context)
    var token by remember { mutableStateOf("") }
    var database by remember { mutableStateOf(NotionPlan.databaseId.orEmpty()) }
    var logDatabase by remember { mutableStateOf(NotionPlan.logDatabaseId.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }

    UnicumCard {
        CardHeader(Icons.Rounded.CalendarMonth, if (NotionPlan.connected) tr("Collegato") else tr("Come collegarlo"))
        Spacer(Modifier.height(8.dp))
        if (NotionPlan.connected) {
            Text(
                when {
                    NotionPlan.syncing -> tr("Lettura in corso…")
                    NotionPlan.error != null -> NotionPlan.error!!
                    NotionPlan.lastSyncMillis > 0 ->
                        tr("%d sedute del programma e %d corse del registro, lette %s.", NotionPlan.runs.size, NotionPlan.log.size, syncTime(NotionPlan.lastSyncMillis))
                    else -> tr("Non ancora letto.")
                },
                color = UnicumDark.copy(alpha = .7f), style = UnicumType.Detail, lineHeight = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Dal Registro corse arrivano tipo, RPE e nota delle corse fatte (vincono su quelli scritti nell'app); dal Programma le prossime sedute e, per intervalli e progressivi, i pezzi della corsa. Si aggiorna all'apertura dell'app, quando tiri giù la pagina e con \"Sincronizza ora\" nel Profilo, insieme a Health Connect. L'app legge soltanto: per cambiare qualcosa modificalo in Notion."),
                    
                    
                    
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
            )
            Spacer(Modifier.height(14.dp))
            Text(tr("Legge da"), color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
            NotionPlan.planSource?.let { SourceRow(tr("Programma"), tr("Programma corse"), it) }
            NotionPlan.logSource?.let { SourceRow(tr("Registro"), tr("Registro corse"), it) } ?: Text(
                tr("Nessun registro: tipo, RPE e nota delle corse si scrivono nell'app."),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { NotionPlan.disconnect(); message = null }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Scollega Notion"), color = DeleteRed, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
            }
        } else {
            listOf(
                tr("1. Da computer apri notion.so/profile/integrations e crea un'integrazione interna (per esempio \"UNICUM\"). Basta la lettura dei contenuti."),
                    
                tr("2. Copia il suo token segreto (inizia con \"ntn_\")."),
                tr("3. In Notion apri la pagina con i database del programma → ••• → Connessioni → aggiungi l'integrazione."),
                tr("4. Incolla qui il token e il link del database del programma (Condividi → Copia link).")
            ).forEach {
                Text(it, color = UnicumDark.copy(alpha = .75f), style = UnicumType.Detail, lineHeight = 18.sp)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
    if (NotionPlan.connected) return

    Spacer(Modifier.height(14.dp))
    UnicumCard {
        CardHeader(Icons.Rounded.Key, tr("Token e database"))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token, onValueChange = { token = it.trim(); message = null },
            label = { Text(tr("Token dell'integrazione")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            colors = unicumFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = database, onValueChange = { database = it; message = null },
            label = { Text(tr("Database del programma (link o id)")) },
            singleLine = true,
            colors = unicumFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = logDatabase, onValueChange = { logDatabase = it; message = null },
            label = { Text(tr("Registro delle corse (facoltativo)")) },
            singleLine = true,
            colors = unicumFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Se lasci vuoto il registro, l'app cerca tra i database condivisi quello con \"Registro\" nel nome o con il campo RPE."),
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 14.sp
        )
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DeleteRed, style = UnicumType.Caption)
        }
        Spacer(Modifier.height(14.dp))
        UnicumButton(
            tr("Collega e leggi"),
            enabled = token.isNotBlank() && database.isNotBlank(),
            onClick = {
                message = NotionPlan.connect(token, database, logDatabase)
                if (message == null) scope.launch { NotionPlan.sync(context) }
            }
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        tr("Il token resta su questo telefono (finisce in un backup solo se lo scegli) e serve unicamente a leggere il database."),
        color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth()
    )
}
