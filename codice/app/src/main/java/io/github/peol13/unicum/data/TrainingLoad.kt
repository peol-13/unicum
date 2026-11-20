package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fatica misurata di un allenamento, dai dati e non dalle sensazioni (logica pura, con test).
 *
 * - **Fatica misurata (1–10)**, sulla stessa scala dell'RPE: ogni istante del battito diventa un valore della scala
 *   CR10 di Borg in base alla % della FC massima (vedi [PTS]); il valore della seduta è metà la media nel tempo e metà
 *   l'80° percentile (le parti dure pesano di più nella fatica percepita), più un piccolo extra per la durata oltre
 *   30 minuti (+0,5 ogni 30', al massimo +1,5).
 * - **Carico** = TRIMP di Edwards: minuti in ogni zona × numero della zona (50-60% FC max = 1 … 90-100% = 5).
 *   Serve alla stima del recupero ([RecoveryEstimate]).
 *
 * Senza la serie del battito si usa la FC media su tutta la durata (meno preciso). Senza battito: null.
 */
object TrainingLoad {

    data class Effort(
        /** Fatica misurata, 1–10. */
        val score: Int,
        /** Valore non arrotondato (per i confronti). */
        val rawScore: Double,
        /** TRIMP di Edwards. */
        val load: Double,
        /** Minuti sopra l'80% della FC massima (soglia e massimale). */
        val hardMinutes: Int,
        /** true se calcolata dalla serie del battito, false dalla sola FC media. */
        val fromSeries: Boolean
    )

    /** % della FC massima → scala CR10 (1 = leggerissimo, 10 = massimo), interpolata tra i punti. */
    private val PTS = listOf(
        .50 to 1.0, .60 to 2.0, .70 to 3.0, .78 to 4.0, .84 to 5.0,
        .88 to 6.0, .92 to 7.0, .95 to 8.0, .98 to 9.0, 1.0 to 10.0
    )

    fun cr10(fractionOfMax: Double): Double {
        val f = fractionOfMax
        if (f <= PTS.first().first) return (f / PTS.first().first).coerceAtLeast(0.0)
        PTS.zipWithNext { (x0, y0), (x1, y1) -> if (f <= x1) return y0 + (y1 - y0) * (f - x0) / (x1 - x0) }
        return 10.0
    }

    /** Zona di Edwards (0 sotto il 50%, poi 1–5). */
    private fun zone(f: Double): Int = listOf(.5, .6, .7, .8, .9).count { f >= it }

    /**
     * FC massima della persona: la più alta tra quella stimata dall'età e quelle registrate negli allenamenti.
     * Una sola per tutti gli allenamenti, così fatica e carico sono confrontabili tra loro.
     */
    fun personalMaxHr(age: Int, workouts: List<WorkoutDetail>): Int =
        maxOf(WorkoutAnalysis.estimatedMaxHr(age), workouts.maxOfOrNull { it.maxHr ?: 0 } ?: 0)

    fun effort(d: WorkoutDetail, maxHr: Int): Effort? {
        if (maxHr <= 0) return null
        val hr = d.hr.sortedBy { it.t }
        if (hr.size >= 10) {
            val parts = mutableListOf<Pair<Double, Int>>()   // (valore CR10, secondi)
            var load = 0.0
            var hard = 0
            hr.zipWithNext { a, b ->
                val dt = (b.t - a.t).coerceIn(0, 30)   // i buchi oltre 30 s non contano (come le zone)
                if (dt > 0 && a.v > 0) {
                    val f = a.v / maxHr.toDouble()
                    parts += cr10(f) to dt
                    load += dt / 60.0 * zone(f)
                    if (f >= .8) hard += dt
                }
            }
            val total = parts.sumOf { it.second }
            if (total >= 60) {
                val mean = parts.sumOf { it.first * it.second } / total
                val sorted = parts.sortedBy { it.first }
                var acc = 0
                val p80 = sorted.first { acc += it.second; acc >= total * .8 }.first
                return build(0.5 * mean + 0.5 * p80, total / 60.0, load, hard / 60, true)
            }
        }
        val avg = d.avgHr ?: return null
        val minutes = d.movingSeconds / 60.0
        if (minutes <= 0) return null
        val f = avg / maxHr.toDouble()
        return build(cr10(f), minutes, minutes * zone(f), if (f >= .8) minutes.roundToInt() else 0, false)
    }

    private fun build(intensity: Double, minutes: Double, load: Double, hardMinutes: Int, fromSeries: Boolean): Effort {
        val bonus = ((minutes - 30) / 30 * 0.5).coerceIn(0.0, 1.5)
        val raw = (intensity + bonus).coerceIn(1.0, 10.0)
        return Effort(raw.roundToInt().coerceIn(1, 10), raw, load, hardMinutes, fromSeries)
    }

    /** Buco massimo nel battito (secondi) di una sessione avviata a mano sull'orologio. */
    private const val MAX_MANUAL_GAP_S = 60

    /**
     * Allenamento da contare per il recupero: le corse sempre, gli altri solo se avviati a mano.
     * Samsung Health scrive "metodo di registrazione sconosciuto" per tutte le sessioni, quindi si guarda il
     * battito: avviando un allenamento l'orologio lo misura ogni 1-2 s senza buchi (nelle corse al massimo
     * ~90 s), mentre negli allenamenti riconosciuti da soli ("Allenamento", ciclismo, nuoto) ci sono solo
     * campioni sparsi con buchi di 100-300 s. Senza battito non c'è fatica misurata e non conta.
     */
    fun countsForRecovery(d: WorkoutDetail): Boolean {
        if (d.automatic) return false
        if (d.type == "Corsa") return true
        val t = d.hr.map { it.t }.sorted()
        if (t.size < 10) return false
        val maxGap = t.zipWithNext { a, b -> b - a }.max()
        return maxGap <= MAX_MANUAL_GAP_S
    }

    /** Stessa scala dell'RPE nelle Sensazioni. */
    fun label(score: Int): String = when (score) {
        in 1..3 -> tr("Leggero")
        in 4..6 -> tr("Moderato")
        in 7..8 -> tr("Duro")
        else -> tr("Massimale")
    }

    /** Confronto tra quello che hai sentito ([rpe]) e quello che dice il battito. */
    fun compare(rpe: Int, measured: Int): String {
        val diff = rpe - measured
        return when {
            abs(diff) <= 1 -> tr("Sensazioni in linea con i dati.")
            diff <= -3 -> tr("Ti è sembrata molto più facile di quanto dice il battito: per il corpo è stata una seduta più dura.")
            diff < 0 -> tr("Ti è sembrata più facile di quanto dice il battito.")
            diff >= 3 -> tr("L'hai sentita molto più dura di quanto dice il battito: stanchezza, poco sonno, caldo o gambe pesanti?")
            else -> tr("L'hai sentita più dura di quanto dice il battito.")
        }
    }
}
