package io.github.peol13.unicum

import io.github.peol13.unicum.data.Advice
import io.github.peol13.unicum.data.AdviceCategory
import io.github.peol13.unicum.data.AdviceEngine
import io.github.peol13.unicum.data.AdviceInput
import io.github.peol13.unicum.data.SleepNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class AdviceEngineTest {

    private val utc = ZoneOffset.UTC

    private fun night(date: String, bedHour: Int, bedMin: Int, wakeHour: Int, wakeMin: Int, minutes: Int): SleepNight {
        val day = java.time.LocalDate.parse(date)
        // si addormenta la sera prima (o dopo la mezzanotte se bedHour < 12)
        val start = if (bedHour < 12) day.atTime(bedHour, bedMin) else day.minusDays(1).atTime(bedHour, bedMin)
        val end = day.atTime(wakeHour, wakeMin)
        fun ms(t: LocalDateTime) = t.toInstant(utc).toEpochMilli()
        return SleepNight(date, ms(start), ms(end), minutes, 0, 0, 0, 0, "test")
    }

    private fun List<Advice>.of(c: AdviceCategory) = filter { it.category == c }

    @Test
    fun recupero_basso_per_hrv_consiglia_recupero_attivo() {
        val out = AdviceEngine.generate(
            AdviceInput(hrv = 40.0, hrvBaseline = 50.0, lastNight = night("2026-10-16", 23, 0, 7, 0, 480)), utc
        )
        val first = out.of(AdviceCategory.ATTIVITA).first()
        assertTrue(first.title.contains("recupero attivo", ignoreCase = true))
        assertTrue(first.reason!!.contains("-20%"))
    }

    @Test
    fun poche_ore_di_sonno_consigliano_recupero_attivo() {
        val out = AdviceEngine.generate(AdviceInput(lastNight = night("2026-10-16", 1, 0, 6, 30, 5 * 60)), utc)
        assertTrue(out.of(AdviceCategory.ATTIVITA).first().title.contains("recupero attivo", ignoreCase = true))
    }

    @Test
    fun ben_riposato_consiglia_allenamento_intenso() {
        val out = AdviceEngine.generate(
            AdviceInput(hrv = 55.0, hrvBaseline = 50.0, lastNight = night("2026-10-16", 22, 30, 7, 0, 8 * 60)), utc
        )
        assertTrue(out.of(AdviceCategory.ATTIVITA).first().title.contains("intenso", ignoreCase = true))
    }

    @Test
    fun senza_dati_lo_dice_e_non_inventa_il_motivo() {
        val first = AdviceEngine.generate(AdviceInput(), utc).of(AdviceCategory.ATTIVITA).first()
        assertNull(first.reason)
        assertTrue(first.text.contains("Health Connect"))
    }

    @Test
    fun obiettivo_settimanale_indica_i_minuti_mancanti() {
        val out = AdviceEngine.generate(AdviceInput(weekMinutes = listOf(0, 30, 0, 45, 0, 0, 0)), utc)
        val a = out.of(AdviceCategory.ATTIVITA).first { it.title.contains("mancano") }
        assertTrue(a.title.contains("75"))
    }

    @Test
    fun obiettivo_settimanale_raggiunto() {
        val out = AdviceEngine.generate(AdviceInput(weekMinutes = listOf(40, 40, 40, 40, 0, 0, 0)), utc)
        assertTrue(out.of(AdviceCategory.ATTIVITA).any { it.title.contains("raggiunto") })
    }

    @Test
    fun orario_per_andare_a_letto_dal_risveglio_abituale() {
        // sveglia sempre alle 07:00 → 07:00 - 8 h - 15 min = 22:45
        val nights = listOf("2026-10-13", "2026-10-14", "2026-10-15").map { night(it, 23, 0, 7, 0, 450) }
        val sonno = AdviceEngine.generate(AdviceInput(recentNights = nights), utc).of(AdviceCategory.SONNO)
        assertEquals("Vai a letto verso le 22:45", sonno.first().title)
    }

    @Test
    fun senza_notti_ipotizza_il_risveglio_alle_sette() {
        val sonno = AdviceEngine.generate(AdviceInput(), utc).of(AdviceCategory.SONNO).first()
        assertEquals("Vai a letto verso le 22:45", sonno.title)
        assertTrue(sonno.reason!!.contains("Nessuna notte registrata"))
    }

    @Test
    fun orari_irregolari_vengono_segnalati() {
        val nights = listOf(
            night("2026-10-12", 21, 30, 6, 0, 480),
            night("2026-10-13", 1, 30, 7, 0, 330),
            night("2026-10-14", 22, 0, 6, 30, 480),
            night("2026-10-15", 2, 0, 8, 0, 360)
        )
        val sonno = AdviceEngine.generate(AdviceInput(recentNights = nights), utc).of(AdviceCategory.SONNO)
        assertTrue(sonno.any { it.title.contains("variabili") })
    }

    @Test
    fun orari_regolari_non_generano_l_avviso() {
        val nights = (13..15).map { night("2026-10-$it", 23, 0, 7, 0, 450) }
        val sonno = AdviceEngine.generate(AdviceInput(recentNights = nights), utc).of(AdviceCategory.SONNO)
        assertTrue(sonno.none { it.title.contains("variabili") })
    }

    @Test
    fun alimentazione_senza_pasti_chiede_di_registrarli() {
        val a = AdviceEngine.generate(AdviceInput(eatenKcal = 0), utc).of(AdviceCategory.ALIMENTAZIONE)
        assertEquals(1, a.size)
        assertTrue(a.first().title.contains("Registra"))
    }

    @Test
    fun alimentazione_con_poche_proteine_le_suggerisce() {
        val a = AdviceEngine.generate(
            AdviceInput(eatenKcal = 900, remainingKcal = 600, proteinEaten = 20f, proteinGoal = 100), utc
        ).of(AdviceCategory.ALIMENTAZIONE)
        assertTrue(a.any { it.title.contains("proteine", ignoreCase = true) })
        assertTrue(a.any { it.title.contains("600") })
    }

    // ------------------------------------------------------------------
    // La tua giornata
    // ------------------------------------------------------------------

    private fun day(minute: Int, eaten: Int, remaining: Int, vararg meals: String, activityKcal: Int = 0, protein: Float = 0f, steps: Int? = null) =
        AdviceEngine.generate(
            AdviceInput(
                eatenKcal = eaten, remainingKcal = remaining, proteinEaten = protein, proteinGoal = 120,
                minuteOfDay = minute, mealTypes = meals.toSet(), activityKcal = activityKcal, weightKg = 70, steps = steps
            ), utc
        ).of(AdviceCategory.GIORNATA)

    @Test
    fun pomeriggio_divide_le_calorie_rimaste_sulla_cena() {
        val a = day(16 * 60, eaten = 1200, remaining = 700, "Colazione", "Pranzo")
        assertEquals("A cena: circa 700 kcal", a.first().title)
    }

    @Test
    fun mattina_divide_tra_pranzo_e_cena() {
        val a = day(12 * 60, eaten = 400, remaining = 1400, "Colazione")
        assertEquals("A pranzo: circa 700 kcal", a.first().title)
    }

    @Test
    fun oltre_obiettivo_propone_camminata() {
        val a = day(21 * 60, eaten = 2300, remaining = -200, "Colazione", "Pranzo", "Cena")
        assertTrue(a.first().title.startsWith("Oltre di 200 kcal"))
    }

    @Test
    fun oltre_obiettivo_ma_coperto_dall_allenamento_non_chiede_di_compensare() {
        val a = day(21 * 60, eaten = 2300, remaining = -200, "Colazione", "Pranzo", "Cena", activityKcal = 600)
        assertTrue(a.none { it.title.startsWith("Oltre") })
        assertTrue(a.any { it.title.startsWith("Allenamento") })
    }

    @Test
    fun sera_con_poche_proteine() {
        val a = day(19 * 60, eaten = 1000, remaining = 900, "Colazione", "Pranzo", protein = 50f)
        assertTrue(a.any { it.title == "Mancano 70 g di proteine" })
    }

    @Test
    fun giorni_passati_niente_consigli_della_giornata() {
        val a = AdviceEngine.generate(AdviceInput(eatenKcal = 900, remainingKcal = 600, minuteOfDay = 600, isToday = false), utc)
        assertTrue(a.of(AdviceCategory.GIORNATA).isEmpty())
    }
}
