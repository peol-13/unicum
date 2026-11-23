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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import io.github.peol13.unicum.i18n.lnum
import androidx.compose.ui.draw.shadow

/** Un alimento già aggiunto al pasto che si sta registrando, con la quantità ancora modificabile. */
private data class StagedFood(val food: Food, val grams: Int) {
    val meal: Meal get() = food.portion(grams)
}

/** Il foglio della quantità: un alimento da aggiungere ([index] null) o uno già nel pasto da correggere. */
private data class QuantityTarget(val food: Food, val grams: Int, val index: Int? = null)

/**
 * Registrazione di un pasto, pensata per tanti alimenti di fila:
 * - in alto solo tipo di pasto, orario e ricerca (che si apre già pronta per scrivere);
 * - sotto, la lista occupa tutto lo spazio: a ricerca vuota "Già mangiati a…", pasti salvati e i tuoi alimenti;
 * - il + di una riga aggiunge subito la quantità abituale (con "Modifica" per correggerla), toccando la riga si
 *   apre un foglio a parte per scegliere porzioni o grammi;
 * - gli alimenti scelti stanno in una barra in basso ("3 alimenti · 812 kcal") con l'unico tasto Salva;
 *   toccandola si apre il riepilogo del pasto, dove si correggono o tolgono.
 * Il pasto in costruzione resta salvato come bozza finché non lo salvi o lo scarti.
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
    var creating by remember { mutableStateOf(false) }
    var choosingType by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<QuantityTarget?>(null) }
    var reviewing by remember { mutableStateOf(false) }

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

    // La ricerca è pronta per scrivere appena si apre il foglio (non quando si riprende una bozza)
    val searchFocus = remember { FocusRequester() }
    var focusTick by remember { mutableStateOf(if (draft?.items.isNullOrEmpty()) 1 else 0) }
    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        runCatching { searchFocus.requestFocus() }
    }

    val customFoods = UserSettings.customFoods
    // Prodotti con marca già usati in passato (salvati in locale), ricercabili anche senza internet
    // Mentre si cercano i nuovi resta il risultato precedente: se la lista si svuotasse a ogni lettera,
    // all'arrivo dei prodotti salvati la lista resterebbe agganciata più in basso (sui risultati online)
    var cachedFoods by remember { mutableStateOf(emptyList<Food>()) }
    LaunchedEffect(query) { cachedFoods = UnicumRepository.searchCachedFoods(context, query) }
    // Tutti gli alimenti già registrati (anche quelli non più nel catalogo): nella ricerca vengono per primi
    val usedFoods by produceState(emptyList<Food>()) { value = UnicumRepository.usedFoods(context) }
    val usedNames = remember(usedFoods) { usedFoods.map { it.name } }
    // L'ultima quantità registrata di ogni alimento: è quella che il + aggiunge
    val lastGrams by produceState(emptyMap<String, Int>()) { value = UnicumRepository.lastGrams(context) }
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
    val results = remember(query, customFoods, cachedFoods, usedFoods, recentNames, typeNames) {
        // Posizione di ogni nome nelle tre liste, calcolata una volta (non una scansione a ogni confronto)
        fun positions(names: List<String>) = HashMap<String, Int>().apply { names.forEachIndexed { i, n -> putIfAbsent(n, i) } }
        val typePos = positions(typeNames)
        val usedPos = positions(usedNames)
        val recentPos = positions(recentNames)
        FoodCatalog.search(query, null, customFoods + cachedFoods + usedFoods).sortedBy { f ->
            typePos[f.name] ?: usedPos[f.name]?.plus(1_000) ?: recentPos[f.name]?.plus(100_000) ?: Int.MAX_VALUE
        }
    }
    // A ogni nuova ricerca (o quando arrivano i risultati) la lista riparte dall'alto
    val listState = rememberLazyListState()
    LaunchedEffect(query, results.firstOrNull()?.name) { listState.scrollToItem(0) }
    // Senza storico per questo pasto si propongono gli ultimi alimenti usati in generale
    val recents = remember(typeFoods, recentNames, customFoods, recentCached) {
        typeFoods.ifEmpty { recentNames.mapNotNull { n -> UserSettings.findFood(n) ?: recentCached.firstOrNull { it.name == n } } }
    }
    val recentsLabel = when {
        typeFoods.isEmpty() -> tr("Usati di recente")
        MealTime.isOther(mealType) -> tr("Già mangiati negli spuntini")
        else -> tr("Già mangiati a %s", MealTime.label(mealType).lowercase(I18n.locale))
    }

    // Quantità proposta: l'ultima registrata, altrimenti una porzione media, altrimenti 100 g
    fun defaultGrams(food: Food): Int = lastGrams[food.name] ?: FoodPortions.of(food).firstOrNull()?.grams ?: 100
    // Il + aggiunge al volo solo se la quantità ha senso (già usata o porzione nota); se no apre la scelta
    fun canQuickAdd(food: Food): Boolean = lastGrams[food.name] != null || FoodPortions.of(food).isNotEmpty()

    fun openQuantity(food: Food) {
        editing = QuantityTarget(food, defaultGrams(food))
    }

    // Avviso "Aggiunto · Modifica" dopo un'aggiunta con il +: resta qualche secondo
    var lastAdded by remember { mutableStateOf<Int?>(null) }
    var addedTick by remember { mutableStateOf(0) }
    LaunchedEffect(addedTick) {
        if (addedTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(3500)
        lastAdded = null
    }

    fun afterAdd() {
        // Pronto per il prossimo: ricerca vuota e tastiera di nuovo sulla ricerca
        if (query.isNotBlank()) focusTick++
        query = ""
        online = OnlineState.Idle
    }

    fun quickAdd(food: Food) {
        if (!canQuickAdd(food)) { openQuantity(food); return }
        haptics.tick()
        staged.add(StagedFood(food, defaultGrams(food)))
        lastAdded = staged.lastIndex
        addedTick++
        afterAdd()
    }

    fun applyQuantity(target: QuantityTarget, grams: Int) {
        haptics.tick()
        val i = target.index
        if (i != null && i in staged.indices) {
            staged[i] = staged[i].copy(grams = grams)
        } else {
            staged.add(StagedFood(target.food, grams))
            afterAdd()
        }
        editing = null
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
                openQuantity(local)
                return@launch
            }
            OpenFoodFacts.byBarcode(code).fold(
                onSuccess = { food ->
                    if (food == null) {
                        scanMessage = tr("Prodotto %s non trovato o senza valori nutrizionali. Puoi crearlo a mano.", code)
                    } else {
                        scannedFood = food; scanMessage = null
                        openQuantity(food)
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
    val blank = query.isBlank()

    // Pasti fissi: in cima alla ricerca (filtrati per nome), si caricano nel pasto e restano modificabili
    val savedMeals = UserSettings.savedMeals
    val shownTemplates = remember(query, savedMeals) { SavedMeal.search(savedMeals, query) }
    // Nome del pasto fisso caricato: proposto quando lo salvi di nuovo (così lo aggiorni)
    var loadedTemplate by rememberSaveable { mutableStateOf<String?>(null) }
    var savingTemplate by remember { mutableStateOf(false) }
    var deletingTemplate by remember { mutableStateOf<SavedMeal?>(null) }

    val totalKcal = staged.sumOf { it.meal.calories }
    // Grammi già nel pasto per ogni alimento: la riga lo segna con "✓"
    val inMeal = staged.groupBy { it.food.name }.mapValues { (_, l) -> l.sumOf { it.grams } }

    fun loadTemplate(m: SavedMeal) {
        haptics.tick()
        staged.addAll(m.items.map { StagedFood(it.food, it.grams) })
        loadedTemplate = m.name
        afterAdd()
    }

    @Composable
    fun FoodItem(food: Food) = FoodRow(
        food = food,
        inMealGrams = inMeal[food.name],
        onClick = { openQuantity(food) },
        onAdd = { quickAdd(food) }
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
            initialName = loadedTemplate ?: MealTime.label(mealType),
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
    if (choosingType) {
        UnicumDialog(onDismiss = { choosingType = false }) {
            Text(tr("Tipo di pasto"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
            MealTypeInput(mealType, {
                mealType = it
                if (!isToday && !minuteTouched) minute = MealTime.typical(it)
            })
            UnicumButton(tr("Fatto"), onClick = { choosingType = false })
        }
    }
    editing?.let { target ->
        QuantitySheet(
            target = target,
            onDismiss = { editing = null },
            onConfirm = { applyQuantity(target, it) },
            onRemove = target.index?.let { i -> { if (i in staged.indices) staged.removeAt(i); editing = null } }
        )
    }
    if (reviewing && staged.isNotEmpty()) {
        val current = staged.map { DraftItem(it.food, it.grams) }
        val savedAs = savedMeals.firstOrNull { it.name.equals(loadedTemplate, true) }
        MealReviewSheet(
            staged = staged,
            mealLabel = MealTime.label(mealType) + " · " + MealTime.format(minute),
            onDismiss = { reviewing = false },
            onEdit = { i -> editing = QuantityTarget(staged[i].food, staged[i].grams, i) },
            onGrams = { i, g -> staged[i] = staged[i].copy(grams = g) },
            onRemove = { i -> staged.removeAt(i) },
            templateLabel = when {
                savedAs != null && savedAs.items == current -> tr("Pasto fisso “%s”", savedAs.name)
                savedAs != null -> tr("Aggiorna il pasto fisso “%s”", savedAs.name)
                else -> tr("Salva come pasto fisso")
            },
            templateSaved = savedAs != null && savedAs.items == current,
            onSaveTemplate = { savingTemplate = true },
            onSave = { reviewing = false; saveMeal() }
        )
    }

    ModalBottomSheet(
        onDismissRequest = ::requestClose,
        sheetState = sheetState,
        containerColor = UnicumBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = UnicumAccent) }
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = 12.dp)) {

                // INTESTAZIONE su una riga: tipo di pasto (si cambia toccandolo), orario, giorno se non è oggi
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MealTypePill(MealTime.label(mealType), onClick = { choosingType = true })
                    Spacer(Modifier.width(8.dp))
                    MealTimePill(minute, { minute = it; minuteTouched = true })
                    if (!isToday) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            DateUtils.parse(date).let { "${it.dayOfMonth}/${it.monthValue}" },
                            color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = ::requestClose) {
                        Icon(Icons.Rounded.Close, tr("Chiudi"), tint = UnicumDark)
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (creating) {
                    CreateFoodForm(
                        initialName = query.trim(),
                        onCancel = { creating = false },
                        onCreated = { food ->
                            UserSettings.addCustomFood(food)
                            creating = false
                            editing = QuantityTarget(food, 100)
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    OutlinedTextField(
                        textStyle = unicumFieldTextStyle(),
                        value = query,
                        onValueChange = {
                            query = it
                            online = OnlineState.Idle
                        },
                        placeholder = { Text(tr("Cerca un alimento"), maxLines = 1) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = UnicumPrimary) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = ""; online = OnlineState.Idle }) {
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
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                    )

                    Spacer(Modifier.height(10.dp))

                    // La lista e, sopra il suo fondo, l'avviso "Aggiunto": l'avviso non toglie spazio alla lista
                    Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
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
                                FoodItem(f)
                            }
                        }
                        // A ricerca vuota: prima quello che mangi di solito, poi i pasti salvati e i tuoi alimenti
                        if (blank && recents.isNotEmpty()) {
                            item(key = "r_head") { ListLabel(recentsLabel) }
                            items(recents, key = { "r_" + it.name }) { food -> FoodItem(food) }
                        }
                        if (shownTemplates.isNotEmpty()) {
                            item(key = "t_head") { ListLabel(tr("Pasti salvati")) }
                            items(shownTemplates, key = { "t_" + it.name }) { m ->
                                SavedMealRow(m, onAdd = { loadTemplate(m) }, onDelete = { deletingTemplate = m })
                            }
                        }
                        if (blank && customFoods.isNotEmpty()) {
                            item(key = "c_head") { ListLabel(tr("I tuoi alimenti")) }
                            items(customFoods, key = { "c_" + it.name }) { food -> FoodItem(food) }
                        }
                        if ((blank && (recents.isNotEmpty() || customFoods.isNotEmpty())) || shownTemplates.isNotEmpty()) {
                            item(key = "a_head") { ListLabel(if (blank) tr("Tutti gli alimenti") else tr("Alimenti")) }
                        }

                        items(results, key = { "a_" + it.name }) { food -> FoodItem(food) }

                        if (results.isEmpty()) {
                            item(key = "empty") {
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
                                items(done.foods, key = { "o_" + (it.barcode ?: it.name) }) { food -> FoodItem(food) }
                            }
                        }

                        item(key = "create") {
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
                    // Appena aggiunto con il +: si può correggere la quantità
                    val added = lastAdded?.takeIf { it in staged.indices }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = added != null,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        val item = added?.let { staged.getOrNull(it) }
                        if (item != null) {
                            AddedToast(
                                text = tr("Aggiunto: %s · %d g", io.github.peol13.unicum.data.FoodNames.label(item.food.name), item.grams),
                                onEdit = {
                                    editing = QuantityTarget(item.food, item.grams, added)
                                    lastAdded = null
                                }
                            )
                        }
                    }
                    }
                }

                // IL PASTO: una barra bassa con il totale e l'unico tasto Salva; toccandola si apre il riepilogo
                if (staged.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    MealBar(
                        count = staged.size,
                        kcal = totalKcal,
                        meals = staged.map { it.meal },
                        onOpen = { reviewing = true },
                        onSave = ::saveMeal
                    )
                }
            }
        }
    }
}

/** Tipo di pasto in alto: pillola piena con la freccia, toccandola si cambia. */
@Composable
private fun MealTypePill(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(UnicumPrimary)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Restaurant, null, tint = UnicumOnPrimary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label, color = UnicumOnPrimary, style = UnicumType.Body, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)
        )
        Icon(Icons.Rounded.ExpandMore, tr("Tipo di pasto"), tint = UnicumOnPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Avviso dopo un'aggiunta con il +: "Aggiunto: Mela · 180 g" e "Modifica". */
@Composable
private fun AddedToast(text: String, onEdit: () -> Unit) {
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(UnicumDark)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, color = UnicumBackground, style = UnicumType.Detail, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onEdit) {
            Text(tr("Modifica"), color = UnicumAccent, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Barra del pasto in costruzione: numero di alimenti, kcal e macro; a destra Salva. */
@Composable
private fun MealBar(count: Int, kcal: Int, meals: List<Meal>, onOpen: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(UnicumPrimary)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    trp(count, "%d alimento", "%d alimenti") + " · ${lnum(kcal)} kcal",
                    color = UnicumOnPrimary, style = UnicumType.Body, fontWeight = FontWeight.SemiBold, maxLines = 1
                )
                Text(
                    "${tr("P")} ${fmtGrams(meals.sumOf { it.protein.toDouble() }.toFloat())} · " +
                        "${tr("C")} ${fmtGrams(meals.sumOf { it.carbohydrates.toDouble() }.toFloat())} · " +
                        "${tr("G")} ${fmtGrams(meals.sumOf { it.fats.toDouble() }.toFloat())} · " + tr("Vedi e modifica"),
                    color = UnicumOnPrimary.copy(alpha = .8f), style = UnicumType.Note, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.ExpandLess, tr("Vedi e modifica"), tint = UnicumOnPrimary)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(UnicumOnPrimary)
                .clickable(onClick = onSave)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(tr("Salva"), color = UnicumPrimary, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Quantità di un alimento in un foglio a parte, sempre visibile per intero: porzioni o grammi, riepilogo
 * e il tasto in fondo. Per un alimento già nel pasto il tasto aggiorna e c'è "Togli dal pasto".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuantitySheet(target: QuantityTarget, onDismiss: () -> Unit, onConfirm: (Int) -> Unit, onRemove: (() -> Unit)?) {
    val food = target.food
    var gramsText by remember(target) { mutableStateOf(target.grams.toString()) }
    val grams = gramsText.toIntOrNull() ?: 0
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = UnicumBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = UnicumAccent) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(foodCategoryIcon(food.category), size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(io.github.peol13.unicum.data.FoodNames.label(food.name), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${lnum(food.caloriesPer100g)} kcal · ${tr("P")} ${fmtGrams(food.proteinPer100g)} · " +
                            "${tr("C")} ${fmtGrams(food.carbsPer100g)} · ${tr("G")} ${fmtGrams(food.fatsPer100g)}  " + tr("per 100 g"),
                        color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, tr("Chiudi"), tint = UnicumDark) }
            }
            Spacer(Modifier.height(16.dp))
            val portions = remember(food.name) { FoodPortions.of(food) }
            if (portions.isEmpty()) GramsInput(gramsText, { gramsText = it })
            else PortionOrGramsInput(portions, gramsText, { gramsText = it })
            Spacer(Modifier.height(12.dp))
            val portion = food.portion(grams)
            NutritionSummary(portion.calories, portion.protein, portion.carbohydrates, portion.fats)
            Spacer(Modifier.height(14.dp))
            UnicumButton(
                if (target.index == null) tr("Aggiungi al pasto") else tr("Aggiorna"),
                onClick = { onConfirm(grams) }, enabled = grams > 0
            )
            if (onRemove != null) {
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Togli dal pasto"), color = DeleteRed, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Riepilogo del pasto in costruzione: tutti gli alimenti con − / +, tocco = quantità, X = togli; poi Salva. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealReviewSheet(
    staged: List<StagedFood>,
    mealLabel: String,
    onDismiss: () -> Unit,
    onEdit: (Int) -> Unit,
    onGrams: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    templateLabel: String,
    templateSaved: Boolean,
    onSaveTemplate: () -> Unit,
    onSave: () -> Unit
) {
    val meals = staged.map { it.meal }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = UnicumBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = UnicumAccent) }
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Il tuo pasto"), color = UnicumDark, style = UnicumType.Heading, fontWeight = FontWeight.SemiBold)
                    Text(mealLabel, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Detail)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, tr("Chiudi"), tint = UnicumDark) }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                staged.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(UnicumAccent.copy(alpha = .3f)))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onEdit(i) }.padding(vertical = 4.dp)) {
                            Text(io.github.peol13.unicum.data.FoodNames.label(item.food.name), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${lnum(item.meal.calories)} kcal", color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note)
                        }
                        StepButton(add = false, size = 34.dp) { if (item.grams > 10) onGrams(i, item.grams - 10) }
                        Text(
                            "${item.grams} g", color = UnicumDark, style = UnicumType.Detail, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, modifier = Modifier.width(58.dp).clickable { onEdit(i) }
                        )
                        StepButton(add = true, size = 34.dp) { onGrams(i, item.grams + 10) }
                        IconButton(onClick = { onRemove(i) }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.Close, tr("Rimuovi"), tint = DeleteRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            NutritionSummary(
                meals.sumOf { it.calories },
                meals.sumOf { it.protein.toDouble() }.toFloat(),
                meals.sumOf { it.carbohydrates.toDouble() }.toFloat(),
                meals.sumOf { it.fats.toDouble() }.toFloat()
            )
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSaveTemplate)
                    .padding(vertical = 8.dp, horizontal = 2.dp),
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
            Spacer(Modifier.height(8.dp))
            UnicumButton(
                trp(staged.size, "Salva %d alimento", "Salva %d alimenti") + " · ${lnum(meals.sumOf { it.calories })} kcal",
                onClick = onSave
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Aggiungi altri alimenti"), color = UnicumPrimary, fontWeight = FontWeight.SemiBold)
            }
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
                meal.items.joinToString(", ") { "${io.github.peol13.unicum.data.FoodNames.label(it.food.name)} ${it.grams} g" },
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

/**
 * Riga di un alimento: kcal e macro per 100 g (o "✓ Nel pasto: 80 g" se è già nel pasto).
 * Tocco sulla riga = scegli la quantità; + = aggiungi subito la quantità abituale.
 */
@Composable
private fun FoodRow(
    food: Food,
    inMealGrams: Int?,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(UnicumSurface)
            .border(1.dp, if (inMealGrams != null) UnicumPrimary.copy(alpha = .6f) else UnicumAccent.copy(alpha = .4f), shape)
            .clickable { onClick() }
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(foodCategoryIcon(food.category), size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(io.github.peol13.unicum.data.FoodNames.label(food.name), color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            if (inMealGrams != null) {
                Text(
                    "✓ " + tr("Nel pasto: %d g", inMealGrams),
                    color = UnicumPrimary, style = UnicumType.Note, fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    "${tr("P")} ${fmtGrams(food.proteinPer100g)} · ${tr("C")} ${fmtGrams(food.carbsPer100g)} · ${tr("G")} ${fmtGrams(food.fatsPer100g)}" +
                        (food.fiberPer100g?.let { " · ${tr("F")} ${fmtGrams(it)}" } ?: "") + "  " + tr("per 100 g"),
                    color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(lnum(food.caloriesPer100g), color = UnicumDark, style = UnicumType.Emphasis, fontWeight = FontWeight.SemiBold)
            Text("kcal", color = UnicumDark.copy(alpha = .5f), style = UnicumType.Micro)
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(UnicumAccent.copy(alpha = .35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, tr("Aggiungi al pasto"), tint = UnicumPrimary, modifier = Modifier.size(18.dp))
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
