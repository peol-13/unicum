package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.lfmt

import io.github.peol13.unicum.i18n.tr

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Tempo di recupero stimato dopo gli allenamenti (logica pura, con test).
 *
 * Si calcola l'**ora in cui sarai recuperato**, non le ore che mancano: quell'ora cambia solo quando arrivano dati
 * nuovi (un allenamento, una notte), non perché passa il tempo.
 *
 * 1. **Modello**: ore = 20 × ln(1 + carico / 40) × (0,8 + 0,05 × fatica misurata), al massimo 72 h
 *    (carico e fatica da [TrainingLoad]). Una corsa facile di 30' in soglia ≈ 28 h, ripetute dure ≈ 30 h,
 *    un lungo di 35' molto intenso ≈ 38 h, 10' di esercizi ≈ 4 h. Conta l'allenamento che finisce di recuperare
 *    più tardi; le ore di recupero degli altri che cadono dentro il suo recupero si aggiungono al 30%.
 * 2. **Come recuperi tu** (dallo storico): per ogni allenamento con HRV affidabile ([HrvData.reliableFrom]) si
 *    guarda in quale notte l'HRV torna nella tua norma (media dei 7 giorni prima − mezza deviazione standard).
 *    Le notti danno solo un intervallo (tra l'ultimo risveglio con HRV bassa e il primo nella norma): se il modello
 *    ci sta dentro è giusto, se no il rapporto è con l'estremo più vicino. La mediana dei rapporti (avvicinata a 1
 *    finché i casi sono pochi, tra 0,7 e 1,5) corregge tutte le stime.
 * 3. **Le notti dopo ogni allenamento**, una dopo l'altra, al risveglio. Tutte le correzioni sono graduali (nessuno
 *    scaglione) e moltiplicano il recupero che resta in quel momento:
 *    - **HRV**: [hrvFactor] dello scarto dalla tua media in deviazioni standard. Alla soglia della norma (media − ½ dev.
 *      std.) non cambia niente; 1 dev. std. sopra la media → × 0,6; 1,75 sotto → × 1,3. Quando allunga, il recupero
 *      dura anche almeno fino al risveglio dopo, in proporzione a quanto allunga ([hrvFloorShare]).
 *    - **Sonno**: [sleepFactor], da × 1 con 7 h fino a × 1,15 con 5 h o meno.
 *    - **Temperatura notturna**: [tempFactor], da × 1 con +0,2 °C fino a × 1,15 con +0,6 °C o più sopra la media.
 *    Per l'allenamento principale le notti prima non contano, ma se non avevi ancora recuperato da uno precedente
 *    quel resto (già corretto dalle sue notti) si aggiunge al 30% come sopra.
 */
object RecoveryEstimate {

    data class Session(
        val id: String,
        val type: String,
        val date: LocalDate,
        val endMillis: Long,
        val effort: TrainingLoad.Effort
    )

    /** Risposta a un allenamento passato: dopo quante ore l'HRV è tornata nella norma. */
    data class PastResponse(
        val session: Session,
        val modelHours: Double,
        /** Ore fino al risveglio con l'HRV di nuovo nella norma; se non è tornata, fino all'ultimo controllo. */
        val observedHours: Double,
        /** Ore fino all'ultimo risveglio con l'HRV ancora sotto la norma (0 = nessuno). */
        val notYetHours: Double,
        /** Notte (1 = la prima dopo) in cui l'HRV è tornata nella norma; null = non ancora prima del controllo. */
        val night: Int?
    ) {
        /**
         * Correzione del modello che basta a stare dentro quello che si è visto: 1 se il modello cade tra
         * [notYetHours] e [observedHours]. null = non dice niente (HRV non ancora tornata, ma il modello prevedeva
         * comunque più ore di quelle controllate).
         */
        val ratio: Double?
            get() = when {
                modelHours <= 0 -> null
                modelHours < notYetHours -> notYetHours / modelHours
                night == null -> null
                modelHours > observedHours -> observedHours / modelHours
                else -> 1.0
            }
    }

    data class Today(
        val hrv: Double?,
        val baseline: Double?,
        /** Scarto % dalla media delle notti dei 14 giorni prima (come pagina Sonno e prontezza). */
        val hrvDeltaPct: Int?,
        val hrvNormal: Boolean?,
        val sleepMinutes: Int?,
        val tempDelta: Double?
    )

