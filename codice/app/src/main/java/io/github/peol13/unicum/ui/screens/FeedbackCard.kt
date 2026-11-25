package io.github.peol13.unicum.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.FeedbackPrompt
import io.github.peol13.unicum.i18n.tr
import io.github.peol13.unicum.ui.components.CardHeader
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumType

/**
 * Card "Come ti trovi?" nella Home: propone il modulo dei pareri a chi usa l'app da qualche giorno.
 * Quando compare e quando sparisce lo decide [FeedbackPrompt].
 */
@Composable
fun FeedbackCard() {
    if (!FeedbackPrompt.visible) return
    val context = LocalContext.current
    UnicumCard {
        CardHeader(Icons.Rounded.Forum, tr("Come ti trovi con UNICUM?"))
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Un minuto, senza account: dimmi cosa migliorare e vota la prossima funzione. Leggo ogni risposta."),
            color = UnicumDark.copy(alpha = .7f), style = UnicumType.Detail, lineHeight = 19.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { FeedbackPrompt.notNow() }) {
                Text(tr("Non ora"), color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail)
            }
            Spacer(Modifier.width(8.dp))
            UnicumButton(
                tr("Rispondi"),
                onClick = {
                    openUrl(context, FeedbackPrompt.formUrl())
                    FeedbackPrompt.answered()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}
