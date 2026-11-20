package io.github.peol13.unicum.ui.components

import io.github.peol13.unicum.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.ActivityLevel
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType

/**
 * Stato di un campo che di default mostra una stima ma che l'utente può sovrascrivere a mano
 * (metabolismo basale, obiettivo calorico). Un valore inserito a mano non viene mai ricalcolato
 * da solo: si torna alla stima solo con [recalculate].
 */
class EstimateInput(initialManual: Boolean, initialText: String) {
    var manual by mutableStateOf(initialManual)
    var text by mutableStateOf(initialText)

    fun edit(newText: String) {
        text = newText
        manual = true
    }

    fun recalculate() {
        manual = false
    }

    /** Valore da salvare: quello inserito a mano (se valido) oppure la stima; null se non valido. */
    fun valueToSave(estimate: Int?, range: IntRange): Int? =
        if (manual) text.toIntOrNull()?.takeIf { it in range } else estimate?.takeIf { it in range }
}

/** Dati personali e obiettivi in modifica (nella schermata di benvenuto e nel Profilo). */
class PersonalFormState {
    var male by mutableStateOf(UserSettings.isMale)
    var age by mutableStateOf(UserSettings.age.toString())
    var weight by mutableStateOf(UserSettings.weightKg.toString())
    var height by mutableStateOf(UserSettings.heightCm.toString())

    val bmr = EstimateInput(UserSettings.bmrIsManual, UserSettings.manualBmr.toString())

    var activityLevel by mutableStateOf(UserSettings.activityLevel)
    var deficit by mutableStateOf(UserSettings.targetDeficitKcal.toString())
    val intake = EstimateInput(UserSettings.intakeIsManual, UserSettings.manualIntake.toString())

    var proteinPct by mutableStateOf(UserSettings.proteinPct.toString())
    var carbsPct by mutableStateOf(UserSettings.carbsPct.toString())
    var fatsPct by mutableStateOf(UserSettings.fatsPct.toString())

    val ageValue get() = age.toIntOrNull()
    val weightValue get() = weight.toIntOrNull()
    val heightValue get() = height.toIntOrNull()
    val deficitValue get() = deficit.toIntOrNull()

    /** Stima del metabolismo basale con i dati inseriti, se sono tutti validi. */
    val bmrEstimate: Int?
        get() {
            val a = ageValue ?: return null
            val w = weightValue ?: return null
            val h = heightValue ?: return null
            if (a !in 14..100 || w !in 30..250 || h !in 120..230) return null
            return UserSettings.estimateBmr(male, w, h, a)
        }

    /** Metabolismo basale che verrà salvato: quello inserito a mano oppure la stima. */
    val bmrToSave: Int? get() = bmr.valueToSave(bmrEstimate, 800..4500)

    /** Obiettivo calorico stimato: basale × livello di attività − deficit (mai sotto il minimo di sicurezza). */
    val intakeEstimate: Int?
        get() {
            val b = bmrToSave ?: return null
            val d = deficitValue?.takeIf { it in 0..1500 } ?: return null
            return UserSettings.estimateIntakeGoal(b, activityLevel, d, male)
        }

    val intakeToSave: Int? get() = intake.valueToSave(intakeEstimate, 800..6000)

    val pctSum: Int get() = (proteinPct.toIntOrNull() ?: 0) + (carbsPct.toIntOrNull() ?: 0) + (fatsPct.toIntOrNull() ?: 0)

    val isValid: Boolean
        get() = bmrEstimate != null && bmrToSave != null && deficitValue in 0..1500 &&
            intakeToSave != null && pctSum == 100

    /** Salva profilo e obiettivi in [UserSettings] (schermata di benvenuto e Profilo). */
    fun save() {
        UserSettings.saveProfile(
            weight = weightValue ?: UserSettings.weightKg,
            height = heightValue ?: UserSettings.heightCm,
            age = ageValue ?: UserSettings.age,
            male = male,
            manualBmr = if (bmr.manual) bmrToSave else null,
            activityLevel = activityLevel,
            manualIntake = if (intake.manual) intakeToSave else null,
            deficitKcal = deficitValue ?: 0,
            proteinPercent = proteinPct.toIntOrNull() ?: 0,
            carbsPercent = carbsPct.toIntOrNull() ?: 0,
            fatsPercent = fatsPct.toIntOrNull() ?: 0
        )
    }

    /** Cambia a ogni modifica di qualsiasi campo (serve a riabilitare il pulsante Salva). */
    val snapshot: List<Any?>
        get() = listOf(
            male, age, weight, height, bmr.manual, bmr.text, activityLevel, deficit,
            intake.manual, intake.text, proteinPct, carbsPct, fatsPct
        )
}

// ------------------------------------------------------------------
// Campi
// ------------------------------------------------------------------

