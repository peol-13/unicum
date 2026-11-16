package io.github.peol13.unicum.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShoesTest {

    private fun w(id: String, date: String, km: Double, seconds: Int, hr: Int? = 150) = WorkoutDetail(
        externalId = id, source = "s", date = date, type = "Corsa", exerciseType = 56,
        startMillis = LocalDate.parse(date).toEpochDay() * 86_400_000L, endMillis = LocalDate.parse(date).toEpochDay() * 86_400_000L + seconds * 1000L,
        movingSeconds = seconds, distanceM = km * 1000, avgHr = hr
    )

    private val a = Shoe("a", "Pegasus", since = "2026-10-01", startKm = 100.0, limitKm = 700)
    private val b = Shoe("b", "Novablast", since = "2026-10-20")

    @Test
    fun laPredefinitaVaSoloAlleCorseDalSuoGiornoSenzaScelta() {
        val runs = listOf(w("r1", "2026-09-28", 5.0, 1800), w("r2", "2026-10-02", 5.0, 1800), w("r3", "2026-10-05", 4.0, 1500))
        val data = ShoeData().upsert(a, makeDefault = true).assign("r3", null).autoAssign(runs)
        assertEquals("a", data.runs["r2"])
        assertNull(data.runs["r1"])          // prima di "in uso dal"
        assertEquals("", data.runs["r3"])    // "Nessuna" scelto a mano resta
        assertNull(data.shoeOf("r3"))
    }

    @Test
    fun cambiareLaPredefinitaNonSpostaLeCorseGiaAssegnate() {
        val runs = listOf(w("r1", "2026-10-02", 5.0, 1800), w("r2", "2026-10-22", 6.0, 2100))
        var data = ShoeData().upsert(a, true).autoAssign(runs.take(1))
        data = data.upsert(b, true).autoAssign(runs)
        assertEquals("a", data.runs["r1"])
        assertEquals("b", data.runs["r2"])
        assertEquals("b", data.defaultId)
    }

    @Test
    fun ritirataNonRicevePiuCorseEdEliminareLiberaLeCorse() {
        var data = ShoeData().upsert(a, true).upsert(a.copy(retired = true), makeDefault = false)
        assertNull(data.defaultId)
        data = data.autoAssign(listOf(w("r1", "2026-10-02", 5.0, 1800)))
        assertTrue(data.runs.isEmpty())
        data = ShoeData().upsert(a, true).autoAssign(listOf(w("r1", "2026-10-02", 5.0, 1800))).remove("a")
        assertEquals("", data.runs["r1"])
        assertTrue(data.shoes.isEmpty())
    }

    @Test
    fun statistiche() {
        val runs = listOf(
            w("r1", "2026-10-02", 5.0, 1800, hr = 150),  // 6'00"
            w("r2", "2026-10-30", 10.0, 3300, hr = 160), // 5'30"
            w("r3", "2026-11-01", 3.0, 1000)              // altra scarpa
        )
        val data = ShoeData(shoes = listOf(a, b), runs = mapOf("r1" to "a", "r2" to "a", "r3" to "b"))
        val s = ShoeStats.of(a, runs, data, mapOf("r1" to "Facile", "r2" to "5km"), LocalDate.of(2026, 11, 7))
        assertEquals(2, s.count)
        assertEquals(115.0, s.km, 1e-6)
        assertEquals(15.0, s.runKm, 1e-6)
        assertEquals(340, s.paceSec)                  // 5100 s / 15 km
        assertEquals((150 * 1800 + 160 * 3300) / 5100.0, s.avgHr!!.toDouble(), 0.5)
        assertEquals(10.0, s.longestKm!!, 1e-6)
        assertEquals(10.0, s.last30Km, 1e-6)
        assertEquals(LocalDate.of(2026, 10, 30), s.last)
        assertEquals(listOf("5km" to 10.0, "Facile" to 5.0), s.byKind)
        assertEquals(585.0, s.remainingKm, 1e-6)
        // Dalla prima corsa (2/10): 37 giorni
        assertEquals(15.0 / (37 / 7.0), s.weeklyKm!!, 1e-6)
        assertEquals((585 / (15.0 / (37 / 7.0))).toInt().toDouble(), s.weeksLeft!!.toDouble(), 1.0)
    }

    @Test
    fun backupUnisceSenzaSovrascrivere() {
        val here = ShoeData(shoes = listOf(a), defaultId = "a", runs = mapOf("r1" to "a"))
        val file = ShoeData(shoes = listOf(a.copy(name = "Altro"), b), defaultId = "b", runs = mapOf("r1" to "b", "r2" to "b"))
        val m = here.merge(file)
        assertEquals(listOf("Pegasus", "Novablast"), m.shoes.map { it.name })
        assertEquals("a", m.defaultId)
        assertEquals("a", m.runs["r1"])
        assertEquals("b", m.runs["r2"])
    }

    // ---------- tag di Notion

    @Test
    fun tipiLettiDalloSchemaDiNotion() {
        val plan = JSONObject(
            """{"object":"database","properties":{"Tipo":{"type":"select","select":{"options":[
                {"name":"Facile","color":"green"},{"name":"Intervalli","color":"red"},
                {"name":"Progressivo","color":"orange"},{"name":"5km","color":"red"}]}}}}"""
        )
        val log = JSONObject(
            """{"properties":{"Tipo":{"type":"select","select":{"options":[
                {"name":"Facile","color":"green"},{"name":"Lungo facile","color":"blue"},{"name":" "}]}}}}"""
        )
        val kinds = NotionPlan.mergeKinds(NotionPlan.kindOptions(plan), NotionPlan.kindOptions(log))
        assertEquals(listOf("Facile", "Intervalli", "Progressivo", "5km", "Lungo facile"), kinds.map { it.name })
        assertEquals("red", kinds[3].color)
        assertTrue(NotionPlan.kindOptions(JSONObject("{}")).isEmpty())
    }

    @Test
    fun nel5kmUnTempoPiuBassoEMeglio() {
        fun r(day: Int, sec: Int) = RunProgress.Run("r$day", LocalDate.of(2026, 10, day), "5km", 5.0, sec, sec / 5, 170, 185, 160, 8, 41.0)
        val rows = RunProgress.compare(listOf(r(1, 2100), r(8, 2040)), "5km")
        val duration = rows.first { it.metric == RunProgress.Metric.DURATION }
        assertEquals(true, duration.deltaGood)          // 34:00 contro 35:00
        assertEquals("r8", duration.best?.id)
        assertNull(rows.first { it.metric == RunProgress.Metric.KM }.deltaGood)
        assertTrue(RunProgress.isDistanceKind("10 km"))
        assertTrue(RunProgress.isEasyKind("Lungo facile"))
    }

    @Test
    fun senzaTipoValeQuelloDelProgramma() {
        val planned = PlannedRun("p", "Test 5 km", "2026-11-05", "5km", null)
        assertEquals("5km", RunFeedback.merge(null, null, planned).kind)
        assertEquals("Facile", RunFeedback.merge(WorkoutFeedback(kind = "Facile"), null, planned).kind)
        val logged = LoggedRun("l", "x", "2026-11-05", kind = "Intervalli")
        assertEquals("Intervalli", RunFeedback.merge(WorkoutFeedback(kind = "Facile"), logged, planned).kind)
    }
}
