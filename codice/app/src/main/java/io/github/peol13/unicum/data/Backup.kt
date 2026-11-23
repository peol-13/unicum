package io.github.peol13.unicum.data

import io.github.peol13.unicum.data.db.ActivityEntity
import io.github.peol13.unicum.data.db.FoodEntity
import io.github.peol13.unicum.data.db.MealEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Esportazione/importazione dei dati creati nell'app (pasti, allenamenti manuali, prodotti salvati,
 * profilo e alimenti personali, scarpe, sensazioni e correzioni delle corse, promemoria, obiettivi di corsa). Sonno, metriche e
 * allenamenti letti da Health Connect non ci sono: tornano con la sincronizzazione. Logica pura, senza Android.
 * Il token di Notion entra solo se chi esporta a mano lo sceglie ([BackupNotion]): il file può finire su Drive
 * o sul PC, quindi di base resta fuori e il backup automatico non lo include mai.
 */
@Serializable
data class BackupMeal(
    val date: String,
    val createdAt: Long,
    val mealType: String,
    val foodName: String,
    val grams: Float,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fats: Float,
    val foodId: String? = null,
    val fiber: Float? = null,
    val sugars: Float? = null,
    val salt: Float? = null
)

@Serializable
data class BackupActivity(
    val date: String,
    val type: String,
    val durationMinutes: Int,
    val kcal: Int,
    val distanceMeters: Int? = null,
    val avgHeartRate: Int? = null,
    val startMinute: Int? = null
)

@Serializable
data class BackupFood(
    val id: String,
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Float? = null,
    val proteinPer100g: Float? = null,
    val carbsPer100g: Float? = null,
    val fatsPer100g: Float? = null,
    val source: String = "",
    val fetchedAt: Long = 0L,
    val fiberPer100g: Float? = null,
    val sugarsPer100g: Float? = null,
    val saltPer100g: Float? = null
)

/** Token e database di Notion. Il token è una chiave in chiaro: entra nel file solo su richiesta. */
@Serializable
data class BackupNotion(
    val token: String,
    val database: String,
    val logDatabase: String? = null
)

/** Profilo, obiettivi, preferenze e alimenti personali (UserSettings). */
@Serializable
data class BackupSettings(
    val onboarded: Boolean = true,
    val age: Int = 30,
    val isMale: Boolean = true,
    val weightKg: Int = 72,
    val heightCm: Int = 175,
    val bmrManual: Boolean = false,
    val bmrValue: Int = 1800,
    val intakeManual: Boolean = false,
    val intakeValue: Int = 2000,
    val activityLevel: String = ActivityLevel.LEGGERO.name,
    val deficitKcal: Int = 500,
    val proteinPct: Int = UserSettings.DEFAULT_PROTEIN_PCT,
    val carbsPct: Int = UserSettings.DEFAULT_CARBS_PCT,
    val fatsPct: Int = UserSettings.DEFAULT_FATS_PCT,
    val themeMode: String = ThemeMode.SISTEMA.name,
    val dashboardMode: String = DashboardMode.NUTRIZIONE.name,
    /** Ordine delle card della Home (vuoto = quello della modalità). */
    val homeLayout: String? = null,
    val customFoods: List<Food> = emptyList(),
    val recentFoods: List<String> = emptyList(),
    /** Pasti fissi salvati. */
    val savedMeals: List<SavedMeal> = emptyList(),
    /** HRV confrontabile dal (yyyy-MM-dd) scelto a mano; null = automatico. */
    val hrvFrom: String? = null,
    /** Giorni segnati come "giornata completa" (yyyy-MM-dd); si uniscono a quelli presenti. */
    val completeDays: List<String> = emptyList()
)

/**
 * Una sessione di Health Connect a cui si riferiscono sensazioni o scarpe. Su un altro telefono Health Connect
 * dà id diversi alle stesse sessioni: le si ritrova da tipo, app e orario di inizio (vedi [BackupCodec.matchSession]).
 */
@Serializable
data class BackupSession(val id: String, val type: String, val source: String, val startMillis: Long)

/** Sensazioni, correzioni a mano e ripetute di un allenamento ([id] = sessione di Health Connect). */
@Serializable
data class BackupRunFeedback(val id: String, val feedback: WorkoutFeedback)

/** Orari dei promemoria dei pasti (tipo → minuto del giorno, -1 = spento) e avvisi sul recupero. */
@Serializable
data class BackupReminders(val minutes: Map<String, Int> = emptyMap(), val recoveryAlerts: Boolean = true)

/**
 * Sensazioni e scarpe di corse che su questo telefono non ci sono ancora (backup importato prima della
 * sincronizzazione): restano in attesa e si assegnano appena le sessioni arrivano da Health Connect.
 */
