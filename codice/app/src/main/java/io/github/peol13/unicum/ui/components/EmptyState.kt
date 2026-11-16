package io.github.peol13.unicum.ui.components

import io.github.peol13.unicum.ui.Route

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSecondary
import io.github.peol13.unicum.ui.theme.UnicumType

/** Navigazione tra le schermate, a disposizione delle card (es. "Apri il Profilo" negli stati vuoti). */
val LocalNavigate = staticCompositionLocalOf<(Route) -> Unit> { {} }

/**
 * Stato vuoto illustrato: i tre cerchi del marchio in tinta tenue, cosa manca in una riga e, se serve,
 * un bottone che porta dove si risolve. Si usa al posto di "Nessun dato" nelle card grandi.
 */
@Composable
fun EmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(UnicumAccent.copy(alpha = .16f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FaintBrandMark(Modifier.size(64.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
            Text(text, color = UnicumDark.copy(alpha = .65f), style = UnicumType.Caption, lineHeight = 17.sp)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    action,
                    color = UnicumOnPrimary, style = UnicumType.Caption, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(UnicumPrimary)
                        .clickable { onAction() }
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** Stato vuoto per i dati di Health Connect: il bottone apre il Profilo, dove si collega e si sincronizza. */
@Composable
fun HealthEmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    val navigate = LocalNavigate.current
    EmptyState(title, text, modifier, action = "Apri il Profilo", onAction = { navigate(Route.Profilo) })
}

/** Il simbolo UNICUM (stessa geometria di [UnicumLogoMark]) nei colori del tema, molto attenuato. */
@Composable
private fun FaintBrandMark(modifier: Modifier) {
    val circles = listOf(UnicumAccent.copy(alpha = .7f), UnicumSecondary.copy(alpha = .35f), UnicumPrimary.copy(alpha = .25f))
    val ecg = UnicumDark.copy(alpha = .4f)
    Canvas(modifier) {
        val s = size.minDimension / 60f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        listOf(30f to 22f, 20.5f to 38.5f, 39.5f to 38.5f).forEachIndexed { i, (x, y) ->
            drawCircle(circles[i], radius = 16f * s, center = p(x, y))
        }
        val line = Path().apply {
            val pts = listOf(11f to 37f, 22f to 37f, 26f to 31f, 30.5f to 45f, 35.5f to 25f, 39.5f to 37f, 49f to 37f)
            pts.forEachIndexed { i, (x, y) -> p(x, y).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        drawPath(line, ecg, style = Stroke(2.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