    data class Result(
        /** Ore che mancano al recupero (0 = recuperato). */
        val remainingHours: Double,
        /** Durata totale del recupero in corso (ore dalla fine dell'allenamento principale all'ora di recupero). */
        val totalHours: Double,
        val readyAtMillis: Long?,
        /** L'allenamento che pesa di più sul recupero (null = nessuno negli ultimi 5 giorni). */
        val main: Session?,
        /** Ore del modello per [main], già con il fattore personale. */
        val mainHours: Double?,
        val lastSession: Session?,
        val personalFactor: Double,
        val history: List<PastResponse>,
        val today: Today,
        /** Motivi delle correzioni delle notti, in parole. */
        val reasons: List<String>
    ) {
        val recovered: Boolean get() = remainingHours <= 0.0
        /** 0–1: quanto del recupero è già fatto. */
        val progress: Float get() = if (totalHours <= 0) 1f else (1 - remainingHours / totalHours).toFloat().coerceIn(0f, 1f)
    }

    const val MAX_HOURS = 72.0
    private const val LOOKBACK_HOURS = 120.0
    private const val MIN_LOAD = 5.0
    /** Un allenamento così pesante "copre" le notti successive: non si attribuisce più l'HRV a quello prima. */
    private const val SIGNIFICANT_LOAD = 20.0
    private const val HOUR = 3_600_000L

    fun modelHours(e: TrainingLoad.Effort): Double {
        if (e.load < MIN_LOAD) return 0.0
        return (20 * ln(1 + e.load / 40) * (0.8 + 0.05 * e.rawScore)).coerceAtMost(MAX_HOURS)
    }

    /** Media e deviazione standard dell'HRV tra [from] e [to] compresi (servono almeno 3 notti). */
    fun baseline(hrv: Map<LocalDate, Double>, from: LocalDate, to: LocalDate): Pair<Double, Double>? {
        val v = hrv.filterKeys { !it.isBefore(from) && !it.isAfter(to) && HrvData.reliable(it) }.values
        if (v.size < 3) return null
        val mean = v.average()
        val sd = sqrt(v.sumOf { (it - mean) * (it - mean) } / (v.size - 1))
        return mean to maxOf(sd, mean * .03)
    }

    private fun threshold(b: Pair<Double, Double>) = b.first - 0.5 * b.second

