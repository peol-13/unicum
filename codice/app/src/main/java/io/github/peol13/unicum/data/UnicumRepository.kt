package io.github.peol13.unicum.data

import io.github.peol13.unicum.data.db.FoodEntity
import io.github.peol13.unicum.data.db.customFoodKey
import io.github.peol13.unicum.data.db.toCustomEntity
import io.github.peol13.unicum.data.db.toFood
import android.content.Context
import android.content.SharedPreferences
import io.github.peol13.unicum.data.db.ActivityEntity
import io.github.peol13.unicum.data.db.DailyMetricEntity
import io.github.peol13.unicum.data.db.SleepEntity
import io.github.peol13.unicum.data.db.MealEntity
import io.github.peol13.unicum.data.db.UnicumDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

data class DaySummary(
    val date: String,
    val kcalIn: Int,
    val activityMinutes: Int,
    val activityKcal: Int
)

object UnicumRepository {
    private const val PREFS_NAME = "unicum_persistence_prefs"
    private const val KEY_MEALS = "key_persisted_meals"
    private const val KEY_ACTIVITIES = "key_persisted_activities"
    private const val KEY_MIGRATED = "key_migrated_to_room"

    fun today(): String = DateUtils.today().toString()

    private fun dao(context: Context) = UnicumDatabase.get(context).dao()

    // ==============================
    // PASTI
    // ==============================

    suspend fun loadMeals(context: Context, date: String): List<Meal> =
        dao(context).mealsForDate(date).first().map { it.toMeal() }.sortedBy { it.minuteOfDay ?: 0 }

    /** Salva i pasti del giorno [date] e restituisce gli id assegnati. */
    suspend fun insertMeals(context: Context, date: String, meals: List<Meal>): List<Long> = withContext(Dispatchers.IO) {
        dao(context).insertMeals(meals.map { it.toEntity(date) })
    }

    /** Aggiorna quantità (con kcal e macro), tipo e orario di un alimento già registrato. */
    suspend fun updateMeal(context: Context, date: String, meal: Meal) = withContext(Dispatchers.IO) {
        dao(context).updateMeal(
            id = meal.id,
            mealType = meal.mealType,
            grams = meal.quantityGrams.toFloat(),
            kcal = meal.calories.toFloat(),
            protein = meal.protein,
            carbs = meal.carbohydrates,
            fats = meal.fats,
            fiber = meal.fiber,
            sugars = meal.sugars,
            salt = meal.salt,
            time = MealTime.toMillis(date, meal.minuteOfDay ?: MealTime.now())
        )
    }

