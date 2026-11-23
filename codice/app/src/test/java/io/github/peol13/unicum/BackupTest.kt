package io.github.peol13.unicum

import io.github.peol13.unicum.data.BackupCodec
import io.github.peol13.unicum.data.BackupReminders
import io.github.peol13.unicum.data.BackupSession
import io.github.peol13.unicum.data.BackupSettings
import io.github.peol13.unicum.data.BackupFile
import io.github.peol13.unicum.data.BackupNotion
import io.github.peol13.unicum.data.RunGoal
import io.github.peol13.unicum.data.RunGoals
import org.junit.Assert.assertFalse
import io.github.peol13.unicum.data.Food
import io.github.peol13.unicum.data.Shoe
import io.github.peol13.unicum.data.ShoeData
import io.github.peol13.unicum.data.WorkoutFeedback
import io.github.peol13.unicum.data.db.ActivityEntity
import io.github.peol13.unicum.data.db.FoodEntity
import io.github.peol13.unicum.data.db.MealEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private fun meal(date: String = "2026-10-12", name: String = "Pasta", at: Long = 1_000_000_000L) =
        MealEntity(id = 1, date = date, mealType = "Pranzo", foodId = null, foodName = name,
            grams = 100f, kcal = 350f, protein = 12f, carbs = 70f, fats = 2f, createdAt = at)

    private fun act(source: String = "manual", ext: String? = null, minutes: Int = 30) =
        ActivityEntity(date = "2026-10-12", type = "Corsa", durationMinutes = minutes, kcal = 300,
            source = source, externalId = ext)

    private fun food(id: String = "off:1") =
        FoodEntity(id, "Biscotti", "Marca", 450f, 6f, 70f, 16f, "openfoodfacts", 5L)

    @Test fun roundTrip() {
        val settings = BackupSettings(weightKg = 80, customFoods = listOf(Food("Mio", 100)), recentFoods = listOf("Mio"))
        val file = BackupCodec.build(listOf(meal()), listOf(act()), listOf(food()), settings, 42L)
        val back = BackupCodec.decode(BackupCodec.encode(file))
        assertNotNull(back)
        assertEquals(file, back)
        assertEquals(80, back!!.settings!!.weightKg)
    }

    @Test fun onlyManualActivitiesAreExported() {
        val file = BackupCodec.build(emptyList(), listOf(act(), act("com.samsung", "x1")), emptyList(), null, 0L)
        assertEquals(1, file.activities.size)
    }

    @Test fun rejectsForeignOrBrokenFiles() {
        assertNull(BackupCodec.decode("non json"))
        assertNull(BackupCodec.decode("""{"app":"altro","version":1}"""))
        assertNull(BackupCodec.decode("""{"app":"unicum","version":99}"""))
    }

    @Test fun ignoresUnknownKeys() {
        assertNotNull(BackupCodec.decode("""{"app":"unicum","version":1,"futuro":true}"""))
    }

    @Test fun mergeSkipsExistingMealsAndKeepsNew() {
        val file = BackupCodec.build(
            listOf(meal(), meal(name = "Riso"), meal(at = 1_000_000_000L + 3_600_000L)),
            emptyList(), emptyList(), null, 0L
        )
        // Pasta alla stessa ora (anche con secondi diversi) esiste già
        val plan = BackupCodec.plan(file, listOf(meal(at = 1_000_000_000L + 5_000L)), emptyList(), emptySet())
        assertEquals(2, plan.newMeals.size)
        assertEquals(1, plan.skippedMeals)
    }

    @Test fun importingTwiceAddsNothing() {
        val file = BackupCodec.build(listOf(meal()), listOf(act()), listOf(food()), null, 0L)
        val first = BackupCodec.plan(file, emptyList(), emptyList(), emptySet())
        assertEquals(1, first.newMeals.size)
        val second = BackupCodec.plan(file, listOf(meal()), listOf(act()), setOf("off:1"))
        assertTrue(second.isEmpty)
    }

    @Test fun repeatedEntriesInsideFileAreAllKept() {
        // Lo stesso pane inserito due volte nello stesso pasto: sono due voci vere
        val file = BackupCodec.build(listOf(meal(), meal()), emptyList(), emptyList(), null, 0L)
        assertEquals(2, BackupCodec.plan(file, emptyList(), emptyList(), emptySet()).newMeals.size)
        // Reimportato sopra uno dei due, entra solo quello che manca; sopra entrambi, niente
        assertEquals(1, BackupCodec.plan(file, listOf(meal()), emptyList(), emptySet()).newMeals.size)
        assertTrue(BackupCodec.plan(file, listOf(meal(), meal()), emptyList(), emptySet()).isEmpty)
    }

    @Test fun sameFoodAtSameTimeInAnotherMealIsNotADuplicate() {
        // Salame a pranzo e a cena con lo stesso orario salvato: due pasti diversi
        val lunch = meal(name = "Salame")
        val dinner = meal(name = "Salame").copy(mealType = "Cena", grams = 30f)
        val file = BackupCodec.build(listOf(lunch, dinner), emptyList(), emptyList(), null, 0L)
        assertEquals(2, BackupCodec.plan(file, emptyList(), emptyList(), emptySet()).newMeals.size)
        assertEquals(1, BackupCodec.plan(file, listOf(lunch), emptyList(), emptySet()).newMeals.size)
    }

    @Test fun notionConnectionOnlyWhenChosen() {
        assertNull(BackupCodec.build(emptyList(), emptyList(), emptyList(), null, 0L).notion)
        // Senza token o senza database non si scrive niente
        assertNull(BackupCodec.build(emptyList(), emptyList(), emptyList(), null, 0L, notion = BackupNotion(" ", "db")).notion)
        val n = BackupNotion("secret_x", "plan", "log")
        val back = BackupCodec.decode(BackupCodec.encode(BackupCodec.build(emptyList(), emptyList(), emptyList(), null, 0L, notion = n)))!!
        assertEquals(n, back.notion)
        assertTrue(BackupCodec.plan(back, emptyList(), emptyList(), emptySet()).hasNotion)
        // Già collegata: il file non cambia il collegamento e reimportare non propone nulla
        assertTrue(BackupCodec.plan(back, emptyList(), emptyList(), emptySet(), notionConnected = true).isEmpty)
    }

    @Test fun mealWithCorrectedGramsIsNotDuplicated() {
        val file = BackupCodec.build(listOf(meal()), emptyList(), emptyList(), null, 0L)
        assertTrue(BackupCodec.plan(file, listOf(meal().copy(grams = 150f)), emptyList(), emptySet()).isEmpty)
    }

    @Test fun importedHealthConnectActivitiesDoNotBlockManualOnes() {
        val file = BackupCodec.build(emptyList(), listOf(act()), emptyList(), null, 0L)
        val plan = BackupCodec.plan(file, emptyList(), listOf(act("com.samsung", "x1")), emptySet())
        assertEquals(1, plan.newActivities.size)
    }

    @Test fun daysCountsDistinctDates() {
        val file = BackupCodec.build(
            listOf(meal(), meal(name = "Riso"), meal(date = "2026-10-13")), listOf(act()), emptyList(), null, 0L
        )
        assertEquals(2, BackupCodec.plan(file, emptyList(), emptyList(), emptySet()).days)
    }

    // ---- sensazioni delle corse e scarpe su un altro telefono

    private val samsung = "com.sec.android.app.shealth"
    private fun session(id: String, start: Long, source: String = samsung, type: String = "Corsa") = BackupSession(id, type, source, start)
    private val corrected = WorkoutFeedback(rpe = 6, kind = "Facile", distanceM = 2710.0, movingSeconds = 1020, avgHr = 147, maxHr = 160)

    @Test fun feedbackAndShoesAreExportedWithTheirSessions() {
        val shoes = ShoeData(listOf(Shoe("s1", "Pegasus", "2026-09-01")), "s1", mapOf("b" to "s1"))
        val file = BackupCodec.build(
            emptyList(), emptyList(), emptyList(), null, 0L, shoes,
            feedback = mapOf("a" to corrected, "vuota" to WorkoutFeedback()),
            sessions = listOf(session("a", 1_000), session("b", 2_000), session("c", 3_000)),
            reminders = BackupReminders(mapOf("Pranzo" to 780, "Altro" to -1), recoveryAlerts = false)
        )
        val back = BackupCodec.decode(BackupCodec.encode(file))!!
        assertEquals(listOf("a"), back.runFeedback.map { it.id })
        assertEquals(corrected, back.runFeedback.single().feedback)
        // Solo le sessioni che servono (con sensazioni o scarpa)
        assertEquals(listOf("a", "b"), back.sessions.map { it.id })
        assertEquals(780, back.reminders!!.minutes["Pranzo"])
        assertEquals(false, back.reminders!!.recoveryAlerts)
    }

    @Test fun runGoalsTravelInTheFile() {
        val goals = RunGoals(short = RunGoal(5.0, 390, "2026-12-31"), long = RunGoal(10.0, 420, "2027-06-30"))
        val file = BackupCodec.build(emptyList(), emptyList(), emptyList(), null, 0L, runGoals = goals)
        val back = BackupCodec.decode(BackupCodec.encode(file))!!
        assertEquals(goals, back.runGoals)
        val plan = BackupCodec.plan(back, emptyList(), emptyList(), emptySet())
        assertTrue(plan.hasGoals)
        assertFalse(plan.isEmpty)
        // Un file vecchio senza obiettivi non tocca quelli attuali
        assertEquals(null, BackupCodec.decode(BackupCodec.encode(BackupFile()))!!.runGoals)
    }

    @Test fun onANewPhoneSessionsAreFoundByStartTime() {
        val file = BackupCodec.build(
            emptyList(), emptyList(), emptyList(), null, 0L,
            ShoeData(listOf(Shoe("s1", "Pegasus", "2026-09-01")), "s1", mapOf("old-a" to "s1")),
            feedback = mapOf("old-a" to corrected, "old-b" to WorkoutFeedback(rpe = 3)),
            sessions = listOf(session("old-a", 1_000_000), session("old-b", 9_000_000))
        )
        // Sul telefono nuovo la stessa corsa ha un altro id (e c'è anche la copia di Health Sync); l'altra non è ancora arrivata
        val local = listOf(
            session("new-a-healthsync", 1_030_000, source = "nl.appyhapps.healthsync"),
            session("new-a", 1_045_000),
            session("altra", 1_000_000, type = "Ciclismo")
        )
        val r = BackupCodec.resolve(file.pending, local)
        assertEquals(mapOf("new-a" to corrected), r.feedback)
        assertEquals(mapOf("new-a" to "s1"), r.shoeRuns)
        assertEquals(listOf("old-b"), r.left.runFeedback.map { it.id })
        assertEquals(listOf("old-b"), r.left.sessions.map { it.id })

        // Dopo la sincronizzazione arriva anche la seconda
        val later = BackupCodec.resolve(r.left, local + session("new-b", 9_010_000))
        assertEquals(3, later.feedback["new-b"]!!.rpe)
        assertTrue(later.left.isEmpty)
    }

    @Test fun backupFillsOnlyWhatIsMissing() {
        val local = WorkoutFeedback(rpe = 8, note = "gambe pesanti")
        val merged = BackupCodec.mergeFeedback(local, corrected)
        assertEquals(8, merged.rpe)
        assertEquals("Facile", merged.kind)
        assertEquals("gambe pesanti", merged.note)
        assertEquals(1020, merged.movingSeconds)
        assertEquals(147, merged.avgHr)
        // Una correzione già fatta qui resta intera
        val mine = WorkoutFeedback(distanceM = 3000.0, movingSeconds = 1100)
        assertNull(BackupCodec.mergeFeedback(mine, corrected).avgHr)
        assertEquals(1100, BackupCodec.mergeFeedback(mine, corrected).movingSeconds)
    }

    @Test fun importingFeedbackTwiceAddsNothing() {
        val file = BackupCodec.build(
            emptyList(), emptyList(), emptyList(), null, 0L,
            feedback = mapOf("a" to corrected), sessions = listOf(session("a", 1_000))
        )
        val local = listOf(session("a", 1_000))
        assertEquals(1, BackupCodec.feedbackChanges(file.pending, local, emptyMap()))
        assertEquals(0, BackupCodec.feedbackChanges(file.pending, local, mapOf("a" to corrected)))
    }

    @Test fun customFoodsMergeKeepsExisting() {
        val merged = BackupCodec.mergeCustomFoods(listOf(Food("Mio", 100)), listOf(Food("mio", 999), Food("Altro", 50)))
        assertEquals(listOf("Mio", "Altro"), merged.map { it.name })
        assertEquals(100, merged[0].caloriesPer100g)
    }

    @Test
    fun keepsNotionUnitsAndWeeklySummary() {
        // Backup automatico (1.1.0): collegamento a Notion, unità di misura e notifica del lunedì restano nel file
        val file = BackupFile(
            settings = BackupSettings(unitSystem = "IMPERIAL", completeDays = listOf("2026-11-20")),
            reminders = BackupReminders(weeklySummary = false),
            notion = BackupNotion("ntn_token", "db-programma", "db-registro")
        )
        val back = BackupCodec.decode(BackupCodec.encode(file))!!
        assertEquals("ntn_token", back.notion?.token)
        assertEquals("db-registro", back.notion?.logDatabase)
        assertEquals("IMPERIAL", back.settings?.unitSystem)
        assertEquals(listOf("2026-11-20"), back.settings?.completeDays)
        assertFalse(back.reminders!!.weeklySummary)
        // File delle versioni precedenti: i campi nuovi prendono il valore di base
        val old = BackupCodec.decode("""{"app":"unicum","version":1,"settings":{"age":30},"reminders":{"recoveryAlerts":false}}""")!!
        assertEquals(null, old.settings?.unitSystem)
        assertTrue(old.reminders!!.weeklySummary)
    }
}
