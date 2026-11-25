package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.i18n.tk

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Conti sugli allenamenti (logica pura, con test): ritmo, parziali per km, zone di frequenza cardiaca,
 * curve di ritmo e altitudine, totali settimanali. Le serie vengono da [WorkoutDetail].
 */
object WorkoutAnalysis {

    // ---------- formati

    /** Ritmo in secondi al km; null se la distanza è troppo corta per dire qualcosa. */
    fun paceSecPerKm(distanceM: Double?, seconds: Int): Int? =
        distanceM?.takeIf { it >= 100 && seconds > 0 }?.let { (seconds / (it / 1000.0)).roundToInt() }

    /** 332 → "5'32\"" */
    fun formatPace(secPerKm: Int?): String =
        secPerKm?.let { "${it / 60}'${(it % 60).toString().padStart(2, '0')}\"" } ?: "–"

    /** 2530 → "42:10", 3730 → "1:02:10" */
    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        else "$m:${s.toString().padStart(2, '0')}"
    }

    /** 8432.0 → "8,43" (km con due decimali, virgola italiana). */
    fun formatKm(meters: Double?): String =
        meters?.let { "%.2f".format(io.github.peol13.unicum.i18n.I18n.locale, it / 1000.0) } ?: "–"

    // ---------- distanza nel tempo

    /** Distanza in metri tra due punti GPS (formula dell'emisenoverso). */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * Distanza percorsa nel tempo: lista di (secondi, metri cumulati). Dal percorso GPS se c'è, altrimenti
     * integrando la velocità. Viene riscalata sulla distanza totale della fonte, che è la più affidabile.
     * Provato sulla corsa del 5/11 contro la scheda "1 km" di Samsung Health: il GPS riscalato è più vicino
     * (scarto medio ~6 s a km) della velocità dell'orologio (~9 s).
     */
    fun distanceTimeline(d: WorkoutDetail): List<Pair<Int, Double>> {
        val raw = when {
            d.route.size >= 2 -> {
                var acc = 0.0
                listOf(d.route.first().t to 0.0) + d.route.zipWithNext { a, b ->
                    acc += haversine(a.lat, a.lon, b.lat, b.lon)
                    b.t to acc
                }
            }
            d.speed.size >= 2 -> {
                var acc = 0.0
                listOf(d.speed.first().t to 0.0) + d.speed.zipWithNext { a, b ->
                    // Buchi lunghi (pausa, sensore perso) non contano come corsa
                    val dt = (b.t - a.t).coerceIn(0, 30)
                    acc += (a.v + b.v) / 2.0 * dt
                    b.t to acc
                }
            }
            else -> return emptyList()
        }
        val measured = raw.last().second
        val total = d.distanceM
        if (measured <= 0) return emptyList()
        val k = if (total != null && total > 0) total / measured else 1.0
        return raw.map { it.first to it.second * k }
    }

    // ---------- parziali per km

    data class Split(
        val index: Int,
        /** Metri del parziale: 1000, tranne l'ultimo che può essere più corto. */
        val meters: Double,
        val seconds: Int,
        val paceSec: Int,
        val avgHr: Int?,
        /** Salita e discesa nel parziale, se il percorso ha l'altitudine. */
        val elevationDelta: Double?
    )

    fun splits(d: WorkoutDetail): List<Split> {
        val line = distanceTimeline(d)
        if (line.size < 2) return emptyList()
        val total = line.last().second
        // Tempo al quale si raggiunge ogni km (interpolando tra due punti)
        fun timeAt(meters: Double): Double {
            val i = line.indexOfFirst { it.second >= meters }
            if (i <= 0) return line.first().first.toDouble()
            val (t0, m0) = line[i - 1]
            val (t1, m1) = line[i]
            return if (m1 == m0) t1.toDouble() else t0 + (meters - m0) / (m1 - m0) * (t1 - t0)
        }
        val marks = generateSequence(0.0) { it + 1000.0 }.takeWhile { it < total }.toList() + total
        return marks.zipWithNext().mapIndexedNotNull { i, (from, to) ->
            val meters = to - from
            if (meters < 50) return@mapIndexedNotNull null
            val t0 = timeAt(from)
            val t1 = timeAt(to)
            val seconds = (t1 - t0).roundToInt().coerceAtLeast(1)
            val hrIn = d.hr.filter { it.t >= t0 && it.t <= t1 }.map { it.v }
            val alts = d.route.filter { it.t >= t0 && it.t <= t1 }.mapNotNull { it.alt }
            Split(
                index = i + 1,
                meters = meters,
                seconds = seconds,
                paceSec = (seconds / (meters / 1000.0)).roundToInt(),
                avgHr = hrIn.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                elevationDelta = if (alts.size >= 2) (alts.last() - alts.first()).toDouble() else null
            )
        }
    }

    // ---------- ripetute scritte dall'utente

    enum class IntervalKind(private val labelIt: String) {
        WARM_UP(tk("Riscaldamento")), WORK(tk("Veloce")), REST(tk("Recupero")), COOL_DOWN(tk("Defaticamento")), LAP(tk("Giro"));

        val label: String get() = tr(labelIt)
    }

    /**
     * Un pezzo della seduta a ripetute: [rep] = numero della ripetuta (per veloce e recupero) o del giro.
     * [lap] = pezzo di una struttura a giri ([IntervalPlan.laps]): si chiama "Giro N", veloce o no.
     */
    data class IntervalSegment(
        val kind: IntervalKind,
        val rep: Int?,
        val startS: Int,
        val endS: Int,
        val meters: Double,
        val paceSec: Int?,
        val avgHr: Int?,
        val maxHr: Int?,
        val lap: Boolean = false
    ) {
        val seconds: Int get() = endS - startS
    }

    /** Distanza nel tempo con ricerca binaria (le serie possono avere migliaia di punti). */
    private class Timeline(val line: List<Pair<Int, Double>>) {
        val end: Int = line.last().first

        fun distanceAt(t: Double): Double {
            var lo = 0
            var hi = line.size
            while (lo < hi) { val m = (lo + hi) / 2; if (line[m].first >= t) hi = m else lo = m + 1 }
            if (lo >= line.size) return line.last().second
            if (lo == 0) return line.first().second
            val (t0, m0) = line[lo - 1]
            val (t1, m1) = line[lo]
            return if (t1 == t0) m1 else m0 + (t - t0) / (t1 - t0) * (m1 - m0)
        }

        fun timeAt(meters: Double): Double {
            var lo = 0
            var hi = line.size
            while (lo < hi) { val m = (lo + hi) / 2; if (line[m].second >= meters) hi = m else lo = m + 1 }
            if (lo >= line.size) return Double.MAX_VALUE
            if (lo == 0) return line.first().first.toDouble()
            val (t0, m0) = line[lo - 1]
            val (t1, m1) = line[lo]
            return if (m1 == m0) t1.toDouble() else t0 + (meters - m0) / (m1 - m0) * (t1 - t0)
        }
    }

    /** Un pezzo della struttura collocato nel tempo: [fits] = false se la registrazione finisce prima. */
    private class Placed(val step: PlanStep, val t0: Double, val t1: Double)

    private fun layout(tl: Timeline, steps: List<PlanStep>, offset: Double): Pair<List<Placed>, Boolean> {
        val out = mutableListOf<Placed>()
        var t = offset
        var fits = true
        for (s in steps) {
            if (!s.part.isSet) continue
            if (t >= tl.end - 1) { fits = false; break }
            val raw = if (s.part.seconds != null && s.part.seconds > 0) t + s.part.seconds else tl.timeAt(tl.distanceAt(t) + s.part.meters!!)
            if (raw > tl.end + 1) fits = false
            val t1 = raw.coerceAtMost(tl.end.toDouble())
            out += Placed(s, t, t1)
            t = t1
        }
        return out to fits
    }

    private fun StepKind.toInterval(): IntervalKind = when (this) {
        StepKind.WARM_UP -> IntervalKind.WARM_UP
        StepKind.WORK -> IntervalKind.WORK
        StepKind.REST -> IntervalKind.REST
        StepKind.COOL_DOWN -> IntervalKind.COOL_DOWN
        StepKind.LAP -> IntervalKind.LAP
    }

    /**
     * Divide la registrazione secondo la struttura: ogni parte finisce dopo i suoi secondi o dopo i suoi metri
     * (letti dalla distanza nel tempo, come i parziali). Prima di [IntervalPlan.offsetS] c'è il riscaldamento
     * non scritto nella struttura; quello che avanza in fondo (almeno 20 s) è il defaticamento. Se la
     * registrazione finisce prima, l'ultima parte si accorcia.
     *
     * Con una struttura a giri ([IntervalPlan.laps]) i giri si tagliano uno dopo l'altro dall'inizio; quello che
     * avanza in fondo è un giro in più (sotto i 20 s si unisce all'ultimo).
     */
    fun intervals(d: WorkoutDetail, plan: IntervalPlan): List<IntervalSegment> {
        val line = distanceTimeline(d)
        val steps = plan.steps()
        if (line.size < 2 || steps.none { (it.kind == StepKind.WORK || it.kind == StepKind.LAP) && it.part.isSet }) return emptyList()
        val tl = Timeline(line)
        val (placed, _) = layout(tl, steps, plan.offsetS.toDouble().coerceIn(0.0, tl.end.toDouble()))
        if (plan.laps) return laps(d, tl, placed)
        val out = mutableListOf<IntervalSegment>()
        fun add(kind: IntervalKind, rep: Int?, t0: Double, t1: Double) {
            if (t1 - t0 >= 1) out += segment(d, kind, rep, t0, t1, tl.distanceAt(t1) - tl.distanceAt(t0))
        }
        var start = 0.0
        placed.forEachIndexed { i, p ->
            val kind = p.step.kind.toInterval()
            // Il riscaldamento non scritto si unisce a quello della struttura; uno breve (< 20 s) al primo pezzo
            val t0 = if (i == 0 && (kind == IntervalKind.WARM_UP || p.t0 < 20)) 0.0 else p.t0
            if (i == 0 && t0 > 0) add(IntervalKind.WARM_UP, null, 0.0, t0)
            // Il defaticamento scritto arriva fino alla fine della registrazione
            val t1 = if (i == placed.lastIndex && kind == IntervalKind.COOL_DOWN) tl.end.toDouble() else p.t1
            add(kind, p.step.rep, t0, t1)
            start = t1
        }
        if (tl.end - start >= 20) add(IntervalKind.COOL_DOWN, null, start, tl.end.toDouble())
        return out
    }

    /** Giri in ordine: quello che sta prima del primo (≥ 20 s) e quello che avanza in fondo sono giri anche loro. */
    private fun laps(d: WorkoutDetail, tl: Timeline, placed: List<Placed>): List<IntervalSegment> {
        if (placed.isEmpty()) return emptyList()
        val bounds = mutableListOf<Triple<StepKind, Double, Double>>()
        val first = placed.first().t0
        if (first >= 20) bounds += Triple(StepKind.LAP, 0.0, first)
        placed.forEachIndexed { i, p -> bounds += Triple(p.step.kind, if (i == 0 && first < 20) 0.0 else p.t0, p.t1) }
        val tail = tl.end - bounds.last().third
        if (tail >= 20) bounds += Triple(StepKind.LAP, bounds.last().third, tl.end.toDouble())
        else if (tail > 0) bounds[bounds.lastIndex] = bounds.last().copy(third = tl.end.toDouble())
        return bounds.filter { it.third - it.second >= 1 }.mapIndexed { i, (kind, t0, t1) ->
            segment(d, kind.toInterval(), i + 1, t0, t1, tl.distanceAt(t1) - tl.distanceAt(t0)).copy(lap = true)
        }
    }

    /**
     * Somma dei giri scritti a mano contro la corsa registrata: a tempo si confronta con la durata (quella con o
     * senza pause, la più vicina: i giri dell'orologio possono escludere le pause), a distanza con i km.
     * [ok] se lo scarto sta entro [LAPS_TOLERANCE_S] secondi o, a distanza, 50 m / 2%.
     */
    data class LapsTotal(val written: Double, val recorded: Double, val byKm: Boolean) {
        /** Positivo = i giri superano la corsa, negativo = ne manca un pezzo. */
        val diff: Double get() = written - recorded
        val tolerance: Double get() = if (byKm) maxOf(50.0, recorded * 0.02) else LAPS_TOLERANCE_S.toDouble()
        val ok: Boolean get() = abs(diff) <= tolerance
    }

    const val LAPS_TOLERANCE_S = 15

    /** null se non c'è nessun giro scritto o se i giri mescolano tempo e distanza. */
    fun lapsTotal(d: WorkoutDetail, parts: List<IntervalPart>): LapsTotal? {
        val set = parts.filter { it.isSet }
        if (set.isEmpty()) return null
        return when {
            set.all { (it.seconds ?: 0) > 0 } -> {
                val written = set.sumOf { it.seconds!! }.toDouble()
                val recorded = listOf(d.durationSeconds, d.movingSeconds).filter { it > 0 }
                    .minByOrNull { abs(it - written) }?.toDouble() ?: return null
                LapsTotal(written, recorded, byKm = false)
            }
            set.all { (it.meters ?: 0.0) > 0 } -> {
                val recorded = d.distanceM?.takeIf { it > 0 } ?: distanceTimeline(d).lastOrNull()?.second ?: return null
                LapsTotal(set.sumOf { it.meters!! }, recorded, byKm = true)
            }
            else -> null
        }
    }

    /**
     * Dove comincia la struttura nella registrazione (secondi), per una seduta del programma che non scrive il
     * riscaldamento a piedi: fra gli inizi possibili (ogni 5 s, fino a 25 minuti) quello in cui i tratti veloci
     * sono più veloci dei recuperi o, senza recuperi (progressivo), di quello che sta fuori dalla struttura.
     * 0 se la struttura non sta nella registrazione o non ci sono serie di distanza.
     */
    fun alignPlan(d: WorkoutDetail, steps: List<PlanStep>, maxOffsetS: Int = 25 * 60): Int {
        val line = distanceTimeline(d)
        if (line.size < 2) return 0
        val tl = Timeline(line)
        val hasRest = steps.any { it.kind == StepKind.REST && it.part.isSet }
        fun speed(parts: List<Pair<Double, Double>>): Double? {
            val sec = parts.sumOf { it.second - it.first }
            if (sec < 30) return null
            return parts.sumOf { tl.distanceAt(it.second) - tl.distanceAt(it.first) } / sec
        }
        fun score(o: Int): Double? {
            val (placed, fits) = layout(tl, steps, o.toDouble())
            if (!fits || placed.isEmpty()) return null
            val work = speed(placed.filter { it.step.kind == StepKind.WORK }.map { it.t0 to it.t1 }) ?: return null
            val other = if (hasRest) speed(placed.filter { it.step.kind == StepKind.REST }.map { it.t0 to it.t1 })
            else speed(listOf(0.0 to placed.first().t0, placed.last().t1 to tl.end.toDouble()))
            return work - (other ?: 0.0)
        }
        var best = 0
        var bestScore = score(0) ?: Double.NEGATIVE_INFINITY
        var o = 5
        while (o <= maxOffsetS && o < tl.end) {
            val sc = score(o) ?: break // più avanti la struttura non ci sta più
            if (sc > bestScore + 1e-4) { best = o; bestScore = sc }
            o += 5
        }
        return best
    }

    /** La struttura con la FC media di tutti i tratti veloci (campioni insieme), da salvare con le ripetute. */
    fun withWorkHr(d: WorkoutDetail, plan: IntervalPlan): IntervalPlan {
        val work = intervals(d, plan).filter { it.kind == IntervalKind.WORK }
        val hr = d.hr.filter { s -> work.any { s.t in it.startS until it.endS } }.map { it.v }
        return plan.copy(workHr = hr.takeIf { it.isNotEmpty() }?.average()?.roundToInt())
    }

    private fun segment(d: WorkoutDetail, kind: IntervalKind, rep: Int?, t0: Double, t1: Double, meters: Double): IntervalSegment {
        val start = t0.roundToInt()
        val stop = t1.roundToInt()
        val hr = d.hr.filter { it.t in start until stop }.map { it.v }
        return IntervalSegment(
            kind, rep, start, stop, meters,
            paceSec = paceSecPerKm(meters, stop - start),
            avgHr = hr.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            maxHr = hr.maxOrNull()?.roundToInt()
        )
    }

    /**
     * Il tratto della registrazione che corrisponde alla corsa vera quando l'utente corregge durata e distanza
     * (l'orologio ha registrato anche riscaldamento o cammino): la finestra di [seconds] secondi in cui si sono
     * percorsi i metri più vicini a [meters]. Restituisce inizio e fine in secondi, null senza serie di distanza.
     */
    fun findSegment(d: WorkoutDetail, seconds: Int, meters: Double): Pair<Int, Int>? {
        val line = distanceTimeline(d)
        if (line.size < 2 || seconds <= 0) return null
        val last = line.last().first
        if (seconds >= last - line.first().first) return line.first().first to last
        fun distAt(t: Double): Double {
            var lo = 0
            var hi = line.lastIndex
            while (hi - lo > 1) { val mid = (lo + hi) / 2; if (line[mid].first <= t) lo = mid else hi = mid }
            val (t0, m0) = line[lo]
            val (t1, m1) = line[hi]
            return if (t1 == t0) m1 else m0 + (m1 - m0) * ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
        }
        var best: Pair<Int, Int>? = null
        var bestErr = Double.MAX_VALUE
        var start = line.first().first
        while (start + seconds <= last) {
            val err = abs(distAt((start + seconds).toDouble()) - distAt(start.toDouble()) - meters)
            if (err < bestErr) { bestErr = err; best = start to start + seconds }
            start += 5
        }
        return best
    }

    /** FC media e massima dentro un tratto (secondi dall'inizio). */
    fun hrIn(d: WorkoutDetail, segment: Pair<Int, Int>): Pair<Int, Int>? =
        d.hr.filter { it.t in segment.first..segment.second }.map { it.v }
            .takeIf { it.isNotEmpty() }?.let { it.average().roundToInt() to it.max().roundToInt() }

    // ---------- curve nel tempo

    /**
     * Ritmo (secondi al km) nel tempo, mediato su [windowSec] secondi per togliere il rumore del GPS.
     * I tratti quasi fermi (sotto 1,5 km/h) non vengono disegnati.
     */
    fun paceSeries(d: WorkoutDetail, windowSec: Int = 30): List<WorkoutSample> {
        val speeds: List<WorkoutSample> = if (d.speed.size >= 2) d.speed else {
            val line = distanceTimeline(d)
            line.zipWithNext { a, b ->
                val dt = b.first - a.first
                WorkoutSample(b.first, if (dt > 0) ((b.second - a.second) / dt).toFloat() else 0f)
            }
        }
        if (speeds.isEmpty()) return emptyList()
        return smooth(speeds, windowSec)
            .filter { it.v > 0.42f }
            .map { WorkoutSample(it.t, (1000f / it.v)) }
    }

    /** Media mobile su una finestra di [windowSec] secondi centrata su ogni campione. */
    fun smooth(samples: List<WorkoutSample>, windowSec: Int): List<WorkoutSample> {
        if (samples.size < 3 || windowSec <= 1) return samples
        val half = windowSec / 2
        var lo = 0
        var hi = 0
        var sum = 0.0
        return samples.map { s ->
            while (hi < samples.size && samples[hi].t <= s.t + half) { sum += samples[hi].v; hi++ }
            while (samples[lo].t < s.t - half) { sum -= samples[lo].v; lo++ }
            WorkoutSample(s.t, (sum / (hi - lo)).toFloat())
        }
    }

    /** Altitudine nel tempo dal percorso GPS (vuota se la fonte non la dà). */
    fun altitudeSeries(d: WorkoutDetail): List<WorkoutSample> =
        smooth(d.route.mapNotNull { p -> p.alt?.let { WorkoutSample(p.t, it) } }, 20)

    /**
     * Dislivello positivo dall'altitudine del percorso: la curva viene addolcita e si contano le salite
     * solo oltre 2 m (isteresi), altrimenti il rumore del GPS gonfia il totale. null senza altitudine.
     */
    fun elevationGain(d: WorkoutDetail): Double? {
        val alt = altitudeSeries(d)
        if (alt.size < 2) return null
        var gain = 0.0
        var ref = alt.first().v
        for (s in alt) {
            if (s.v - ref >= 2f) { gain += s.v - ref; ref = s.v }
            else if (s.v < ref) ref = s.v
        }
        return gain
    }

    /** Cadenza media (passi al minuto): dai campioni, altrimenti dai passi della sessione. */
    fun averageCadence(d: WorkoutDetail): Int? =
        d.cadence.filter { it.v > 0 }.takeIf { it.isNotEmpty() }?.map { it.v }?.average()?.roundToInt()
            ?: d.avgCadence
            ?: d.steps?.takeIf { it > 0 && d.movingSeconds > 60 }?.let { (it * 60.0 / d.movingSeconds).roundToInt() }

    /** Ritmo medio sul tempo in movimento. */
    fun averagePace(d: WorkoutDetail): Int? = paceSecPerKm(d.distanceM, d.movingSeconds)

    /** Il km più veloce tra i parziali completi. */
    fun bestSplit(splits: List<Split>): Split? = splits.filter { it.meters >= 990 }.minByOrNull { it.paceSec }

    // ---------- zone di frequenza cardiaca

    data class HrZone(val number: Int, val name: String, val fromBpm: Int, val toBpm: Int, val seconds: Int)

    private val zoneNames = listOf(tk("Riscaldamento"), tk("Facile"), tk("Aerobica"), tk("Soglia"), tk("Massimale"))

    /** FC massima stimata dall'età (formula di Tanaka: 208 − 0,7 × età). */
    fun estimatedMaxHr(age: Int): Int = (208 - 0.7 * age).roundToInt()

    /** Tempo in ciascuna delle 5 zone (50-60-70-80-90-100% della FC massima). Buchi oltre 30 s non contano. */
    fun hrZones(samples: List<WorkoutSample>, maxHr: Int): List<HrZone> {
        val bounds = listOf(.5, .6, .7, .8, .9, 1.0).map { (it * maxHr).roundToInt() }
        val seconds = IntArray(5)
        samples.sortedBy { it.t }.zipWithNext { a, b ->
            val dt = (b.t - a.t).coerceIn(0, 30)
            val z = bounds.indexOfLast { a.v >= it }.coerceAtMost(4)
            if (z >= 0) seconds[z] += dt
        }
        return (0 until 5).map { HrZone(it + 1, tr(zoneNames[it]), bounds[it], bounds[it + 1], seconds[it]) }
    }

    // ---------- doppioni tra app

    /**
     * Lo stesso allenamento scritto da due app (es. Samsung Health e Health Sync): sessioni dello stesso tipo
     * che si sovrappongono per più di metà della più corta. Resta quella della fonte preferita, a parità quella
     * con più dati (distanza, battito). Ordine di uscita: dal più recente.
     */
    fun dedupe(workouts: List<WorkoutDetail>): List<WorkoutDetail> {
        val ordered = workouts.sortedWith(
            compareBy<WorkoutDetail> { SourceInfo.rank(it.source) }
                .thenByDescending { (if (it.distanceM != null) 2 else 0) + (if (it.avgHr != null) 1 else 0) }
        )
        val groups = mutableListOf<MutableList<WorkoutDetail>>()
        for (w in ordered) {
            val group = groups.firstOrNull { sameSession(it.first(), w) }
            if (group != null) group += w else groups += mutableListOf(w)
        }
        return groups.map { merge(it.first(), it.drop(1)) }.sortedByDescending { it.startMillis }
    }

    /**
     * Versione generica per qualunque sessione con tipo, orari e fonte (es. le attività da salvare):
     * restituisce quelle da tenere, una per allenamento, preferendo la fonte con [SourceInfo.rank] più basso.
     */
    fun <T> dedupeSessions(
        items: List<T>,
        type: (T) -> String,
        start: (T) -> Long,
        end: (T) -> Long,
        source: (T) -> String
    ): List<T> {
        val kept = mutableListOf<T>()
        for (w in items.sortedBy { SourceInfo.rank(source(it)) }) {
            val dup = kept.any { k ->
                type(k) == type(w) && run {
                    val overlap = minOf(end(k), end(w)) - maxOf(start(k), start(w))
                    val shorter = minOf(end(k) - start(k), end(w) - start(w))
                    shorter > 0 && overlap > shorter / 2
                }
            }
            if (!dup) kept += w
        }
        return kept
    }

    /** Stesso tipo e sovrapposte per più di metà della più corta. */
    fun sameSession(a: WorkoutDetail, b: WorkoutDetail): Boolean {
        if (a.type != b.type) return false
        val overlap = minOf(a.endMillis, b.endMillis) - maxOf(a.startMillis, b.startMillis)
        val shorter = minOf(a.endMillis - a.startMillis, b.endMillis - b.startMillis)
        return shorter > 0 && overlap > shorter / 2
    }

    /**
     * Completa la sessione preferita con quello che manca e un doppione ha: es. Samsung Health non condivide
     * il percorso GPS, ma la stessa corsa scritta da Health Sync sì. Le serie si spostano sull'orario
     * della sessione preferita.
     */
    fun merge(primary: WorkoutDetail, others: List<WorkoutDetail>): WorkoutDetail {
        var d = primary
        for (o in others) {
            val shift = ((o.startMillis - d.startMillis) / 1000).toInt()
            fun List<WorkoutSample>.moved() = map { it.copy(t = it.t + shift) }
            d = d.copy(
                distanceM = d.distanceM ?: o.distanceM,
                kcal = d.kcal ?: o.kcal,
                steps = d.steps ?: o.steps,
                avgHr = d.avgHr ?: o.avgHr,
                maxHr = d.maxHr ?: o.maxHr,
                minHr = d.minHr ?: o.minHr,
                // Alcune app scrivono 0 per "non misurato"
                elevationGainM = d.elevationGainM?.takeIf { it > 0 } ?: o.elevationGainM?.takeIf { it > 0 },
                vo2max = d.vo2max ?: o.vo2max,
                maxSpeedMs = d.maxSpeedMs ?: o.maxSpeedMs,
                avgCadence = d.avgCadence ?: o.avgCadence,
                hr = d.hr.ifEmpty { o.hr.moved() },
                speed = d.speed.ifEmpty { o.speed.moved() },
                cadence = d.cadence.ifEmpty { o.cadence.moved() },
                route = d.route.ifEmpty { o.route.map { it.copy(t = it.t + shift) } },
                routeStatus = if (d.routeStatus == RouteStatus.DATA || d.route.isNotEmpty()) d.routeStatus
                    else if (o.routeStatus == RouteStatus.DATA) RouteStatus.DATA
                    else if (o.routeStatus == RouteStatus.CONSENT) RouteStatus.CONSENT else d.routeStatus,
                laps = d.laps.ifEmpty { o.laps },
                device = d.device ?: o.device
            )
        }
        return d
    }

    // ---------- storico

    data class WeekTotal(val monday: LocalDate, val meters: Double, val count: Int, val seconds: Int)

    /** Totali delle ultime [weeks] settimane (dalla più vecchia), anche quelle senza allenamenti. */
    fun weeklyTotals(workouts: List<WorkoutDetail>, weeks: Int, today: LocalDate): List<WeekTotal> {
        val current = DateUtils.weekStart(today)
        val byWeek = workouts.groupBy { DateUtils.weekStart(LocalDate.parse(it.date)) }
        return ((weeks - 1) downTo 0).map { i ->
            val monday = current.minusWeeks(i.toLong())
            val list = byWeek[monday].orEmpty()
            WeekTotal(monday, list.sumOf { it.distanceM ?: 0.0 }, list.size, list.sumOf { it.movingSeconds })
        }
    }
}
