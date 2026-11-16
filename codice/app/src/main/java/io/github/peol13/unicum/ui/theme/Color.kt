package io.github.peol13.unicum.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.peol13.unicum.data.TrainingPlan

// Colori del brand (valori fissi, vedi grafiche/stile grafico.txt)
val BrandPrimary = Color(0xFF045F6A)
val BrandSecondary = Color(0xFF3FA7A3)
val BrandAccent = Color(0xFFA7D8D3)
val BrandBackground = Color(0xFFF9F6EF)
val BrandDark = Color(0xFF0B2F34)

/** Colori dell'interfaccia che cambiano tra tema chiaro e scuro. */
@Immutable
class UnicumColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,        // card, campi di testo, barra di navigazione
    val text: Color,
    val primary: Color,        // azioni, icone attive, valori evidenziati
    val onPrimary: Color,      // testo/icone sopra il colore primario
    val secondary: Color,
    val accent: Color,         // sfondi leggeri, bordi, badge delle icone
    val track: Color           // fondo degli anelli di progresso
)

val LightUnicumColors = UnicumColors(
    isDark = false,
    background = BrandBackground,
    surface = Color.White,
    text = BrandDark,
    primary = BrandPrimary,
    onPrimary = Color.White,
    secondary = BrandSecondary,
    accent = BrandAccent,
    track = Color(0xFFDCE8E8)
)

// Tema scuro: come il chiaro (sfondo neutro caldo + card più chiare), ma in grafite appena verde-acqua.
// Il teal resta per azioni e valori, così risalta invece di colorare tutta la schermata.
val DarkUnicumColors = UnicumColors(
    isDark = true,
    background = Color(0xFF101617),
    surface = Color(0xFF1A2325),
    text = Color(0xFFECE8DF),
    primary = Color(0xFF5BBDB7),
    onPrimary = Color(0xFF101617),
    secondary = Color(0xFF86CFC9),
    accent = Color(0xFF2E7477),
    track = Color(0xFF273234)
)

val LocalUnicumColors = staticCompositionLocalOf { LightUnicumColors }

// Accesso rapido ai colori del tema corrente. Si usano come costanti, ma solo dentro @Composable:
// nelle lambda di disegno (Canvas) vanno letti prima e salvati in una variabile.
val UnicumPrimary: Color @Composable @ReadOnlyComposable get() = LocalUnicumColors.current.primary
val UnicumSecondary: Color @Composable @ReadOnlyComposable get() = LocalUnicumColors.current.secondary
val UnicumAccent: Color @Composable @ReadOnlyComposable get() = LocalUnicumColors.current.accent
val UnicumBackground: Color @Composable @ReadOnlyComposable get() = LocalUnicumColors.current.background
val UnicumDark: Color @Composable @ReadOnlyComposable get() = LocalUnicumColors.current.text
val UnicumSurface: Color @Composable @ReadOnlyComposable get() = LocalUnicumColors.current.surface
val UnicumOnPrimary: Color @Composable @ReadOnlyComposable get() = LocalUnicumColors.current.onPrimary
val UnicumTrack: Color @Composable @ReadOnlyComposable get() = LocalUnicumColors.current.track

// Colori dei tre macronutrienti: toni piatti della palette teal, senza colori accesi.
val MacroProteinColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalUnicumColors.current.isDark) Color(0xFFA7D8D3) else BrandPrimary
val MacroCarbsColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalUnicumColors.current.isDark) Color(0xFF4DB8B3) else BrandSecondary
val MacroFatsColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalUnicumColors.current.isDark) Color(0xFF2F8F94) else BrandAccent

// Tipi di corsa (come nel registro su Notion): i toni teal per le uscite di fondo, sabbia e ardesia
// tenui per le altre, così si distinguono nelle colonne impilate senza colori accesi.
@Composable
@ReadOnlyComposable
fun runKindColor(kind: String): Color {
    val dark = LocalUnicumColors.current.isDark
    return when (kind) {
        "Facile" -> if (dark) Color(0xFF4DB8B3) else BrandSecondary
        "Lungo facile" -> if (dark) Color(0xFF2F8F94) else BrandPrimary
        "Progressivo" -> if (dark) Color(0xFFA7D8D3) else Color(0xFF8CCBC5)
        "Intervalli" -> Color(0xFFC9A66B)
        "Test" -> if (dark) Color(0xFF8FA3A6) else Color(0xFF7A8E91)
        "Libera" -> if (dark) Color(0xFF8C8576) else Color(0xFFD9CFBD)
        else -> notionTone(TrainingPlan.kindColor(kind), dark) ?: LocalUnicumColors.current.track
    }
}

/**
 * Tipi nuovi creati in Notion (es. "5km"): il colore scelto lì, portato su un tono tenue della palette
 * (niente colori accesi). Il rosso di Notion diventa terracotta, così non si confonde con gli Intervalli.
 */
private fun notionTone(color: String?, dark: Boolean): Color? = when (color) {
    "red" -> if (dark) Color(0xFFD08F82) else Color(0xFFC27A6B)
    "orange" -> if (dark) Color(0xFFDDB07E) else Color(0xFFD39B62)
    "yellow" -> if (dark) Color(0xFFD9C98A) else Color(0xFFCBB46A)
    "green" -> if (dark) Color(0xFF8CC4A6) else Color(0xFF6FAE8C)
    "blue" -> if (dark) Color(0xFF86A9C7) else Color(0xFF5F8DB3)
    "purple" -> if (dark) Color(0xFFAA9BC4) else Color(0xFF8E7DB0)
    "pink" -> if (dark) Color(0xFFD3A5B8) else Color(0xFFC28AA2)
    "brown" -> if (dark) Color(0xFFAE9480) else Color(0xFF977A63)
    "gray", "default" -> if (dark) Color(0xFF8FA3A6) else Color(0xFF7A8E91)
    else -> null
}

// Fasi del sonno: toni piatti della palette (da quella più profonda alla più leggera) e un neutro per "sveglio".
val SleepDeepColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalUnicumColors.current.isDark) Color(0xFF2F8F94) else BrandPrimary
val SleepRemColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalUnicumColors.current.isDark) Color(0xFF4DB8B3) else BrandSecondary
val SleepLightColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalUnicumColors.current.isDark) Color(0xFFA7D8D3) else Color(0xFF8CCBC5)
val SleepAwakeColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalUnicumColors.current.isDark) Color(0xFF6E8588) else Color(0xFFC4BCA9)
