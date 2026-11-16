package io.github.peol13.unicum.ui.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.data.MapTiles
import io.github.peol13.unicum.data.RoutePoint
import io.github.peol13.unicum.data.WorkoutAnalysis
import io.github.peol13.unicum.ui.components.animatedFraction
import io.github.peol13.unicum.ui.theme.LocalUnicumColors
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSecondary
import io.github.peol13.unicum.ui.theme.UnicumSurface
import io.github.peol13.unicum.ui.theme.UnicumTrack
import io.github.peol13.unicum.ui.theme.UnicumType
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Grafico a linea toccabile (battito, ritmo, altitudine nel tempo; ritmo o km per corsa nello storico).
 * Tocco o trascinamento sceglie il punto più vicino: sopra compaiono il suo valore e la sua posizione.
 * [invert] = valori bassi in alto (il ritmo: più basso è più veloce). Gli estremi (2% per lato) non
 * allargano la scala, così un istante fermo al semaforo non schiaccia tutto il grafico.
 */
@Composable
fun SeriesChart(
    xs: List<Float>,
    ys: List<Float>,
    color: Color,
    xLabel: (Float) -> String,
    yLabel: (Float) -> String,
    summary: String,
    modifier: Modifier = Modifier,
    invert: Boolean = false,
    selectedX: Float? = null,
    onSelect: ((Float?) -> Unit)? = null,
    height: Dp = 130.dp,
    dots: Boolean = false,
    /** Linea tratteggiata di riferimento (es. la media del tipo di corsa). */
    reference: Float? = null
) {
    if (xs.size < 2) return
    val sortedY = (ys + listOfNotNull(reference)).sorted()
    val lo = sortedY[(sortedY.size * .02).toInt()]
    val hi = sortedY[((sortedY.size - 1) * .98).roundToInt()]
    val pad = ((hi - lo) * .08f).coerceAtLeast(if (hi == lo) 1f else 0f)
    val yMin = lo - pad
    val yMax = hi + pad
    val xMin = xs.first()
    val xMax = xs.last()
    val grid = UnicumDark.copy(alpha = .08f)
    val cursor = UnicumDark.copy(alpha = .45f)
    val surface = UnicumSurface
    val grow = animatedFraction(1f, durationMillis = 600)
    val select by rememberUpdatedState(onSelect)

    val selIndex = selectedX?.let { s -> xs.indices.minByOrNull { abs(xs[it] - s) } }

    Column(modifier.fillMaxWidth()) {
        Text(
            if (selIndex != null) "${xLabel(xs[selIndex])} · ${yLabel(ys[selIndex])}" else summary,
            color = if (selIndex != null) color else UnicumDark.copy(alpha = .6f),
            style = UnicumType.Caption, fontWeight = if (selIndex != null) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Box {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .pointerInput(xs) {
                        fun pick(px: Float) {
                            val x = xMin + (px / size.width).coerceIn(0f, 1f) * (xMax - xMin)
                            select?.invoke(x)
                        }
                        detectTapGestures { pick(it.x) }
                    }
                    .pointerInput(xs) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val x = xMin + (change.position.x / size.width).coerceIn(0f, 1f) * (xMax - xMin)
                            select?.invoke(x)
                        }
                    }
            ) {
                fun px(x: Float) = (x - xMin) / (xMax - xMin).coerceAtLeast(1e-6f) * size.width
                fun py(y: Float): Float {
                    val f = ((y.coerceIn(yMin, yMax) - yMin) / (yMax - yMin)).let { if (invert) it else 1f - it }
                    return f * size.height
                }
                for (i in 0..2) {
                    val y = size.height * i / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val shown = (xs.size * grow).toInt().coerceAtLeast(2)
                val line = Path()
                val fill = Path()
                for (i in 0 until shown) {
                    val p = Offset(px(xs[i]), py(ys[i]))
                    if (i == 0) { line.moveTo(p.x, p.y); fill.moveTo(p.x, size.height); fill.lineTo(p.x, p.y) }
                    else { line.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) }
                }
                fill.lineTo(px(xs[shown - 1]), size.height)
                fill.close()
                drawPath(fill, color.copy(alpha = .12f))
                if (reference != null) {
                    drawLine(
                        cursor, Offset(0f, py(reference)), Offset(size.width, py(reference)), 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    )
                }
                drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (dots) for (i in 0 until shown) {
                    drawCircle(surface, 4.dp.toPx(), Offset(px(xs[i]), py(ys[i])))
                    drawCircle(color, 3.dp.toPx(), Offset(px(xs[i]), py(ys[i])))
                }
                if (selIndex != null) {
                    val sx = px(xs[selIndex])
                    drawLine(cursor, Offset(sx, 0f), Offset(sx, size.height), 1.dp.toPx())
                    drawCircle(surface, 6.dp.toPx(), Offset(sx, py(ys[selIndex])))
                    drawCircle(color, 4.5.dp.toPx(), Offset(sx, py(ys[selIndex])))
                }
            }
            // Valori ai bordi della scala
            Text(
                yLabel(if (invert) yMin else yMax), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Micro,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp)
            )
            Text(
                yLabel(if (invert) yMax else yMin), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Micro,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 2.dp, bottom = 2.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(xLabel(xMin), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Micro, modifier = Modifier.weight(1f))
            Text(xLabel((xMin + xMax) / 2), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Micro)
            Text(
                xLabel(xMax), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Micro,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

/** Tempo nelle cinque zone di frequenza cardiaca, una barra per zona (toni del teal, dal chiaro allo scuro). */
@Composable
fun HrZoneBars(zones: List<WorkoutAnalysis.HrZone>) {
    val total = zones.sumOf { it.seconds }.coerceAtLeast(1)
    val light = UnicumAccent
    val dark = UnicumPrimary
    val track = UnicumTrack
    val grow = animatedFraction(1f, durationMillis = 700)
    Column {
        zones.reversed().forEach { z ->
            val color = lerp(light, dark, (z.number - 1) / 4f)
            val f = z.seconds / total.toFloat()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(112.dp)) {
                    Text("Z${z.number} ${z.name}", color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("${z.fromBpm}–${z.toBpm} bpm", color = UnicumDark.copy(alpha = .5f), style = UnicumType.Micro)
                }
                Canvas(Modifier.weight(1f).height(12.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track, size = size, cornerRadius = r)
                    if (f > 0) drawRoundRect(color, size = Size((size.width * f * grow).coerceAtLeast(size.height), size.height), cornerRadius = r)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${WorkoutAnalysis.formatDuration(z.seconds)}  ${(f * 100).roundToInt()}%",
                    color = UnicumDark.copy(alpha = .75f), style = UnicumType.Caption,
                    modifier = Modifier.width(78.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

/**
 * Il percorso GPS su una mappa (riquadri CARTO/OpenStreetMap), con partenza, arrivo e, se si tocca un
 * grafico, il punto in cui si era in quel momento ([selectedT], secondi dall'inizio).
 */
@Composable
fun RouteMap(route: List<RoutePoint>, selectedT: Int?, modifier: Modifier = Modifier, height: Dp = 240.dp) {
    if (route.size < 2) return
    val context = LocalContext.current
    val dark = LocalUnicumColors.current.isDark
    val tilePx = with(LocalDensity.current) { 128.dp.toPx() }
    val lineColor = UnicumPrimary
    val outline = UnicumSurface
    val startColor = UnicumSecondary
    val track = UnicumTrack
    // La mappa di OpenStreetMap è molto colorata: si toglie quasi tutto il colore e, nel tema scuro,
    // la si copre in parte con lo sfondo, così il percorso resta l'elemento in evidenza
    val veil = if (dark) UnicumSurface.copy(alpha = .55f) else UnicumSurface.copy(alpha = .18f)
    val tileFilter = remember(dark) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(if (dark) .15f else .35f) })
    }
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }

    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)).background(track)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val z = remember(route, w, h) { MapTiles.fitZoom(route, w, h, tilePx) }
        val scale = tilePx / MapTiles.TILE.toFloat()
        val wx = remember(route, z) { route.map { MapTiles.worldX(it.lon, z) } }
        val wy = remember(route, z) { route.map { MapTiles.worldY(it.lat, z) } }
        // Angolo in alto a sinistra della vista, in pixel-mondo
        val left = (wx.min() + wx.max()) / 2 - w / 2 / scale
        val top = (wy.min() + wy.max()) / 2 - h / 2 / scale
        val txs = floor(left / MapTiles.TILE).toInt()..floor((left + w / scale) / MapTiles.TILE).toInt()
        val tys = floor(top / MapTiles.TILE).toInt()..floor((top + h / scale) / MapTiles.TILE).toInt()

        LaunchedEffect(z, txs, tys) {
            for (x in txs) for (y in tys) {
                val key = "$z/$x/$y"
                if (key !in tiles) MapTiles.tile(context, z, x, y)?.let { tiles[key] = it.asImageBitmap() }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            for (x in txs) for (y in tys) {
                val img = tiles["$z/$x/$y"] ?: continue
                val ox = ((x * MapTiles.TILE - left) * scale).roundToInt()
                val oy = ((y * MapTiles.TILE - top) * scale).roundToInt()
                drawImage(
                    img, srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(ox, oy), dstSize = IntSize(tilePx.roundToInt() + 1, tilePx.roundToInt() + 1),
                    colorFilter = tileFilter
                )
            }
            drawRect(veil)
            fun pt(i: Int) = Offset(((wx[i] - left) * scale).toFloat(), ((wy[i] - top) * scale).toFloat())
            val path = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1 until route.size) lineTo(pt(i).x, pt(i).y)
            }
            drawPath(path, outline, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, lineColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(outline, 7.dp.toPx(), pt(0))
            drawCircle(startColor, 5.dp.toPx(), pt(0))
            drawCircle(outline, 7.dp.toPx(), pt(route.lastIndex))
            drawCircle(lineColor, 5.dp.toPx(), pt(route.lastIndex))
            if (selectedT != null) {
                val i = route.indices.minBy { abs(route[it].t - selectedT) }
                drawCircle(lineColor.copy(alpha = .25f), 14.dp.toPx(), pt(i))
                drawCircle(outline, 7.dp.toPx(), pt(i))
                drawCircle(lineColor, 5.dp.toPx(), pt(i))
            }
        }
        Text(
            MapTiles.ATTRIBUTION, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Micro,
            modifier = Modifier.align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(topStart = 8.dp))
                .background(UnicumSurface.copy(alpha = .75f))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}
