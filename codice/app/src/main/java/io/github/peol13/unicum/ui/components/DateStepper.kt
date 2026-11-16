package io.github.peol13.unicum.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType

/**
 * Navigazione tra date "‹ etichetta ›": serve per i giorni (Diario) e per le settimane (Statistiche).
 * La freccia avanti si disattiva quando non c'è niente di più recente da vedere.
 */
@Composable
fun DateStepper(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, "Precedente", tint = UnicumPrimary, modifier = Modifier.size(30.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label, color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(subtitle, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption, textAlign = TextAlign.Center)
            }
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.Rounded.ChevronRight, "Successivo",
                tint = if (canGoNext) UnicumPrimary else UnicumDark.copy(alpha = .25f),
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
