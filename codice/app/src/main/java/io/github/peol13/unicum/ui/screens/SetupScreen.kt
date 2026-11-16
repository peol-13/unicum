package io.github.peol13.unicum.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.data.ActivityLevel
import io.github.peol13.unicum.data.DashboardMode
import io.github.peol13.unicum.data.LocalPlan
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.components.EstimatedNumberField
import io.github.peol13.unicum.ui.components.ExpandRow
import io.github.peol13.unicum.ui.components.PersonalFormState
import io.github.peol13.unicum.ui.components.ThinProgress
import io.github.peol13.unicum.ui.components.UnicumButton
import io.github.peol13.unicum.ui.components.UnicumNumberField
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSurface
import io.github.peol13.unicum.ui.theme.UnicumType

/** Da dove arriva il programma di corsa, scelto al primo avvio. */
private enum class PlanChoice { NOTION, PDF, NONE }

/**
 * Le risposte del primo avvio: stanno fuori dalla composizione così non si perdono se Android ricrea la
 * schermata (rotazione del tablet, tema del telefono). Si salvano tutte insieme all'ultimo passo.
 */
private object SetupDraft {
    val form by lazy { PersonalFormState() }
    var mode by mutableStateOf(DashboardMode.NUTRIZIONE)
    var plan by mutableStateOf<PlanChoice?>(null)
    var macrosOpen by mutableStateOf(false)
}

/** I passi del primo avvio, uno per schermata; [PLAN_SETUP] c'è solo se si sceglie Notion o PDF. */
private enum class Step { WELCOME, SEX, AGE, WEIGHT, HEIGHT, ACTIVITY, GOAL, NEEDS, HOME, PLAN, PLAN_SETUP, DONE }