@Serializable
data class BackupPending(
    val sessions: List<BackupSession> = emptyList(),
    val runFeedback: List<BackupRunFeedback> = emptyList(),
    /** id della sessione → id della scarpa ("" = nessuna scarpa scelta a mano). */
    val shoeRuns: Map<String, String> = emptyMap()
) {
    val isEmpty: Boolean get() = runFeedback.isEmpty() && shoeRuns.isEmpty()

    /** Aggiunge [other]: a parità di sessione vince [other] (il backup importato per ultimo). */
    operator fun plus(other: BackupPending) = BackupPending(
        sessions = (other.sessions + sessions).distinctBy { it.id },
        runFeedback = (other.runFeedback + runFeedback).distinctBy { it.id },
        shoeRuns = shoeRuns + other.shoeRuns
    )
}

@Serializable
data class BackupFile(
    val app: String = BackupCodec.APP_ID,
    val version: Int = BackupCodec.VERSION,
    val exportedAt: Long = 0L,
    val meals: List<BackupMeal> = emptyList(),
    val activities: List<BackupActivity> = emptyList(),
    val foods: List<BackupFood> = emptyList(),
    val settings: BackupSettings? = null,
    /** Scarpe da corsa e corse in cui sono state usate. */
    val shoes: ShoeData? = null,
    /** Sessioni citate da [runFeedback] e dalle scarpe, per ritrovarle su un altro telefono. */
    val sessions: List<BackupSession> = emptyList(),
    val runFeedback: List<BackupRunFeedback> = emptyList(),
    val reminders: BackupReminders? = null,
    /** Obiettivi di corsa a breve e lungo termine. */
    val runGoals: RunGoals? = null,
    /** Programma di allenamento tenuto nell'app (importato da PDF o scritto a mano). */
    val localPlan: LocalPlanData? = null,
    /** Collegamento a Notion: solo se chi esporta a mano lo sceglie (mai nel backup automatico). */
    val notion: BackupNotion? = null
) {
    /** Quello che va assegnato alle sessioni di Health Connect (sensazioni e scarpe). */
    val pending: BackupPending get() = BackupPending(sessions, runFeedback, shoes?.runs.orEmpty())
}

object BackupCodec {
    const val APP_ID = "unicum"
    const val VERSION = 1

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** null se il testo non è un backup di UNICUM leggibile. */
    fun decode(text: String): BackupFile? {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrNull() ?: return null
        return if (file.app == APP_ID && file.version in 1..VERSION) file else null
    }

    fun nameFor(dateIso: String) = "unicum-backup-$dateIso.json"

    /**
     * true se [new] ha molti meno dati di [old] (la metà o meno, partendo da una base non piccola): un database
     * svuotato per sbaglio non deve sovrascrivere il backup buono.
     */
    fun shrunk(old: BackupFile?, new: BackupFile): Boolean {
        if (old == null) return false
        fun lost(before: Int, after: Int, min: Int) = before >= min && after < before / 2
        return lost(old.meals.size, new.meals.size, 20) || lost(old.runFeedback.size, new.runFeedback.size, 10)
    }

    // ---- da database a file ----

    fun build(
        meals: List<MealEntity>,
        activities: List<ActivityEntity>,
        foods: List<FoodEntity>,
        settings: BackupSettings?,
        nowMillis: Long,
        shoes: ShoeData? = null,
        /** Sensazioni salvate nell'app: id della sessione → sensazioni. */
        feedback: Map<String, WorkoutFeedback> = emptyMap(),
        /** Sessioni di Health Connect salvate (per id, tipo, app e orario). */
        sessions: List<BackupSession> = emptyList(),
        reminders: BackupReminders? = null,
        runGoals: RunGoals? = null,
        localPlan: LocalPlanData? = null,
        notion: BackupNotion? = null
    ) = BackupFile(
        exportedAt = nowMillis,
        meals = meals.map {
            BackupMeal(
                it.date, it.createdAt, it.mealType, it.foodName, it.grams, it.kcal, it.protein, it.carbs, it.fats, it.foodId,
                it.fiber, it.sugars, it.salt
            )
        },
        // Solo gli allenamenti inseriti a mano: gli altri tornano da Health Connect
        activities = activities.filter { it.source == "manual" }.map {
            BackupActivity(it.date, it.type, it.durationMinutes, it.kcal, it.distanceMeters, it.avgHeartRate, it.startMinute)
        },
        foods = foods.map {
            BackupFood(
                it.id, it.name, it.brand, it.kcalPer100g, it.proteinPer100g, it.carbsPer100g, it.fatsPer100g, it.source, it.fetchedAt,
                it.fiberPer100g, it.sugarsPer100g, it.saltPer100g
            )
        },
        settings = settings,
        shoes = shoes?.takeIf { it.shoes.isNotEmpty() },
        runFeedback = feedback.filterValues { it != WorkoutFeedback() }.map { (id, f) -> BackupRunFeedback(id, f) }.sortedBy { it.id },
        sessions = run {
            val used = feedback.keys + shoes?.takeIf { it.shoes.isNotEmpty() }?.runs?.keys.orEmpty()
            sessions.filter { it.id in used }.sortedBy { it.startMillis }
        },
        reminders = reminders,
        runGoals = runGoals,
        localPlan = localPlan?.takeIf { it.runs.isNotEmpty() },
        notion = notion?.takeIf { it.token.isNotBlank() && it.database.isNotBlank() }
    )