    private fun wake(date: LocalDate, sleepEnd: Map<LocalDate, Long>, zone: ZoneId): Long =
        sleepEnd[date] ?: date.atTime(LocalTime.of(7, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun hours(ms: Long) = ms / HOUR.toDouble()

    /** Come hai recuperato dagli allenamenti passati (solo con HRV affidabile). */
    fun history(
        sessions: List<Session>, hrv: Map<LocalDate, Double>, sleepEnd: Map<LocalDate, Long>, zone: ZoneId
    ): List<PastResponse> {
        val sorted = sessions.sortedBy { it.endMillis }
        return sorted.mapNotNull { s ->
            val model = modelHours(s.effort)
            if (s.effort.load < SIGNIFICANT_LOAD || model <= 0 || !HrvData.reliable(s.date)) return@mapNotNull null
            val base = baseline(hrv, s.date.minusDays(6), s.date) ?: return@mapNotNull null
            val next = sorted.firstOrNull { it.endMillis > s.endMillis && it.effort.load >= SIGNIFICANT_LOAD }
            var notYet = 0.0
            var lastChecked: Double? = null
            for (n in 1..4) {
                val night = s.date.plusDays(n.toLong())
                val wakeAt = wake(night, sleepEnd, zone)
                if (wakeAt <= s.endMillis) continue
                if (next != null && next.endMillis < wakeAt) break
                val v = hrv[night] ?: continue
                val h = hours(wakeAt - s.endMillis)
                if (v >= threshold(base)) return@mapNotNull PastResponse(s, model, h, notYet, n)
                notYet = h
                lastChecked = h
            }
            lastChecked?.let { PastResponse(s, model, it, it, null) }
        }
    }

    /** Fattore personale: mediana dei rapporti osservato/modello, avvicinata a 1 se i casi sono pochi. */
    fun personalFactor(history: List<PastResponse>): Double {
        val r = history.mapNotNull { it.ratio }.sorted()
        if (r.size < 2) return 1.0
        val median = if (r.size % 2 == 1) r[r.size / 2] else (r[r.size / 2 - 1] + r[r.size / 2]) / 2
        val n = r.size
        return (1 + (median - 1) * n / (n + 2.0)).coerceIn(0.7, 1.5)
    }

    /**
     * Effetto dell'HRV di una notte sul recupero che resta, dallo scarto [z] dalla tua media in deviazioni standard
     * (lineare, tra 0,6 e 1,3): 0,86 alla media, ~1 alla soglia della norma (z = −0,5), 0,6 da +1 in su, 1,3 da −1,75 in giù.
     */
    fun hrvFactor(z: Double): Double = (0.86 - 0.26 * z).coerceIn(0.6, 1.3)

    /** Quanto del "almeno fino al risveglio dopo" vale per un effetto [factor] dell'HRV: 0 fino a 1, tutto da 1,2 in su. */
    fun hrvFloorShare(factor: Double): Double = ((factor - 1) / 0.2).coerceIn(0.0, 1.0)

    /** Sonno corto: × 1 con [minutes] ≥ 7 h, × 1,15 con 5 h o meno, lineare in mezzo. */
    fun sleepFactor(minutes: Int): Double = 1 + 0.15 * ((420 - minutes) / 120.0).coerceIn(0.0, 1.0)

    /** Temperatura notturna sopra la media: × 1 fino a +0,2 °C, × 1,15 da +0,6 °C, lineare in mezzo. */
    fun tempFactor(delta: Double): Double = 1 + 0.15 * ((delta - 0.2) / 0.4).coerceIn(0.0, 1.0)

    /**
     * I dati di una notte (data = giorno del risveglio), confrontati con i 7 giorni prima per la stima. Lo scarto %
     * mostrato usa invece [shownMean], la media delle notti dei 14 giorni prima, come pagina Sonno e prontezza.
     */
    private class Night(
        val hrv: Double?, val base: Pair<Double, Double>?, val shownMean: Double?, val sleep: Int?, val tempDelta: Double?
    ) {
        val hrvNormal: Boolean? get() = if (hrv != null && base != null) hrv >= threshold(base) else null
        val hrvDeltaPct: Int? get() = if (hrv != null && shownMean != null) percentVs(hrv, shownMean) else null
        /** Scarto dalla media in deviazioni standard. */
        val hrvZ: Double? get() = if (hrv != null && base != null) (hrv - base.first) / base.second else null
    }

    private fun night(
        d: LocalDate, hrv: Map<LocalDate, Double>, sleepMinutes: Map<LocalDate, Int>, nightTemp: Map<LocalDate, Double>
    ): Night {
        val tempBase = nightTemp.filterKeys { it.isBefore(d) && !it.isBefore(d.minusDays(7)) }.values
        val base = baseline(hrv, d.minusDays(7), d.minusDays(1))
        return Night(
            hrv = hrv[d],
            base = base,
            shownMean = baseline(hrv, d.minusDays(14), d.minusDays(1))?.first ?: base?.first,
            sleep = sleepMinutes[d],
            tempDelta = nightTemp[d]?.takeIf { tempBase.size >= 3 }?.let { it - tempBase.average() }
        )
    }

    fun estimate(
        sessions: List<Session>,
        hrv: Map<LocalDate, Double>,
        sleepEnd: Map<LocalDate, Long>,
        sleepMinutes: Map<LocalDate, Int>,
        nightTemp: Map<LocalDate, Double>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Result {
        val reliableHrv = hrv.filterKeys { HrvData.reliable(it) }
        val history = history(sessions, reliableHrv, sleepEnd, zone)
        val factor = personalFactor(history)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val done = sessions.filter { it.endMillis <= now }
        val last = done.maxByOrNull { it.endMillis }

        val n = night(today, reliableHrv, sleepMinutes, nightTemp)
        val todayInfo = Today(n.hrv, n.shownMean, n.hrvDeltaPct, n.hrvNormal, n.sleep, n.tempDelta)

        /** Fine del recupero di un allenamento: modello × fattore personale, poi le notti che seguono, una alla volta. */
        class Adjusted(val ready: Long, val floor: Long, val reasons: List<Pair<LocalDate, String>>)
        fun adjust(s: Session): Adjusted {
            var r = s.endMillis + (modelHours(s.effort) * factor * HOUR).toLong()
            var floor = 0L
            val why = mutableListOf<Pair<LocalDate, String>>()
            var d = Instant.ofEpochMilli(s.endMillis).atZone(zone).toLocalDate()
            while (!d.isAfter(today)) {
                val w = wake(d, sleepEnd, zone)
                // Solo le notti finite e con il recupero ancora in corso al risveglio (<= r: con l'HRV bassa il
                // recupero arriva proprio al risveglio dopo, e quella notte va guardata)
                if (w > now || w > r) break
                if (w > s.endMillis) {
                    val info = night(d, reliableHrv, sleepMinutes, nightTemp)
                    val prefix = (if (d == today) tr("Stanotte:") else tr("%s al risveglio:", DateUtils.relativeLabel(d, today))) + " "
                    fun scale(k: Double) { r = w + ((r - w) * k).toLong() }
                    val z = info.hrvZ
                    if (z != null) {
                        val f = hrvFactor(z)
                        scale(f)
                        val share = hrvFloorShare(f)
                        if (share > 0) {
                            // Più l'HRV è bassa, più il recupero dura almeno fino al risveglio dopo
                            val nextWake = sleepEnd[d.plusDays(1)] ?: (w + 24 * HOUR)
                            val until = w + ((nextWake - w) * share).toLong()
                            floor = maxOf(floor, until)
                            r = maxOf(r, until)
                        }
                        val effect = ((f - 1) * 100).roundToInt()
                        val vs = "HRV ${info.hrv?.roundToInt()} ms " + tr("(%s vs media)", signedPercent(info.hrvDeltaPct ?: 0))
                        why += d to prefix + when {
                            effect <= -3 -> tr("%s, nella tua norma: il recupero che resta si accorcia del %d%%.", vs, -effect)
                            effect >= 3 -> tr("%s, sotto la tua norma: il recupero che resta si allunga del %d%%", vs, effect) +
                                (if (share >= 0.5) ", " + tr("almeno fino al risveglio dopo.") else ".")
                            else -> tr("%s, vicina alla tua norma: il recupero cambia di poco.", vs)
                        }
                    }
                    val sleep = info.sleep
                    if (sleep != null) {
                        val f = sleepFactor(sleep)
                        val effect = ((f - 1) * 100).roundToInt()
                        if (effect > 0) {
                            scale(f)
                            why += d to prefix + tr("dormito %s: il recupero si allunga del %d%%.", "${sleep / 60} h ${"%02d".format(sleep % 60)}", effect)
                        }
                    }
                    val temp = info.tempDelta
                    if (temp != null) {
                        val f = tempFactor(temp)
                        val effect = ((f - 1) * 100).roundToInt()
                        if (effect > 0) {
                            scale(f)
                            why += d to prefix + tr("temperatura notturna %s sopra la media: il recupero si allunga del %d%%.", lfmt("%+.1f °C", temp), effect)
                        }
                    }
                }
                d = d.plusDays(1)
            }
            return Adjusted(maxOf(minOf(r, s.endMillis + (MAX_HOURS * HOUR).toLong()), floor), floor, why)
        }

        val adjusted = done.associateWith { adjust(it) }
        // Conta l'allenamento recente che finisce di recuperare più tardi
        val main = done.filter { hours(now - it.endMillis) <= LOOKBACK_HOURS && modelHours(it.effort) > 0 }
            .maxByOrNull { adjusted.getValue(it).ready }
        var ready: Long? = null
        var reasons = emptyList<String>()
        if (main != null) {
            val m = adjusted.getValue(main)
            // Le ore di recupero degli altri allenamenti che cadono dentro quello principale contano al 30%
            val others = done.filter { it !== main }.map { s ->
                val a = adjusted.getValue(s)
                a to maxOf(0L, a.ready - maxOf(main.endMillis, s.endMillis))
            }
            val overlap = others.sumOf { it.second }
            ready = maxOf(minOf(m.ready + (0.3 * overlap).toLong(), main.endMillis + (MAX_HOURS * HOUR).toLong()), m.floor)
            reasons = (m.reasons + others.filter { it.second > 0 }.flatMap { it.first.reasons })
                .sortedBy { it.first }.map { it.second }.distinct()
        }

        val remaining = if (ready == null) 0.0 else maxOf(0.0, hours(ready - now))
        return Result(
            remainingHours = remaining,
            totalHours = if (main == null || ready == null) 0.0 else hours(ready - main.endMillis),
            readyAtMillis = if (remaining > 0) ready else null,
            main = main,
            mainHours = main?.let { modelHours(it.effort) * factor },
            lastSession = last,
            personalFactor = factor,
            history = history,
            today = todayInfo,
            reasons = reasons
        )
    }
}
