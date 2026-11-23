package io.github.peol13.unicum.ui.components

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.tk

import io.github.peol13.unicum.i18n.trp

import io.github.peol13.unicum.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.peol13.unicum.data.SavedMeal
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.Food
import io.github.peol13.unicum.data.FoodCatalog
import io.github.peol13.unicum.data.FoodPortions
import io.github.peol13.unicum.data.Meal
import io.github.peol13.unicum.data.DraftItem
import io.github.peol13.unicum.data.MealDraft
import io.github.peol13.unicum.data.MealTime
import io.github.peol13.unicum.data.OpenFoodFacts
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumOnPrimary
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import io.github.peol13.unicum.ui.theme.UnicumType

/** Un alimento già aggiunto al pasto che si sta registrando, con la quantità ancora modificabile. */
private data class StagedFood(val food: Food, val grams: Int) {
    val meal: Meal get() = food.portion(grams)
}

/**
 * Registrazione rapida di un pasto: cerchi l'alimento per nome, scegli i grammi e lo aggiungi.
 * Il database contiene centinaia di alimenti; se manca puoi crearne uno personalizzato.
 * Gli alimenti aggiunti restano modificabili (quantità) finché non salvi; l'orario vale per tutto il pasto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealDialog(
    initialMealType: String? = null,
    date: String = UnicumAppState.selectedDate,
    onDismiss: () -> Unit,
    onConfirmMeals: (List<Meal>) -> Unit
) {
    val isToday = date == UnicumRepository.today()
    // Senza un tipo indicato (es. dalla Home) si propone quello dell'ora attuale
    // Una bozza lasciata a metà per questo giorno viene ripresa
    val draft = remember(date) { MealDraft.load(date) }
    var mealType by remember { mutableStateOf(draft?.mealType ?: initialMealType ?: if (isToday) MealTime.suggestType() else "Pranzo") }
    // Oggi: adesso. Giorni passati: l'orario tipico del pasto, finché non lo cambi a mano
    var minute by remember { mutableStateOf(draft?.minute ?: if (isToday) MealTime.now() else MealTime.typical(mealType)) }
    var minuteTouched by remember { mutableStateOf(draft?.minuteTouched ?: false) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }

    var selectedFood by remember { mutableStateOf<Food?>(null) }
    // Lo stesso alimento può comparire in "Recenti" e nell'elenco: la chiave dice quale riga è aperta
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var gramsText by remember { mutableStateOf("100") }
    var creating by remember { mutableStateOf(false) }

    val staged = remember { mutableStateListOf<StagedFood>().also { l -> draft?.items?.forEach { l.add(StagedFood(it.food, it.grams)) } } }
    var confirmClose by remember { mutableStateOf(false) }
    // Ogni modifica al pasto in costruzione viene salvata subito come bozza
    LaunchedEffect(staged.toList(), mealType, minute, minuteTouched) {
        MealDraft.save(MealDraft(date, mealType, minute, minuteTouched, staged.map { DraftItem(it.food, it.grams) }))
    }
    val haptics = rememberHaptics()

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var online by remember { mutableStateOf<OnlineState>(OnlineState.Idle) }

    val customFoods = UserSettings.customFoods
    // Prodotti con marca già usati in passato (salvati in locale), ricercabili anche senza internet
    // Mentre si cercano i nuovi resta il risultato precedente: se la lista si svuotasse a ogni lettera,
    // all'arrivo dei prodotti salvati la lista resterebbe agganciata più in basso (sui risultati online)
    var cachedFoods by remember { mutableStateOf(emptyList<Food>()) }
    LaunchedEffect(query) { cachedFoods = UnicumRepository.searchCachedFoods(context, query) }
    // Tutti gli alimenti già registrati (anche quelli non più nel catalogo): nella ricerca vengono per primi
    val usedFoods by produceState(emptyList<Food>()) { value = UnicumRepository.usedFoods(context) }
    val usedNames = remember(usedFoods) { usedFoods.map { it.name } }
    val recentNames = UserSettings.recentFoodNames
    val recentCached by produceState(emptyList<Food>(), recentNames) {
        value = UnicumRepository.cachedFoodsByNames(context, recentNames)
    }
    // Suggeriti: gli alimenti già mangiati in questo pasto (es. a colazione), dal più recente
    val typeFoods by produceState(emptyList<Food>(), mealType) {
        kotlinx.coroutines.delay(200)  // mentre scrivi il nome del pasto non si interroga il database a ogni lettera
        value = UnicumRepository.foodsUsedIn(context, mealType)
    }
    val typeNames = remember(typeFoods) { typeFoods.map { it.name } }
    // Gli alimenti già scelti in passato vengono per primi: prima quelli di questo pasto, poi tutti quelli
    // già registrati (dal più recente); a parità resta l'ordine di pertinenza della ricerca
    val results = remember(query, category, customFoods, cachedFoods, usedFoods, recentNames, typeNames) {
        // Posizione di ogni nome nelle tre liste, calcolata una volta (non una scansione a ogni confronto)
        fun positions(names: List<String>) = HashMap<String, Int>().apply { names.forEachIndexed { i, n -> putIfAbsent(n, i) } }
        val typePos = positions(typeNames)
        val usedPos = positions(usedNames)
        val recentPos = positions(recentNames)
        FoodCatalog.search(query, category, customFoods + cachedFoods + usedFoods).sortedBy { f ->
            typePos[f.name] ?: usedPos[f.name]?.plus(1_000) ?: recentPos[f.name]?.plus(100_000) ?: Int.MAX_VALUE
        }
    }
    // A ogni nuova ricerca (o quando arrivano i risultati) la lista riparte dall'alto
    val listState = rememberLazyListState()
    LaunchedEffect(query, category, results.firstOrNull()?.name) { listState.scrollToItem(0) }
    // Senza storico per questo pasto si propongono gli ultimi alimenti usati in generale
    val recents = remember(typeFoods, recentNames, customFoods, recentCached) {
        typeFoods.ifEmpty { recentNames.mapNotNull { n -> UserSettings.findFood(n) ?: recentCached.firstOrNull { it.name == n } } }
    }
    val recentsLabel = when {
        typeFoods.isEmpty() -> tr("Usati di recente")
        MealTime.isOther(mealType) -> tr("Già mangiati negli spuntini")
        else -> tr("Già mangiati a %s", MealTime.label(mealType).lowercase(I18n.locale))
    }

    // Codice a barre: fotocamera aperta, ricerca in corso, prodotto trovato (o messaggio d'errore)
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var scannedFood by remember { mutableStateOf<Food?>(null) }

    fun lookupBarcode(code: String) {
        scanning = false
        scanMessage = tr("Cerco il prodotto %s…", code)
        scannedFood = null
        scope.launch {
            val local = UnicumRepository.cachedFoodByBarcode(context, code)
            if (local != null) {
                scannedFood = local; scanMessage = null
                selectedFood = local; selectedKey = "s_" + code; gramsText = "100"
                return@launch
            }
            OpenFoodFacts.byBarcode(code).fold(
                onSuccess = { food ->
                    if (food == null) {
                        scanMessage = tr("Prodotto %s non trovato o senza valori nutrizionali. Puoi crearlo a mano.", code)
                    } else {
                        scannedFood = food; scanMessage = null
                        selectedFood = food; selectedKey = "s_" + code; gramsText = "100"
                    }
                },
                onFailure = { scanMessage = OpenFoodFacts.friendlyError(it) }
            )
        }
    }

    fun searchOnline() {
        val q = query.trim()
        if (q.length < 2) return
        online = OnlineState.Loading
        scope.launch {
            val r = OpenFoodFacts.search(q)
            // Il risultato vale solo se nel frattempo non è cambiata la ricerca
            if (query.trim() == q) {
                online = r.fold(
                    onSuccess = { OnlineState.Done(it) },
                    onFailure = { OnlineState.Error(OpenFoodFacts.friendlyError(it)) }
                )
            }
        }
    }
    // La ricerca online parte da sola poco dopo che smetti di scrivere
    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            kotlinx.coroutines.delay(450)
            searchOnline()
        }
    }
    val showRecents = query.isBlank() && category == null && recents.isNotEmpty()

    // Pasti fissi: in cima alla ricerca (filtrati per nome), si caricano nel pasto e restano modificabili
    val savedMeals = UserSettings.savedMeals
    val shownTemplates = remember(query, category, savedMeals) {
        if (category != null) emptyList() else SavedMeal.search(savedMeals, query)
    }
    // Nome del pasto fisso caricato: proposto quando lo salvi di nuovo (così lo aggiorni)
    var loadedTemplate by rememberSaveable { mutableStateOf<String?>(null) }
    var savingTemplate by remember { mutableStateOf(false) }
    var deletingTemplate by remember { mutableStateOf<SavedMeal?>(null) }

    val totalKcal = staged.sumOf { it.meal.calories }

    fun loadTemplate(m: SavedMeal) {
        haptics.tick()
        staged.addAll(m.items.map { StagedFood(it.food, it.grams) })
        loadedTemplate = m.name
        selectedFood = null
        selectedKey = null
        query = ""
    }

    fun stageSelected() {
        val food = selectedFood ?: return
        val grams = gramsText.toIntOrNull() ?: return
        if (grams <= 0) return
        haptics.tick()
        staged.add(StagedFood(food, grams))
        selectedFood = null
        selectedKey = null
        gramsText = "100"
        query = ""
    }

    fun toggle(food: Food, key: String) {
        val open = selectedKey == key
        selectedFood = if (open) null else food
        selectedKey = if (open) null else key
        // Con porzioni medie si parte da una porzione, altrimenti da 100 g
        gramsText = (FoodPortions.of(food).firstOrNull()?.grams ?: 100).toString()
    }

    @Composable
    fun FoodItem(food: Food, key: String) = FoodRow(
        food = food,
        expanded = selectedKey == key,
        gramsText = gramsText,
        onGramsChange = { gramsText = it },
        onClick = { toggle(food, key) },
        onAdd = ::stageSelected
    )

    if (scanning) BarcodeScannerDialog(onCode = ::lookupBarcode, onDismiss = { scanning = false })

    // Foglio che sale dal basso, comodo con il pollice: si chiude trascinandolo giù, con Indietro o con la X
    // Con alimenti già aggiunti, trascinando giù il foglio torna su e chiede prima cosa farne
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = {
            if (it == SheetValue.Hidden && staged.isNotEmpty()) {
                confirmClose = true
                false
            } else true
        }
    )
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    fun requestClose() {
        if (staged.isEmpty()) close() else confirmClose = true
    }
    fun saveMeal() {
        haptics.confirm()
        UserSettings.markFoodUsed(staged.map { it.food.name })
        // I prodotti con marca usati si salvano in locale: si ritrovano anche senza internet
        val branded = staged.map { it.food }.filter { it.barcode != null }
        if (branded.isNotEmpty()) {
            ioScope.launch { UnicumRepository.cacheOnlineFoods(context, branded) }
        }
        val meals = staged.map { it.food.portion(it.grams, mealType.trim(), minute) }
        MealDraft.clear()
        onConfirmMeals(meals)
    }
    if (confirmClose) {
        UnicumDialog(onDismiss = { confirmClose = false }) {
            Text(tr("Pasto non salvato"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
            Text(
                tr("%s (%,d kcal) nel pasto. Cosa ne facciamo?", trp(staged.size, "Hai %d alimento", "Hai %d alimenti"), totalKcal),
                color = UnicumDark.copy(alpha = .7f), style = UnicumType.Body
            )
            UnicumButton(tr("Salva pasto"), onClick = { confirmClose = false; saveMeal() })
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, UnicumDark.copy(alpha = .12f), RoundedCornerShape(16.dp))
                    .clickable {
                        confirmClose = false
                        MealDraft.clear()
                        staged.clear()
                        close()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("Scarta pasto"), color = DeleteRed, style = UnicumType.Body, fontWeight = FontWeight.Medium)
            }
            TextButton(onClick = { confirmClose = false }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Continua a modificare"), color = UnicumDark.copy(alpha = .7f))
            }
        }
    }
    if (savingTemplate) {
        SaveTemplateDialog(
            initialName = loadedTemplate ?: mealType,
            onDismiss = { savingTemplate = false },
            onSave = { name ->
                UserSettings.saveMealTemplate(SavedMeal(name, staged.map { DraftItem(it.food, it.grams) }))
                loadedTemplate = name
                savingTemplate = false
                haptics.confirm()
            }
        )
    }
    deletingTemplate?.let { m ->
        UnicumDialog(onDismiss = { deletingTemplate = null }) {
            Text(tr("Eliminare “%s”?", m.name), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
            Text(
                tr("Il pasto fisso non sarà più proposto. I pasti già registrati restano."),
                color = UnicumDark.copy(alpha = .7f), style = UnicumType.Body
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, UnicumDark.copy(alpha = .12f), RoundedCornerShape(16.dp))
                    .clickable {
                        UserSettings.deleteMealTemplate(m.name)
                        if (loadedTemplate.equals(m.name, true)) loadedTemplate = null
                        deletingTemplate = null
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("Elimina"), color = DeleteRed, style = UnicumType.Body, fontWeight = FontWeight.Medium)
            }
            TextButton(onClick = { deletingTemplate = null }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f))
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = ::requestClose,
        sheetState = sheetState,
        containerColor = UnicumBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = UnicumAccent) }
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {

                // INTESTAZIONE: titolo, giorno e orario del pasto
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Restaurant, size = 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Registra pasto"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            if (isToday) tr("Oggi") else DateUtils.parse(date).let { "${it.dayOfMonth}/${it.monthValue}" },
                            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail
                        )
                    }
                    MealTimePill(minute, { minute = it; minuteTouched = true })
                    IconButton(onClick = ::requestClose) {
                        Icon(Icons.Rounded.Close, tr("Chiudi"), tint = UnicumDark)
                    }
                }

                Spacer(Modifier.height(14.dp))

                MealTypeInput(mealType, {
                    mealType = it
                    if (!isToday && !minuteTouched) minute = MealTime.typical(it)
                })

                Spacer(Modifier.height(12.dp))

                if (creating) {
                    CreateFoodForm(
                        initialName = query.trim(),
                        onCancel = { creating = false },
                        onCreated = { food ->
                            UserSettings.addCustomFood(food)
                            creating = false
                            selectedFood = food
                            selectedKey = "a_" + food.name
                            gramsText = "100"
                            query = food.name
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    OutlinedTextField(
                        textStyle = unicumFieldTextStyle(),
                        value = query,
                        onValueChange = {
                            query = it
                            selectedFood = null
                            selectedKey = null
                            online = OnlineState.Idle
                        },
                        placeholder = { Text(tr("Cerca un alimento"), maxLines = 1) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = UnicumPrimary) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = ""; selectedFood = null; selectedKey = null; online = OnlineState.Idle }) {
                                        Icon(Icons.Rounded.Close, tr("Cancella"), tint = UnicumDark.copy(alpha = .6f))
                                    }
                                }
                                IconButton(onClick = { scanning = true }) {
                                    Icon(Icons.Rounded.QrCodeScanner, tr("Scansiona codice a barre"), tint = UnicumPrimary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = unicumFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { UnicumChip(tr("Tutti"), category == null, { category = null; selectedFood = null; selectedKey = null }, compact = true) }
                        items(
                            FoodCatalog.categories +
                                (if (customFoods.isNotEmpty()) listOf(tk("Personalizzati")) else emptyList()) +
                                (if (cachedFoods.isNotEmpty() || category == OpenFoodFacts.CATEGORY) listOf(OpenFoodFacts.CATEGORY) else emptyList())
                        ) { cat ->
                            UnicumChip(tr(cat), category == cat, { category = cat; selectedFood = null; selectedKey = null }, compact = true)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        scanMessage?.let { msg ->
                            item(key = "scan_msg") {
                                Text(msg, color = UnicumDark.copy(alpha = .7f), style = UnicumType.Detail, modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                        scannedFood?.let { f ->
                            item(key = "scan_food") {
                                ListLabel(tr("Prodotto scansionato"))
                                Spacer(Modifier.height(4.dp))
                                FoodItem(f, "s_" + (f.barcode ?: f.name))
                            }
                        }
                        if (shownTemplates.isNotEmpty()) {
                            item(key = "t_head") { ListLabel(tr("Pasti salvati")) }
                            items(shownTemplates, key = { "t_" + it.name }) { m ->
                                SavedMealRow(m, onAdd = { loadTemplate(m) }, onDelete = { deletingTemplate = m })
                            }
                        }
                        if (showRecents) {
                            item { ListLabel(recentsLabel) }
                            items(recents, key = { "r_" + it.name }) { food -> FoodItem(food, "r_" + food.name) }
                        }
                        if (showRecents || shownTemplates.isNotEmpty()) {
                            item { ListLabel(if (query.isBlank()) tr("Tutti gli alimenti") else tr("Alimenti")) }
                        }

                        items(results, key = { it.name }) { food -> FoodItem(food, "a_" + food.name) }

                        if (results.isEmpty()) {
                            item {
                                Text(
                                    tr("Nessun alimento trovato per “%s”.", query.trim()),
                                    color = UnicumDark.copy(alpha = .6f),
                                    style = UnicumType.Detail,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }

                        // Ricerca online di prodotti con marca (Open Food Facts)
                        if (query.trim().length >= 2) {
                            item(key = "online_head") {
                                OnlineSearchHeader(query.trim(), online, onSearch = ::searchOnline)
                            }
                            (online as? OnlineState.Done)?.let { done ->
                                items(done.foods, key = { "o_" + (it.barcode ?: it.name) }) { food ->
                                    FoodItem(food, "o_" + (food.barcode ?: food.name))
                                }
                            }
                        }

                        item {
                            TextButton(onClick = { creating = true }) {
                                Icon(Icons.Rounded.Add, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (query.isBlank()) tr("Crea un nuovo alimento") else tr("Crea “%s”", query.trim()),
                                    color = UnicumPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // PASTO IN COSTRUZIONE: quantità ancora modificabili prima di salvare
                if (staged.isNotEmpty()) {
                    // Già salvato così com'è? Allora il link lo dice, altrimenti propone di salvarlo/aggiornarlo
                    val current = staged.map { DraftItem(it.food, it.grams) }
                    val savedAs = savedMeals.firstOrNull { it.name.equals(loadedTemplate, true) }
                    StagedList(
                        staged = staged,
                        onGrams = { i, g -> staged[i] = staged[i].copy(grams = g) },
                        onRemove = { staged.removeAt(it) },
                        templateLabel = when {
                            savedAs != null && savedAs.items == current -> tr("Pasto fisso “%s”", savedAs.name)
                            savedAs != null -> tr("Aggiorna il pasto fisso “%s”", savedAs.name)
                            else -> tr("Salva come pasto fisso")
                        },
                        templateSaved = savedAs != null && savedAs.items == current,
                        onSaveTemplate = { savingTemplate = true }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                UnicumButton(
                    text = if (staged.isEmpty()) tr("Aggiungi almeno un alimento")
                    else trp(staged.size, "Salva %d alimento", "Salva %d alimenti") + " · ${io.github.peol13.unicum.i18n.lnum(totalKcal)} kcal",
                    enabled = staged.isNotEmpty(),
                    onClick = ::saveMeal
                )
            }
        }
    }
}

/** Gli alimenti già aggiunti: − / + di 10 g per correggere la quantità, x per toglierli. */
@Composable
private fun StagedList(
    staged: List<StagedFood>,
    onGrams: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    templateLabel: String,
    templateSaved: Boolean,
    onSaveTemplate: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(UnicumSurface)
            .border(1.dp, UnicumAccent.copy(alpha = .45f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("NEL PASTO"), color = UnicumDark.copy(alpha = .45f), style = UnicumType.Note,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
            )
            val meals = staged.map { it.meal }
            Text(
                "${tr("P")} ${fmtGrams(meals.sumOf { it.protein.toDouble() }.toFloat())}  " +
                    "${tr("C")} ${fmtGrams(meals.sumOf { it.carbohydrates.toDouble() }.toFloat())}  " +
                    "${tr("G")} ${fmtGrams(meals.sumOf { it.fats.toDouble() }.toFloat())}",
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
            )
        }
        Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
            staged.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.food.name, color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("${io.github.peol13.unicum.i18n.lnum(item.meal.calories)} kcal", color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note)
                    }
                    StepButton(add = false, size = 30.dp) { if (item.grams > 10) onGrams(i, item.grams - 10) }
                    Text(
                        "${item.grams} g", color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.width(56.dp)
                    )
                    StepButton(add = true, size = 30.dp) { onGrams(i, item.grams + 10) }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onRemove(i) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, tr("Rimuovi"), tint = DeleteRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Row(
            Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSaveTemplate)
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (templateSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null,
                tint = UnicumPrimary, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                templateLabel, color = if (templateSaved) UnicumDark.copy(alpha = .6f) else UnicumPrimary,
                style = UnicumType.Detail, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Pasto fisso nella ricerca: tocco = i suoi alimenti entrano nel pasto (poi modificabili). */
@Composable
private fun SavedMealRow(meal: SavedMeal, onAdd: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val portions = meal.items.map { it.food.portion(it.grams) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(UnicumSurface)
            .border(1.dp, UnicumPrimary.copy(alpha = .35f), shape)
            .clickable(onClick = onAdd)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Rounded.Bookmark, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(meal.name, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                meal.items.joinToString(", ") { "${it.food.name} ${it.grams} g" },
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${tr("P")} ${fmtGrams(portions.sumOf { it.protein.toDouble() }.toFloat())} · " +
                    "${tr("C")} ${fmtGrams(portions.sumOf { it.carbohydrates.toDouble() }.toFloat())} · " +
                    "${tr("G")} ${fmtGrams(portions.sumOf { it.fats.toDouble() }.toFloat())}",
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${meal.calories}", color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
            Text("kcal", color = UnicumDark.copy(alpha = .5f), style = UnicumType.Micro)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(UnicumAccent.copy(alpha = .35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.DeleteOutline, tr("Elimina pasto fisso"), tint = UnicumDark.copy(alpha = .4f), modifier = Modifier.size(18.dp))
        }
    }
}

/** Nome del pasto fisso; se esiste già, il salvataggio lo aggiorna. */
@Composable
private fun SaveTemplateDialog(initialName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val exists = UserSettings.savedMeals.any { it.name.equals(name.trim(), true) }
    UnicumDialog(onDismiss = onDismiss) {
        Text(tr("Pasto fisso"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        Text(
            tr("Lo ritrovi in cima alla ricerca quando registri un pasto: un tocco e gli alimenti entrano nel pasto, poi puoi cambiare grammi o aggiungere altro."),
                
            color = UnicumDark.copy(alpha = .7f), style = UnicumType.Detail
        )
        OutlinedTextField(
            textStyle = unicumFieldTextStyle(),
            value = name, onValueChange = { name = it.take(40) },
            label = { Text(tr("Nome (es. Colazione)")) }, singleLine = true,
            shape = RoundedCornerShape(14.dp), colors = unicumFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (exists) {
            Text(
                tr("Esiste già un pasto fisso con questo nome: verrà aggiornato."),
                color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
            )
        }
        UnicumButton(if (exists) tr("Aggiorna") else tr("Salva"), onClick = { onSave(name.trim()) }, enabled = name.isNotBlank())
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f))
        }
    }
}

@Composable
private fun ListLabel(text: String) {
    Text(
        text.uppercase(),
        color = UnicumDark.copy(alpha = .45f),
        style = UnicumType.Note,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

/** Riga di un alimento: chiusa mostra kcal e macro per 100 g; aperta, quantità e riepilogo. */
@Composable
private fun FoodRow(
    food: Food,
    expanded: Boolean,
    gramsText: String,
    onGramsChange: (String) -> Unit,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    val grams = gramsText.toIntOrNull() ?: 0
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(UnicumSurface)
            .border(1.dp, if (expanded) UnicumPrimary else UnicumAccent.copy(alpha = .4f), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(foodCategoryIcon(food.category), size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(food.name, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${tr("P")} ${fmtGrams(food.proteinPer100g)} · ${tr("C")} ${fmtGrams(food.carbsPer100g)} · ${tr("G")} ${fmtGrams(food.fatsPer100g)}" +
                        (food.fiberPer100g?.let { " · ${tr("F")} ${fmtGrams(it)}" } ?: "") + "  " + tr("per 100 g"),
                    color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${food.caloriesPer100g}", color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
                Text("kcal", color = UnicumDark.copy(alpha = .5f), style = UnicumType.Micro)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(if (expanded) UnicumPrimary else UnicumAccent.copy(alpha = .35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.Add, null,
                    tint = if (expanded) UnicumOnPrimary else UnicumPrimary, modifier = Modifier.size(18.dp)
                )
            }
        }

        if (expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(UnicumAccent.copy(alpha = .35f)))
                Spacer(Modifier.height(12.dp))
                val portions = remember(food.name) { FoodPortions.of(food) }
                if (portions.isEmpty()) GramsInput(gramsText, onGramsChange)
                else PortionOrGramsInput(portions, gramsText, onGramsChange)
                Spacer(Modifier.height(10.dp))
                val portion = food.portion(grams)
                NutritionSummary(portion.calories, portion.protein, portion.carbohydrates, portion.fats)
                Spacer(Modifier.height(10.dp))
                UnicumButton(tr("Aggiungi al pasto"), onClick = onAdd, enabled = grams > 0)
            }
        }
    }
}

@Composable
private fun CreateFoodForm(
    initialName: String,
    onCancel: () -> Unit,
    onCreated: (Food) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fats by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var sugars by remember { mutableStateOf("") }
    var salt by remember { mutableStateOf("") }

    val kcalValue = kcal.toIntOrNull() ?: 0
    // Se l'utente conosce solo i macro, le calorie si ricavano da quelli
    val macroKcal = ((protein.toFloatOrNull() ?: 0f) * 4 + (carbs.toFloatOrNull() ?: 0f) * 4 + (fats.toFloatOrNull() ?: 0f) * 9).roundToInt()
    val finalKcal = if (kcalValue > 0) kcalValue else macroKcal
    val valid = name.isNotBlank() && finalKcal > 0

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Nuovo alimento"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
        Text(tr("Valori per 100 g, come sull'etichetta."), color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)

        OutlinedTextField(
            textStyle = unicumFieldTextStyle(),
            value = name, onValueChange = { name = it },
            label = { Text(tr("Nome")) }, singleLine = true,
            shape = RoundedCornerShape(14.dp), colors = unicumFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            textStyle = unicumFieldTextStyle(),
            value = kcal, onValueChange = { kcal = it.filter(Char::isDigit).take(4) },
            label = { Text(tr("Calorie (kcal / 100 g)")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp), colors = unicumFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField(tr("Proteine"), protein, { protein = it }, Modifier.weight(1f))
            DecimalField(tr("Carbo"), carbs, { carbs = it }, Modifier.weight(1f))
            DecimalField(tr("Grassi"), fats, { fats = it }, Modifier.weight(1f))
        }
        Text(
            tr("Le calorie si calcolano dai macro se lasci vuoto il campo kcal."),
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField(tr("Fibre"), fiber, { fiber = it }, Modifier.weight(1f))
            DecimalField(tr("Zuccheri"), sugars, { sugars = it }, Modifier.weight(1f))
            DecimalField(tr("Sale"), salt, { salt = it }, Modifier.weight(1f))
        }
        Text(
            tr("Fibre, zuccheri e sale sono facoltativi: se li scrivi, entrano nei totali del giorno."),
            color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note
        )

        Spacer(Modifier.height(8.dp))

        UnicumButton(
            tr("Salva alimento"),
            enabled = valid,
            onClick = {
                onCreated(
                    Food(
                        name = name.trim(),
                        caloriesPer100g = finalKcal,
                        proteinPer100g = protein.toFloatOrNull() ?: 0f,
                        carbsPer100g = carbs.toFloatOrNull() ?: 0f,
                        fatsPer100g = fats.toFloatOrNull() ?: 0f,
                        category = tk("Personalizzati"),
                        fiberPer100g = fiber.toFloatOrNull(),
                        sugarsPer100g = sugars.toFloatOrNull(),
                        saltPer100g = salt.toFloatOrNull()
                    )
                )
            }
        )
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Annulla"), color = UnicumDark.copy(alpha = .7f))
        }
    }
}

@Composable
private fun DecimalField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        textStyle = unicumFieldTextStyle(),
        value = value,
        onValueChange = { v -> onChange(v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(5)) },
        label = { Text(label, maxLines = 1) },
        suffix = { Text("g") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        colors = unicumFieldColors(),
        modifier = modifier
    )
}

private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Stato della ricerca online nel dialog. */
private sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Done(val foods: List<Food>) : OnlineState
    data class Error(val message: String) : OnlineState
}

/** Pulsante "Cerca su Open Food Facts" e, sotto, esito: caricamento, errore o avviso sui risultati. */
@Composable
private fun OnlineSearchHeader(query: String, state: OnlineState, onSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        when (state) {
            OnlineState.Idle -> TextButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, null, tint = UnicumPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    tr("Cerca “%s” online (marche e prodotti)", query),
                    color = UnicumPrimary, fontWeight = FontWeight.SemiBold, style = UnicumType.Detail
                )
            }
            OnlineState.Loading -> Text(
                tr("Cerco su Open Food Facts…"),
                color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            is OnlineState.Error -> {
                Text(state.message, color = DeleteRed, style = UnicumType.Caption, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = onSearch) { Text(tr("Riprova"), color = UnicumPrimary, fontWeight = FontWeight.SemiBold) }
            }
            is OnlineState.Done -> {
                ListLabel(tr("Da Open Food Facts"))
                Text(
                    if (state.foods.isEmpty()) tr("Nessun prodotto trovato online per “%s”.", query)
                    else tr("Valori per 100 g, inseriti dagli utenti: controlla l'etichetta se sono importanti. Fonte: Open Food Facts (ODbL)."),
                    color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}
