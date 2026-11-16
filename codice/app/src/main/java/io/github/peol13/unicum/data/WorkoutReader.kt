package io.github.peol13.unicum.data

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.reflect.KClass

/**
 * Legge da Health Connect il dettaglio di un allenamento: totali (distanza, calorie, passi, dislivello,
 * battito medio/max) e serie nel tempo (battito, velocità, cadenza, percorso GPS), più il VO₂ max.
 * Si chiede tutto alla stessa app che ha scritto la sessione, così non si mescolano dispositivi diversi.
 */
internal object WorkoutReader {
    private const val TAG = "UnicumWorkout"
    /** Oltre questo numero di punti le serie vengono mediate a intervalli regolari. */
    private const val MAX_SAMPLES = 1800

    suspend fun read(
        client: HealthConnectClient,
        r: ExerciseSessionRecord,
        type: String,
        zone: ZoneId,
        can: (KClass<out Record>) -> Boolean,
        /** Chiamata per ogni lettura fallita (la sessione resta incompleta e va riletta). */
        onError: (Throwable) -> Unit = {}
    ): WorkoutDetail {
        val origin = setOf(DataOrigin(r.metadata.dataOrigin.packageName))
        val start = r.startTime
        val range = TimeRangeFilter.between(r.startTime, r.endTime)
        fun sec(t: Instant) = Duration.between(start, t).seconds.toInt()

        suspend fun <T : Any> aggregate(metric: AggregateMetric<T>): T? = runCatching {
            client.aggregate(AggregateRequest(setOf(metric), range, origin))[metric]
        }.onFailure(onError).getOrNull()

        // I campioni fuori dalla sessione (un record può iniziare prima) si scartano
        fun inSession(t: Instant) = !t.isBefore(r.startTime) && !t.isAfter(r.endTime)
        val hr = if (can(HeartRateRecord::class)) series<HeartRateRecord>(client, r, origin, onError)
            .flatMap { rec -> rec.samples.filter { inSession(it.time) }.map { WorkoutSample(sec(it.time), it.beatsPerMinute.toFloat()) } }
        else emptyList()
        val speed = if (can(SpeedRecord::class)) series<SpeedRecord>(client, r, origin, onError)
            .flatMap { rec -> rec.samples.filter { inSession(it.time) }.map { WorkoutSample(sec(it.time), it.speed.inMetersPerSecond.toFloat()) } }
        else emptyList()
        val cadence = if (can(StepsCadenceRecord::class)) series<StepsCadenceRecord>(client, r, origin, onError)
            .flatMap { rec -> rec.samples.filter { inSession(it.time) }.map { WorkoutSample(sec(it.time), it.rate.toFloat()) } }
        else emptyList()

        val distance = if (can(DistanceRecord::class)) aggregate(DistanceRecord.DISTANCE_TOTAL)?.inMeters else null
        val active = if (can(ActiveCaloriesBurnedRecord::class)) aggregate(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories else null
        val kcal = active?.takeIf { it > 0 }
            ?: if (can(TotalCaloriesBurnedRecord::class)) aggregate(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories else null
        // Samsung Health non lega i passi alla sessione: se mancano si prendono quelli di tutte le fonti nell'intervallo
        val steps = if (can(StepsRecord::class)) aggregate(StepsRecord.COUNT_TOTAL)
            ?: runCatching { client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL] }
                .onFailure { Log.w(TAG, "passi non letti", it) }.getOrNull()
        else null
        val elevation = if (can(ElevationGainedRecord::class)) aggregate(ElevationGainedRecord.ELEVATION_GAINED_TOTAL)?.inMeters?.takeIf { it > 0 } else null

        // VO₂ max: quello calcolato con la sessione (anche qualche ora dopo), altrimenti l'ultimo dei 60 giorni prima
        var vo2: Double? = null
        var vo2FromSession = false
        if (can(Vo2MaxRecord::class)) runCatching {
            val all = HealthConnectSource.readAll<Vo2MaxRecord>(client, start.minus(Duration.ofDays(60)), r.endTime.plus(Duration.ofHours(6)))
            val during = all.filter { it.time >= start }.minByOrNull { it.time }
            val before = all.filter { it.time < start }.maxByOrNull { it.time }
            vo2 = (during ?: before)?.vo2MillilitersPerMinuteKilogram
            vo2FromSession = during != null
        }

        val routeStatus: RouteStatus
        val route: List<RoutePoint>
        when (val rr = r.exerciseRouteResult) {
            is ExerciseRouteResult.Data -> {
                routeStatus = RouteStatus.DATA
                route = rr.exerciseRoute.route.map {
                    RoutePoint(sec(it.time), it.latitude, it.longitude, it.altitude?.inMeters?.toFloat())
                }
            }
            is ExerciseRouteResult.ConsentRequired -> { routeStatus = RouteStatus.CONSENT; route = emptyList() }
            else -> { routeStatus = RouteStatus.NONE; route = emptyList() }
        }

        val pauses = r.segments.filter {
            it.segmentType == ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE || it.segmentType == ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST
        }.sumOf { Duration.between(it.startTime, it.endTime).seconds }
        val total = Duration.between(r.startTime, r.endTime).seconds

        val detail = WorkoutDetail(
            externalId = r.metadata.id,
            source = r.metadata.dataOrigin.packageName,
            date = start.atZone(zone).toLocalDate().toString(),
            type = type,
            exerciseType = r.exerciseType,
            title = r.title?.takeIf { it.isNotBlank() },
            startMillis = r.startTime.toEpochMilli(),
            endMillis = r.endTime.toEpochMilli(),
            movingSeconds = (total - pauses).toInt().coerceAtLeast(1),
            automatic = r.metadata.recordingMethod == Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
            distanceM = distance,
            kcal = kcal,
            steps = steps,
            avgHr = hr.takeIf { it.isNotEmpty() }?.map { it.v }?.average()?.toInt(),
            maxHr = hr.maxOfOrNull { it.v }?.toInt(),
            minHr = hr.minOfOrNull { it.v }?.toInt(),
            elevationGainM = elevation,
            vo2max = vo2,
            vo2maxFromSession = vo2FromSession,
            maxSpeedMs = speed.maxOfOrNull { it.v }?.toDouble(),
            hr = downsample(hr),
            speed = downsample(speed),
            cadence = downsample(cadence),
            route = if (route.size > MAX_SAMPLES) route.filterIndexed { i, _ -> i % ceil(route.size / MAX_SAMPLES.toDouble()).toInt() == 0 } else route,
            routeStatus = routeStatus,
            laps = r.laps.map { WorkoutLap(sec(it.startTime), sec(it.endTime), it.length?.inMeters) },
            device = r.metadata.device?.let { listOfNotNull(it.manufacturer, it.model).joinToString(" ").ifBlank { null } }
        )
        Log.i(
            TAG,
            "${detail.date} $type (${r.exerciseType}) da ${detail.source} rec=${r.metadata.recordingMethod}: " +
                "dist=${distance?.toInt()} kcal=${kcal?.toInt()} passi=$steps disl=$elevation vo2=$vo2(sess=$vo2FromSession) " +
                "hr=${hr.size} speed=${speed.size} cad=${cadence.size} route=$routeStatus/${route.size} " +
                "giri=${r.laps.size} segmenti=${r.segments.size} device=${detail.device}"
        )
        return detail
    }

    /** Serie della stessa app della sessione; se quella app non ne scrive, quelle di qualunque fonte nello stesso intervallo. */
    private suspend inline fun <reified T : Record> series(
        client: HealthConnectClient,
        r: ExerciseSessionRecord,
        origin: Set<DataOrigin>,
        onError: (Throwable) -> Unit
    ): List<T> = runCatching {
        HealthConnectSource.readAll<T>(client, r.startTime, r.endTime, origin)
            .ifEmpty { HealthConnectSource.readAll<T>(client, r.startTime, r.endTime) }
    }.onFailure(onError).getOrDefault(emptyList())

    /** Medie a intervalli regolari quando i campioni sono troppi (es. un battito al secondo per due ore). */
    internal fun downsample(samples: List<WorkoutSample>, max: Int = MAX_SAMPLES): List<WorkoutSample> {
        val sorted = samples.sortedBy { it.t }
        if (sorted.size <= max) return sorted
        val span = (sorted.last().t - sorted.first().t).coerceAtLeast(1)
        val bucket = ceil(span / max.toDouble()).toInt().coerceAtLeast(1)
        return sorted.groupBy { (it.t - sorted.first().t) / bucket }
            .map { (_, l) -> WorkoutSample(l.first().t, l.map { it.v }.average().toFloat()) }
    }
}
