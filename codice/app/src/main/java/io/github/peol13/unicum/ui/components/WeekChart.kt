package io.github.peol13.unicum.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumTrack
import io.github.peol13.unicum.ui.theme.UnicumType

/** Una colonna del grafico settimanale. `value` null = nessun dato (colonna vuota). */
class WeekBar(
    val label: String,
    val value: Float?,
    val valueText: String,
    val color: Color,
    val highlighted: Boolean = false
)

/**
 * Grafico a colonne per i sette giorni della settimana (L M M G V S D).
 * Supporta valori negativi: le colonne scendono sotto la linea dello zero.
 */
@Composable
fun WeekChart(bars: List<WeekBar>, modifier: Modifier = Modifier, height: Dp = 110.dp) {
    val track = UnicumTrack
    val zeroLine = UnicumDark.copy(alpha = .25f)
    val positives = bars.mapNotNull { it.value }.filter { it > 0f }
    val negatives = bars.mapNotNull { it.value }.filter { it < 0f }
    val maxPositive = (positives.maxOrNull() ?: 0f)
    val maxNegative = -(negatives.minOrNull() ?: 0f)
    val scale = (maxPositive + maxNegative).coerceAtLeast(1f)
    val grow = animatedFraction(1f, durationMillis = 700)

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val slot = size.width / bars.size
            val barWidth = slot * 0.5f
            val corner = CornerRadius(6.dp.toPx())
            val baseline = size.height * (maxPositive / scale).coerceIn(0f, 1f).let { if (maxNegative == 0f) 1f else it }
            val usableUp = baseline
            val usableDown = size.height - baseline
            bars.forEachIndexed { i, bar ->
                val x = slot * i + (slot - barWidth) / 2f
                val v = bar.value
                if (v == null) {
                    drawRoundRect(track, Offset(x, baseline - 4.dp.toPx()), Size(barWidth, 4.dp.toPx()), corner)
                } else if (v >= 0f) {
                    val h = if (maxPositive > 0f) (v / maxPositive * usableUp * grow).coerceAtLeast(4.dp.toPx()) else 4.dp.toPx()
                    drawRoundRect(bar.color, Offset(x, baseline - h), Size(barWidth, h), corner)
                } else {
                    val h = (-v / maxNegative * usableDown * grow).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(bar.color, Offset(x, baseline), Size(barWidth, h), corner)
                }
            }
            if (maxNegative > 0f) {
                drawLine(zeroLine, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            bars.forEach { bar ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        bar.valueText,
                        color = UnicumDark.copy(alpha = if (bar.value == null) .35f else .8f),
                        style = UnicumType.Micro, textAlign = TextAlign.Center, maxLines = 1
                    )
                    Text(
                        bar.label,
                        color = if (bar.highlighted) UnicumPrimary else UnicumDark.copy(alpha = .55f),
                        style = UnicumType.Caption,
                        fontWeight = if (bar.highlighted) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
