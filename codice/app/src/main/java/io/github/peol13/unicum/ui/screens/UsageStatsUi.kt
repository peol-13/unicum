package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.tr

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.UsageStats
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.components.UnicumDialog
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType

private val WHAT_IS_SENT get() = tr(
        "Una volta al giorno: quali schermate hai aperto e quante volte, in quali giorni, versione dell'app e di Android, quali collegamenti usi (Health Connect, Notion). Con un codice casuale, non con il tuo nome. Mai dati di salute, pasti, allenamenti o profilo."
)
        
        

/**
 * Domanda delle statistiche dettagliate per chi ha aggiornato (a chi installa per la prima volta la fa il primo avvio).
 * "Sì" è il pulsante grande; "Non ora" è piccolo e la domanda torna più tardi.
 */
@Composable
fun UsageStatsConsentDialog() {
    if (!UsageStats.asking) return
    UnicumDialog(onDismiss = {}) {
        Text(tr("Ci aiuti a migliorare UNICUM?"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        Text(
            tr("UNICUM è un'app nuova: le statistiche anonime ci dicono quali funzioni usi di più e quale migliorare per prima. Nessun nome, mai dati di salute."),
            color = UnicumDark, style = UnicumType.Detail, lineHeight = 20.sp
        )
        Text(WHAT_IS_SENT, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 17.sp)
        UnicumButton(tr("Sì, invia"), onClick = { UsageStats.choose(true) })
        TextButton(onClick = { UsageStats.postpone() }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Non ora"), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, fontWeight = FontWeight.Medium)
        }
    }
}

/** Sotto-pagina del Profilo: interruttore delle statistiche dettagliate. */
@Composable
fun UsageStatsPage() {
    UnicumCard {
        CardHeader(Icons.Rounded.BarChart, tr("Invio statistiche anonime"))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Aiutano a capire quante persone usano UNICUM e quali schermate servono di più."),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = UsageStats.enabled == true,
                onCheckedChange = { UsageStats.choose(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = UnicumOnPrimary, checkedTrackColor = UnicumPrimary,
                    uncheckedTrackColor = UnicumAccent.copy(alpha = .3f)
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(WHAT_IS_SENT, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Spegnendole, i conteggi non ancora inviati e il codice casuale si cancellano dal telefono."),
            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
        )
        UsageStats.installId?.let { id ->
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Il tuo codice: %s. Per far cancellare i dati già inviati, scrivi allo sviluppatore con questo codice.", id.take(8)),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, lineHeight = 16.sp
            )
        }
    }
}

/** Riga del menu del Profilo. */
val usageStatsSummary: String
    get() = when (UsageStats.enabled) {
        true -> tr("Attive: anonime, una volta al giorno")
        else -> tr("Spente · aiuta a capire quali parti migliorare")
    }
