package io.github.peol13.unicum.ui.theme
import io.github.peol13.unicum.R

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Montserrat = FontFamily(
    Font(R.font.montserrat_light, FontWeight.Light),
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold)
)

/**
 * Scala tipografica di UNICUM: ogni testo usa uno di questi stili (`style = UnicumType.Body`) invece di
 * una dimensione scritta a mano, così la grafica resta coerente e si ritocca da qui.
 * Il peso (SemiBold, Medium...) si aggiunge sul singolo testo quando serve; la riga è di 24 sp come
 * il testo di base di Material, e i paragrafi su più righe indicano la propria interlinea.
 */
object UnicumType {
    private fun size(sp: Int) = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = sp.sp,
        lineHeight = 24.sp,
        // Come il testo di base di Material: la riga intera resta anche sopra e sotto (aria tra le righe)
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    )

    /** Numeri del racconto della settimana, su tutto lo schermo. */
    val Display = size(56).copy(lineHeight = 64.sp)
    /** Numeri principali (peso, minuti di attività). */
    val Hero = size(34)
    /** Titoli delle schermate ("Oggi", "Diario"). */
    val Screen = size(30)
    /** Titoli delle pagine aperte dalla Home e numeri degli anelli. */
    val Page = size(26)
    /** Intestazioni delle sezioni e numeri delle card. */
    val Section = size(22)
    /** Titoli dei dialog e valori in evidenza. */
    val Heading = size(18)
    /** Titoli delle card. */
    val Card = size(16)
    /** Titoli di elementi, banner e pulsanti. */
    val Emphasis = size(15)
    /** Testo normale, nomi degli alimenti. */
    val Body = size(14)
    /** Testo secondario. */
    val Detail = size(13)
    /** Didascalie e spiegazioni. */
    val Caption = size(12)
    /** Note piccole sotto i grafici. */
    val Note = size(11)
    /** Etichette dei grafici e delle legende. */
    val Micro = size(10)
}

val Typography = Typography(

    // Titoli principali
    displayLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp
    ),

    headlineLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp
    ),

    headlineMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),

    // Titoli delle sezioni
    titleLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),

    titleMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),

    // Testo principale
    bodyLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),

    bodyMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),

    // Informazioni secondarie
    bodySmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),

    // Elementi dell'interfaccia
    labelLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),

    labelMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),

    labelSmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Light,
        fontSize = 11.sp
    )
)