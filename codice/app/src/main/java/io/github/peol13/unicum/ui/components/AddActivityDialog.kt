package io.github.peol13.unicum.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.data.MealTime
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.theme.UnicumDark
import kotlin.math.roundToInt
import io.github.peol13.unicum.ui.theme.UnicumType

private data class SportPreset(val name: String, val met: Float)

private val sports = listOf(
    SportPreset("Corsa", 9.8f),
    SportPreset("Camminata", 3.8f),
    SportPreset("Palestra", 5f),
    SportPreset("Ciclismo", 7.5f),
    SportPreset("Nuoto", 7f),
    SportPreset("Yoga", 2.5f),
    SportPreset("Calcio", 7f),
    SportPreset("Altro", 5f)
)

/** Registrazione di un allenamento: sport, durata e calorie (stimate dal peso se non le conosci). */
@Composable
fun AddActivityDialog(
    onDismiss: () -> Unit,
    onConfirm: (type: String, durationMinutes: Int, calories: Int, startMinute: Int) -> Unit
) {
    var type by remember { mutableStateOf(sports.first().name) }
    var durationText by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }
    var startMinute by remember { mutableStateOf(MealTime.now()) }

    val duration = durationText.toIntOrNull() ?: 0
    // Stima: MET × peso (kg) × ore
    val met = sports.firstOrNull { it.name == type }?.met ?: 5f
    val estimate = (met * UserSettings.weightKg * duration / 60f).roundToInt()
    val calories = caloriesText.toIntOrNull() ?: estimate
    val valid = type.isNotBlank() && duration in 1..1440 && calories in 1..5000
    val haptics = rememberHaptics()

    UnicumDialog(onDismiss, spacing = 12.dp) {
        Text("Registra allenamento", color = UnicumDark, style = UnicumType.Section, fontWeight = FontWeight.SemiBold)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sports) { s -> UnicumChip(s.name, type == s.name, { type = s.name }) }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Orario di inizio", color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            MealTimePill(startMinute, { startMinute = it })
        }

        OutlinedTextField(
            textStyle = unicumFieldTextStyle(),
            value = durationText,
            onValueChange = { durationText = it.filter(Char::isDigit).take(4) },
            label = { Text("Durata") },
            suffix = { Text("min") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            colors = unicumFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            textStyle = unicumFieldTextStyle(),
            value = caloriesText,
            onValueChange = { caloriesText = it.filter(Char::isDigit).take(4) },
            label = { Text("Calorie bruciate") },
            suffix = { Text("kcal") },
            placeholder = { if (estimate > 0) Text("Stimate: $estimate") },
            supportingText = { Text("Lascia vuoto per usare la stima in base al tuo peso.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            colors = unicumFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(2.dp))
        UnicumButton("Salva allenamento", enabled = valid, onClick = { haptics.confirm(); onConfirm(type, duration, calories, startMinute) })
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Annulla", color = UnicumDark.copy(alpha = .7f))
        }
    }
}
