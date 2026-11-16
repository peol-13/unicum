package io.github.peol13.unicum.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import android.util.Log
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.peol13.unicum.data.db.ActivityEntity
import io.github.peol13.unicum.data.db.DailyMetricEntity
import io.github.peol13.unicum.data.db.SleepEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** Esito di una sincronizzazione, mostrato all'utente nel Profilo. */
data class SyncResult(
    val workouts: Int = 0,
    val sleepNights: Int = 0,
    val metrics: Int = 0,
    val mealsWritten: Int = 0,
    val skipped: List<String> = emptyList(),
    val error: String? = null
) {
    val isEmpty get() = workouts == 0 && sleepNights == 0 && metrics == 0
}

/**
 * Legge da Health Connect i dati scritti da Samsung Health, Ultrahuman e altre app e li salva
 * nel database locale. L'unica scrittura sono i pasti registrati in UNICUM (vedi [HealthConnectWriter]).
 */
object HealthConnectSource {
    const val SOURCE_AGGREGATE = "health_connect"

    private val readPermissions: Map<String, KClass<out Record>> = mapOf(
        "Passi" to StepsRecord::class,
        "Calorie attive" to ActiveCaloriesBurnedRecord::class,
        "Calorie totali" to TotalCaloriesBurnedRecord::class,
        "Distanza" to DistanceRecord::class,
        "Allenamenti" to ExerciseSessionRecord::class,
        "Battito" to HeartRateRecord::class,
        "HRV" to HeartRateVariabilityRmssdRecord::class,
        "Sonno" to SleepSessionRecord::class,
        "Temperatura cutanea" to SkinTemperatureRecord::class,
        "Temperatura corporea" to BodyTemperatureRecord::class,
        "Peso" to WeightRecord::class,
        "Grasso corporeo" to BodyFatRecord::class,
        "Velocità" to SpeedRecord::class,
        "Cadenza" to StepsCadenceRecord::class,
        "Dislivello" to ElevationGainedRecord::class,
        "VO₂ max" to Vo2MaxRecord::class
    )

    /**
     * Lettura di tutti i percorsi GPS (Android 15+). La libreria 1.1.0 non ha la costante: senza questo
     * permesso Health Connect chiede il consenso per ogni singolo allenamento.
     */
    const val PERMISSION_READ_EXERCISE_ROUTES = "android.permission.health.READ_EXERCISE_ROUTES"

    /** Lettura dei dati ad app chiusa (per l'aggiornamento automatico). */
    const val PERMISSION_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    /** Permessi da chiedere all'utente (stringhe di sistema): lettura dei dati e scrittura dei pasti. */
    val permissions: Set<String> =
        readPermissions.values.map { HealthPermission.getReadPermission(it) }.toSet() + HealthConnectWriter.writePermission +
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY +
        (if (android.os.Build.VERSION.SDK_INT >= 35) setOf(PERMISSION_READ_EXERCISE_ROUTES) else emptySet())

    sealed interface Status {
        /** Health Connect non è installato o va aggiornato. */
        data object Unavailable : Status
        data object UpdateRequired : Status
        /**
         * [backgroundSupported]: Health Connect di questo telefono permette di leggere ad app chiusa (Android 14+ con
         * Health Connect aggiornato); in quel caso serve anche [PERMISSION_BACKGROUND].
         */
        data class Available(val granted: Set<String>, val backgroundSupported: Boolean = false) : Status {
            /** I permessi da chiedere: quelli di sempre, più la lettura ad app chiusa se il telefono la offre. */
            val wanted: Set<String> get() = if (backgroundSupported) permissions + PERMISSION_BACKGROUND else permissions
            val allGranted get() = granted.containsAll(wanted)
            val anyGranted get() = granted.any { it in permissions }
            val backgroundGranted get() = PERMISSION_BACKGROUND in granted
            /** Si possono leggere i dati di prima dell'installazione (altrimenti Health Connect dà solo 30 giorni). */
            val historyGranted get() = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
        }
    }

    /** Giorni scaricati una volta sola, alla prima sincronizzazione con il permesso dello storico. */
    const val HISTORY_DAYS = 365

