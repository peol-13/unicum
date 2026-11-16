package io.github.peol13.unicum

import io.github.peol13.unicum.data.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateUtilsTest {

    private val friday = LocalDate.of(2026, 10, 16)

    @Test
    fun la_settimana_inizia_di_lunedi() {
        assertEquals(LocalDate.of(2026, 10, 12), DateUtils.weekStart(friday))
        // un lunedì è già l'inizio della sua settimana, una domenica appartiene a quella che finisce
        assertEquals(LocalDate.of(2026, 10, 12), DateUtils.weekStart(LocalDate.of(2026, 10, 12)))
        assertEquals(LocalDate.of(2026, 10, 12), DateUtils.weekStart(LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun la_settimana_ha_sette_giorni_da_lunedi_a_domenica() {
        val days = DateUtils.weekDays(LocalDate.of(2026, 10, 12))
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 12), days.first())
        assertEquals(LocalDate.of(2026, 10, 18), days.last())
        assertEquals("LMMGVSD", days.joinToString("") { DateUtils.weekdayInitial(it) })
    }

    @Test
    fun ultimi_giorni_finiscono_alla_data_indicata() {
        val days = DateUtils.lastDays(3, friday)
        assertEquals(listOf("2026-10-14", "2026-10-15", "2026-10-16"), days)
    }

    @Test
    fun etichette_relative() {
        assertEquals("Oggi", DateUtils.relativeLabel(friday, friday))
        assertEquals("Ieri", DateUtils.relativeLabel(friday.minusDays(1), friday))
        assertTrue(DateUtils.relativeLabel(friday.minusDays(3), friday).startsWith("Mar", ignoreCase = true))
    }

    @Test
    fun etichette_della_settimana() {
        assertEquals("12 – 18 Ott", DateUtils.weekLabel(LocalDate.of(2026, 10, 12)))
        // settimana a cavallo di due mesi: compaiono entrambi
        assertEquals("28 Set – 4 Ott", DateUtils.weekLabel(LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun data_completa_con_iniziali_maiuscole() {
        assertEquals("Venerdì 16 Ottobre", DateUtils.fullLabel(friday))
    }
}
