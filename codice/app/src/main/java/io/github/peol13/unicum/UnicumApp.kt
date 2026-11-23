package io.github.peol13.unicum

import io.github.peol13.unicum.data.DayEnergy
import io.github.peol13.unicum.data.DateUtils
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import io.github.peol13.unicum.data.Activity
import io.github.peol13.unicum.data.AutoBackup
import io.github.peol13.unicum.data.DayData
import io.github.peol13.unicum.data.DaySummary
import io.github.peol13.unicum.data.HealthConnectSource
import io.github.peol13.unicum.data.HealthConnectWriter
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.HealthData
import io.github.peol13.unicum.data.MetricType
import io.github.peol13.unicum.data.RecoveryAlert
import io.github.peol13.unicum.data.RecoveryAlertNotifier
import io.github.peol13.unicum.data.RecoveryAlerts
import io.github.peol13.unicum.data.SleepNight
import io.github.peol13.unicum.data.SyncResult
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.data.Meal
import io.github.peol13.unicum.data.UnicumRepository
import io.github.peol13.unicum.widget.UnicumWidget
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.peol13.unicum.i18n.tr

object UnicumAppState {

    var dayData by mutableStateOf(
        DayData(
            meals = mutableStateListOf(),
            activities = mutableStateListOf()
        )
    )

    // Ultimi 7 giorni (l'ultimo è oggi), per i mini grafici
    var week by mutableStateOf<List<DaySummary>>(emptyList())
        private set

    // Sonno e metriche (passi, HRV, battito a riposo...) degli ultimi 14 giorni
    var health by mutableStateOf(HealthData())
        private set

    var syncing by mutableStateOf(false)
        private set

    var lastSyncResult by mutableStateOf<SyncResult?>(null)
        private set

    /** Giorno mostrato in Home, pagine di dettaglio e Diario (formato yyyy-MM-dd). */
    var selectedDate by mutableStateOf(UnicumRepository.today())
        private set

    val isToday: Boolean get() = selectedDate == UnicumRepository.today()

    /** Cambia il giorno mostrato e ricarica i dati di quel giorno. */
    fun selectDay(context: Context, date: String) {
        if (date == selectedDate) return
        selectedDate = date
        scope.launch { reload(context) }
    }

    fun backToToday(context: Context) = selectDay(context, UnicumRepository.today())

    /** Registrazione di un pasto chiesta da fuori (widget o promemoria): tipo di pasto, "" = in base all'ora. */
    var pendingAddMeal by mutableStateOf<String?>(null)
        private set

    /** Apre la Home di oggi con la registrazione del pasto già aperta. */
    fun requestAddMeal(context: Context, mealType: String) {
        backToToday(context)
        pendingAddMeal = mealType
    }

    fun consumeAddMeal() {
        pendingAddMeal = null
    }

    /** Apertura della sezione Allenamenti chiesta da fuori (widget del recupero). */
    var pendingOpenTraining by mutableStateOf(false)
        private set

    fun requestOpenTraining() {
        pendingOpenTraining = true
    }

    fun consumeOpenTraining() {
        pendingOpenTraining = false
    }

    /** Sposta il giorno mostrato di [delta] giorni, senza andare nel futuro. false = non si è mosso. */
    fun shiftDay(context: Context, delta: Int): Boolean {
        val target = DateUtils.parse(selectedDate).plusDays(delta.toLong())
        if (target.isAfter(DateUtils.today())) return false
        selectDay(context, target.toString())
        return true
    }

    // Il giorno che era "oggi" all'ultimo controllo: serve a seguire il cambio di data a mezzanotte
    private var knownToday = UnicumRepository.today()

    /**
     * Se nel frattempo è cambiata la data (app rimasta aperta oltre la mezzanotte) e si stava guardando
     * "oggi", passa al nuovo giorno. Un giorno passato scelto dall'utente resta com'è.
     */
    fun followToday(context: Context) {
        val today = UnicumRepository.today()
        if (today == knownToday) return
        val wasOnToday = selectedDate == knownToday
        knownToday = today
        if (wasOnToday) selectDay(context, today)
    }

