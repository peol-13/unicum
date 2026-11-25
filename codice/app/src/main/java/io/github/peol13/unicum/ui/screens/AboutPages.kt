package io.github.peol13.unicum.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.BuildConfig
import io.github.peol13.unicum.data.AppInfo
import io.github.peol13.unicum.data.FeedbackPrompt
import io.github.peol13.unicum.i18n.tr
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.ChevronRight

/** Indirizzi pubblici del progetto (gli stessi della pagina web). */
internal object ProjectLinks {
    const val ISSUES = "${AppInfo.PROJECT_URL}/issues/new/choose"
    const val PRIVACY = "https://peol-13.github.io/unicum/privacy.html"
    const val RELEASES = "${AppInfo.PROJECT_URL}/releases"
}

internal fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** "Invia un parere": in privato (modulo, senza account) o su GitHub, come sulla pagina web. */
@Composable
fun FeedbackPage() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            tr("Un problema, un PDF che non legge, un'idea o solo com'è andata: leggo tutto."),
            color = UnicumDark.copy(alpha = .7f), style = UnicumType.Detail, lineHeight = 19.sp
        )
        LinkRow(
            Icons.Rounded.MailOutline, tr("In privato"),
            tr("Un modulo di un minuto, senza account. Arriva direttamente all'autore.")
        ) {
            openUrl(context, FeedbackPrompt.formUrl())
            FeedbackPrompt.answered()
        }
        LinkRow(
            Icons.Rounded.Forum, tr("Su GitHub, in pubblico"),
            tr("Gli altri possono leggere e commentare. Serve un account GitHub, gratuito.")
        ) { openUrl(context, ProjectLinks.ISSUES) }
        Text(
            tr("Se segnali un problema, scrivi anche questa riga: %s", FeedbackPrompt.deviceLine()),
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Informazioni: versione, codice sorgente, privacy, licenza e crediti dei dati e delle librerie usate. */
@Composable
fun AboutPage(onWhatsNew: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UnicumCard {
            Text("UNICUM ${BuildConfig.VERSION_NAME}", color = UnicumDark, style = UnicumType.Card, fontWeight = FontWeight.SemiBold)
            Text("Your health. Unified.", color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail)
            Spacer(Modifier.height(8.dp))
            Text(
                tr("App open source: i tuoi dati restano sul telefono. Licenza GPL-3.0 o successiva."),
                color = UnicumDark.copy(alpha = .7f), style = UnicumType.Caption, lineHeight = 17.sp
            )
        }
        LinkRow(Icons.Rounded.NewReleases, tr("Novità di questa versione"), tr("Cosa è cambiato e le versioni precedenti"), external = false, onClick = onWhatsNew)
        LinkRow(Icons.Rounded.Code, tr("Codice sorgente"), "github.com/peol-13/unicum") { openUrl(context, AppInfo.PROJECT_URL) }
        LinkRow(Icons.Rounded.Policy, tr("Informativa sulla privacy"), tr("Cosa legge l'app e a chi si collega")) { openUrl(context, ProjectLinks.PRIVACY) }
        UnicumCard {
            CardHeader(Icons.Rounded.Code, tr("Crediti"))
            Spacer(Modifier.height(8.dp))
            listOf(
                "Open Food Facts" to tr("Prodotti con marca e codici a barre · dati ODbL, © i contributori di Open Food Facts"),
                "OpenStreetMap" to tr("Mappe dei percorsi · © i contributori di OpenStreetMap (ODbL)"),
                "Health Connect" to tr("Dati di salute dalle app del telefono · Google"),
                "Montserrat" to "SIL Open Font License 1.1",
                "PdfBox-Android" to "Apache License 2.0",
                "ML Kit Barcode Scanning" to tr("Lettura dei codici a barre sul telefono · termini di Google"),
                "Jetpack Compose, AndroidX, Kotlin" to "Apache License 2.0"
            ).forEach { (name, detail) ->
                Column(Modifier.padding(vertical = 5.dp)) {
                    Text(name, color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
                    Text(detail, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, external: Boolean = true, onClick: () -> Unit) {
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
            Icon(
                if (external) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.ChevronRight, null,
                tint = UnicumDark.copy(alpha = .35f), modifier = Modifier.size(if (external) 18.dp else 24.dp)
            )
        }
    }
}

/** Finestra con le novità della versione installata; "Tutte le versioni" apre le release su GitHub. */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val release = io.github.peol13.unicum.data.WhatsNew.forVersion(BuildConfig.VERSION_NAME)
        ?: io.github.peol13.unicum.data.WhatsNew.releases.first()
    io.github.peol13.unicum.ui.components.UnicumDialog(onDismiss = onDismiss, spacing = 10.dp) {
        Text(tr("Novità di UNICUM %s", release.version), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        release.items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(UnicumPrimary)
                )
                Spacer(Modifier.width(10.dp))
                Text(tr(item), color = UnicumDark.copy(alpha = .85f), style = UnicumType.Detail, lineHeight = 19.sp)
            }
        }
        io.github.peol13.unicum.ui.components.UnicumButton(tr("Fatto"), onClick = onDismiss)
        androidx.compose.material3.TextButton(onClick = { openUrl(context, ProjectLinks.RELEASES) }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Tutte le versioni"), color = UnicumPrimary)
        }
    }
}

/** C'è una finestra delle novità da mostrare: le altre richieste (statistiche anonime) aspettano. */
fun whatsNewPending(): Boolean {
    val current = io.github.peol13.unicum.data.WhatsNew.core(BuildConfig.VERSION_NAME)
    return io.github.peol13.unicum.data.UserSettings.whatsNewSeen != current &&
        !io.github.peol13.unicum.UnicumAppState.onboardedThisSession &&
        io.github.peol13.unicum.data.WhatsNew.forVersion(current) != null
}

/** Dopo un aggiornamento la finestra delle novità compare una volta (non a chi ha appena installato l'app). */
@Composable
fun WhatsNewOnUpdate() {
    val current = io.github.peol13.unicum.data.WhatsNew.core(BuildConfig.VERSION_NAME)
    if (io.github.peol13.unicum.data.UserSettings.whatsNewSeen == current) return
    if (io.github.peol13.unicum.UnicumAppState.onboardedThisSession || io.github.peol13.unicum.data.WhatsNew.forVersion(current) == null) {
        androidx.compose.runtime.LaunchedEffect(current) { io.github.peol13.unicum.data.UserSettings.markWhatsNewSeen(current) }
        return
    }
    WhatsNewDialog(onDismiss = { io.github.peol13.unicum.data.UserSettings.markWhatsNewSeen(current) })
}
