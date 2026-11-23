package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tk

import io.github.peol13.unicum.i18n.tr

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** Le tre schermate principali tra cui l'utente può scegliere dalle impostazioni. */
enum class DashboardMode(private val labelIt: String, private val descriptionIt: String) {
    NUTRIZIONE(tk("Nutrizione"), tk("Calorie e macronutrienti in primo piano")),
    ATTIVITA(tk("Attività"), tk("Movimento e allenamenti in primo piano")),
    RECUPERO(tk("Recupero"), tk("Sonno e recupero in primo piano"));

    val label: String get() = tr(labelIt)
    val description: String get() = tr(descriptionIt)
}

/** Livello di attività abituale: serve SOLO a stimare l'obiettivo calorico, non cambia con i movimenti del giorno. */
enum class ActivityLevel(private val labelIt: String, private val descriptionIt: String, val factor: Float) {
    SEDENTARIO(tk("Sedentario"), tk("Poco o nessun movimento, lavoro da seduto"), 1.2f),
    LEGGERO(tk("Leggero"), tk("Cammini o ti alleni 1–3 volte a settimana"), 1.375f),
    MODERATO(tk("Moderato"), tk("Ti alleni 3–5 volte a settimana"), 1.55f),
    MOLTO_ATTIVO(tk("Molto attivo"), tk("Allenamenti intensi quasi ogni giorno"), 1.725f);

    val label: String get() = tr(labelIt)
    val description: String get() = tr(descriptionIt)
}

/**
 * Un valore che di default è stimato ma che l'utente può sovrascrivere a mano
 * (metabolismo basale, obiettivo calorico). Un valore a mano non viene ricalcolato da solo.
 */
class OverridableValue(defaultManual: Int) {
    var isManual by mutableStateOf(false)
        private set
    var manualValue by mutableStateOf(defaultManual)
        private set

    fun load(manual: Boolean, value: Int) {
        isManual = manual
        manualValue = value
    }

    /** [manual] null = torna alla stima automatica. */
    fun set(manual: Int?, range: IntRange) {
        isManual = manual != null
        if (manual != null) manualValue = manual.coerceIn(range)
    }

    fun resolve(estimate: Int): Int = if (isManual) manualValue else estimate
}

/** Aspetto dell'app: segue il sistema oppure forzato chiaro/scuro. */
enum class ThemeMode(private val labelIt: String) {
    SISTEMA(tk("Come il sistema")),
    CHIARO(tk("Chiaro")),
    SCURO(tk("Scuro"));

    val label: String get() = tr(labelIt)
}

/**
 * Impostazioni e profilo dell'utente, salvati in SharedPreferences.
 * Lo stato è osservabile da Compose: basta leggere le proprietà dentro un @Composable.
 */
object UserSettings {
    private const val PREFS = "unicum_settings"

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private var initialized = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Primo avvio completato (schermata di benvenuto con i dati personali). */
    var onboarded by mutableStateOf(false)
        private set

    var age by mutableStateOf(30)
        private set
    var isMale by mutableStateOf(true)
        private set

    // Metabolismo basale: stimato da peso, altezza, età, sesso oppure inserito dall'utente
    private val bmrSetting = OverridableValue(1800)
    val bmrIsManual: Boolean get() = bmrSetting.isManual
    val manualBmr: Int get() = bmrSetting.manualValue

    // Obiettivo calorico giornaliero: stimato oppure inserito dall'utente. È FISSO: non dipende dall'attività del giorno.
    private val intakeSetting = OverridableValue(2000)
    val intakeIsManual: Boolean get() = intakeSetting.isManual
    val manualIntake: Int get() = intakeSetting.manualValue

    /** Livello di attività abituale, usato solo per stimare l'obiettivo calorico. */
    var activityLevel by mutableStateOf(ActivityLevel.LEGGERO)
        private set

    /** Metabolismo basale stimato con i dati personali salvati. */
    val estimatedBmr: Int get() = estimateBmr(isMale, weightKg, heightCm, age)