    suspend fun mealEntities(context: Context, ids: List<Long>): List<MealEntity> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList() else dao(context).mealsByIds(ids)
    }

    suspend fun mealEntitiesBetween(context: Context, from: String, to: String): List<MealEntity> =
        withContext(Dispatchers.IO) { dao(context).mealsBetween(from, to).first() }

    suspend fun deleteMeal(context: Context, id: Long) = withContext(Dispatchers.IO) {
        dao(context).deleteMealById(id)
    }

    // ==============================
    // ATTIVITÀ
    // ==============================

    suspend fun loadActivities(context: Context, date: String): List<Activity> =
        dao(context).activitiesForDate(date).first().map { it.toActivity() }

    suspend fun insertActivity(context: Context, date: String, activity: Activity) =
        insertActivities(context, date, listOf(activity))

    suspend fun insertActivities(context: Context, date: String, activities: List<Activity>) = withContext(Dispatchers.IO) {
        dao(context).insertActivities(activities.map { it.toEntity(date) })
    }

    suspend fun deleteActivity(context: Context, id: Long) = withContext(Dispatchers.IO) {
        dao(context).deleteActivityById(id)
    }

    // ==============================
    // ULTIMI 7 GIORNI (per grafici e trend)
    // ==============================

    /** Le ultime [n] date (formato yyyy-MM-dd) che finiscono con [end], dalla più vecchia a [end]. */
    fun lastDays(n: Int, end: String = today()): List<String> =
        DateUtils.lastDays(n, DateUtils.parse(end))

    /** Riepilogo giornaliero dei 7 giorni che finiscono con [end], dal più vecchio a [end]. */
    suspend fun loadWeek(context: Context, end: String = today()): List<DaySummary> = withContext(Dispatchers.IO) {
        val days = lastDays(7, end)
        val from = days.first()
        val to = days.last()
        val meals = dao(context).mealsBetween(from, to).first().groupBy { it.date }
        val activities = dao(context).activitiesBetween(from, to).first().groupBy { it.date }
        days.map { d ->
            DaySummary(
                date = d,
                kcalIn = meals[d].orEmpty().sumOf { it.kcal.toDouble() }.roundToInt(),
                activityMinutes = activities[d].orEmpty().sumOf { it.durationMinutes },
                activityKcal = activities[d].orEmpty().sumOf { it.kcal }
            )
        }
    }

    // ==============================
    // PRODOTTI ONLINE SALVATI (Open Food Facts)
    // ==============================

    /** Salva in locale i prodotti con codice a barre che l'utente ha usato: così si ritrovano anche senza internet. */
    suspend fun cacheOnlineFoods(context: Context, foods: List<Food>) = withContext(Dispatchers.IO) {
        val rows = foods.filter { it.barcode != null }.map { it.toEntity() }
        if (rows.isNotEmpty()) dao(context).upsertFoods(rows)
    }

    /** Prodotti salvati che contengono tutte le parole cercate (senza maiuscole né accenti). */
    suspend fun searchCachedFoods(context: Context, query: String): List<Food> = withContext(Dispatchers.IO) {
        val words = FoodCatalog.normalize(query).split(" ").filter { it.isNotEmpty() }
        val rows = if (words.isEmpty()) dao(context).recentCachedFoods()
        else dao(context).cachedFoodsLike(words.first().replace("%", "").replace("_", ""))
        rows.map { it.toFood() }
            .filter { f -> val n = FoodCatalog.normalize(f.name); words.all { n.contains(it) } }
            .take(50)
    }

    /** Un prodotto già salvato in locale, cercato per codice a barre. */
    suspend fun cachedFoodByBarcode(context: Context, barcode: String): Food? = withContext(Dispatchers.IO) {
        dao(context).foodById(OpenFoodFacts.ID_PREFIX + barcode)?.toFood()
    }

    suspend fun cachedFoodsByNames(context: Context, names: List<String>): List<Food> = withContext(Dispatchers.IO) {
        if (names.isEmpty()) emptyList() else dao(context).foodsByNames(names).map { it.toFood() }
    }

    /**
     * Gli alimenti già mangiati in questo tipo di pasto, dal più recente. Per un pasto "Altro" con nome
     * vengono prima quelli di quel nome, poi quelli degli altri spuntini.
     * Se l'alimento non è più nel catalogo si ricostruisce dai valori registrati.
     */
    suspend fun foodsUsedIn(context: Context, mealType: String, limit: Int = 12): List<Food> = withContext(Dispatchers.IO) {
        val d = dao(context)
        val type = mealType.trim()
        val rows = d.recentMealsOfType(type) + (if (MealTime.isOther(type)) d.recentOtherMeals() else emptyList())
        val names = rows.map { it.foodName }.distinct().take(limit)
        if (names.isEmpty()) return@withContext emptyList()
        val cached = d.foodsByNames(names).map { it.toFood() }
        names.mapNotNull { n ->
            UserSettings.findFood(n) ?: cached.firstOrNull { it.name == n } ?: rows.first { it.foodName == n }.toFoodGuess()
        }
    }

    /** Tutti gli alimenti già registrati almeno una volta, dal più recente: nella ricerca vengono per primi. */
    suspend fun usedFoods(context: Context): List<Food> = withContext(Dispatchers.IO) {
        val d = dao(context)
        val rows = d.latestEntryPerFood()
        val cached = rows.map { it.foodName }.chunked(500).flatMap { d.foodsByNames(it) }.map { it.toFood() }.associateBy { it.name }
        rows.mapNotNull { r -> UserSettings.findFood(r.foodName) ?: cached[r.foodName] ?: r.toFoodGuess() }
    }

    /** L'ultima quantità in grammi registrata per ogni alimento (nome → grammi). */
    suspend fun lastGrams(context: Context): Map<String, Int> = withContext(Dispatchers.IO) {
        dao(context).latestEntryPerFood().filter { it.grams > 0f }.associate { it.foodName to Math.round(it.grams) }
    }

    /** I nomi propri già dati ai pasti "Altro", dal più recente. */
    suspend fun otherMealNames(context: Context): List<String> = withContext(Dispatchers.IO) {
        dao(context).recentMealTypes().mapNotNull { MealTime.customName(it) }.distinctBy { it.lowercase() }.take(8)
    }

    private fun MealEntity.toFoodGuess(): Food? {
        if (grams <= 0f) return null
        val k = 100f / grams
        return Food(
            name = foodName,
            caloriesPer100g = (kcal * k).roundToInt(),
            proteinPer100g = protein * k,
            carbsPer100g = carbs * k,
            fatsPer100g = fats * k,
            fiberPer100g = fiber?.times(k),
            sugarsPer100g = sugars?.times(k),
            saltPer100g = salt?.times(k)
        )
    }

    // ==============================
    // ALIMENTI PERSONALI
    // ==============================

    /** Gli alimenti creati dall'utente, dal più recente. */
    suspend fun loadCustomFoods(context: Context): List<Food> = withContext(Dispatchers.IO) {
        dao(context).allCustomFoods().map { it.toFood() }
    }

    /**
     * Salva (o aggiorna, a parità di nome) degli alimenti personali. Con [keepExisting] quelli già presenti non
     * si toccano (importazioni, migrazione dalle preferenze); l'ordine di [foods] è dal più recente.
     */
    suspend fun saveCustomFoods(context: Context, foods: List<Food>, keepExisting: Boolean = false) = withContext(Dispatchers.IO) {
        val d = dao(context)
        val known = if (keepExisting) d.customFoodKeys().toHashSet() else emptySet()
        val now = System.currentTimeMillis()
        val rows = foods.filter { it.name.isNotBlank() && customFoodKey(it.name) !in known }
            .distinctBy { customFoodKey(it.name) }
            .mapIndexed { i, f -> f.toCustomEntity(now - i) }   // il primo resta il più recente
        if (rows.isNotEmpty()) d.upsertCustomFoods(rows)
    }

    /**
     * Una tantum: gli alimenti personali che stavano nelle preferenze ([prefs], chiave `custom_foods`) passano nel
     * database. La chiave si toglie solo dopo che i dati sono entrati; se il JSON è illeggibile resta dov'è.
     */
    suspend fun migrateCustomFoodsFromPrefs(
        context: Context,
        prefs: SharedPreferences = context.getSharedPreferences("unicum_settings", Context.MODE_PRIVATE)
    ) = withContext(Dispatchers.IO) {
        val json = prefs.getString("custom_foods", null) ?: return@withContext
        val foods = runCatching { Json.decodeFromString<List<Food>>(json) }.getOrNull() ?: return@withContext
        saveCustomFoods(context, foods, keepExisting = true)
        prefs.edit().remove("custom_foods").commit()
    }

    // ==============================
    // BACKUP
    // ==============================

    /** [includeNotion]: aggiunge token e database di Notion (esportazione a mano se scelto, backup automatico se acceso). */
    suspend fun exportBackup(context: Context, includeNotion: Boolean = false): String = withContext(Dispatchers.IO) {
        val d = dao(context)
        val notion = if (includeNotion) { NotionPlan.init(context); NotionPlan.exportConnection() } else null
        BackupCodec.encode(
            BackupCodec.build(
                // Gli alimenti personali dal database (non dalla memoria: ad app chiusa non sono ancora caricati)
                d.allMeals(), d.allActivities(), d.allFoods(),
                UserSettings.exportSettings().copy(customFoods = d.allCustomFoods().map { it.toFood() }),
                System.currentTimeMillis(),
                Shoes.data, loadWorkoutFeedback(context), localSessions(context), exportReminders(context),
                RunGoalStore.load(context), LocalPlan.load(context), notion
            )
        )
    }

    /** Cosa entrerebbe dal file [file] senza duplicare quanto c'è già. */
    suspend fun planImport(context: Context, file: BackupFile): BackupCodec.Plan = withContext(Dispatchers.IO) {
        val d = dao(context)
        val feedback = BackupCodec.feedbackChanges(file.pending, localSessions(context), loadWorkoutFeedback(context))
        NotionPlan.init(context)
        BackupCodec.plan(file, d.allMeals(), d.allActivities(), d.allFoods().map { it.id }.toSet(), feedback, NotionPlan.connected)
    }

    /** Le sessioni di Health Connect salvate, per abbinare sensazioni e scarpe di un backup. */
    private fun localSessions(context: Context): List<BackupSession> =
        dao(context).allWorkoutSummaries().mapNotNull { decodeDetail(it) }
            .map { BackupSession(it.externalId, it.type, it.source, it.startMillis) }

    private fun exportReminders(context: Context) = BackupReminders(
        minutes = MealReminders.types.associateWith { MealReminders.minuteOf(context, it) ?: -1 },
        recoveryAlerts = RecoveryAlertNotifier.isEnabled(context),
        weeklySummary = WeeklySummary.isEnabled(context)
    )

    private fun pendingPrefs(context: Context) = context.applicationContext.getSharedPreferences("backup_pending", Context.MODE_PRIVATE)

    private fun loadPending(context: Context): BackupPending = runCatching {
        workoutJson.decodeFromString(BackupPending.serializer(), pendingPrefs(context).getString("data", null) ?: "{}")
    }.getOrDefault(BackupPending())

    private fun savePending(context: Context, p: BackupPending) {
        pendingPrefs(context).edit().apply {
            if (p.isEmpty) remove("data") else putString("data", workoutJson.encodeToString(BackupPending.serializer(), p))
        }.commit()
    }

    /**
     * Assegna sensazioni e scarpe di un backup alle corse arrivate da Health Connect: subito dopo l'importazione
     * e dopo ogni sincronizzazione, finché non restano più corse in attesa.
     */
    suspend fun resolvePendingBackup(context: Context) = withContext(Dispatchers.IO) {
        val pending = loadPending(context)
        if (pending.isEmpty) return@withContext
        val r = BackupCodec.resolve(pending, localSessions(context))
        val existing = loadWorkoutFeedback(context)
        r.feedback.forEach { (id, f) ->
            val merged = BackupCodec.mergeFeedback(existing[id], f)
            if (merged != existing[id]) saveWorkoutFeedback(context, id, merged)
        }
        if (r.shoeRuns.isNotEmpty()) withContext(Dispatchers.Main) { Shoes.init(context); Shoes.importRuns(r.shoeRuns) }
        savePending(context, r.left)
    }

    suspend fun applyImport(context: Context, file: BackupFile, plan: BackupCodec.Plan) = withContext(Dispatchers.IO) {
        with(BackupCodec) {
            val d = dao(context)
            if (plan.newMeals.isNotEmpty()) d.insertMeals(plan.newMeals.map { it.toEntity() })
            if (plan.newActivities.isNotEmpty()) d.insertActivities(plan.newActivities.map { it.toEntity() })
            if (plan.newFoods.isNotEmpty()) d.upsertFoods(plan.newFoods.map { it.toEntity() })
        }
        file.settings?.let { withContext(Dispatchers.Main) { UserSettings.importSettings(it) } }
        // Le scarpe; le corse in cui sono state usate passano dall'abbinamento delle sessioni (qui sotto)
        file.shoes?.let { withContext(Dispatchers.Main) { Shoes.init(context); Shoes.importData(it.copy(runs = emptyMap())) } }
        file.runGoals?.let { withContext(Dispatchers.Main) { RunGoalStore.init(context); RunGoalStore.importGoals(it) } }
        file.localPlan?.let { withContext(Dispatchers.Main) { LocalPlan.init(context); LocalPlan.importBackup(it) } }
        if (plan.hasNotion) file.notion?.let { n ->
            val error = withContext(Dispatchers.Main) { NotionPlan.init(context); NotionPlan.importConnection(n) }
            if (error == null) NotionPlan.sync(context)
        }
        file.reminders?.let { r ->
            r.minutes.forEach { (type, minute) -> if (type in MealReminders.types) MealReminders.set(context, type, minute.takeIf { it >= 0 }) }
            RecoveryAlertNotifier.setEnabled(context, r.recoveryAlerts)
            WeeklySummary.setEnabled(context, r.weeklySummary)
        }
        // Sensazioni e scarpe delle corse: subito quelle già sincronizzate, le altre dopo la sincronizzazione
        if (!file.pending.isEmpty) {
            savePending(context, loadPending(context) + file.pending)
            resolvePendingBackup(context)
        }
    }

    /** Corse del backup ancora in attesa della sincronizzazione. */
    suspend fun pendingBackupRuns(context: Context): Int = withContext(Dispatchers.IO) {
        loadPending(context).let { p -> (p.runFeedback.map { it.id } + p.shoeRuns.keys).distinct().size }
    }

    // ==============================
    // SETTIMANA (Statistiche)
    // ==============================

    /** Una notte per giorno: prima la fonte preferita, poi la sessione più lunga (non un pisolino). */
    internal fun pickNight(sessions: List<SleepEntity>): SleepEntity =
        sessions.sortedWith(
            compareBy<SleepEntity> { SourceInfo.rank(it.source) }.thenByDescending { it.durationMinutes }
        ).first()

    /** I sette giorni da lunedì a domenica della settimana che inizia a [monday]. */
    suspend fun loadWeekStats(context: Context, monday: java.time.LocalDate): List<WeekDay> =
        loadDays(context, DateUtils.weekDays(monday))

    /** Numeri giorno per giorno (pasti, deficit, attività, sonno, recupero) per le date [days], in ordine. */
    suspend fun loadDays(context: Context, days: List<java.time.LocalDate>): List<WeekDay> =
        withContext(Dispatchers.IO) {
            val from = days.first().toString()
            val to = days.last().toString()
            val dao = dao(context)
            val meals = dao.mealsBetween(from, to).first().groupBy { it.date }
            val activities = dao.activitiesBetween(from, to).first().groupBy { it.date }
            val sleeps = dao.sleepBetween(from, to).first().groupBy { it.date }
            val metrics = preferredMetrics(dao.metricsBetween(from, to).first())
            val today = DateUtils.today()

            days.map { day ->
                val iso = day.toString()
                fun metric(type: String) = metrics[type]?.get(iso)
                val dayMeals = meals[iso].orEmpty()
                val dayActivities = activities[iso].orEmpty()
                val future = day.isAfter(today)
                val eaten = dayMeals.sumOf { it.kcal.toDouble() }.roundToInt()
                val extra = DayEnergy.extraBurn(
                    activeKcalHealthConnect = metric(MetricType.ACTIVE_KCAL),
                    importedWorkoutKcal = dayActivities.filter { it.source != "manual" }.sumOf { it.kcal },
                    manualWorkoutKcal = dayActivities.filter { it.source == "manual" }.sumOf { it.kcal }
                )
                // Per oggi conta solo il basale maturato fino a ora, per i giorni passati quello intero
                val resting = if (day == today) UserSettings.restingBurnSoFar() else UserSettings.dailyBurnKcal
                val temp = metric(MetricType.NIGHT_TEMP)
                val tempDelta = metric(MetricType.NIGHT_TEMP_DELTA)
                val night = sleeps[iso]?.let { pickNight(it) }
                WeekDay(
                    date = day,
                    isToday = day == today,
                    isFuture = future,
                    eatenKcal = eaten,
                    activityMinutes = dayActivities.sumOf { it.durationMinutes },
                    workouts = dayActivities.size,
                    deficitKcal = if (future || dayMeals.isEmpty()) null else DayEnergy.deficit(eaten, resting, extra),
                    countsForAverages = !UserSettings.usesCompleteDays || UserSettings.isDayComplete(iso),
                    sleepMinutes = night?.durationMinutes,
                    sleepStartMillis = night?.startMillis,
                    sleepEndMillis = night?.endMillis,
                    hrv = metric(MetricType.HRV),
                    nightTemp = temp ?: tempDelta,
                    tempIsDelta = temp == null && tempDelta != null,
                    steps = metric(MetricType.STEPS)?.toInt()
                )
            }
        }

    /** I giorni degli ultimi [days] giorni come campioni per le correlazioni (il deficit di oggi è parziale e non conta). */
    suspend fun loadInsightSamples(context: Context, days: Int = 60): List<DaySample> =
        loadDays(context, DateUtils.lastDays(days).map(DateUtils::parse)).map {
            DaySample(it.date, it.sleepMinutes, it.hrv, it.workouts, it.steps, if (it.isToday) null else it.deficitKcal)
        }

    // ==============================
    // DATI DI SALUTE (Health Connect)
    // ==============================

    suspend fun saveHealthData(
        context: Context,
        activities: List<ActivityEntity>,
        sleep: List<SleepEntity>,
        metrics: List<DailyMetricEntity>
    ) = withContext(Dispatchers.IO) {
        val dao = dao(context)
        if (activities.isNotEmpty()) dao.upsertActivities(activities)
        if (sleep.isNotEmpty()) dao.upsertSleep(sleep)
        if (metrics.isNotEmpty()) dao.upsertMetrics(metrics)
    }

    // ==============================
    // ALLENAMENTI (dettaglio)
    // ==============================

    private val workoutJson = Json { ignoreUnknownKeys = true }

    suspend fun workoutDetailIds(context: Context): Set<String> = withContext(Dispatchers.IO) {
        dao(context).workoutDetailIds().toSet()
    }

    /** Allenamenti importati da Health Connect (non quelli a mano) tra due date ISO. */
    suspend fun importedActivitiesBetween(context: Context, from: String, to: String): List<ActivityEntity> =
        withContext(Dispatchers.IO) {
            dao(context).activitiesBetween(from, to).first().filter { it.externalId != null }
        }

    suspend fun deleteImportedActivities(context: Context, externalIds: List<String>) = withContext(Dispatchers.IO) {
        if (externalIds.isNotEmpty()) externalIds.chunked(500).forEach { dao(context).deleteActivitiesByExternalIds(it) }
    }

    /** Sessioni di Health Connect già importate (anche le camminate automatiche, che non hanno dettaglio). */
    suspend fun importedActivityIds(context: Context): Set<String> = withContext(Dispatchers.IO) {
        dao(context).activityExternalIds().toSet()
    }

    private fun decodeDetail(json: String?): WorkoutDetail? =
        json?.let { runCatching { workoutJson.decodeFromString(WorkoutDetail.serializer(), it) }.getOrNull() }

    /**
     * Quello che una nuova lettura non ha (lettura interrotta, percorso autorizzato una volta sola) resta
     * quello già salvato: un dettaglio buono non viene mai sostituito da uno più povero.
     */
    internal fun keepKnown(new: WorkoutDetail, old: WorkoutDetail?): WorkoutDetail {
        if (old == null) return new
        val keepRoute = new.route.isEmpty() && old.route.isNotEmpty()
        return new.copy(
            distanceM = new.distanceM ?: old.distanceM,
            kcal = new.kcal ?: old.kcal,
            steps = new.steps ?: old.steps,
            avgHr = new.avgHr ?: old.avgHr,
            maxHr = new.maxHr ?: old.maxHr,
            minHr = new.minHr ?: old.minHr,
            elevationGainM = new.elevationGainM ?: old.elevationGainM,
            vo2max = new.vo2max ?: old.vo2max,
            vo2maxFromSession = if (new.vo2max != null) new.vo2maxFromSession else old.vo2maxFromSession,
            maxSpeedMs = new.maxSpeedMs ?: old.maxSpeedMs,
            hr = new.hr.ifEmpty { old.hr },
            speed = new.speed.ifEmpty { old.speed },
            cadence = new.cadence.ifEmpty { old.cadence },
            route = if (keepRoute) old.route else new.route,
            routeStatus = if (keepRoute) old.routeStatus else new.routeStatus,
            laps = new.laps.ifEmpty { old.laps }
        )
    }

    /** Ultima modifica in Health Connect delle sessioni già lette per intero (id → millisecondi). */
    suspend fun workoutModifiedTimes(context: Context): Map<String, Long> = withContext(Dispatchers.IO) {
        dao(context).allWorkoutSummaries().mapNotNull { decodeDetail(it) }
            .mapNotNull { d -> d.modifiedMillis?.let { d.externalId to it } }.toMap()
    }

    /** Toglie i dettagli dal giorno [from] in poi che non sono più tra le sessioni [present] di Health Connect. */
    suspend fun deleteWorkoutDetailsNotIn(context: Context, from: String, present: Set<String>): Int = withContext(Dispatchers.IO) {
        val gone = dao(context).workoutDetailIdsFrom(from).filter { it !in present }
        gone.chunked(500).forEach { dao(context).deleteWorkoutDetails(it) }
        gone.size
    }

    /** Toglie le notti tra [from] e [to] (giorno del risveglio) che la fonte non ha più: [keep] = (fonte, id). */
    suspend fun deleteSleepNotIn(context: Context, from: String, to: String, keep: Set<Pair<String, String>>) =
        withContext(Dispatchers.IO) {
            val gone = dao(context).sleepBetween(from, to).first().filter { (it.source to it.externalId) !in keep }.map { it.id }
            gone.chunked(500).forEach { dao(context).deleteSleepByIds(it) }
        }

    /** Il riepilogo (senza le serie) di un solo allenamento. */
    suspend fun loadWorkoutSummary(context: Context, externalId: String): WorkoutDetail? = withContext(Dispatchers.IO) {
        decodeDetail(dao(context).workoutSummary(externalId))
    }

    suspend fun saveWorkoutDetails(context: Context, details: List<WorkoutDetail>) = withContext(Dispatchers.IO) {
        if (details.isEmpty()) return@withContext
        dao(context).upsertWorkoutDetails(details.map { new ->
            val d = keepKnown(new, decodeDetail(dao(context).workoutDetailJson(new.externalId)))
            io.github.peol13.unicum.data.db.WorkoutDetailEntity(
                externalId = d.externalId, source = d.source, date = d.date, type = d.type,
                startMillis = d.startMillis, automatic = d.automatic,
                summary = workoutJson.encodeToString(WorkoutDetail.serializer(),
                    d.copy(avgCadence = WorkoutAnalysis.averageCadence(d),
                        elevationGainM = d.elevationGainM?.takeIf { it > 0 } ?: WorkoutAnalysis.elevationGain(d),
                        hr = emptyList(), speed = emptyList(), cadence = emptyList(), route = emptyList())),
                json = workoutJson.encodeToString(WorkoutDetail.serializer(), d)
            )
        })
    }

    /** Tutti gli allenamenti (senza le camminate automatiche), dal più recente, senza le serie. */
    suspend fun loadWorkoutSummaries(context: Context): List<WorkoutDetail> = withContext(Dispatchers.IO) {
        dao(context).workoutSummaries().mapNotNull { runCatching { workoutJson.decodeFromString(WorkoutDetail.serializer(), it) }.getOrNull() }
    }

    /** Il dettaglio completo, unito agli eventuali doppioni scritti da altre app (vedi [WorkoutAnalysis.merge]). */
    suspend fun loadWorkoutDetail(
        context: Context,
        externalId: String,
        /** Riepiloghi già letti (chi carica molti dettagli di fila li passa, così non si rileggono ogni volta). */
        summaries: List<WorkoutDetail>? = null
    ): WorkoutDetail? = withContext(Dispatchers.IO) {
        val main = decodeDetail(dao(context).workoutDetailJson(externalId)) ?: return@withContext null
        val twins = (summaries ?: loadWorkoutSummaries(context))
            .filter { it.externalId != externalId && WorkoutAnalysis.sameSession(it, main) }
            .mapNotNull { decodeDetail(dao(context).workoutDetailJson(it.externalId)) }
        WorkoutAnalysis.merge(main, twins).let { it.copy(elevationGainM = it.elevationGainM?.takeIf { g -> g > 0 } ?: WorkoutAnalysis.elevationGain(it)) }
    }

    suspend fun loadWorkoutFeedback(context: Context): Map<String, WorkoutFeedback> = withContext(Dispatchers.IO) {
        dao(context).allWorkoutFeedback().associate {
            it.key to WorkoutFeedback(
                it.rpe, WorkoutFeedback.normalizeKind(it.kind), it.note, it.distanceM, it.movingSeconds, it.avgHr, it.maxHr,
                intervals = it.intervals?.let { j -> runCatching { workoutJson.decodeFromString(IntervalPlan.serializer(), j) }.getOrNull() },
                exercises = it.exercises?.let { j -> runCatching { workoutJson.decodeFromString(exerciseList, j) }.getOrNull() }
            )
        }
    }

    suspend fun saveWorkoutFeedback(context: Context, key: String, feedback: WorkoutFeedback) = withContext(Dispatchers.IO) {
        dao(context).upsertWorkoutFeedback(
            io.github.peol13.unicum.data.db.WorkoutFeedbackEntity(
                key, feedback.rpe, feedback.kind, feedback.note?.takeIf { it.isNotBlank() }, feedback.distanceM, feedback.movingSeconds,
                feedback.avgHr, feedback.maxHr,
                feedback.intervals?.let { workoutJson.encodeToString(IntervalPlan.serializer(), it) },
                feedback.exercises?.let { workoutJson.encodeToString(exerciseList, it) }
            )
        )
    }

    private val exerciseList = kotlinx.serialization.builtins.ListSerializer(Exercise.serializer())

    /**
     * Metriche raggruppate per tipo e giorno (tipo -> data -> valore); con più fonti vince la preferita.
     * L'HRV prima del salto di scala ([HrvData.reliableFrom]) non è confrontabile e qui viene scartata: vale per tutta l'app.
     */
    private fun preferredMetrics(rows: List<DailyMetricEntity>): Map<String, Map<String, Double>> =
        rows.filter { !HrvData.isHrv(it.type) || HrvData.reliable(it.date) }.groupBy { it.type }.mapValues { (_, ofType) ->
            ofType.groupBy { it.date }.mapValues { (_, sameDay) -> SourceInfo.preferred(sameDay) { it.source }!!.value }
        }

    /**
     * Cerca il salto di scala dell'HRV nell'ultimo anno (tutte le letture, anche quelle che poi verrebbero scartate)
     * e lo salva come data automatica in [UserSettings]. Da chiamare dopo ogni sincronizzazione.
     */
    suspend fun refreshHrvShift(context: Context) = withContext(Dispatchers.IO) {
        val range = lastDays(365)
        val day = dao(context).metricsBetween(range.first(), range.last()).first()
            .filter { it.type == MetricType.HRV }
            .groupBy { it.date }
            .mapValues { (_, sameDay) -> SourceInfo.preferred(sameDay) { it.source }!!.value }
        UserSettings.updateHrvAutoShift(HrvData.detectShift(day))
    }

    /** Serie giornaliera di una metrica (data ISO -> valore) negli ultimi [days] giorni, con la fonte preferita. */
    suspend fun loadMetricSeries(context: Context, type: String, days: Int): Map<String, Double> =
        withContext(Dispatchers.IO) {
            val range = lastDays(days)
            preferredMetrics(dao(context).metricsBetween(range.first(), range.last()).first().filter { it.type == type })[type]
                .orEmpty()
        }

    /** Sonno e metriche degli ultimi [days] giorni; con più fonti per lo stesso dato vince la preferita. */
    suspend fun loadHealth(context: Context, days: Int = 14, end: String = today()): HealthData = withContext(Dispatchers.IO) {
        val range = lastDays(days, end)
        val from = range.first()
        val to = range.last()

        val nights = dao(context).sleepBetween(from, to).first()
            .groupBy { it.date }
            .map { (_, sessions) -> pickNight(sessions) }
            .sortedBy { it.date }
            .map {
                SleepNight(
                    it.date, it.startMillis, it.endMillis, it.durationMinutes,
                    it.deepMinutes, it.remMinutes, it.lightMinutes, it.awakeMinutes, it.source,
                    SleepStages.decode(it.stages)
                )
            }

        HealthData(nights, preferredMetrics(dao(context).metricsBetween(from, to).first()))
    }

    // ==============================
    // MIGRAZIONE DA SHAREDPREFERENCES (una tantum)
    // I dati salvati nella vecchia versione non avevano data: vengono
    // assegnati alla data di oggi.
    // ==============================

    // Pasti e attività vengono migrati separatamente. Una chiave viene rimossa solo
    // dopo che i suoi dati sono entrati nel database; se il JSON è illeggibile la chiave
    // resta dov'è (nessun dato perso) e al prossimo avvio si riprova. Il flag di
    // migrazione completata si imposta solo quando non resta più nulla da importare.
    suspend fun migrateLegacyPrefs(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return@withContext

        val date = today()

        migrateKey(prefs, KEY_MEALS) { json ->
            val legacyMeals = Json.decodeFromString<List<Meal>>(json)
            if (legacyMeals.isNotEmpty()) insertMeals(context, date, legacyMeals)
        }
        migrateKey(prefs, KEY_ACTIVITIES) { json ->
            val legacyActivities = Json.decodeFromString<List<Activity>>(json)
            if (legacyActivities.isNotEmpty()) insertActivities(context, date, legacyActivities)
        }

        if (!prefs.contains(KEY_MEALS) && !prefs.contains(KEY_ACTIVITIES)) {
            prefs.edit().putBoolean(KEY_MIGRATED, true).commit()
        }
    }

    private suspend fun migrateKey(
        prefs: SharedPreferences,
        key: String,
        import: suspend (String) -> Unit
    ) {
        val json = prefs.getString(key, null) ?: return
        try {
            import(json)
            prefs.edit().remove(key).commit()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non blocchiamo l'app: la chiave viene conservata per un nuovo tentativo
            e.printStackTrace()
        }
    }

    // ==============================
    // MAPPER
    // ==============================

    private fun MealEntity.toMeal() = Meal(
        name = foodName,
        calories = kcal.roundToInt(),
        mealType = mealType,
        quantityGrams = grams.roundToInt(),
        protein = protein,
        carbohydrates = carbs,
        fats = fats,
        id = id,
        minuteOfDay = MealTime.fromMillis(createdAt),
        fiber = fiber,
        sugars = sugars,
        salt = salt
    )

    private fun Meal.toEntity(date: String) = MealEntity(
        date = date,
        mealType = mealType,
        foodId = null,
        foodName = name,
        grams = quantityGrams.toFloat(),
        kcal = calories.toFloat(),
        protein = protein,
        carbs = carbohydrates,
        fats = fats,
        createdAt = minuteOfDay?.let { MealTime.toMillis(date, it) } ?: System.currentTimeMillis(),
        fiber = fiber,
        sugars = sugars,
        salt = salt
    )

    private fun Food.toEntity() = FoodEntity(
        id = OpenFoodFacts.ID_PREFIX + barcode,
        name = name,
        brand = brand,
        kcalPer100g = caloriesPer100g.toFloat(),
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatsPer100g = fatsPer100g,
        source = OpenFoodFacts.SOURCE,
        fiberPer100g = fiberPer100g,
        sugarsPer100g = sugarsPer100g,
        saltPer100g = saltPer100g
    )

    private fun FoodEntity.toFood() = Food(
        name = name,
        caloriesPer100g = (kcalPer100g ?: 0f).roundToInt(),
        proteinPer100g = proteinPer100g ?: 0f,
        carbsPer100g = carbsPer100g ?: 0f,
        fatsPer100g = fatsPer100g ?: 0f,
        category = OpenFoodFacts.CATEGORY,
        brand = brand,
        barcode = id.removePrefix(OpenFoodFacts.ID_PREFIX),
        fiberPer100g = fiberPer100g,
        sugarsPer100g = sugarsPer100g,
        saltPer100g = saltPer100g
    )

    private fun ActivityEntity.toActivity() = Activity(
        type = type,
        durationMinutes = durationMinutes,
        calories = kcal,
        id = id,
        source = source,
        distanceMeters = distanceMeters,
        avgHeartRate = avgHeartRate,
        startMinute = startMinute
    )

    private fun Activity.toEntity(date: String) = ActivityEntity(
        date = date,
        type = type,
        durationMinutes = durationMinutes,
        kcal = calories,
        source = source,
        distanceMeters = distanceMeters,
        avgHeartRate = avgHeartRate,
        startMinute = startMinute
    )
}
