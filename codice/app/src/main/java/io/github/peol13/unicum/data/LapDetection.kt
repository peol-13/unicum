package io.github.peol13.unicum.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Giri di una seduta strutturata (es. 2 × (6' + 2') + 25') stimati incrociando il programma con i dati veri, perché
 * i giri dell'orologio non arrivano in Health Connect. Logica pura, con test.
 *
 * 1. **Zona del cambio**: la corsa si divide in pezzi a velocità quasi costante (programmazione dinamica sulla
 *    velocità e, con peso [W_HR], sul battito spostato indietro di [HR_LAG_S] perché arriva in ritardo). Ogni pezzo
 *    può durare tra [MIN_SHARE] e [MAX_SHARE] volte quello del programma e allontanarsi dalla durata prevista costa
 *    ([W_PRIOR]); prima della struttura può esserci un riscaldamento non scritto e dopo un resto.
 * 2. **Inizio del cambio**: dopo il cambio la velocità non salta, scende o sale in 20-40 s; il giro comincia quando
 *    il ritmo *comincia* a cambiare. Intorno a ogni confine (±[REFINE_S]) si adatta piatto + rampa (0-[MAX_RAMP_S]) +
 *    piatto e si prende l'inizio della rampa.
 * 3. **Certezza**: un cambio è sicuro se i due ritmi sono ben diversi (almeno [MIN_CONTRAST] m/s e tre volte il
 *    rumore) e il battito va nella stessa direzione nei 90 s dopo; se il ritmo non è chiaro resta la stima del punto 1.
 *
 * Sulla corsa del 24/11/2026 (programma 2 × 6' + 2' + 25', giri veri 6:41, 8:40, 14:57, 17:00) trova 6:44, 8:50,
 * 15:02, 16:40 (lì il ritmo sale già a 16:42); a durata fissa sarebbero stati 6:00, 8:00, 14:00, 16:00. Sugli
 * intervalli del 5/11 (10' + 3 × (4' + 2')) i cambi delle ripetute cadono entro 10 s da quelli del profilo del ritmo;
 * la fine del riscaldamento (da 7:05 a 6:45/km) è segnata come incerta.
 */
object LapDetection {
    private const val STEP = 2
    const val HR_LAG_S = 20
    const val W_HR = 0.5
    const val W_PRIOR = 4.0
    const val MIN_SHARE = 0.6
    const val MAX_SHARE = 1.5
    private const val MAX_FREE_START_S = 25 * 60
    const val REFINE_S = 120
    const val MAX_RAMP_S = 60
    const val MIN_CONTRAST = 0.15
    /** Un riscaldamento non scritto più corto di così si unisce al primo pezzo. */
    private const val MIN_FREE_S = 60

    private class Seg(val step: Int?, val expected: Double?, val open: Boolean)

    /** Valori ogni [STEP] secondi da 0 a [end] (interpolazione lineare). */
    private fun grid(samples: List<WorkoutSample>, end: Int): DoubleArray {
        val s = samples.sortedBy { it.t }
        val out = DoubleArray(end / STEP + 1)
        var j = 0
        for (k in out.indices) {
            val t = k * STEP
            while (j < s.size - 1 && s[j + 1].t <= t) j++
            out[k] = when {
                t <= s.first().t -> s.first().v.toDouble()
                j >= s.size - 1 -> s.last().v.toDouble()
                else -> {
                    val a = s[j]; val b = s[j + 1]
                    if (b.t > a.t) a.v + (b.v - a.v) * (t - a.t).toDouble() / (b.t - a.t) else b.v.toDouble()
                }
            }
        }
        return out
    }

    private class Prefix(x: DoubleArray) {
        private val s = DoubleArray(x.size + 1)
        private val q = DoubleArray(x.size + 1)
        init { for (i in x.indices) { s[i + 1] = s[i] + x[i]; q[i + 1] = q[i] + x[i] * x[i] } }
        /** Somma degli scarti al quadrato dalla media in [i, j). */
        fun sse(i: Int, j: Int): Double {
            val n = j - i
            if (n <= 0) return 0.0
            val sum = s[j] - s[i]
            return (q[j] - q[i]) - sum * sum / n
        }
    }

    class Change(
        /** Secondi dall'inizio. */
        val atS: Int,
        val sure: Boolean
    )

    /**
     * La struttura [steps] del programma con le durate trovate nella registrazione [d]; null se mancano le serie o
     * se la struttura non ci sta. [name] = nome della seduta del programma.
     */
    fun detect(d: WorkoutDetail, steps: List<PlanStep>, name: String?): IntervalPlan? {
        val placed = steps.filter { it.part.isSet }
        if (placed.isEmpty() || d.speed.size < 30) return null
        val end = d.speed.maxOf { it.t }
        val v = grid(d.speed, end)
        val n = v.size
        val hasHr = d.hr.size >= 30
        val hr = if (hasHr) grid(d.hr, end).let { h -> val lag = HR_LAG_S / STEP; DoubleArray(n) { h[min(it + lag, n - 1)] } } else DoubleArray(n)
        val avgSpeed = v.average().takeIf { it > 0.3 } ?: return null

        // Pezzi: [riscaldamento non scritto] + programma + [resto]; il defaticamento scritto arriva fino alla fine
        val segs = mutableListOf<Seg>()
        if (placed.first().kind != StepKind.WARM_UP) segs += Seg(null, null, true)
        placed.forEachIndexed { i, st ->
            val sec = st.part.seconds?.toDouble() ?: (st.part.meters!! / avgSpeed)
            val coolToEnd = i == placed.lastIndex && st.kind == StepKind.COOL_DOWN
            segs += Seg(i, if (coolToEnd) null else sec / STEP, false)
        }
        if (placed.last().kind != StepKind.COOL_DOWN) segs += Seg(null, null, true)
        val bounds = segmentation(v, hr, if (hasHr) W_HR else 0.0, segs) ?: return null

        // Inizio di ogni cambio tra due pezzi non vuoti
        val edges = listOf(0) + bounds + listOf(n)
        val refined = bounds.toMutableList()
        val sure = BooleanArray(bounds.size) { true }
        for (k in bounds.indices) {
            val prev = edges[k]; val b = edges[k + 1]; val next = edges[k + 2]
            if (b - prev <= 0 || next - b <= 0) continue
            val c = refine(v, hr, hasHr, prev, b, next)
            refined[k] = c.first
            sure[k] = c.second
        }

        // Durate dei pezzi del programma
        val starts = IntArray(segs.size) { if (it == 0) 0 else refined[it - 1] }
        val ends = IntArray(segs.size) { if (it == segs.lastIndex) n - 1 else refined[it] }
        var offset = 0
        var uncertain = 0
        val out = mutableListOf<PlanStep>()
        segs.forEachIndexed { k, s ->
            val i = s.step ?: return@forEachIndexed
            var t0 = starts[k] * STEP
            if (out.isEmpty()) {
                // Riscaldamento non scritto corto: dentro il primo pezzo
                if (t0 < MIN_FREE_S) t0 = 0
                offset = t0
            }
            val t1 = ends[k] * STEP
            if (t1 - t0 <= 0) return null
            out += placed[i].copy(part = IntervalPart(seconds = t1 - t0))
            // Cambio all'inizio di questo pezzo, se c'è un pezzo del programma prima
            if (k > 0 && segs[k - 1].step != null && !sure[k - 1]) uncertain++
        }
        val (steps2, offset2) = snapToPlan(placed, out, offset, (n - 1) * STEP)
        return IntervalPlan.ofSteps(steps2, name)?.copy(offsetS = offset2, estimated = true, uncertainChanges = uncertain)
    }

    /** Scarto massimo (secondi) di ogni cambio trovato da quello del programma per usare le durate esatte. */
    const val SNAP_S = 20

    /**
     * Pezzi a tempo, giri e cambi trovati tutti entro [SNAP_S] da quelli del programma: l'orologio della seduta guidata li
     * taglia esattamente (3' e 2', non 2:44 e 2:12), quindi si usano le durate del programma, dall'inizio che va meglio
     * con tutti i cambi (mediana). Altrimenti (giri liberi, programma non seguito) resta quello trovato nei dati.
     * [found] = pezzi trovati (stesso ordine di [placed]), [end] = secondi della registrazione.
     */
    fun snapToPlan(placed: List<PlanStep>, found: List<PlanStep>, offset: Int, end: Int): Pair<List<PlanStep>, Int> {
        if (found.size != placed.size || placed.any { it.part.seconds == null }) return found to offset
        // Un defaticamento scritto arriva fino alla fine: la sua durata non si confronta
        val toEnd = placed.last().kind == StepKind.COOL_DOWN
        val fixed = if (toEnd) placed.size - 1 else placed.size
        if (fixed < 2) return found to offset
        // Ogni giro a tempo vicino a quello del programma (un giro libero di 40 s in più non si aggancia)
        if ((0 until fixed).any { abs(found[it].part.seconds!! - placed[it].part.seconds!!) > SNAP_S }) return found to offset
        // Inizio di ogni pezzo (e fine dell'ultimo a tempo) trovato e secondo il programma
        val real = mutableListOf(offset); val plan = mutableListOf(0)
        for (i in 0 until fixed) { real += real.last() + found[i].part.seconds!!; plan += plan.last() + placed[i].part.seconds!! }
        val start = real.zip(plan) { r, p -> r - p }.sorted().let { it[it.size / 2] }.coerceAtLeast(0)
        if (real.zip(plan).any { (r, p) -> abs(r - (start + p)) > SNAP_S }) return found to offset
        if (start + plan.last() > end) return found to offset
        val out = (0 until fixed).map { placed[it] }.toMutableList()
        if (toEnd) {
            val rest = end - (start + plan.last())
            if (rest <= 0) return found to offset
            out += placed.last().copy(part = IntervalPart(seconds = rest))
        }
        return out to start
    }

    /** Confini (indici della griglia) dei pezzi [segs], scelti insieme. */
    private fun segmentation(v: DoubleArray, hr: DoubleArray, wHr: Double, segs: List<Seg>): List<Int>? {
        val n = v.size
        val pv = Prefix(v); val ph = Prefix(hr)
        val varV = (pv.sse(0, n) / n).coerceAtLeast(1e-6)
        val varH = (ph.sse(0, n) / n).coerceAtLeast(1e-6)
        val k = segs.size
        val inf = Double.MAX_VALUE
        val cost = Array(k + 1) { DoubleArray(n + 1) { inf } }
        val back = Array(k + 1) { IntArray(n + 1) { -1 } }
        cost[0][0] = 0.0
        for (s in 1..k) {
            val seg = segs[s - 1]
            val last = s == k
            for (j in 0..n) {
                if (last && j != n) continue
                val e = seg.expected
                val lo: Int; val hi: Int
                when {
                    seg.open -> { lo = 0; hi = if (s == 1) min(j, MAX_FREE_START_S / STEP) else j }
                    e == null -> { lo = 1; hi = j }
                    else -> { lo = max(1, (e * MIN_SHARE).toInt()); hi = min(j, (e * MAX_SHARE).toInt() + 1) }
                }
                var best = inf; var arg = -1
                for (len in lo..hi) {
                    val i = j - len
                    if (i < 0 || cost[s - 1][i] == inf) continue
                    var c = cost[s - 1][i] + pv.sse(i, j) / varV + wHr * ph.sse(i, j) / varH
                    if (e != null) { val dev = (len - e) / e; c += W_PRIOR * dev * dev * e / 10 }
                    if (c < best) { best = c; arg = i }
                }
                cost[s][j] = best; back[s][j] = arg
            }
        }
        if (cost[k][n] == inf) return null
        val out = IntArray(k - 1)
        var j = n
        for (s in k downTo 2) { j = back[s][j]; out[s - 2] = j }
        return out.toList()
    }

    /**
     * Inizio del cambio vicino a [b] (tra i pezzi che iniziano a [prev] e finiscono a [next]): piatto + rampa + piatto
     * sulla velocità. Restituisce l'indice e se il cambio è sicuro (altrimenti resta [b]).
     */
    private fun refine(v: DoubleArray, hr: DoubleArray, hasHr: Boolean, prev: Int, b: Int, next: Int): Pair<Int, Boolean> {
        val w = REFINE_S / STEP
        val lo = max(prev + 10, b - w)
        val hi = min(next - 10, b + w)
        if (hi - lo < 12) return b to false
        val maxRamp = MAX_RAMP_S / STEP
        var bestSse = Double.MAX_VALUE; var bestS = b; var bestA = 0.0; var bestB = 0.0
        val m = hi - lo
        val f = DoubleArray(m)
        for (s in lo + 3 until hi - 3) {
            for (len in 0..maxRamp) {
                val e = s + len
                if (e >= hi - 2) break
                var sf = 0.0; var sy = 0.0
                for (x in 0 until m) {
                    val i = lo + x
                    f[x] = if (i <= s) 0.0 else if (i >= e) 1.0 else (i - s).toDouble() / max(1, len)
                    sf += f[x]; sy += v[i]
                }
                val mf = sf / m; val my = sy / m
                var sff = 0.0; var sfy = 0.0
                for (x in 0 until m) { val df = f[x] - mf; sff += df * df; sfy += df * (v[lo + x] - my) }
                if (sff < 1e-9) continue
                val slope = sfy / sff
                val a0 = my - slope * mf
                var sse = 0.0
                for (x in 0 until m) { val r = v[lo + x] - (a0 + slope * f[x]); sse += r * r }
                // A parità quasi perfetta, il punto più vicino alla stima della divisione
                sse += 1e-4 * abs(s - b)
                if (sse < bestSse) { bestSse = sse; bestS = s; bestA = a0; bestB = a0 + slope }
            }
        }
        val contrast = abs(bestB - bestA)
        var r2 = 0.0
        for (i in lo until bestS) r2 += (v[i] - bestA) * (v[i] - bestA)
        val noise = if (bestS > lo) sqrt(r2 / (bestS - lo)) else 0.0
        val clear = contrast > max(MIN_CONTRAST, 3 * noise)
        if (!clear) return b to false
        // Il giro comincia con il primo campione che si stacca dal ritmo di prima
        val start = bestS + 1
        // Il battito deve andare nella direzione del ritmo nei 90 s dopo (hr è già spostato del suo ritardo)
        val hrOk = !hasHr || run {
            val later = min(v.size - 1, start + 90 / STEP)
            (hr[later] > hr[start]) == (bestB > bestA)
        }
        return start to hrOk
    }
}