    /** Metabolismo basale in uso (kcal consumate a riposo in 24 ore, prima degli allenamenti). */
    val dailyBurnKcal: Int get() = bmrSetting.resolve(estimatedBmr)

    /** Deficit calorico che l'utente vuole raggiungere ogni giorno (0 = mantenimento). */
    var targetDeficitKcal by mutableStateOf(500)
        private set

    var weightKg by mutableStateOf(72)
        private set
    var heightCm by mutableStateOf(175)
        private set

    // Ripartizione delle calorie obiettivo tra i macronutrienti, in percentuale (somma 100)
    var proteinPct by mutableStateOf(DEFAULT_PROTEIN_PCT)
        private set
    var carbsPct by mutableStateOf(DEFAULT_CARBS_PCT)
        private set
    var fatsPct by mutableStateOf(DEFAULT_FATS_PCT)
        private set

    var themeMode by mutableStateOf(ThemeMode.SISTEMA)
        private set

    var dashboardMode by mutableStateOf(DashboardMode.NUTRIZIONE)
        private set

    /** Ordine delle card scelto a mano; null = quello della modalità ([HomeLayout.default]). */
    var homeLayout by mutableStateOf<List<HomeCard>?>(null)
        private set

    /** Le card della Home, nell'ordine in cui compaiono. */
    val homeCards: List<HomeCard> get() = homeLayout ?: HomeLayout.default(dashboardMode)

    /** Quando è avvenuta l'ultima sincronizzazione riuscita con Health Connect (0 = mai). */
    var lastSyncMillis by mutableStateOf(0L)
        private set

    var customFoods by mutableStateOf<List<Food>>(emptyList())
        private set

    var recentFoodNames by mutableStateOf<List<String>>(emptyList())
        private set

    /** Pasti fissi salvati (es. la colazione di sempre), dal più recente. */
    var savedMeals by mutableStateOf<List<SavedMeal>>(emptyList())
        private set

    /** Lunedì (ISO) dell'ultima settimana di cui è stato aperto il racconto: l'invito nella Home sparisce. */
    var weekStorySeen by mutableStateOf<String?>(null)
        private set

    /** Ultima versione di cui si è vista la finestra delle novità (null = mai: chi aggiorna da 1.0.x la vede). */
    var whatsNewSeen by mutableStateOf<String?>(null)
        private set

    fun markWhatsNewSeen(version: String) {
        whatsNewSeen = version
        prefs.edit().putString("whats_new_seen", version).apply()
    }

    /**
     * Giorni segnati come "giornata completa" (yyyy-MM-dd): hai registrato tutto quello che hai mangiato.
     * Appena ne segni uno, medie del deficit e peso stimato contano solo i giorni completi.
     */
    var completeDays by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Usa le giornate complete (almeno un giorno segnato): le medie ignorano i giorni non completi. */
    val usesCompleteDays: Boolean get() = completeDays.isNotEmpty()

    fun isDayComplete(date: String): Boolean = date in completeDays

    fun setDayComplete(date: String, complete: Boolean) {
        completeDays = if (complete) completeDays + date else completeDays - date
        prefs.edit().putStringSet("complete_days", completeDays).apply()
    }

    /** La card "Primi passi" della Home è stata nascosta a mano. */
    var firstStepsHidden by mutableStateOf(false)
        private set

    /** Metabolismo basale stimato con Mifflin-St Jeor (la formula più usata per adulti sani). */
    fun estimateBmr(male: Boolean, weightKg: Int, heightCm: Int, age: Int): Int =
        (10f * weightKg + 6.25f * heightCm - 5f * age + if (male) 5f else -161f).roundToInt()

    /** Minimo di sicurezza per l'obiettivo calorico stimato (1500 kcal uomo, 1200 donna). */
    fun minimumIntake(male: Boolean): Int = if (male) 1500 else 1200