    /** Stato di Health Connect e permessi concessi. Non solleva eccezioni: se il servizio non risponde è "non disponibile". */
    suspend fun status(context: Context): Status = withContext(Dispatchers.IO) {
        try {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    val client = HealthConnectClient.getOrCreate(context)
                    val background = runCatching {
                        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
                    }.getOrDefault(false)
                    Status.Available(client.permissionController.getGrantedPermissions(), background)
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UpdateRequired
                else -> Status.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Status.Unavailable
        }
    }

    // Una sincronizzazione alla volta: due insieme leggerebbero e cancellerebbero i doppioni in parallelo
    private val syncMutex = Mutex()
    @Volatile private var runningDays = 0
    @Volatile private var lastResult: SyncResult? = null

    /**
     * Scarica gli ultimi [days] giorni (oggi compreso) e li salva. Non solleva eccezioni.
     * Se ne sta già girando una che copre lo stesso periodo, si aspetta quella e se ne restituisce l'esito.
     */
    suspend fun sync(context: Context, days: Int = 7): SyncResult {
        if (syncMutex.isLocked && runningDays >= days) {
            syncMutex.withLock { }
            lastResult?.let { return it }
        }
        return syncMutex.withLock {
            runningDays = days
            try {
                doSync(context, days).also {
                    lastResult = it
                    // L'HRV appena letto può rivelare (o smentire) un salto di scala
                    runCatching { UnicumRepository.refreshHrvShift(context) }
                }
            } finally {
                runningDays = 0
            }
        }
    }