@Composable
fun PersonalDataFields(state: PersonalFormState) {
    Text(tr("Sesso"), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UnicumChip(tr("Uomo"), state.male, { state.male = true })
        UnicumChip(tr("Donna"), !state.male, { state.male = false })
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UnicumNumberField(tr("Età"), state.age, tr("anni"), Modifier.weight(1f)) { state.age = it }
        UnicumNumberField(tr("Peso"), state.weight, "kg", Modifier.weight(1f)) { state.weight = it }
        UnicumNumberField(tr("Altezza"), state.height, "cm", Modifier.weight(1f)) { state.height = it }
    }

    Spacer(Modifier.height(18.dp))
    Text(tr("Metabolismo basale"), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
    Text(
        tr("Le calorie che il corpo consuma a riposo in un giorno, senza allenamenti."),
        color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
    )
    Spacer(Modifier.height(8.dp))
    EstimatedNumberField(
        label = tr("Metabolismo basale"),
        suffix = "kcal",
        estimate = state.bmrEstimate,
        input = state.bmr,
        explanation = tr("Calcolata con la formula di Mifflin-St Jeor da sesso, età, peso e altezza. Se conosci il valore reale (bilancia impedenziometrica, test), scrivilo.")
            
    )
}

/** Livello di attività, deficit, obiettivo calorico giornaliero (stimato o a mano) e ripartizione dei macro. */
@Composable
fun GoalFields(state: PersonalFormState) {
    Text(tr("Livello di attività abituale"), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
    Text(
        tr("Serve solo a stimare l'obiettivo calorico. Il movimento di ogni giorno non lo cambia."),
        color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
    )
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ActivityLevel.entries) { level ->
            UnicumChip(level.label, state.activityLevel == level, { state.activityLevel = level })
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(state.activityLevel.description, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption)

    Spacer(Modifier.height(14.dp))
    UnicumNumberField(tr("Deficit giornaliero"), state.deficit, "kcal") { state.deficit = it }
    Text(
        tr("Quanto vuoi bruciare in più di quanto mangi ogni giorno (0 = mantenimento)."),
        color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption,
        modifier = Modifier.padding(top = 4.dp)
    )

    Spacer(Modifier.height(14.dp))
    Text(tr("Obiettivo calorico"), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
    Text(
        tr("Le calorie da mangiare ogni giorno. È fisso: l'attività fisica non lo aumenta, aumenta solo il deficit."),
        color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
    )
    Spacer(Modifier.height(8.dp))
    EstimatedNumberField(
        label = tr("Obiettivo calorico"),
        suffix = "kcal",
        estimate = state.intakeEstimate,
        input = state.intake,
        explanation = tr("Metabolismo basale × livello di attività − deficit.")
    )

    Spacer(Modifier.height(14.dp))
    Text(tr("Ripartizione dei macronutrienti"), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
    Text(
        tr("Che quota delle calorie obiettivo viene da ciascun macro. Devono sommare 100%."),
        color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UnicumNumberField(tr("Proteine"), state.proteinPct, "%", Modifier.weight(1f)) { state.proteinPct = it.take(3) }
        UnicumNumberField(tr("Carbo"), state.carbsPct, "%", Modifier.weight(1f)) { state.carbsPct = it.take(3) }
        UnicumNumberField(tr("Grassi"), state.fatsPct, "%", Modifier.weight(1f)) { state.fatsPct = it.take(3) }
    }
    Spacer(Modifier.height(6.dp))
    val goal = state.intakeToSave ?: 0
    Text(
        if (state.pctSum == 100)
            tr("Con %d kcal: proteine %d g, carboidrati %d g, grassi %d g.", goal,
                UserSettings.gramsFor(goal, state.proteinPct.toIntOrNull() ?: 0, 4),
                                UserSettings.gramsFor(goal, state.carbsPct.toIntOrNull() ?: 0, 4),
                                UserSettings.gramsFor(goal, state.fatsPct.toIntOrNull() ?: 0, 9))
        else tr("Il totale è %d%%: deve essere 100%%.", state.pctSum),
        color = if (state.pctSum == 100) UnicumDark.copy(alpha = .6f) else DeleteRed,
        style = UnicumType.Caption, lineHeight = 17.sp
    )
}

/**
 * Campo numerico che mostra una stima. Se lo modifichi diventa un valore tuo (non viene più
 * ricalcolato) e compare "Ricalcola stima" per tornare al valore automatico.
 */
@Composable
fun EstimatedNumberField(
    label: String,
    suffix: String,
    estimate: Int?,
    input: EstimateInput,
    explanation: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val shown = if (input.manual) input.text else estimate?.toString() ?: ""
    Column(modifier) {
        UnicumNumberField(label, shown, suffix) { input.edit(it) }
        Spacer(Modifier.height(4.dp))
        if (input.manual) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Inserito a mano: non viene ricalcolato da solo."),
                        color = UnicumDark.copy(alpha = .7f), style = UnicumType.Caption, fontWeight = FontWeight.Medium
                    )
                    if (estimate != null) {
                        Text(
                            tr("La stima dai tuoi dati sarebbe %s.", "$estimate $suffix"),
                            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
                        )
                    }
                }
                TextButton(onClick = { input.recalculate() }) {
                    Text(tr("Ricalcola stima"), color = UnicumPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(UnicumAccent.copy(alpha = .22f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    if (estimate != null) tr("Stima automatica") else tr("Completa i dati per la stima"),
                    color = UnicumDark, style = UnicumType.Caption, fontWeight = FontWeight.SemiBold
                )
                Text(explanation, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun UnicumNumberField(
    label: String,
    value: String,
    suffix: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        textStyle = unicumFieldTextStyle(),
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label, maxLines = 1) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        colors = unicumFieldColors(),
        modifier = modifier
    )
}