    // ---- sensazioni e scarpe sulle sessioni di questo telefono ----

    /** Scarto massimo tra gli orari di inizio della stessa sessione su due telefoni. */
    private const val SAME_START_MS = 120_000L

    /**
     * La sessione di questo telefono che corrisponde a [s]: lo stesso id, oppure lo stesso tipo iniziato entro
     * 2 minuti (prima quella della stessa app, poi la più vicina). null = non ancora sincronizzata.
     */
    fun matchSession(s: BackupSession, local: List<BackupSession>): String? =
        local.firstOrNull { it.id == s.id }?.id
            ?: local.filter { it.type == s.type && kotlin.math.abs(it.startMillis - s.startMillis) <= SAME_START_MS }
                .minWithOrNull(compareBy<BackupSession>({ it.source != s.source }, { kotlin.math.abs(it.startMillis - s.startMillis) }))
                ?.id

    /** Sensazioni e scarpe portate sugli id di questo telefono; [left] = quelle di sessioni non ancora arrivate. */
    data class Resolved(val feedback: Map<String, WorkoutFeedback>, val shoeRuns: Map<String, String>, val left: BackupPending)

    fun resolve(p: BackupPending, local: List<BackupSession>): Resolved {
        val byId = p.sessions.associateBy { it.id }
        val localIds = local.mapTo(HashSet()) { it.id }
        // Esercizi di una seduta senza allenamento registrato: legati alla seduta del programma, non a una sessione
        fun target(id: String): String? =
            if (id in localIds || WorkoutFeedback.isPlanKey(id)) id else byId[id]?.let { matchSession(it, local) }
        val feedback = mutableMapOf<String, WorkoutFeedback>()
        val leftFeedback = mutableListOf<BackupRunFeedback>()
        p.runFeedback.forEach { e -> target(e.id)?.let { feedback[it] = e.feedback } ?: leftFeedback.add(e) }
        val shoes = mutableMapOf<String, String>()
        val leftShoes = mutableMapOf<String, String>()
        p.shoeRuns.forEach { (id, shoe) -> target(id)?.let { shoes[it] = shoe } ?: leftShoes.put(id, shoe) }
        val leftIds = leftFeedback.map { it.id }.toSet() + leftShoes.keys
        return Resolved(feedback, shoes, BackupPending(p.sessions.filter { it.id in leftIds }, leftFeedback, leftShoes))
    }

    /**
     * Sensazioni del backup su quelle già presenti: quello che c'è già resta, il backup riempie i vuoti.
     * La correzione a mano (durata, distanza e FC del tratto) va presa tutta insieme o per niente.
     */
    fun mergeFeedback(local: WorkoutFeedback?, incoming: WorkoutFeedback): WorkoutFeedback {
        if (local == null) return incoming
        val correction = if (local.isCorrected) local else incoming
        return local.copy(
            rpe = local.rpe ?: incoming.rpe,
            kind = local.kind ?: incoming.kind,
            note = local.note ?: incoming.note,
            distanceM = correction.distanceM, movingSeconds = correction.movingSeconds,
            avgHr = correction.avgHr, maxHr = correction.maxHr,
            intervals = local.intervals ?: incoming.intervals,
            exercises = local.exercises ?: incoming.exercises
        )
    }

    /** Quante corse riceverebbero sensazioni nuove (anche quelle che arriveranno con la sincronizzazione). */
    fun feedbackChanges(p: BackupPending, local: List<BackupSession>, existing: Map<String, WorkoutFeedback>): Int {
        val r = resolve(p, local)
        return r.feedback.count { (id, f) -> mergeFeedback(existing[id], f) != existing[id] } + r.left.runFeedback.size
    }

    // ---- unione ----

    // Stesso pasto = stessa data, stesso tipo (pranzo, cena...), stesso nome e stesso minuto.
    // Le grammi restano fuori: una quantità corretta dopo il backup non deve creare un doppione.
    internal fun mealKey(date: String, mealType: String, name: String, createdAt: Long) =
        "$date|${mealType.trim().lowercase()}|${name.trim().lowercase()}|${createdAt / 60_000L}"