    private suspend fun doSync(context: Context, days: Int): SyncResult = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            fun can(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

            val zone = ZoneId.systemDefault()
            val firstDay = LocalDate.now(zone).minusDays((days - 1).toLong())
            val now = Instant.now()
            val skipped = mutableListOf<String>()
            readPermissions.forEach { (label, type) -> if (!can(type)) skipped += label }
            if (HealthConnectWriter.writePermission !in granted) skipped += "Scrittura pasti"

            val metrics = mutableListOf<DailyMetricEntity>()
            val activities = mutableListOf<ActivityEntity>()
            val sleep = mutableListOf<SleepEntity>()

            // --- passi, calorie attive e distanza per giorno (Health Connect unisce le fonti senza doppi conteggi)
            runCatching {
                val wanted = buildSet {
                    if (can(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
                    if (can(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                    if (can(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
                }
                if (wanted.isNotEmpty()) {
                    val grouped = client.aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(
                            metrics = wanted,
                            timeRangeFilter = TimeRangeFilter.between(
                                LocalDateTime.of(firstDay, LocalTime.MIDNIGHT), LocalDateTime.now(zone)
                            ),
                            timeRangeSlicer = Period.ofDays(1)
                        )
                    )
                    for (g in grouped) {
                        val date = g.startTime.toLocalDate().toString()
                        g.result[StepsRecord.COUNT_TOTAL]?.let {
                            metrics += metric(date, MetricType.STEPS, it.toDouble(), SOURCE_AGGREGATE)
                        }
                        g.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let {
                            metrics += metric(date, MetricType.ACTIVE_KCAL, it.inKilocalories, SOURCE_AGGREGATE)
                        }
                        g.result[DistanceRecord.DISTANCE_TOTAL]?.let {
                            metrics += metric(date, MetricType.DISTANCE_M, it.inMeters, SOURCE_AGGREGATE)
                        }
                    }
                }
            }.onFailure { rethrowCancellation(it); skipped += "Passi/calorie (${it.javaClass.simpleName})" }

            // --- sonno (si parte dal giorno prima: la notte inizia prima della mezzanotte)
            var sleepRead = false
            if (can(SleepSessionRecord::class)) runCatching {
                val from = firstDay.minusDays(1).atStartOfDay(zone).toInstant()
                for (r in readAll<SleepSessionRecord>(client, from, now)) {
                    sleep += r.toEntity(zone)
                }
                sleepRead = true
            }.onFailure { rethrowCancellation(it); skipped += "Sonno (${it.javaClass.simpleName})" }

            // --- HRV e temperatura notturna: media giornaliera per fonte
            val from = firstDay.atStartOfDay(zone).toInstant()
            // La stessa notte mostrata nell'app (fonte preferita, poi la più lunga). Solo i giorni del periodo:
            // del giorno prima si legge solo una parte (un pisolino sostituirebbe la notte vera).
            val nights = sleep.filter { it.date >= firstDay.toString() }.groupBy { it.date }
                .mapValues { (_, l) -> UnicumRepository.pickNight(l) }
            if (can(HeartRateVariabilityRmssdRecord::class)) runCatching {
                // Dal giorno prima: i campioni della notte iniziano prima della mezzanotte
                val hrv = readAll<HeartRateVariabilityRmssdRecord>(client, firstDay.minusDays(1).atStartOfDay(zone).toInstant(), now)
                metrics += dailyAverages(hrv.filter { !it.time.isBefore(from) }, MetricType.HRV, zone,
                    { it.time }, { it.heartRateVariabilityMillis })
                // HRV della notte (per il recupero dopo gli allenamenti): solo i campioni dentro la notte di sonno
                nights.forEach { (date, night) ->
                    hrv.filter { it.time.toEpochMilli() in night.startMillis..night.endMillis }
                        .groupBy { it.metadata.dataOrigin.packageName }
                        .forEach { (src, list) ->
                            metrics += metric(date, MetricType.HRV_NIGHT, list.map { it.heartRateVariabilityMillis }.average(), src)
                        }
                }
            }.onFailure { rethrowCancellation(it); skipped += "HRV" }

            // Temperatura: la fonte registra tutto il giorno (circa ogni 10 minuti), quindi si fa la media
            // solo dei campioni dentro la notte di sonno (la più lunga di ogni giorno).
            // Con la base personale della fonte si ricava il valore assoluto, altrimenti resta lo scarto.
            class TempSample(val at: Long, val value: Double, val delta: Boolean, val source: String)
            val samples = mutableListOf<TempSample>()
            val tempFrom = firstDay.minusDays(1).atStartOfDay(zone).toInstant()

            if (can(SkinTemperatureRecord::class)) runCatching {
                readAll<SkinTemperatureRecord>(client, tempFrom, now).forEach { r ->
                    val base = r.baseline
                    val src = r.metadata.dataOrigin.packageName
                    r.deltas.forEach { d ->
                        val v = d.delta.inCelsius
                        samples += if (base != null) TempSample(d.time.toEpochMilli(), base.inCelsius + v, false, src)
                        else TempSample(d.time.toEpochMilli(), v, true, src)
                    }
                }
            }.onFailure { rethrowCancellation(it); skipped += "Temperatura cutanea" }

            if (can(BodyTemperatureRecord::class)) runCatching {
                readAll<BodyTemperatureRecord>(client, tempFrom, now).forEach { r ->
                    samples += TempSample(r.time.toEpochMilli(), r.temperature.inCelsius, false, r.metadata.dataOrigin.packageName)
                }
            }.onFailure { rethrowCancellation(it); skipped += "Temperatura corporea" }

            nights.forEach { (date, night) ->
                samples.filter { it.at in night.startMillis..night.endMillis }
                    .groupBy { it.source to it.delta }
                    .forEach { (key, list) ->
                        val type = if (key.second) MetricType.NIGHT_TEMP_DELTA else MetricType.NIGHT_TEMP
                        metrics += metric(date, type, list.map { it.value }.average(), key.first)
                    }
            }

            // --- peso e grasso corporeo: media giornaliera per fonte. Con il permesso "storico" si legge un anno
            // (per il grafico espanso nella Home), altrimenti solo il periodo della sincronizzazione.
            val bodyFrom = if (HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted)
                minOf(from, LocalDate.now(zone).minusDays(365).atStartOfDay(zone).toInstant()) else from
            if (can(WeightRecord::class)) runCatching {
                metrics += dailyAverages(readAll<WeightRecord>(client, bodyFrom, now), MetricType.WEIGHT_KG, zone,
                    { it.time }, { it.weight.inKilograms })
            }.onFailure { rethrowCancellation(it); skipped += "Peso" }

            if (can(BodyFatRecord::class)) runCatching {
                metrics += dailyAverages(readAll<BodyFatRecord>(client, bodyFrom, now), MetricType.BODY_FAT_PCT, zone,
                    { it.time }, { it.percentage.value })
            }.onFailure { rethrowCancellation(it); skipped += "Grasso corporeo" }

            // --- allenamenti
            // Per ognuno si legge anche il dettaglio (serie, percorso, VO₂ max) per la sezione Allenamenti.
            // Si scorre l'elenco di un anno (una lettura sola, leggera) ma si approfondiscono solo le sessioni
            // del periodo sincronizzato (la fonte può completarle dopo) e quelle nuove: così entrano anche le
            // corse vecchie aggiunte in seguito da un'altra app (es. Health Sync).
            // Le camminate non avviate a mano (riconosciute in automatico) restano solo in Attività.
            val details = mutableListOf<WorkoutDetail>()
            var workoutFromDate = firstDay
            // Letture fallite dentro una sessione (es. app finita in secondo piano): non devono rovinare i dati già salvati
            var readErrors = 0
            val onReadError: (Throwable) -> Unit = {
                rethrowCancellation(it); readErrors++; Log.w("UnicumWorkout", "lettura non riuscita", it)
            }
            // Sessioni che Health Connect ha ancora (per togliere quelle cancellate nella fonte)
            val presentIds = mutableSetOf<String>()
            var sessionsRead = false
            var windowSessions = 0
            if (can(ExerciseSessionRecord::class)) runCatching {
                // La prima volta si rilegge tutto l'anno
                val history = UserSettings.needsWorkoutHistory
                val canHistory = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
                if (canHistory) workoutFromDate = LocalDate.now(zone).minusDays(365)
                val workoutFrom = workoutFromDate.atStartOfDay(zone).toInstant()
                val known = UnicumRepository.workoutDetailIds(context) + UnicumRepository.importedActivityIds(context) +
                    UserSettings.duplicateSessionIds
                var failures = 0
                val modified = UnicumRepository.workoutModifiedTimes(context)
                // Dopo un giorno la fonte ha finito di scrivere le serie: se la sessione non è cambiata non si rilegge
                val settled = now.minus(Duration.ofDays(1))
                for (r in readAll<ExerciseSessionRecord>(client, workoutFrom, now)) {
                    presentIds += r.metadata.id
                    if (!r.startTime.isBefore(from)) windowSessions++
                    val changedAt = r.metadata.lastModifiedTime.toEpochMilli()
                    if (!history && r.metadata.id in known) {
                        if (r.startTime.isBefore(from)) continue
                        if (r.endTime.isBefore(settled) && modified[r.metadata.id] == changedAt) continue
                    }
                    val errorsBefore = readErrors
                    val entity = exerciseToEntity(client, r, zone, ::can, onReadError)
                    activities += entity
                    val autoWalk = entity.type == "Camminata" &&
                        r.metadata.recordingMethod != Metadata.RECORDING_METHOD_ACTIVELY_RECORDED
                    if (autoWalk) {
                        Log.i("UnicumWorkout", "${entity.date} camminata automatica esclusa (rec=${r.metadata.recordingMethod})")
                        continue
                    }
                    runCatching {
                        val d = WorkoutReader.read(client, r, entity.type, zone, ::can, onReadError)
                        // Letta senza errori: si ricorda la versione, così la prossima volta non si rilegge
                        details += d.copy(modifiedMillis = changedAt.takeIf { readErrors == errorsBefore })
                    }.onFailure { rethrowCancellation(it); failures++; Log.w("UnicumWorkout", "dettaglio non letto", it) }
                }
                sessionsRead = true
                // Se qualcosa non si è letto (es. app finita in secondo piano) lo storico si riprova la volta dopo
                if (history && canHistory && failures == 0 && readErrors == 0) UserSettings.markWorkoutHistoryDone()
            }.onFailure { rethrowCancellation(it); skipped += "Allenamenti (${it.javaClass.simpleName})" }

            // --- doppioni: la stessa sessione scritta da due app (es. Samsung Health e Health Sync) conterebbe
            // due volte minuti e calorie. Si confrontano le nuove con quelle già salvate e resta la fonte preferita;
            // le copie già nel database si cancellano. Il dettaglio delle copie resta (serve a completare la
            // sessione preferita, es. con il percorso GPS: vedi WorkoutAnalysis.merge).
            val stored = runCatching {
                UnicumRepository.importedActivitiesBetween(context, workoutFromDate.toString(), LocalDate.now(zone).toString())
            }.onFailure { rethrowCancellation(it) }.getOrDefault(emptyList())
            // Una lettura riuscita a metà (0 kcal, niente distanza o battito) non sostituisce i valori già salvati
            val storedById = stored.associateBy { it.externalId }
            for (i in activities.indices) {
                val a = activities[i]
                val old = storedById[a.externalId] ?: continue
                activities[i] = a.copy(
                    kcal = if (a.kcal > 0) a.kcal else old.kcal,
                    distanceMeters = a.distanceMeters ?: old.distanceMeters,
                    avgHeartRate = a.avgHeartRate ?: old.avgHeartRate
                )
            }
            runCatching {
                val existing = stored.filter { e -> activities.none { it.externalId == e.externalId } }
                fun startOf(a: ActivityEntity) =
                    LocalDate.parse(a.date).atStartOfDay(zone).toInstant().toEpochMilli() + (a.startMinute ?: 0) * 60_000L
                val all = (existing + activities).filter { it.startMinute != null && it.externalId != null }
                val kept = WorkoutAnalysis.dedupeSessions(
                    all, { it.type }, ::startOf, { startOf(it) + it.durationMinutes.coerceAtLeast(1) * 60_000L }, { it.source }
                ).toSet()
                val dropped = all.filter { it !in kept }
                activities.removeAll(dropped.toSet())
                UnicumRepository.deleteImportedActivities(context, dropped.mapNotNull { it.externalId })
                UserSettings.addDuplicateSessionIds(dropped.mapNotNull { it.externalId })
                if (dropped.isNotEmpty()) Log.i("UnicumWorkout", "doppioni tolti: ${dropped.size}")
            }.onFailure { rethrowCancellation(it); Log.w("UnicumWorkout", "doppioni non controllati", it) }

            // --- cancellati nella fonte: allenamenti e notti del periodo sincronizzato che Health Connect non ha più
            // si tolgono anche qui. Solo se la lettura è riuscita e ha restituito qualcosa: una risposta vuota
            // (dati di Health Connect azzerati) non cancella niente.
            if (sessionsRead && windowSessions > 0) runCatching {
                val gone = stored.filter { it.date >= firstDay.toString() }.mapNotNull { it.externalId }.filter { it !in presentIds }
                UnicumRepository.deleteImportedActivities(context, gone)
                val removed = UnicumRepository.deleteWorkoutDetailsNotIn(context, firstDay.toString(), presentIds)
                if (gone.isNotEmpty() || removed > 0) Log.i("UnicumWorkout", "cancellati nella fonte: ${gone.size} attività, $removed dettagli")
            }.onFailure { rethrowCancellation(it); Log.w("UnicumWorkout", "cancellati non controllati", it) }
            if (sleepRead && sleep.isNotEmpty()) runCatching {
                UnicumRepository.deleteSleepNotIn(
                    context, firstDay.toString(), LocalDate.now(zone).toString(), sleep.map { it.source to it.externalId }.toSet()
                )
            }.onFailure { rethrowCancellation(it); Log.w("UnicumWorkout", "notti cancellate non controllate", it) }

            UnicumRepository.saveHealthData(context, activities, sleep, metrics)
            UnicumRepository.saveWorkoutDetails(context, details)

            // --- pasti di UNICUM verso Health Connect (aggiorna anche quelli registrati prima del permesso)
            val mealsWritten = HealthConnectWriter.upsertMeals(
                context, UnicumRepository.mealEntitiesBetween(context, firstDay.toString(), LocalDate.now(zone).toString())
            )
            SyncResult(
                workouts = activities.size,
                sleepNights = sleep.size,
                metrics = metrics.size,
                mealsWritten = mealsWritten,
                skipped = skipped.distinct()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult(error = e.message ?: e.javaClass.simpleName)
        }
    }

    // ------------------------------------------------------------------

    private fun rethrowCancellation(t: Throwable) {
        if (t is CancellationException) throw t
    }

    private fun metric(date: String, type: String, value: Double, source: String) =
        DailyMetricEntity(date = date, type = type, value = value, source = source)

    /** Una misura al giorno per ogni fonte: la media dei campioni di quel giorno. */
    private fun <T : Record> dailyAverages(
        records: List<T>,
        type: String,
        zone: ZoneId,
        time: (T) -> Instant,
        value: (T) -> Double
    ): List<DailyMetricEntity> =
        records.groupBy { it.metadata.dataOrigin.packageName to time(it).atZone(zone).toLocalDate().toString() }
            .map { (key, list) -> metric(key.second, type, list.map(value).average(), key.first) }

    /** Legge tutte le pagine di record di un tipo nell'intervallo (solo delle app in [origins], se indicate). */
    internal suspend inline fun <reified T : Record> readAll(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
        origins: Set<DataOrigin> = emptySet()
    ): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    dataOriginFilter = origins,
                    pageToken = token
                )
            )
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }

    private fun SleepSessionRecord.toEntity(zone: ZoneId): SleepEntity {
        fun minutes(vararg types: Int) = stages.filter { it.stage in types }
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()

        val inBed = Duration.between(startTime, endTime).toMinutes().toInt()
        val deep = minutes(SleepSessionRecord.STAGE_TYPE_DEEP)
        val rem = minutes(SleepSessionRecord.STAGE_TYPE_REM)
        val light = minutes(SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING)
        val awake = minutes(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        )
        // Senza fasi (alcune app scrivono solo inizio e fine) si considera tutto il tempo come sonno
        val asleep = if (deep + rem + light > 0) deep + rem + light else inBed
        val timeline = SleepStages.normalize(
            stages.mapNotNull { s ->
                val type = when (s.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.PROFONDO
                    SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
                    SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageType.LEGGERO
                    SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageType.SVEGLIO
                    else -> null
                }
                type?.let { SleepStage(it, s.startTime.toEpochMilli(), s.endTime.toEpochMilli()) }
            },
            startTime.toEpochMilli(), endTime.toEpochMilli()
        )
        return SleepEntity(
            date = endTime.atZone(zone).toLocalDate().toString(),
            startMillis = startTime.toEpochMilli(),
            endMillis = endTime.toEpochMilli(),
            durationMinutes = asleep,
            deepMinutes = deep,
            remMinutes = rem,
            lightMinutes = light,
            awakeMinutes = awake,
            source = metadata.dataOrigin.packageName,
            externalId = metadata.id,
            stages = if (timeline.isEmpty()) null else SleepStages.encode(timeline)
        )
    }

    private suspend fun exerciseToEntity(
        client: HealthConnectClient,
        r: ExerciseSessionRecord,
        zone: ZoneId,
        can: (KClass<out Record>) -> Boolean,
        onError: (Throwable) -> Unit
    ): ActivityEntity {
        val origin = setOf(DataOrigin(r.metadata.dataOrigin.packageName))
        val range = TimeRangeFilter.between(r.startTime, r.endTime)

        suspend fun <T : Any> aggregate(metric: androidx.health.connect.client.aggregate.AggregateMetric<T>): T? =
            runCatching {
                client.aggregate(AggregateRequest(setOf(metric), range, origin))[metric]
            }.onFailure(onError).getOrNull()

        val active = if (can(ActiveCaloriesBurnedRecord::class))
            aggregate(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories else null
        // Alcune app scrivono solo le calorie totali della sessione
        val kcal = active?.takeIf { it > 0 }
            ?: if (can(TotalCaloriesBurnedRecord::class))
                aggregate(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories else null
        val distance = if (can(DistanceRecord::class)) aggregate(DistanceRecord.DISTANCE_TOTAL)?.inMeters else null
        val avgHr = if (can(HeartRateRecord::class)) aggregate(HeartRateRecord.BPM_AVG) else null

        return ActivityEntity(
            date = r.startTime.atZone(zone).toLocalDate().toString(),
            type = exerciseName(r),
            durationMinutes = Duration.between(r.startTime, r.endTime).toMinutes().toInt().coerceAtLeast(1),
            kcal = (kcal ?: 0.0).toInt(),
            source = r.metadata.dataOrigin.packageName,
            externalId = r.metadata.id,
            distanceMeters = distance?.toInt(),
            avgHeartRate = avgHr?.toInt(),
            startMinute = r.startTime.atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
        )
    }

    private fun exerciseName(r: ExerciseSessionRecord): String = when (r.exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Corsa"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Camminata"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Ciclismo"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Nuoto"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Palestra"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Escursionismo"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Calcio"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        else -> r.title?.takeIf { it.isNotBlank() } ?: "Allenamento"
    }
}
