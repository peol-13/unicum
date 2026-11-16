package io.github.peol13.unicum

import io.github.peol13.unicum.data.RecoveryAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecoveryAlertsTest {
    private val today = LocalDate.of(2026, 10, 19)

    /** HRV di 50 ms per i 10 giorni prima delle ultime due notti, poi i valori dati. */
    private fun hrv(beforeLast: Double, last: Double) =
        (2L..11L).associate { today.minusDays(it) to 50.0 } + mapOf(today.minusDays(1) to beforeLast, today to last)

    private fun temp(last: Double) = (1L..10L).associate { today.minusDays(it) to 34.3 } + (today to last)

    @Test
    fun `HRV sotto del 20 percento per due notti`() {
        val a = RecoveryAlerts.check(hrv(39.0, 38.0), emptyMap(), today)!!
        assertEquals(22, a.hrvDropPct)          // conta la meno bassa delle due (39 su 50)
        assertNull(a.tempRise)
        assertTrue(a.title.contains("HRV"))
    }

    @Test
    fun `una sola notte bassa non basta`() {
        assertNull(RecoveryAlerts.check(hrv(48.0, 30.0), emptyMap(), today))
        assertNull(RecoveryAlerts.check(hrv(41.0, 40.0), emptyMap(), today))   // -18%: sotto soglia
    }

    @Test
    fun `temperatura mezzo grado sopra la media`() {
        val a = RecoveryAlerts.check(emptyMap(), temp(34.8), today)!!
        assertEquals(0.5, a.tempRise!!, 1e-6)
        assertNull(RecoveryAlerts.check(emptyMap(), temp(34.7), today))
    }

    @Test
    fun `HRV bassa e febbre insieme`() {
        val a = RecoveryAlerts.check(hrv(38.0, 37.0), temp(35.0), today)!!
        assertNotNull(a.hrvDropPct)
        assertEquals(0.7, a.tempRise!!, 1e-6)
        assertTrue(a.text.contains("malattia"))
    }

    @Test
    fun `senza storico nessun avviso`() {
        val poche = mapOf(today.minusDays(3) to 50.0, today.minusDays(2) to 50.0, today.minusDays(1) to 30.0, today to 30.0)
        assertNull(RecoveryAlerts.check(poche, emptyMap(), today))
    }
}
