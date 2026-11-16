package io.github.peol13.unicum.ui.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.data.SleepNight
import io.github.peol13.unicum.data.SleepStage
import io.github.peol13.unicum.data.SleepStageType
import io.github.peol13.unicum.data.SleepStages
import io.github.peol13.unicum.ui.theme.SleepAwakeColor
import io.github.peol13.unicum.ui.theme.SleepDeepColor
import io.github.peol13.unicum.ui.theme.SleepLightColor
import io.github.peol13.unicum.ui.theme.SleepRemColor
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumType

/** Colore piatto di ogni fase, tutto in palette. */
@Composable
internal fun sleepStageColor(type: SleepStageType): Color = when (type) {
    SleepStageType.SVEGLIO -> SleepAwakeColor
    SleepStageType.REM -> SleepRemColor
    SleepStageType.LEGGERO -> SleepLightColor
    SleepStageType.PROFONDO -> SleepDeepColor
}

/**
 * Cicli del sonno come grafico a gradini: ogni fase sta su una riga (sveglio in alto, profondo in basso)
 * e i tratti sono uniti da linee verticali. Toccando o trascinando un dito sul grafico si vede la fase
 * in quel momento, con durata e orari.
 */
@Composable
internal fun SleepHypnogram(night: SleepNight, modifier: Modifier = Modifier) {
    val stages = night.stages
    if (stages.size < 2) return

    val start = stages.first().startMillis
    val end = stages.last().endMillis
    val total = (end - start).coerceAtLeast(1L).toFloat()

    var widthPx by remember { mutableIntStateOf(0) }
    var selected by remember(night) { mutableStateOf<SleepStage?>(null) }

    val colors = SleepStageType.entries.associateWith { sleepStageColor(it) }
    val lineColor = UnicumDark.copy(alpha = .22f)
    val cursorColor = UnicumDark.copy(alpha = .45f)
    val rows = SleepStageType.entries   // dall'alto: sveglio, REM, leggero, profondo

    fun select(x: Float) {
        if (widthPx <= 0) return
        val time = start + (x / widthPx).coerceIn(0f, 1f) * total
        selected = SleepStages.stageAt(stages, time.toLong())
    }

    Column(modifier.fillMaxWidth()) {
        // Intestazione: fase toccata (colorata) oppure titolo
        val sel = selected
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sel == null) {
                Text("Fasi del sonno", color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("tocca il grafico per i dettagli", color = UnicumDark.copy(alpha = .45f), style = UnicumType.Note)
            } else {
                Text(
                    "${sel.type.label} ${formatDuration(sel.minutes)}",
                    color = colors.getValue(sel.type).darkenForText(), style = UnicumType.Body, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${formatClock(sel.startMillis)} – ${formatClock(sel.endMillis)}",
                    color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(night) {
                    detectTapGestures(onPress = { select(it.x) })
                }
                .pointerInput(night) {
                    detectHorizontalDragGestures(
                        onDragStart = { select(it.x) },
                        onHorizontalDrag = { change, _ -> select(change.position.x) }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val band = h / rows.size
            val barH = band * 0.55f
            fun centerY(type: SleepStageType) = rows.indexOf(type) * band + band / 2f
            fun xOf(t: Long) = (t - start) / total * w

            // linee orizzontali di riferimento
            drawLine(lineColor, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1.dp.toPx())
            drawLine(lineColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())

            // collegamenti verticali tra un tratto e il successivo
            stages.zipWithNext().forEach { (a, b) ->
                val x = xOf(b.startMillis)
                drawLine(lineColor.copy(alpha = .5f), Offset(x, centerY(a.type)), Offset(x, centerY(b.type)), strokeWidth = 1.dp.toPx())
            }

            // tratti: se uno è selezionato gli altri si attenuano
            stages.forEach { s ->
                val x1 = xOf(s.startMillis)
                val x2 = maxOf(xOf(s.endMillis), x1 + 3.dp.toPx())
                val dim = selected != null && selected != s
                drawRoundRect(
                    color = colors.getValue(s.type).copy(alpha = if (dim) .4f else 1f),
                    topLeft = Offset(x1, centerY(s.type) - barH / 2f),
                    size = Size(x2 - x1, barH),
                    cornerRadius = CornerRadius(barH / 2.6f)
                )
            }

            // cursore sulla fase scelta
            selected?.let {
                val mid = xOf((it.startMillis + it.endMillis) / 2)
                drawLine(cursorColor, Offset(mid, 0f), Offset(mid, h), strokeWidth = 1.dp.toPx())
            }
        }

        // asse del tempo: quattro orari equidistanti
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { f ->
                Text(
                    formatClock(start + (f * total).toLong()),
                    color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        StageLegend(night, colors)
    }
}

/** Legenda con le durate totali di ogni fase. */
@Composable
internal fun StageLegend(night: SleepNight, colors: Map<SleepStageType, Color>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        night.stageTotals.forEach { (type, minutes) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.getValue(type)))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${type.label} ${formatDuration(minutes)}",
                    color = UnicumDark.copy(alpha = .7f), style = UnicumType.Micro, maxLines = 1
                )
            }
        }
    }
}

/** Le fasi chiare (leggero, sveglio) sul fondo crema si leggono male come testo: le scuriamo un po'. */
private fun Color.darkenForText(): Color =
    Color(red * 0.75f, green * 0.75f, blue * 0.75f, alpha)