    internal fun activityKey(date: String, type: String, minutes: Int, kcal: Int) =
        "$date|${type.trim().lowercase()}|$minutes|$kcal"

    /** Cosa entrerebbe nel database e che cosa invece c'è già. */
    data class Plan(
        val newMeals: List<BackupMeal>,
        val newActivities: List<BackupActivity>,
        val newFoods: List<BackupFood>,
        val skippedMeals: Int,
        val skippedActivities: Int,
        val hasSettings: Boolean,
        /** Corse a cui il backup aggiunge sensazioni, correzioni o ripetute. */
        val newRunFeedback: Int = 0,
        val hasReminders: Boolean = false,
        val hasGoals: Boolean = false,
        val hasPlan: Boolean = false,
        /** Il file porta il collegamento a Notion e l'app non è ancora collegata. */
        val hasNotion: Boolean = false
    ) {
        val days: Int get() = (newMeals.map { it.date } + newActivities.map { it.date }).distinct().size
        val isEmpty: Boolean get() = newMeals.isEmpty() && newActivities.isEmpty() && newFoods.isEmpty() && !hasSettings &&
            newRunFeedback == 0 && !hasReminders && !hasGoals && !hasPlan && !hasNotion
    }

    fun plan(
        file: BackupFile,
        existingMeals: List<MealEntity>,
        existingActivities: List<ActivityEntity>,
        existingFoodIds: Set<String>,
        newRunFeedback: Int = file.runFeedback.size,
        /** L'app è già collegata a Notion: il collegamento del file non la tocca. */
        notionConnected: Boolean = false
    ): Plan {
        // Si contano le copie: due voci uguali nel file (lo stesso pane inserito due volte) entrano
        // entrambe, ma solo quante ne mancano nel database, così importare due volte non aggiunge nulla.
        val mealsHere = existingMeals.groupingBy { mealKey(it.date, it.mealType, it.foodName, it.createdAt) }
            .eachCount().toMutableMap()
        val actsHere = existingActivities.filter { it.source == "manual" }
            .groupingBy { activityKey(it.date, it.type, it.durationMinutes, it.kcal) }.eachCount().toMutableMap()
        fun MutableMap<String, Int>.take(key: String): Boolean {
            val left = this[key] ?: 0
            if (left > 0) this[key] = left - 1
            return left == 0
        }
        val meals = file.meals.filter { mealsHere.take(mealKey(it.date, it.mealType, it.foodName, it.createdAt)) }
        val acts = file.activities.filter { actsHere.take(activityKey(it.date, it.type, it.durationMinutes, it.kcal)) }
        val foodIds = existingFoodIds.toHashSet()
        val foods = file.foods.filter { foodIds.add(it.id) }
        return Plan(
            newMeals = meals,
            newActivities = acts,
            newFoods = foods,
            skippedMeals = file.meals.size - meals.size,
            skippedActivities = file.activities.size - acts.size,
            hasSettings = file.settings != null,
            newRunFeedback = newRunFeedback,
            hasReminders = file.reminders != null,
            hasGoals = file.runGoals != null,
            hasPlan = file.localPlan != null,
            hasNotion = file.notion != null && !notionConnected
        )
    }

    fun BackupMeal.toEntity() = MealEntity(
        date = date, mealType = mealType, foodId = foodId, foodName = foodName,
        grams = grams, kcal = kcal, protein = protein, carbs = carbs, fats = fats, createdAt = createdAt,
        fiber = fiber, sugars = sugars, salt = salt
    )

    fun BackupActivity.toEntity() = ActivityEntity(
        date = date, type = type, durationMinutes = durationMinutes, kcal = kcal,
        source = "manual", distanceMeters = distanceMeters, avgHeartRate = avgHeartRate, startMinute = startMinute
    )

    fun BackupFood.toEntity() = FoodEntity(
        id = id, name = name, brand = brand, kcalPer100g = kcalPer100g, proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g, fatsPer100g = fatsPer100g, source = source, fetchedAt = fetchedAt,
        fiberPer100g = fiberPer100g, sugarsPer100g = sugarsPer100g, saltPer100g = saltPer100g
    )

    /** Alimenti personali: quelli già presenti (stesso nome) restano; i recenti: prima i già presenti. */
    fun mergeCustomFoods(current: List<Food>, incoming: List<Food>): List<Food> =
        current + incoming.filter { f -> current.none { it.name.equals(f.name, true) } }

    fun mergeRecent(current: List<String>, incoming: List<String>): List<String> =
        (current + incoming).distinct().take(12)
}