    // Un errore in un'operazione in background viene registrato nel log, senza chiudere l'app
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e("UnicumAppState", "Operazione fallita", e) }
    )

    private var isInitialized = false

    suspend fun initPersistence(context: Context) {
        if (isInitialized) return
        isInitialized = true

        UnicumRepository.migrateLegacyPrefs(context)
        UnicumRepository.migrateCustomFoodsFromPrefs(context)
        UserSettings.onCustomFoodsLoaded(UnicumRepository.loadCustomFoods(context))
        reload(context)
    }

    // I pasti vanno anche su Health Connect (se c'è il permesso di scrittura), dopo aver aggiornato la UI
    fun addMeals(context: Context, newMeals: List<Meal>) {
        val date = selectedDate
        scope.launch {
            val ids = UnicumRepository.insertMeals(context, date, newMeals)
            reload(context)
            HealthConnectWriter.upsertMeals(context, UnicumRepository.mealEntities(context, ids))
        }
    }

    /** Salva le modifiche a un alimento già registrato (quantità, tipo di pasto, orario). */
    fun updateMeal(context: Context, meal: Meal) {
        val date = selectedDate
        scope.launch {
            UnicumRepository.updateMeal(context, date, meal)
            reload(context)
            HealthConnectWriter.upsertMeals(context, UnicumRepository.mealEntities(context, listOf(meal.id)))
        }
    }

    /** Un'eliminazione appena fatta: l'avviso in basso la mostra con "Annulla" per qualche secondo. */
    class UndoAction(val message: String, val undo: () -> Unit)

    /** L'ultima eliminazione annullabile (pasto o allenamento manuale); null = nessun avviso. */
    var undo by mutableStateOf<UndoAction?>(null)
        private set

    /** L'avviso è sparito (tempo scaduto, annullato o sostituito da un'altra eliminazione). */
    fun clearUndo(action: UndoAction) {
        if (undo === action) undo = null
    }

    fun removeMeal(context: Context, meal: Meal) {
        val date = selectedDate
        scope.launch {
            UnicumRepository.deleteMeal(context, meal.id)
            reload(context)
            HealthConnectWriter.deleteMeals(context, listOf(meal.id))
        }
        // Annulla: lo stesso alimento torna con lo stesso id (e lo stesso record in Health Connect)
        undo = UndoAction(tr("«%s» eliminato", meal.name)) {
            scope.launch {
                val ids = UnicumRepository.insertMeals(context, date, listOf(meal))
                reload(context)
                HealthConnectWriter.upsertMeals(context, UnicumRepository.mealEntities(context, ids))
            }
        }
    }

    /** Solo gli allenamenti scritti a mano: quelli di Health Connect tornerebbero alla sincronizzazione. */
    fun removeActivity(context: Context, activity: Activity) {
        if (!activity.isManual) return
        val date = selectedDate
        scope.launch {
            UnicumRepository.deleteActivity(context, activity.id)
            reload(context)
        }
        undo = UndoAction(tr("«%s» eliminato", tr(activity.type))) {
            scope.launch {
                UnicumRepository.insertActivities(context, date, listOf(activity))
                reload(context)
            }
        }
    }

    fun addActivity(context: Context, activity: Activity) {
        val date = selectedDate
        scope.launch {
            UnicumRepository.insertActivity(context, date, activity)
            reload(context)
        }
    }

    // ------------------------------------------------------------------
    // Health Connect
    // ------------------------------------------------------------------

    /**
     * Sincronizza ora con Health Connect (se già autorizzato) e aggiorna la UI. Con [withNotion] rilegge nello
     * stesso momento anche il programma e il registro delle corse da Notion (tasto "Sincronizza ora").
     */
    fun sync(context: Context, days: Int = 30, withNotion: Boolean = true) {
        if (syncing) return
        syncing = true
        if (withNotion) scope.launch {
            NotionPlan.sync(context)
            reload(context)
        }
        scope.launch {
            // La prima volta con il permesso dello storico si scarica un anno intero (sonno, HRV, metriche)
            val history = UserSettings.needsHealthHistory &&
                (HealthConnectSource.status(context) as? HealthConnectSource.Status.Available)?.historyGranted == true
            val span = if (history) maxOf(days, HealthConnectSource.HISTORY_DAYS) else days
            val result = try {
                HealthConnectSource.sync(context, span)
            } finally {
                withContext(Dispatchers.Main) { syncing = false }
            }
            if (result.error == null) {
                UserSettings.markSynced()
                if (span >= 30) UserSettings.markStagesResyncDone()
                if (history) UserSettings.markHealthHistoryDone()
            }
            UnicumRepository.resolvePendingBackup(context)
            withContext(Dispatchers.Main) { lastSyncResult = result }
            reload(context)
        }
    }

    /** true mentre gira l'aggiornamento "tira verso il basso" (sincronizzazione o semplice rilettura). */
    var refreshing by mutableStateOf(false)
        private set

    /**
     * Aggiornamento manuale (pagina tirata verso il basso): sincronizza Health Connect, se è collegato e
     * autorizzato, e nello stesso momento rilegge Notion; poi ricarica i dati. La rotella resta finché non hanno
     * finito entrambi, e almeno un istante, così si vede che è partito.
     */
    fun refresh(context: Context) {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val started = System.currentTimeMillis()
            try {
                coroutineScope {
                    launch { NotionPlan.sync(context) }
                    launch {
                        val status = HealthConnectSource.status(context)
                        if (status is HealthConnectSource.Status.Available && status.anyGranted) {
                            val result = HealthConnectSource.sync(context, days = 7)
                            if (result.error == null) UserSettings.markSynced()
                            UnicumRepository.resolvePendingBackup(context)
                            withContext(Dispatchers.Main) { lastSyncResult = result }
                        }
                    }
                }
                reload(context)
            } finally {
                val left = 700 - (System.currentTimeMillis() - started)
                if (left > 0) kotlinx.coroutines.delay(left)
                withContext(Dispatchers.Main) { refreshing = false }
            }
        }
    }

    /** Sincronizza all'apertura dell'app se ci sono permessi e l'ultima volta è passato un po'. */
    fun autoSync(context: Context, minIntervalMillis: Long = 10 * 60_000L) {
        scope.launch { NotionPlan.sync(context, force = false) }
        // Backup automatico: se è passato un giorno dall'ultimo
        scope.launch { AutoBackup.run(context) }
        val resync = (UserSettings.needsStagesResync || UserSettings.needsHealthHistory) && UserSettings.lastSyncMillis != 0L
        if (!resync && System.currentTimeMillis() - UserSettings.lastSyncMillis < minIntervalMillis) return
        scope.launch {
            val status = HealthConnectSource.status(context)
            if (status is HealthConnectSource.Status.Available && status.anyGranted) {
                // Prima sincronizzazione: 30 giorni, o un anno se c'è il permesso dello storico (lo decide sync);
                // poi bastano gli ultimi 7. Lo storico manca ancora solo se ora si può leggere.
                val history = UserSettings.needsHealthHistory && status.historyGranted
                val days = if (UserSettings.lastSyncMillis == 0L || UserSettings.needsStagesResync || history) 30 else 7
                if (!history && !UserSettings.needsStagesResync &&
                    System.currentTimeMillis() - UserSettings.lastSyncMillis < minIntervalMillis) return@launch
                withContext(Dispatchers.Main) { sync(context, days, withNotion = false) }
            }
        }
    }

    /** Calorie bruciate oltre il metabolismo basale nel giorno mostrato (vedi [DayEnergy.extraBurn]). */
    val extraBurnKcal: Int
        get() = DayEnergy.extraBurn(
            activeKcalHealthConnect = health.metric(MetricType.ACTIVE_KCAL, selectedDate),
            importedWorkoutKcal = dayData.activities.filter { !it.isManual }.sumOf { it.calories },
            manualWorkoutKcal = dayData.activities.filter { it.isManual }.sumOf { it.calories }
        )

    val steps: Int?
        get() = health.metric(MetricType.STEPS, selectedDate)?.toInt()

    /**
     * La notte che finisce nel giorno mostrato. Se è oggi e la notte di oggi non c'è ancora,
     * si mostra quella di ieri.
     */
    val lastNight: SleepNight?
        get() {
            val night = health.sleep.firstOrNull { it.date == selectedDate }
            if (night != null || !isToday) return night
            val yesterday = DateUtils.lastDays(2).first()
            return health.sleep.firstOrNull { it.date == yesterday }
        }

    /** Minuti dormiti nei 7 giorni della settimana (0 dove manca il dato). */
    val sleepWeekMinutes: List<Int>
        get() = week.map { day -> health.sleep.firstOrNull { it.date == day.date }?.durationMinutes ?: 0 }

    /** Valore più recente di una metrica nei 3 giorni fino a quello mostrato. */
    fun latestMetric(type: String): Double? =
        UnicumRepository.lastDays(3, selectedDate).reversed().firstNotNullOfOrNull { health.metric(type, it) }

    /** Ultima temperatura notturna: (valore, true se è solo lo scarto dalla base personale). */
    fun latestNightTemp(): Pair<Double, Boolean>? {
        latestMetric(MetricType.NIGHT_TEMP)?.let { return it to false }
        return latestMetric(MetricType.NIGHT_TEMP_DELTA)?.let { it to true }
    }

    /** Media della metrica nei giorni precedenti all'ultimo valore (serve almeno 3 giorni di dati). */
    fun metricBaseline(type: String): Double? {
        val byDate = health.metrics[type] ?: return null
        val dates = byDate.keys.sorted()
        if (dates.size < 4) return null
        return dates.dropLast(1).map { byDate.getValue(it) }.average()
    }

    /**
     * Avviso sul recupero per il giorno [date] (HRV bassa da due notti e/o temperatura notturna alta rispetto
     * alla tua media), oppure null. Se la fonte dà solo lo scarto di temperatura, si usa quello.
     */
    fun recoveryAlert(data: HealthData = health, date: String = selectedDate): RecoveryAlert? =
        RecoveryAlerts.fromHealth(data, java.time.LocalDate.parse(date))

    /** Dopo un'importazione: rilegge i dati e riscrive su Health Connect i pasti nuovi. */
    suspend fun afterImport(context: Context) {
        reload(context)
        val range = UnicumRepository.lastDays(30)
        HealthConnectWriter.upsertMeals(context, UnicumRepository.mealEntitiesBetween(context, range.first(), range.last()))
    }

    // Una rilettura alla volta: due insieme potrebbero finire in ordine inverso e lasciare a schermo i dati vecchi
    private val reloadMutex = Mutex()

    /** Rilegge dal database (per quando l'app torna in primo piano: ad app chiusa può essere cambiato qualcosa). */
    suspend fun reloadFromDatabase(context: Context) {
        if (isInitialized) reload(context)
    }

    // Rilegge la giornata dal database e aggiorna lo stato osservato dalla UI
    private suspend fun reload(context: Context): Unit = reloadMutex.withLock {
        val day = selectedDate
        val meals = UnicumRepository.loadMeals(context, day)
        val activities = UnicumRepository.loadActivities(context, day)
        val weekSummary = UnicumRepository.loadWeek(context, day)
        val healthData = UnicumRepository.loadHealth(context, 14, day)

        withContext(Dispatchers.Main) {
            // Nel frattempo è stato scelto un altro giorno: questi dati sono vecchi, ci pensa il reload di quel giorno
            if (day != selectedDate) return@withContext
            dayData.meals.clear()
            dayData.meals.addAll(meals)

            dayData.activities.clear()
            dayData.activities.addAll(activities)

            week = weekSummary
            health = healthData
        }
        // Dati di oggi aggiornati: widget ridisegnato e, se il recupero è anomalo, una notifica (al massimo una al giorno)
        if (day == UnicumRepository.today()) {
            UnicumWidget.refresh(context)
            recoveryAlert(healthData, day)?.let { RecoveryAlertNotifier.notifyOnce(context, it) }
        }
    }
}
