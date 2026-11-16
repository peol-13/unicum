package io.github.peol13.unicum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.peol13.unicum.data.OpenFoodFacts
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.peol13.unicum.data.Meal
import io.github.peol13.unicum.data.MealTime
import io.github.peol13.unicum.data.Portion
import androidx.compose.foundation.lazy.items
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSurface
import io.github.peol13.unicum.ui.theme.UnicumTrack
import kotlin.math.roundToInt
import io.github.peol13.unicum.ui.theme.UnicumType

/*
 * Pezzi comuni all'inserimento e alla modifica di un pasto: tipo di pasto, orario,
 * quantità con − / + e preset, riepilogo kcal e macro.
 */

private val GramPresets = listOf(30, 50, 100, 150, 200)

/** Icona lineare della categoria di un alimento (database, prodotti con marca, alimenti personali). */
fun foodCategoryIcon(category: String): ImageVector = when (category) {
    "Carboidrati" -> Icons.Rounded.BakeryDining
    "Proteine" -> Icons.Rounded.SetMeal
    "Latticini" -> Icons.Rounded.LocalDrink
    "Verdura" -> Icons.Rounded.Eco
    "Frutta" -> Icons.Rounded.Spa
    "Legumi" -> Icons.Rounded.Grain
    "Grassi e frutta secca" -> Icons.Rounded.WaterDrop
    "Dolci e snack" -> Icons.Rounded.Cookie
    "Bevande" -> Icons.Rounded.LocalCafe
    "Alcolici" -> Icons.Rounded.WineBar
    "Condimenti" -> Icons.Rounded.Opacity
    "Piatti pronti" -> Icons.Rounded.RamenDining
    OpenFoodFacts.CATEGORY -> Icons.Rounded.QrCode
    "Personalizzati" -> Icons.Rounded.EditNote
    else -> Icons.Rounded.Restaurant
}

@Composable
fun MealTypeSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    UnicumSegmented(
        options = MealTime.TYPES,
        selected = MealTime.TYPES.indexOf(selected).takeIf { it >= 0 },
        onSelect = { onSelect(MealTime.TYPES[it]) },
        modifier = modifier,
        height = 42.dp,
        style = UnicumType.Caption
    )
}

/**
 * Tipo di pasto con, per "Altro", il nome proprio (es. "Spuntino mattina"): due pasti "Altro" con nomi
 * diversi restano separati. [onChange] riceve il tipo da salvare ("Altro" senza nome, altrimenti il nome).
 * Sotto il campo i nomi già usati, da toccare per riprenderli.
 */
@Composable
fun MealTypeInput(initialType: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var base by remember { mutableStateOf(MealTime.baseType(initialType)) }
    var name by remember { mutableStateOf(MealTime.customName(initialType) ?: "") }
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val pastNames by androidx.compose.runtime.produceState(emptyList<String>()) {
        value = io.github.peol13.unicum.data.UnicumRepository.otherMealNames(context)
    }
    fun emit() = onChange(MealTime.compose(base, name))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MealTypeSelector(base, { base = it; emit() })
        if (base == MealTime.OTHER) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30); emit() },
                placeholder = { Text("Nome (es. Spuntino mattina)", maxLines = 1) },
                singleLine = true,
                textStyle = UnicumType.Body,
                shape = RoundedCornerShape(16.dp),
                colors = unicumFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            val shown = pastNames.filter { !it.equals(name.trim(), ignoreCase = true) }
            if (shown.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown) { n -> UnicumChip(n, false, { name = n; emit() }, compact = true) }
                }
            }
        }
    }
}

