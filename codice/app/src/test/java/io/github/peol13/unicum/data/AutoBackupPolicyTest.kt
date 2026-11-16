package io.github.peol13.unicum.data

import io.github.peol13.unicum.data.AutoBackupPolicy.Health
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupPolicyTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test
    fun `un backup al giorno ma senza slittare di un'ora ogni volta`() {
        val last = 1_000 * day
        assertFalse(AutoBackupPolicy.isDue(last, last + 19 * hour))
        assertTrue(AutoBackupPolicy.isDue(last, last + 20 * hour))
        assertTrue(AutoBackupPolicy.isDue(0L, last))
    }

    @Test
    fun `lo stato dice quando avvisare`() {
        val now = 1_000 * day
        fun h(enabled: Boolean = true, last: Long, setUp: Long = now - 30 * day, error: String? = null, held: String? = null) =
            AutoBackupPolicy.health(enabled, last, setUp, now, error, held)

        assertEquals(Health.OFF, h(enabled = false, last = 0))
        assertEquals(Health.OK, h(last = now - 5 * hour))
        // Un errore di stanotte con un backup di ieri è ancora sopportabile
        assertEquals(Health.OK, h(last = now - 30 * hour, error = "rete assente"))
        // Due giorni senza riuscire: si segnala l'errore
        assertEquals(Health.ERROR, h(last = now - 2 * day - hour, error = "rete assente"))
        // Da più di tre giorni nessun backup: avviso, con o senza errore
        assertEquals(Health.STALE, h(last = now - 4 * day))
        assertEquals(Health.STALE, h(last = now - 4 * day, error = "rete assente"))
        // Il blocco ha la precedenza
        assertEquals(Health.HELD, h(last = now - 5 * hour, held = "pochi dati"))
    }

    @Test
    fun `appena attivato non si avvisa e un errore subito si vede`() {
        val now = 1_000 * day
        assertEquals(Health.OK, AutoBackupPolicy.health(true, 0, now - hour, now, null, null))
        assertEquals(Health.ERROR, AutoBackupPolicy.health(true, 0, now - hour, now, "permesso perso", null))
        assertEquals(Health.STALE, AutoBackupPolicy.health(true, 0, now - 4 * day, now, null, null))
    }

    private fun file(meals: Int, feedback: Int = 0) = BackupFile(
        meals = List(meals) { BackupMeal("2026-10-01", it.toLong(), "Pranzo", "Pasta $it", 100f, 350f, 12f, 70f, 2f) },
        runFeedback = List(feedback) { BackupRunFeedback("run-$it", WorkoutFeedback(rpe = 5)) }
    )

    @Test
    fun `un database svuotato non sovrascrive il backup buono`() {
        val old = file(meals = 259, feedback = 21)
        assertTrue(BackupCodec.shrunk(old, file(meals = 0, feedback = 0)))
        assertTrue(BackupCodec.shrunk(old, file(meals = 100, feedback = 21)))
        // Anche se i pasti ci sono ancora ma le sensazioni delle corse sono sparite
        assertTrue(BackupCodec.shrunk(old, file(meals = 259, feedback = 3)))
    }

    @Test
    fun `le variazioni normali non bloccano il backup`() {
        val old = file(meals = 259, feedback = 21)
        assertFalse(BackupCodec.shrunk(old, file(meals = 260, feedback = 21)))
        assertFalse(BackupCodec.shrunk(old, file(meals = 200, feedback = 21)))
        // Con pochi dati (appena iniziato) non si può dire nulla
        assertFalse(BackupCodec.shrunk(file(meals = 10), file(meals = 0)))
        assertFalse(BackupCodec.shrunk(null, file(meals = 0)))
    }
}
