package io.github.peol13.unicum.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.UpdateCheck
import io.github.peol13.unicum.i18n.tr
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType

/**
 * Banner in cima alla Home quando su GitHub c'è una versione più nuova ([UpdateCheck]).
 * Toccandolo si apre la pagina della release con l'APK; "×" lo nasconde fino alla release successiva.
 */
@Composable
fun UpdateBanner() {
    val version = UpdateCheck.available ?: return
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(UnicumAccent.copy(alpha = .28f))
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(UpdateCheck.downloadUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = UnicumPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr("È uscita UNICUM %s", version),
                color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold
            )
            Text(
                tr("Tocca per scaricarla e installala sopra: i tuoi dati restano."),
                color = UnicumDark.copy(alpha = .65f), style = UnicumType.Caption, lineHeight = 17.sp
            )
        }
        IconButton(onClick = { UpdateCheck.dismiss() }) {
            Icon(Icons.Rounded.Close, tr("Nascondi"), tint = UnicumDark.copy(alpha = .6f), modifier = Modifier.size(20.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
}