/** Pillola con l'orario del pasto: toccandola si apre l'orologio per cambiarlo. */
@Composable
fun MealTimePill(minuteOfDay: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(UnicumAccent.copy(alpha = .28f))
            .clickable { picking = true }
            .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Schedule, "Orario", tint = UnicumPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(MealTime.format(minuteOfDay), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
    }
    if (picking) {
        MealTimePickerDialog(
            initial = minuteOfDay,
            onDismiss = { picking = false },
            onConfirm = { onChange(it); picking = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTimePickerDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = UnicumBackground) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Orario del pasto", color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = UnicumTrack,
                        clockDialSelectedContentColor = UnicumOnPrimary,
                        clockDialUnselectedContentColor = UnicumDark,
                        selectorColor = UnicumPrimary,
                        containerColor = UnicumBackground,
                        timeSelectorSelectedContainerColor = UnicumAccent.copy(alpha = .55f),
                        timeSelectorUnselectedContainerColor = UnicumSurface,
                        timeSelectorSelectedContentColor = UnicumPrimary,
                        timeSelectorUnselectedContentColor = UnicumDark
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annulla", color = UnicumDark.copy(alpha = .7f)) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                        Text("Conferma", color = UnicumPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Bottone tondo − / + di 10 grammi (grande nella quantità, piccolo nella lista "Nel pasto"). */
@Composable
internal fun StepButton(add: Boolean, size: Dp = 48.dp, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(UnicumAccent.copy(alpha = .3f))
            .clickable { haptics.tick(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (add) Icons.Rounded.Add else Icons.Rounded.Remove,
            if (add) "Più 10 grammi" else "Meno 10 grammi",
            tint = UnicumPrimary,
            modifier = Modifier.size(size / 2)
        )
    }
}

/**
 * Quantità in grammi: campo centrale, − / + di 10 g ai lati e preset sotto (a segmenti di uguale
 * larghezza, così "200 g" resta sempre dentro lo schermo).
 */
@Composable
fun GramsInput(gramsText: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val grams = gramsText.toIntOrNull() ?: 0
    fun step(delta: Int) = onChange(((grams + delta).coerceIn(0, 9999)).toString())
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StepButton(add = false) { step(-10) }
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                textStyle = unicumFieldTextStyle().copy(textAlign = TextAlign.Center, fontSize = UnicumType.Heading.fontSize),
                value = gramsText,
                onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
                suffix = { Text("g") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                colors = unicumFieldColors(),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            StepButton(add = true) { step(10) }
        }
        Spacer(Modifier.height(10.dp))
        UnicumSegmented(
            options = GramPresets.map { "$it g" },
            selected = GramPresets.indexOf(grams).takeIf { it >= 0 },
            onSelect = { onChange(GramPresets[it].toString()) },
            height = 38.dp
        )
    }
}

/**
 * Quantità per un alimento con porzioni medie (un frutto, una fetta, un bicchiere…): scegli la porzione e
 * quante, in passi di mezza porzione; in alternativa passi ai grammi. [gramsText] resta la verità.
 */
@Composable
fun PortionOrGramsInput(
    portions: List<Portion>,
    gramsText: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var gramsMode by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(0) }
    var count by remember { mutableStateOf(1f) }
    val portion = portions[unit.coerceIn(portions.indices)]
    fun apply(newUnit: Int = unit, newCount: Float = count) {
        unit = newUnit; count = newCount
        onChange((portions[newUnit].grams * newCount).roundToInt().toString())
    }
    Column(modifier.fillMaxWidth()) {
        UnicumSegmented(
            options = listOf("Porzioni", "Grammi"),
            selected = if (gramsMode) 1 else 0,
            onSelect = { gramsMode = it == 1; if (!gramsMode) apply() },
            height = 36.dp
        )
        Spacer(Modifier.height(12.dp))
        if (gramsMode) {
            GramsInput(gramsText, onChange)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StepButton(add = false) { if (count > 0.5f) apply(newCount = count - 0.5f) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (count % 1f == 0f) count.roundToInt().toString() else "%.1f".format(count).replace('.', ','),
                        color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold
                    )
                    Text("${portion.label} · ${portion.grams} g", color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail)
                }
                StepButton(add = true) { if (count < 20f) apply(newCount = count + 0.5f) }
            }
            if (portions.size > 1) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(portions.size) { i ->
                        UnicumChip("${portions[i].label} · ${portions[i].grams} g", i == unit, { apply(newUnit = i) }, compact = true)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "= ${gramsText.toIntOrNull() ?: 0} g",
                color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }
}

/** Riepilogo: kcal grandi e i tre macro in grammi, su una pillola tenue. */
@Composable
fun NutritionSummary(kcal: Int, protein: Float, carbs: Float, fats: Float, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(UnicumAccent.copy(alpha = .22f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            Text("$kcal", color = UnicumPrimary, style = UnicumType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Text("kcal", color = UnicumPrimary, style = UnicumType.Detail, modifier = Modifier.padding(bottom = 3.dp))
        }
        MacroValue("Proteine", protein)
        Spacer(Modifier.width(14.dp))
        MacroValue("Carbo", carbs)
        Spacer(Modifier.width(14.dp))
        MacroValue("Grassi", fats)
    }
}

@Composable
private fun MacroValue(label: String, grams: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${fmtGrams(grams)} g", color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold)
        Text(label, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Micro)
    }
}

fun fmtGrams(v: Float): String =
    if (v >= 10f || v == v.toInt().toFloat()) v.roundToInt().toString() else "%.1f".format(v)

/**
 * Modifica di un alimento già registrato: quantità (kcal e macro si ricalcolano in proporzione),
 * tipo di pasto e orario. Se il pasto non ha i grammi (voci vecchie) si modificano le kcal.
 */
@Composable
fun EditMealDialog(
    meal: Meal,
    onDismiss: () -> Unit,
    onSave: (Meal) -> Unit,
    onDelete: () -> Unit
) {
    val hasGrams = meal.quantityGrams > 0
    var gramsText by remember { mutableStateOf(if (hasGrams) meal.quantityGrams.toString() else "") }
    var kcalText by remember { mutableStateOf(meal.calories.toString()) }
    var mealType by remember { mutableStateOf(meal.mealType) }
    var minute by remember { mutableStateOf(meal.minuteOfDay ?: MealTime.typical(meal.mealType)) }

    val haptics = rememberHaptics()
    val updated: Meal? = if (hasGrams) {
        gramsText.toIntOrNull()?.takeIf { it > 0 }?.let { meal.withGrams(it, io.github.peol13.unicum.data.UserSettings.findFood(meal.name)) }
    } else {
        kcalText.toIntOrNull()?.takeIf { it > 0 }?.let { meal.copy(calories = it) }
    }

    UnicumDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Modifica alimento", color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
                Text(meal.name, color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Chiudi", tint = UnicumDark) }
        }

        MealTypeInput(mealType, { mealType = it })

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Orario", color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            MealTimePill(minute, { minute = it })
        }

        if (hasGrams) {
            GramsInput(gramsText, { gramsText = it })
        } else {
            OutlinedTextField(
                textStyle = unicumFieldTextStyle(),
                value = kcalText,
                onValueChange = { kcalText = it.filter(Char::isDigit).take(4) },
                label = { Text("Calorie") },
                suffix = { Text("kcal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                colors = unicumFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val shown = updated ?: meal
        NutritionSummary(shown.calories, shown.protein, shown.carbohydrates, shown.fats)

        UnicumButton(
            "Salva modifiche",
            enabled = updated != null,
            onClick = { updated?.let { haptics.confirm(); onSave(it.copy(mealType = mealType, minuteOfDay = minute)) } }
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, UnicumDark.copy(alpha = .12f), RoundedCornerShape(16.dp))
                .clickable { onDelete() }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = DeleteRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Elimina dal pasto", color = DeleteRed, style = UnicumType.Body, fontWeight = FontWeight.Medium)
        }
    }
}
