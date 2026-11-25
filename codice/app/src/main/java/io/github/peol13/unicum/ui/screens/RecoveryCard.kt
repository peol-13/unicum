package io.github.peol13.unicum.ui.screens

import io.github.peol13.unicum.i18n.lfmt

import io.github.peol13.unicum.i18n.trp

import io.github.peol13.unicum.i18n.tr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.DateUtils
import io.github.peol13.unicum.data.HrvData
import io.github.peol13.unicum.data.RecoveryData
import io.github.peol13.unicum.data.RecoveryEstimate
import io.github.peol13.unicum.data.TrainingLoad
import io.github.peol13.unicum.data.signedPercent
import io.github.peol13.unicum.ui.components.ExpandRow
import io.github.peol13.unicum.ui.components.RingGauge
import io.github.peol13.unicum.ui.components.UnicumCard
import io.github.peol13.unicum.ui.sections.formatDuration
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Stima del recupero, ricalcolata a ogni sincronizzazione (vedi [RecoveryData]). */
@Composable
fun rememberRecovery(): RecoveryData.Loaded? {
    val context = LocalContext.current
    val data by produceState<RecoveryData.Loaded?>(null, UnicumAppState.health) {
        value = runCatching { RecoveryData.load(context) }.getOrNull()
    }
    return data
}

/** "14 h", "2 g 3 h". */
internal fun hoursText(hours: Double): String {
    val h = ceil(hours).toInt().coerceAtLeast(1)
    return if (h < 48) "$h h" else tr("%d g", h / 24) + if (h % 24 > 0) " ${h % 24} h" else ""
}

/** "oggi alle 21:30", "domani alle 9:00", "Gio 12 Nov alle 8:00". */
internal fun readyText(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = DateUtils.today()
    val day = when (t.toLocalDate()) {
        today -> tr("oggi")
        today.plusDays(1) -> tr("domani")
        else -> DateUtils.shortLabel(t.toLocalDate())
    }
    return tr("%s alle %s", day, "${t.hour}:${"%02d".format(t.minute)}")
}

/** "Corsa di ieri", "Allenamento di oggi", "Corsa del Ven 6 Nov". */
private fun sessionText(s: RecoveryEstimate.Session): String {
    val day = DateUtils.relativeLabel(s.date)
    return if (s.date >= DateUtils.today().minusDays(1)) tr("%s di %s", tr(s.type), day.lowercase(io.github.peol13.unicum.i18n.I18n.locale)) else tr("%s del %s", tr(s.type), day)
}

/**
 * In cima alla sezione Allenamenti, due anelli affiancati: a sinistra quanto manca al recupero dopo gli ultimi
 * allenamenti, a destra la prontezza all'allenamento dalle ultime notti ([io.github.peol13.unicum.data.Readiness]).
 * Sotto, aperta a richiesta, la spiegazione: carico e fatica misurata, come hai recuperato in passato
 * (HRV notturna dal 28/10), HRV, sonno e temperatura di stanotte, e da cosa viene la prontezza.
 */
@Composable
fun RecoveryCard(data: RecoveryData.Loaded) {
    val r = data.result
    val ready = data.readiness
    var open by rememberSaveable { mutableStateOf(false) }
    UnicumCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            RingStat(
                Modifier.weight(1f), r.progress, "${(r.progress * 100).roundToInt()}%", tr("Recupero"),
                if (r.recovered) tr("Recuperato") else tr("Pronto tra %s", hoursText(r.remainingHours)),
                when {
                    !r.recovered && r.readyAtMillis != null -> readyText(r.readyAtMillis)
                    r.lastSession == null -> tr("Nessun allenamento recente")
                    else -> null
                }
            )
            Spacer(Modifier.width(10.dp))
            RingStat(
                Modifier.weight(1f), (ready?.score ?: 0) / 100f, ready?.let { "${it.score}%" } ?: "–", tr("Prontezza"),
                ready?.level ?: "–",
                when {
                    ready == null -> tr("Mancano i dati delle notti")
                    ready.score != ready.morningScore -> tr("Al risveglio %d%%", ready.morningScore)
                    else -> null
                }
            )
        }
        ExpandRow(open, tr("Come è calcolato"), { open = !open })
        if (open) RecoveryDetails(data)
    }
}

