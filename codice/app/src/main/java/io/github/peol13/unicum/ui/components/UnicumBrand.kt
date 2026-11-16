package io.github.peol13.unicum.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import io.github.peol13.unicum.ui.theme.LocalUnicumColors
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType

/*
 * Marchio UNICUM: simbolo (tre cerchi + ECG) e logotipo con la N a forma di onda ECG.
 * Il simbolo ha la stessa geometria di res/drawable/ic_launcher_foreground.xml, così la splash
 * di sistema (che mostra quel disegno) e la schermata di brand dell'app sono sovrapponibili.
 */

// Colori fissi del marchio (come le risorse unicum_logo_* in res/values e values-night)
private val LightCircles = listOf(Color(0xFFA7D8D3), Color(0xFF7EC6C3), Color(0xFF3FA7A3))
private val DarkCircles = listOf(Color(0xFF3FA7A3), Color(0xFF1F7A80), Color(0xFF2A9694))
private val LightEcg = Color(0xFF045F6A)
private val DarkEcg = Color(0xFFF9F6EF)

/** Dimensione del simbolo uguale a quella che Android 12+ dà all'icona della splash di sistema. */
val BrandMarkSize = 288.dp

/**
 * Simbolo UNICUM. [size] è il lato dell'area di 108 unità dell'icona adattiva: il disegno vero e proprio
 * occupa circa il 60% centrale (come nella splash di sistema).
 */
@Composable
fun UnicumLogoMark(modifier: Modifier = Modifier, size: Dp = BrandMarkSize) {
    val dark = LocalUnicumColors.current.isDark
    val circles = if (dark) DarkCircles else LightCircles
    val ecg = if (dark) DarkEcg else LightEcg
    Canvas(modifier.size(size)) {
        val s = this.size.width / 108f
        // riquadro 60x60 del disegno -> 108 unità, ingrandito 1,18 attorno al centro
        fun p(x: Float, y: Float) = Offset((54f + (x - 30f) * 1.18f) * s, (54f + (y - 30f) * 1.18f) * s)
        val r = 16f * 1.18f * s
        listOf(30f to 22f, 20.5f to 38.5f, 39.5f to 38.5f).forEachIndexed { i, (x, y) ->
            drawCircle(circles[i].copy(alpha = .9f), radius = r, center = p(x, y))
        }
        val line = Path().apply {
            val pts = listOf(11f to 37f, 22f to 37f, 26f to 31f, 30.5f to 45f, 35.5f to 25f, 39.5f to 37f, 49f to 37f)
            pts.forEachIndexed { i, (x, y) -> p(x, y).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        drawPath(line, ecg, style = Stroke(2.6f * 1.18f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Logotipo disegnato a tratti (linea uniforme, estremità tonde), in unità di altezza delle maiuscole.
private const val WM_WIDTH = 6.50f     // larghezza totale
private const val WM_TOP = 0.42f       // spazio sopra le maiuscole per il picco dell'ECG
private const val WM_HEIGHT = 1.52f    // altezza totale (picco e linea inclusi)

/** "UNICUM" con la N a forma di onda ECG. [capHeight] è l'altezza delle lettere maiuscole. */
@Composable
fun UnicumWordmark(modifier: Modifier = Modifier, capHeight: Dp = 30.dp) {
    val ink = UnicumDark
    val pulse = UnicumPrimary
    Canvas(modifier.size(capHeight * WM_WIDTH, capHeight * WM_HEIGHT)) {
        val u = capHeight.toPx()
        fun pt(x: Float, y: Float) = Offset(x * u, (y + WM_TOP) * u)
        val stroke = Stroke(u * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun uLetter(x0: Float) = Path().apply {
            moveTo(pt(x0, 0f).x, pt(x0, 0f).y)
            lineTo(pt(x0, 0.61f).x, pt(x0, 0.61f).y)
            arcTo(
                Rect(pt(x0, 0.22f), pt(x0 + 0.78f, 1f)),
                startAngleDegrees = 180f, sweepAngleDegrees = -180f, forceMoveTo = false
            )
            lineTo(pt(x0 + 0.78f, 0f).x, pt(x0 + 0.78f, 0f).y)
        }
        fun poly(vararg pts: Pair<Float, Float>) = Path().apply {
            pts.forEachIndexed { i, (x, y) -> pt(x, y).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }

        drawPath(uLetter(0.04f), ink, style = stroke)
        // N: la coda piatta, il picco, la discesa e la risalita fanno l'onda ECG
        drawPath(poly(0.95f to 0.55f, 1.25f to 0.55f, 1.48f to -0.32f, 1.80f to 1f, 2.05f to 0f), pulse, style = stroke)
        drawPath(poly(2.42f to 0f, 2.42f to 1f), ink, style = stroke)                 // I
        drawArc(                                                                         // C
            ink, startAngle = 38f, sweepAngle = 284f, useCenter = false,
            topLeft = pt(2.80f, 0f), size = androidx.compose.ui.geometry.Size(u, u), style = stroke
        )
        drawPath(uLetter(4.06f), ink, style = stroke)                                  // U
        drawPath(poly(5.22f to 1f, 5.22f to 0f, 5.84f to 0.72f, 6.46f to 0f, 6.46f to 1f), ink, style = stroke) // M
    }
}

/**
 * Schermata di brand all'avvio. Il simbolo sta esattamente dove Android 12+ disegna l'icona della
 * splash di sistema (centro dello schermo), così il passaggio non si nota; nome e motto compaiono sotto.
 */
@Composable
fun UnicumBrandScreen() {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(450)) }
    Box(Modifier.fillMaxSize().background(UnicumBackground), contentAlignment = Alignment.Center) {
        UnicumLogoMark()
        Column(
            Modifier
                .offset(y = 128.dp)
                .alpha(reveal.value)
                .graphicsLayer { translationY = (1f - reveal.value) * 8.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UnicumWordmark(capHeight = 34.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Your health. Unified.",
                color = UnicumDark.copy(alpha = .6f),
                style = UnicumType.Caption,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp
            )
        }
    }
}