    /**
     * Obiettivo calorico stimato: metabolismo basale × livello di attività abituale − deficit scelto.
     * Non usa mai i movimenti del giorno: l'attività aumenta il deficit, non le calorie da mangiare.
     */
    fun estimateIntakeGoal(bmr: Int, level: ActivityLevel, deficit: Int, male: Boolean): Int =
        maxOf((bmr * level.factor).roundToInt() - deficit, minimumIntake(male))

    val estimatedIntakeGoal: Int get() = estimateIntakeGoal(dailyBurnKcal, activityLevel, targetDeficitKcal, isMale)

    /** Calorie da mangiare ogni giorno: valore fisso (stimato o inserito a mano). */
    val intakeGoalKcal: Int get() = intakeSetting.resolve(estimatedIntakeGoal)

    // Grammi obiettivo: 4 kcal/g per proteine e carboidrati, 9 kcal/g per i grassi
    val proteinGoal: Int get() = gramsFor(intakeGoalKcal, proteinPct, 4)
    val carbsGoal: Int get() = gramsFor(intakeGoalKcal, carbsPct, 4)
    val fatsGoal: Int get() = gramsFor(intakeGoalKcal, fatsPct, 9)

    const val DEFAULT_PROTEIN_PCT = 25
    const val DEFAULT_CARBS_PCT = 45
    const val DEFAULT_FATS_PCT = 30

    fun gramsFor(kcal: Int, percent: Int, kcalPerGram: Int): Int =
        (kcal * percent / 100f / kcalPerGram).roundToInt()