/**
 * Primo avvio, in stile app moderna: una domanda per schermata (sesso, età, peso, altezza, attività, obiettivo),
 * poi il fabbisogno calcolato (modificabile), la schermata principale e il programma di corsa (Notion, PDF o
 * nessuno). Alla fine si salva tutto e si apre l'app. Tutto resta modificabile dal Profilo.
 */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val form = SetupDraft.form
    var index by rememberSaveable { mutableIntStateOf(0) }
    var forward by rememberSaveable { mutableStateOf(true) }
    val steps = Step.entries.filter { it != Step.PLAN_SETUP || SetupDraft.plan == PlanChoice.NOTION || SetupDraft.plan == PlanChoice.PDF }
    val step = steps[index.coerceIn(0, steps.lastIndex)]

    fun go(delta: Int) {
        forward = delta > 0
        index = (index + delta).coerceIn(0, steps.lastIndex)
    }

    fun finish() {
        form.save()
        UserSettings.chooseDashboardMode(SetupDraft.mode)
        UserSettings.completeOnboarding()
        onDone()
    }

    BackHandler(enabled = index > 0) { go(-1) }

    val canContinue = when (step) {
        Step.AGE -> form.ageValue in 14..100
        Step.WEIGHT -> form.weightValue in 30..250
        Step.HEIGHT -> form.heightValue in 120..230
        Step.GOAL -> form.deficitValue in 0..1500
        Step.NEEDS -> form.isValid
        Step.PLAN -> SetupDraft.plan != null
        else -> true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(UnicumBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // In alto: indietro e avanzamento (non nel benvenuto)
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (index > 0) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { go(-1) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", tint = UnicumDark) }
                Spacer(Modifier.width(8.dp))
                ThinProgress(index / steps.lastIndex.toFloat(), Modifier.weight(1f).padding(end = 12.dp))
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(320)) { it / 5 * dir } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it / 5 * dir } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "passo"
        ) { s ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    StepContent(s, form)
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                UnicumButton(
                    text = when (step) {
                        Step.WELCOME -> "Iniziamo"
                        Step.PLAN_SETUP -> "Continua"
                        Step.DONE -> "Apri UNICUM"
                        else -> "Avanti"
                    },
                    enabled = canContinue,
                    onClick = { if (step == Step.DONE) finish() else go(1) }
                )
                if (step == Step.PLAN_SETUP) {
                    Text(
                        "Puoi anche farlo dopo da Profilo → Programma di allenamento.",
                        color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepContent(step: Step, form: PersonalFormState) {
    when (step) {
        Step.WELCOME -> Welcome()

        Step.SEX -> {
            Question("Partiamo da te", "Serve a stimare quante calorie consumi a riposo.")
            BigOption(Icons.Rounded.Male, "Uomo", null, form.male) { form.male = true }
            Spacer(Modifier.height(10.dp))
            BigOption(Icons.Rounded.Female, "Donna", null, !form.male) { form.male = false }
        }

        Step.AGE -> {
            Question("Quanti anni hai?", null)
            BigNumberInput(form.age, "anni", 14..100, 1) { form.age = it }
        }

        Step.WEIGHT -> {
            Question("Quanto pesi?", "Puoi aggiornarlo quando vuoi: se hai una bilancia collegata arriva da sola.")
            BigNumberInput(form.weight, "kg", 30..250, 1) { form.weight = it }
        }

        Step.HEIGHT -> {
            Question("Quanto sei alto?", null)
            BigNumberInput(form.height, "cm", 120..230, 1) { form.height = it }
        }

        Step.ACTIVITY -> {
            Question("Quanto ti muovi di solito?", "Serve solo a stimare l'obiettivo calorico: il movimento di ogni giorno lo conta l'app.")
            ActivityLevel.entries.forEach { level ->
                BigOption(null, level.label, level.description, form.activityLevel == level) { form.activityLevel = level }
                Spacer(Modifier.height(10.dp))
            }
        }

        Step.GOAL -> {
            Question("Qual è il tuo obiettivo?", "Il deficit è quanto vuoi bruciare in più di quanto mangi ogni giorno.")
            val presets = listOf(
                Triple("Dimagrire", "Deficit di 500 kcal al giorno (circa ½ kg a settimana)", 500),
                Triple("Dimagrire con calma", "Deficit di 250 kcal al giorno", 250),
                Triple("Mantenere il peso", "Nessun deficit", 0)
            )
            presets.forEach { (title, desc, kcal) ->
                BigOption(null, title, desc, form.deficitValue == kcal) { form.deficit = kcal.toString() }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            UnicumNumberField("Oppure scrivi il deficit", form.deficit, "kcal") { form.deficit = it }
        }

        Step.NEEDS -> {
            Question("Il tuo fabbisogno", "Calcolato dai tuoi dati. Se conosci i valori veri, scrivili.")
            Text("Metabolismo basale", color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
            Text("Le calorie che consumi a riposo in un giorno.", color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
            Spacer(Modifier.height(8.dp))
            EstimatedNumberField(
                "Metabolismo basale", "kcal", form.bmrEstimate, form.bmr,
                "Formula di Mifflin-St Jeor da sesso, età, peso e altezza."
            )
            Spacer(Modifier.height(18.dp))
            Text("Obiettivo calorico", color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
            Text(
                "Le calorie da mangiare ogni giorno. È fisso: l'attività aumenta solo il deficit.",
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption
            )
            Spacer(Modifier.height(8.dp))
            EstimatedNumberField(
                "Obiettivo calorico", "kcal", form.intakeEstimate, form.intake,
                "Metabolismo basale × livello di attività − deficit."
            )
            ExpandRow(
                SetupDraft.macrosOpen,
                "Macro: proteine ${form.proteinPct}% · carboidrati ${form.carbsPct}% · grassi ${form.fatsPct}%",
                { SetupDraft.macrosOpen = !SetupDraft.macrosOpen }
            )
            if (SetupDraft.macrosOpen || form.pctSum != 100) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnicumNumberField("Proteine", form.proteinPct, "%", Modifier.weight(1f)) { form.proteinPct = it.take(3) }
                    UnicumNumberField("Carbo", form.carbsPct, "%", Modifier.weight(1f)) { form.carbsPct = it.take(3) }
                    UnicumNumberField("Grassi", form.fatsPct, "%", Modifier.weight(1f)) { form.fatsPct = it.take(3) }
                }
                if (form.pctSum != 100) {
                    Text(
                        "Il totale è ${form.pctSum}%: deve essere 100%.",
                        color = io.github.peol13.unicum.ui.components.DeleteRed, style = UnicumType.Caption,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Step.HOME -> {
            Question("Cosa vuoi vedere per primo?", "La schermata principale. Puoi riordinare le card quando vuoi.")
            DashboardMode.entries.forEach { m ->
                val icon = when (m) {
                    DashboardMode.NUTRIZIONE -> Icons.Rounded.Restaurant
                    DashboardMode.ATTIVITA -> Icons.AutoMirrored.Rounded.DirectionsRun
                    DashboardMode.RECUPERO -> Icons.Rounded.Bedtime
                }
                BigOption(icon, m.label, m.description, SetupDraft.mode == m) { SetupDraft.mode = m }
                Spacer(Modifier.height(10.dp))
            }
        }

        Step.PLAN -> {
            Question("Hai un programma di corsa?", "Le sedute compaiono in Allenamenti → Programma e le corse fatte si riconoscono dal giorno.")
            BigOption(Icons.Rounded.ViewKanban, "Da Notion", "Leggo il tuo database del programma (e il registro, se c'è)",
                SetupDraft.plan == PlanChoice.NOTION) { SetupDraft.plan = PlanChoice.NOTION }
            Spacer(Modifier.height(10.dp))
            BigOption(Icons.Rounded.Description, "Da un PDF", "Importo le sedute dal file del tuo allenatore o di un'app",
                SetupDraft.plan == PlanChoice.PDF) { SetupDraft.plan = PlanChoice.PDF }
            Spacer(Modifier.height(10.dp))
            BigOption(Icons.Rounded.EditCalendar, "Nessuno per ora", "Potrai scrivere a mano le prossime corse in Allenamenti → Programma",
                SetupDraft.plan == PlanChoice.NONE) { SetupDraft.plan = PlanChoice.NONE }
        }

        Step.PLAN_SETUP -> when (SetupDraft.plan) {
            PlanChoice.NOTION -> {
                Question("Collega Notion", null)
                NotionPage()
            }
            else -> {
                Question("Importa il programma", "Scegli il PDF: controlli le sedute trovate e le salvi.")
                LocalPlanPage()
            }
        }

        Step.DONE -> Done(form)
    }
}

@Composable
private fun Welcome() {
    Spacer(Modifier.height(32.dp))
    Box(
        Modifier.size(88.dp).clip(CircleShape).background(UnicumAccent.copy(alpha = .35f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Rounded.DirectionsRun, null, tint = UnicumPrimary, modifier = Modifier.size(44.dp))
    }
    Spacer(Modifier.height(24.dp))
    Text("Benvenuto in UNICUM", color = UnicumDark, style = UnicumType.Screen, fontWeight = FontWeight.SemiBold)
    Text("Your health. Unified.", color = UnicumDark.copy(alpha = .55f), style = UnicumType.Heading, fontWeight = FontWeight.Light)
    Spacer(Modifier.height(28.dp))
    listOf(
        Icons.Rounded.Restaurant to "Alimentazione: calorie, macro e deficit della giornata",
        Icons.AutoMirrored.Rounded.DirectionsRun to "Allenamenti e corse da Health Connect, con il tuo programma",
        Icons.Rounded.Bedtime to "Sonno e recupero da anello e orologio"
    ).forEach { (icon, text) ->
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(UnicumSurface),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = UnicumPrimary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Text(text, color = UnicumDark.copy(alpha = .8f), style = UnicumType.Body, lineHeight = 20.sp)
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        "Qualche domanda per partire: ci vuole un minuto e puoi cambiare tutto dal Profilo. I tuoi dati restano sul telefono.",
        color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail, lineHeight = 19.sp
    )
}

@Composable
private fun Done(form: PersonalFormState) {
    Spacer(Modifier.height(32.dp))
    Box(
        Modifier.size(88.dp).clip(CircleShape).background(UnicumPrimary.copy(alpha = .15f)),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Rounded.Check, null, tint = UnicumPrimary, modifier = Modifier.size(44.dp)) }
    Spacer(Modifier.height(24.dp))
    Text("Tutto pronto", color = UnicumDark, style = UnicumType.Screen, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(16.dp))
    val plan = when {
        NotionPlan.connected -> "Programma da Notion collegato"
        !LocalPlan.isEmpty -> "Programma nell'app: ${LocalPlan.runs.size} sedute"
        else -> "Nessun programma: le corse future si aggiungono in Allenamenti → Programma"
    }
    listOfNotNull(
        form.intakeToSave?.let { "Obiettivo: $it kcal al giorno" },
        form.deficitValue?.let { if (it > 0) "Deficit previsto: $it kcal" else "Obiettivo: mantenere il peso" },
        "Schermata principale: ${SetupDraft.mode.label}",
        plan
    ).forEach {
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Check, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Text(it, color = UnicumDark.copy(alpha = .8f), style = UnicumType.Body, lineHeight = 20.sp)
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(
        "Poi collega Health Connect dal Profilo: da lì arrivano passi, allenamenti, sonno e recupero.",
        color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail, lineHeight = 19.sp
    )
}

/** Titolo grande della domanda e, sotto, una spiegazione breve. */
@Composable
private fun Question(title: String, hint: String?) {
    Spacer(Modifier.height(12.dp))
    Text(title, color = UnicumDark, style = UnicumType.Screen, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp)
    if (hint != null) {
        Spacer(Modifier.height(6.dp))
        Text(hint, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail, lineHeight = 19.sp)
    }
    Spacer(Modifier.height(24.dp))
}

/** Una risposta a scelta, grande e toccabile: icona facoltativa, titolo, descrizione, segno di spunta se scelta. */
@Composable
private fun BigOption(icon: ImageVector?, title: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) UnicumPrimary.copy(alpha = .10f) else UnicumSurface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) UnicumPrimary else UnicumAccent.copy(alpha = .5f), shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(UnicumAccent.copy(alpha = .35f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = UnicumPrimary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = UnicumDark, style = UnicumType.Card, fontWeight = FontWeight.SemiBold)
            if (description != null) {
                Text(description, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, lineHeight = 16.sp)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, "Scelta", tint = UnicumPrimary, modifier = Modifier.size(22.dp))
    }
}

/** Numero grande al centro, scrivibile, con − e + ai lati; [range] = valori accettati (fuori resta rosso). */
@Composable
private fun BigNumberInput(value: String, unit: String, range: IntRange, step: Int, onChange: (String) -> Unit) {
    val n = value.toIntOrNull()
    val ok = n != null && n in range
    Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        StepButton(Icons.Rounded.Remove, "Meno") { onChange(((n ?: range.first) - step).coerceIn(range).toString()) }
        Spacer(Modifier.width(20.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.filter(Char::isDigit).take(3)) },
                singleLine = true,
                textStyle = UnicumType.Display.copy(
                    color = if (ok || value.isEmpty()) UnicumDark else io.github.peol13.unicum.ui.components.DeleteRed,
                    textAlign = TextAlign.Center, fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(UnicumPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(150.dp)
            )
            Text(unit, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Heading)
        }
        Spacer(Modifier.width(20.dp))
        StepButton(Icons.Rounded.Add, "Più") { onChange(((n ?: range.first) + step).coerceIn(range).toString()) }
    }
    if (!ok && value.isNotEmpty()) {
        Text(
            "Valore tra ${range.first} e ${range.last} $unit.",
            color = io.github.peol13.unicum.ui.components.DeleteRed, style = UnicumType.Caption, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(UnicumSurface)
            .border(1.dp, UnicumAccent.copy(alpha = .5f), CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = UnicumPrimary, modifier = Modifier.size(26.dp)) }
}