/** Mezza card: anello con la percentuale, titolo piccolo, valore e (se c'è) una riga sotto. */
@Composable
private fun RingStat(modifier: Modifier, progress: Float, ringText: String, title: String, value: String, sub: String?) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RingGauge(progress, size = 50.dp, stroke = 5.dp) {
            Text(ringText, color = UnicumDark, style = UnicumType.Note, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Caption)
            Text(value, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
            if (sub != null) Text(sub, color = UnicumDark.copy(alpha = .55f), style = UnicumType.Note, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun RecoveryDetails(data: RecoveryData.Loaded) {
    val r = data.result
    Spacer(Modifier.height(10.dp))
    val main = r.main
    if (main == null) r.lastSession?.let { DetailLine(null, tr("Ultimo allenamento: %s", sessionText(it))) }
    if (main != null && r.mainHours != null) {
        DetailLine(
            tr("Allenamento"),
            tr("%s: carico %d, fatica misurata %d/10 (%s) → circa %s di recupero.", sessionText(main), main.effort.load.roundToInt(), main.effort.score,
                                TrainingLoad.label(main.effort.score).lowercase(io.github.peol13.unicum.i18n.I18n.locale), hoursText(r.mainHours))
        )
    }
    val n = r.history.size
    DetailLine(
        tr("Come recuperi tu"),
        when {
            n == 0 -> tr("Non ci sono ancora allenamenti con HRV affidabile da confrontare: stima standard.")
            n == 1 -> tr("Un solo allenamento con HRV affidabile finora: ne servono almeno 2 per adattare la stima a te.")
            else -> {
                val pct = ((r.personalFactor - 1) * 100).roundToInt()
                val firstNight = r.history.count { it.night == 1 }
                tr("Su %d allenamenti l'HRV è tornata nella tua norma %s:", n,
                                        if (firstNight == n) tr("sempre la prima notte")
                                        else trp(firstNight, "la prima notte %d volta", "la prima notte %d volte") + " " + tr("su %d", n)) + " " +
                    when {
                        pct <= -5 -> tr("recuperi più in fretta della stima standard (%d%% di tempo).", pct)
                        pct >= 5 -> tr("ti serve più tempo della stima standard (+%d%%).", pct)
                        else -> tr("recuperi come la stima standard.")
                    }
            }
        }
    )
    val t = r.today
    val tonight = listOfNotNull(
        t.hrv?.let { "HRV ${it.roundToInt()} ms" + (t.hrvDeltaPct?.let { p -> " " + tr("(%s vs media)", signedPercent(p)) } ?: "") },
        t.sleepMinutes?.let { tr("sonno %s", "${it / 60} h ${"%02d".format(it % 60)}") },
        t.tempDelta?.let { tr("temperatura %s", lfmt("%+.1f °C", it)) }
    )
    DetailLine(tr("Stanotte"), if (tonight.isEmpty()) tr("Dati della notte non ancora arrivati.") else tonight.joinToString(" · ").replaceFirstChar { it.uppercase() })
    r.reasons.forEach { DetailLine(null, it) }
    data.readiness?.let { p ->
        val parts = listOfNotNull(
            tr("HRV %s rispetto alla notte prima", signedPercent(p.hrvVsLastNightPct)) +
                " " + tr("(%s vs media)", signedPercent(p.hrvDeltaPct)),
            tr("sonno %s, di cui %s tra profondo e REM", formatDuration(p.sleepMinutes), formatDuration(p.restorativeMinutes))
        )
        val sinceWaking = listOfNotNull(
            p.workoutPoints.takeIf { it > 0 }?.let {
                if (p.workoutsToday == 1) tr("−%d per l'allenamento di oggi, che torna man mano che recuperi", it)
                else tr("−%d per gli allenamenti di oggi, che tornano man mano che recuperi", it)
            },
            p.awakePoints.takeIf { it > 0 }?.let { tr("−%d perché sei sveglio da %d ore", it, p.awakeHours.toInt()) }
        )
        DetailLine(
            tr("Prontezza") + " · " + DateUtils.relativeLabel(p.date),
            tr("Al risveglio %d%%, %s:", p.morningScore, p.morningLevel.lowercase(io.github.peol13.unicum.i18n.I18n.locale)) + " " +
                parts.joinToString(", ") + ". " +
                (if (sinceWaking.isNotEmpty()) tr("Adesso %d%%:", p.score) + " " + sinceWaking.joinToString(", ") + ". " else "") +
                tr("Al risveglio conta soprattutto se l'HRV sale o scende rispetto alla notte prima, poi il sonno profondo e REM. Durante il giorno scende dopo un allenamento e risale man mano che recuperi; dopo 16 ore da sveglio cala un po'.")
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        tr("Carico = minuti in ogni zona × numero della zona (FC max %d bpm).", data.maxHr) + " " +
            tr("HRV media della notte") +
            (HrvData.reliableFrom?.let { ", " + tr("considerata solo dal %s: prima i valori erano su un'altra scala (Profilo → Recupero).", "${it.dayOfMonth}/${it.monthValue}") } ?: "."),
        color = UnicumDark.copy(alpha = .5f), style = UnicumType.Note, lineHeight = 15.sp
    )
}

@Composable
private fun DetailLine(title: String?, text: String) {
    Column(Modifier.padding(bottom = 8.dp)) {
        if (title != null) Text(title, color = UnicumDark.copy(alpha = .6f), style = UnicumType.Caption, fontWeight = FontWeight.Medium)
        Text(text, color = UnicumDark, style = UnicumType.Detail, lineHeight = 18.sp)
    }
}