    /**
     * Calorie già consumate a riposo fino a questo momento: il fabbisogno giornaliero
     * matura in modo uniforme dalla mezzanotte, quindi alle 12 ne è passata metà.
     */
    fun restingBurnSoFar(nowMillis: Long = System.currentTimeMillis()): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val minutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return (dailyBurnKcal * minutes / 1440f).roundToInt()
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        // I valori personali di chi compila (personal.properties) vanno scritti prima di leggere qualsiasi preferenza
        PersonalSeed.apply(appContext)
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Chi aveva già salvato un consumo a riposo lo mantiene come valore manuale
        val legacyBurn = if (prefs.contains("burn")) prefs.getInt("burn", 2000) else null
        bmrSetting.load(prefs.getBoolean("bmr_manual", legacyBurn != null), prefs.getInt("bmr_value", legacyBurn ?: 1800))
        intakeSetting.load(prefs.getBoolean("intake_manual", false), prefs.getInt("intake_value", 2000))
        activityLevel = runCatching {
            ActivityLevel.valueOf(prefs.getString("activity_level", null) ?: "")
        }.getOrDefault(ActivityLevel.LEGGERO)
        age = prefs.getInt("age", 30)
        isMale = prefs.getBoolean("male", true)
        lastSyncMillis = prefs.getLong("last_sync", 0L)
        onboarded = prefs.getBoolean("onboarded", legacyBurn != null)
        targetDeficitKcal = prefs.getInt("deficit", 500)
        weightKg = prefs.getInt("weight", 72)
        heightCm = prefs.getInt("height", 175)
        // Le vecchie impostazioni in grammi (p_goal, c_goal, f_goal) non valgono più: ora sono percentuali
        val p = prefs.getInt("p_pct", DEFAULT_PROTEIN_PCT)
        val c = prefs.getInt("c_pct", DEFAULT_CARBS_PCT)
        val f = prefs.getInt("f_pct", DEFAULT_FATS_PCT)
        if (p + c + f == 100) {
            proteinPct = p; carbsPct = c; fatsPct = f
        }
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "")
        }.getOrDefault(ThemeMode.SISTEMA)
        dashboardMode = runCatching {
            DashboardMode.valueOf(prefs.getString("dashboard_mode", null) ?: "")
        }.getOrDefault(DashboardMode.NUTRIZIONE)

        // Gli alimenti personali stanno nel database: si caricano in memoria dopo l'avvio (onCustomFoodsLoaded)
        recentFoodNames = (prefs.getString("recent_foods", "") ?: "")
            .split("\n").filter { it.isNotBlank() }
        savedMeals = runCatching {
            Json.decodeFromString<List<SavedMeal>>(prefs.getString("saved_meals", null) ?: "[]")
        }.getOrDefault(emptyList())
        weekStorySeen = prefs.getString("week_story_seen", null)
        firstStepsHidden = prefs.getBoolean("first_steps_hidden", false)
        whatsNewSeen = prefs.getString("whats_new_seen", null)
        completeDays = prefs.getStringSet("complete_days", emptySet())?.toSet() ?: emptySet()
        homeLayout = HomeLayout.decode(prefs.getString("home_layout", null))
        hrvFromManual = prefs.getString("hrv_from_manual", null)?.let(::parseIsoDate)
        hrvFromAuto = prefs.getString("hrv_from_auto", null)?.let(::parseIsoDate)
        HrvData.reliableFrom = hrvReliableFrom
    }

    private fun parseIsoDate(text: String) = runCatching { java.time.LocalDate.parse(text) }.getOrNull()

    /** Da quando l'HRV è confrontabile, scelto a mano nel Profilo (null = automatico, vedi [hrvFromAuto]). */
    var hrvFromManual by mutableStateOf<java.time.LocalDate?>(null)
        private set
    /** Il salto di scala dell'HRV trovato da solo ([HrvData.detectShift]); null = nessun salto. */
    var hrvFromAuto by mutableStateOf<java.time.LocalDate?>(null)
        private set
    /** La data che vale: quella a mano se c'è, altrimenti quella trovata da sola. */
    val hrvReliableFrom: java.time.LocalDate? get() = hrvFromManual ?: hrvFromAuto

    /** [date] null = torna al riconoscimento automatico. */
    fun chooseHrvFrom(date: java.time.LocalDate?) {
        hrvFromManual = date
        HrvData.reliableFrom = hrvReliableFrom
        prefs.edit().putString("hrv_from_manual", date?.toString()).apply()
    }

    /** Dopo ogni sincronizzazione: il salto trovato nella serie (non tocca una data scelta a mano). */
    fun updateHrvAutoShift(date: java.time.LocalDate?) {
        if (date == hrvFromAuto) return
        hrvFromAuto = date
        HrvData.reliableFrom = hrvReliableFrom
        prefs.edit().putString("hrv_from_auto", date?.toString()).apply()
    }

    /** Salva l'ordine delle card della Home; null = torna a quello della modalità. */
    fun saveHomeLayout(cards: List<HomeCard>?) {
        homeLayout = cards?.takeIf { it.isNotEmpty() }
        prefs.edit().putString("home_layout", homeLayout?.let { HomeLayout.encode(it) }).apply()
    }

    fun hideFirstSteps() {
        firstStepsHidden = true
        prefs.edit().putBoolean("first_steps_hidden", true).apply()
    }

    fun markWeekStorySeen(monday: String) {
        weekStorySeen = monday
        prefs.edit().putString("week_story_seen", monday).apply()
    }

    fun saveProfile(
        weight: Int,
        height: Int,
        age: Int,
        male: Boolean,
        manualBmr: Int?,        // null = stima automatica
        activityLevel: ActivityLevel,
        manualIntake: Int?,     // null = stima automatica
        deficitKcal: Int,
        proteinPercent: Int,
        carbsPercent: Int,
        fatsPercent: Int
    ) {
        weightKg = weight.coerceIn(30, 250)
        heightCm = height.coerceIn(120, 230)
        this.age = age.coerceIn(14, 100)
        isMale = male
        bmrSetting.set(manualBmr, 800..4500)
        intakeSetting.set(manualIntake, 800..6000)
        this.activityLevel = activityLevel
        targetDeficitKcal = deficitKcal.coerceIn(0, 1500)
        // Una ripartizione che non fa 100 viene ignorata e si tiene quella precedente
        if (proteinPercent + carbsPercent + fatsPercent == 100 &&
            minOf(proteinPercent, carbsPercent, fatsPercent) >= 0
        ) {
            proteinPct = proteinPercent
            carbsPct = carbsPercent
            fatsPct = fatsPercent
        }
        prefs.edit()
            .putInt("weight", weightKg)
            .putInt("height", heightCm)
            .putInt("age", this.age)
            .putBoolean("male", isMale)
            .putBoolean("bmr_manual", bmrSetting.isManual)
            .putInt("bmr_value", bmrSetting.manualValue)
            .putBoolean("intake_manual", intakeSetting.isManual)
            .putInt("intake_value", intakeSetting.manualValue)
            .putString("activity_level", this.activityLevel.name)
            .putInt("deficit", targetDeficitKcal)
            .putInt("p_pct", proteinPct)
            .putInt("c_pct", carbsPct)
            .putInt("f_pct", fatsPct)
            .remove("p_goal").remove("c_goal").remove("f_goal")
            .remove("burn")
            .apply()
    }

    /** true finché non è stata fatta la risincronizzazione di 30 giorni che riempie le fasi del sonno. */
    val needsStagesResync: Boolean get() = !prefs.getBoolean("stages_resync_v3", false)

    fun markStagesResyncDone() {
        prefs.edit().putBoolean("stages_resync_v3", true).apply()
    }

    /**
     * true finché sonno, HRV e metriche non sono stati scaricati per un anno intero (una volta sola: su un telefono
     * nuovo o dopo un ripristino il database ha solo quello che arriva dalle sincronizzazioni).
     */
    val needsHealthHistory: Boolean get() = !prefs.getBoolean("health_history_v1", false)

    fun markHealthHistoryDone() {
        prefs.edit().putBoolean("health_history_v1", true).apply()
    }

    /** true finché all'avvio non è stato chiesto il permesso delle notifiche (Android 13+): si chiede una volta sola. */
    val needsNotificationPrompt: Boolean get() = !prefs.getBoolean("notifications_asked_v1", false)

    fun markNotificationPromptDone() {
        prefs.edit().putBoolean("notifications_asked_v1", true).apply()
    }

    /** true finché non è stato letto lo storico degli allenamenti (un anno) per la sezione Allenamenti. */
    val needsWorkoutHistory: Boolean get() = !prefs.getBoolean("workout_history_v8", false)

    fun markWorkoutHistoryDone() {
        prefs.edit().putBoolean("workout_history_v8", true).apply()
    }

    /** Sessioni di Health Connect scartate perché copie di un'altra (es. da Health Sync): non si rileggono. */
    val duplicateSessionIds: Set<String> get() = prefs.getStringSet("duplicate_sessions", emptySet()).orEmpty()

    fun addDuplicateSessionIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet("duplicate_sessions", (duplicateSessionIds + ids).toSet()).apply()
    }

    fun markSynced() {
        lastSyncMillis = System.currentTimeMillis()
        prefs.edit().putLong("last_sync", lastSyncMillis).apply()
    }

    fun completeOnboarding() {
        onboarded = true
        prefs.edit().putBoolean("onboarded", true).apply()
    }

    fun chooseThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    /** La modalità è un punto di partenza: sceglierla rimette l'ordine delle card di quella modalità. */
    fun chooseDashboardMode(mode: DashboardMode) {
        dashboardMode = mode
        homeLayout = null
        prefs.edit().putString("dashboard_mode", mode.name).remove("home_layout").apply()
    }

    /** Bozza del pasto in inserimento (JSON), così non si perde se la schermata si chiude per sbaglio. */
    var mealDraftJson: String?
        get() = if (initialized) prefs.getString("meal_draft", null) else null
        set(value) {
            if (!initialized) return
            prefs.edit().apply { if (value == null) remove("meal_draft") else putString("meal_draft", value) }.apply()
        }

    /** Mette in memoria gli alimenti personali letti dal database (dopo l'avvio). */
    fun onCustomFoodsLoaded(foods: List<Food>) {
        customFoods = foods
    }

    fun addCustomFood(food: Food) {
        customFoods = (listOf(food) + customFoods.filterNot { it.name.equals(food.name, true) })
        persistCustomFoods(listOf(food))
    }

    private fun persistCustomFoods(foods: List<Food>) {
        if (!::appContext.isInitialized) return
        ioScope.launch { UnicumRepository.saveCustomFoods(appContext, foods) }
    }

    /** Salva un pasto fisso; uno con lo stesso nome viene aggiornato. */
    fun saveMealTemplate(meal: SavedMeal) {
        savedMeals = SavedMeal.upsert(savedMeals, meal)
        prefs.edit().putString("saved_meals", Json.encodeToString(savedMeals)).apply()
    }

    fun deleteMealTemplate(name: String) {
        savedMeals = SavedMeal.remove(savedMeals, name)
        prefs.edit().putString("saved_meals", Json.encodeToString(savedMeals)).apply()
    }

    fun markFoodUsed(names: List<String>) {
        recentFoodNames = (names.reversed() + recentFoodNames).distinct().take(12)
        prefs.edit().putString("recent_foods", recentFoodNames.joinToString("\n")).apply()
    }

    /** Profilo, obiettivi, preferenze e alimenti personali per il backup. */
    fun exportSettings() = BackupSettings(
        onboarded = onboarded, age = age, isMale = isMale, weightKg = weightKg, heightCm = heightCm,
        bmrManual = bmrIsManual, bmrValue = manualBmr, intakeManual = intakeIsManual, intakeValue = manualIntake,
        activityLevel = activityLevel.name, deficitKcal = targetDeficitKcal,
        proteinPct = proteinPct, carbsPct = carbsPct, fatsPct = fatsPct,
        themeMode = themeMode.name, dashboardMode = dashboardMode.name, homeLayout = homeLayout?.let { HomeLayout.encode(it) },
        customFoods = customFoods, recentFoods = recentFoodNames, savedMeals = savedMeals,
        hrvFrom = hrvFromManual?.toString(), completeDays = completeDays.sorted()
    )

    /** Ripristina profilo e preferenze dal backup; alimenti personali e recenti si uniscono a quelli presenti. */
    fun importSettings(s: BackupSettings) {
        saveProfile(
            weight = s.weightKg, height = s.heightCm, age = s.age, male = s.isMale,
            manualBmr = if (s.bmrManual) s.bmrValue else null,
            activityLevel = runCatching { ActivityLevel.valueOf(s.activityLevel) }.getOrDefault(ActivityLevel.LEGGERO),
            manualIntake = if (s.intakeManual) s.intakeValue else null,
            deficitKcal = s.deficitKcal,
            proteinPercent = s.proteinPct, carbsPercent = s.carbsPct, fatsPercent = s.fatsPct
        )
        runCatching { ThemeMode.valueOf(s.themeMode) }.getOrNull()?.let { chooseThemeMode(it) }
        runCatching { DashboardMode.valueOf(s.dashboardMode) }.getOrNull()?.let { chooseDashboardMode(it) }
        HomeLayout.decode(s.homeLayout)?.let { saveHomeLayout(it) }
        s.hrvFrom?.let(::parseIsoDate)?.let { chooseHrvFrom(it) }
        if (s.onboarded) completeOnboarding()
        val mergedFoods = BackupCodec.mergeCustomFoods(customFoods, s.customFoods)
        persistCustomFoods(mergedFoods.filter { f -> customFoods.none { it.name.equals(f.name, true) } })
        customFoods = mergedFoods
        recentFoodNames = BackupCodec.mergeRecent(recentFoodNames, s.recentFoods)
        savedMeals = SavedMeal.merge(savedMeals, s.savedMeals)
        completeDays = completeDays + s.completeDays
        prefs.edit()
            .putStringSet("complete_days", completeDays)
            .putString("saved_meals", Json.encodeToString(savedMeals))
            .putString("recent_foods", recentFoodNames.joinToString("\n"))
            .apply()
    }

    /** Alimento (del catalogo o personalizzato) dato il nome, usato per i "Recenti". */
    fun findFood(name: String): Food? =
        customFoods.firstOrNull { it.name == name } ?: FoodCatalog.defaultFoods.firstOrNull { it.name == name }
}
