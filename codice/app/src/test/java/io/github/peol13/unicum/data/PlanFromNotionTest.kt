package io.github.peol13.unicum.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Struttura del programma Notion → pezzi della corsa; registro → tipo, RPE e nota. */
class PlanFromNotionTest {

    private fun kinds(steps: List<PlanStep>?) = steps!!.joinToString(" ") {
        "${it.kind.name.take(2)}${it.rep ?: ""}:" + (it.part.seconds?.let { s -> "${s}s" } ?: "${it.part.meters!!.toInt()}m")
    }

    // ---------- strutture vere del "Programma corse"

    @Test
    fun intervalsWithWarmUpAndCoolDown() {
        val s = PlanStructure.parse(
            "10' di corsa facile a 7:15–7:45 + 3×(4' a 6:45–6:55 min/km + 2' lento) + 5' defaticamento, RPE 6/10. " +
                "Regola chiave: non scendere sotto 6:45 nei blocchi (il 15/10 le ripetute erano troppo veloci)."
        )
        assertEquals("WA:600s WO1:240s RE1:120s WO2:240s RE2:120s WO3:240s RE3:120s CO:300s", kinds(s))
    }

    @Test
    fun intervalsWithSecondsAndNoWarmUp() {
        assertEquals(
            "WO1:60s RE1:90s WO2:60s RE2:90s WO3:60s RE3:90s WO4:60s RE4:90s WO5:60s RE5:90s WO6:60s RE6:90s",
            kinds(PlanStructure.parse("6×(1' brillante a 5:45–6:05 min/km + 90'' di corsa lenta), RPE 7/10. Obiettivo: ripetute regolari, senza sprintare."))
        )
        assertEquals(
            "WO1:300s RE1:120s WO2:300s RE2:120s WO3:300s RE3:120s",
            kinds(PlanStructure.parse("3×(5' sostenuti a 6:00–6:20 min/km + 2' di corsa lenta), RPE 7/10. Obiettivo: tenere lo stesso ritmo."))
        )
    }

    @Test
    fun progressive() {
        assertEquals(
            "WO1:300s WO2:300s WO3:300s",
            kinds(PlanStructure.parse("15' progressivi: 5' a 7:10–6:50, 5' a 6:50–6:25, 5' a 6:25–6:05 min/km, RPE finale 6/10. Obiettivo: accelerare senza strappi."))
        )
    }

    @Test
    fun distancesAndUnstructured() {
        assertEquals("WA:1000m WO1:400m RE1:200m WO2:400m RE2:200m", kinds(PlanStructure.parse("1 km facile + 2×(400 m veloci + 200 m lenti)")))
        // Corsa continua, test, camminata + corsa: nessuna struttura (restano i km)
        assertNull(PlanStructure.parse("20' di corsa continua a 6:40–7:15 min/km, RPE 4/10. Obiettivo: respirazione regolare."))
        assertNull(PlanStructure.parse("5' di camminata veloce + 25' di corsa continua a 7:15–7:45 min/km, RPE 3/10."))
        assertNull(PlanStructure.parse("Riscaldamento ~25': 10' camminata veloce, 3–5' attivazione. Test 5 km: km 1 6:50."))
        assertNull(PlanStructure.parse(null))
    }

    @Test
    fun onlyIntervalOrProgressiveSessionsAreSplit() {
        val planned = PlannedRun("p", "Attivazione", "2026-11-10", "Facile", "Da fare",
            structure = "15' di corsa facile + 3×(20'' di allungo + 40'' lento) + 5' camminata, RPE 3/10.")
        assertNull(RunFeedback.planSteps(null, planned))
        assertNull(RunFeedback.planSteps("Facile", planned.copy(kind = "Intervalli")))
        assertTrue(RunFeedback.planSteps("Intervalli", planned) != null)
        assertTrue(RunFeedback.planSteps(null, planned.copy(kind = "Intervalli")) != null)
    }

    // ---------- collocazione sulla registrazione

    /** 7' di cammino a 1,4 m/s, poi 3 × (4' a 3 m/s + 2' a 1,6 m/s), poi 3' a 1,4 m/s. */
    private fun runWithUnwrittenWarmUp(): WorkoutDetail {
        fun v(t: Int): Float = when {
            t < 420 || t >= 420 + 3 * 360 -> 1.4f
            (t - 420) % 360 < 240 -> 3f
            else -> 1.6f
        }
        val end = 420 + 3 * 360 + 180
        val speed = (0..end).map { WorkoutSample(it, v(it)) }
        val hr = (0..end step 5).map { WorkoutSample(it, if (v(it) == 3f) 172f else 140f) }
        return WorkoutDetail(
            externalId = "x", source = "s", date = "2026-10-15", type = "Corsa", exerciseType = 56,
            startMillis = 0, endMillis = end * 1000L, movingSeconds = end,
            distanceM = (0 until end).sumOf { v(it).toDouble() }, speed = speed, hr = hr
        )
    }

    @Test
    fun planIsAlignedWhereTheFastPartsAre() {
        val d = runWithUnwrittenWarmUp()
        val steps = PlanStructure.parse("3×(4' sostenuti + 2' di corsa lenta), RPE 7/10.")!!
        val offset = WorkoutAnalysis.alignPlan(d, steps)
        assertEquals(420, offset)
        val planned = PlannedRun("p", "Tempo 3×4'", "2026-10-15", "Intervalli", "Completata")
        val plan = RunFeedback.autoPlan(d, steps, planned)!!
        assertEquals(420, plan.offsetS)
        assertEquals(172, plan.workHr)
        val seg = WorkoutAnalysis.intervals(d, plan)
        // Riscaldamento non scritto, 3 veloci + 3 recuperi, defaticamento
        assertEquals(WorkoutAnalysis.IntervalKind.WARM_UP, seg.first().kind)
        assertEquals(420, seg.first().seconds)
        val work = seg.filter { it.kind == WorkoutAnalysis.IntervalKind.WORK }
        assertEquals(3, work.size)
        work.forEach { assertEquals(720.0, it.meters, 2.0) }
        assertEquals(WorkoutAnalysis.IntervalKind.COOL_DOWN, seg.last().kind)
        assertEquals(180, seg.last().seconds)
    }

    @Test
    fun planLongerThanRecordingStartsAtZero() {
        val d = runWithUnwrittenWarmUp()
        val steps = PlanStructure.parse("20' facile + 5×(4' veloce + 2' lento)")!!
        assertEquals(0, WorkoutAnalysis.alignPlan(d, steps))
    }

    // ---------- registro corse

    @Test
    fun logRowsAreParsedAndMatched() {
        val json = JSONObject(
            """{"results":[
              {"id":"r1","url":"u","properties":{
                "Corsa":{"title":[{"plain_text":"Ritmo test 3×4'"}]},
                "Data":{"date":{"start":"2026-11-05"}},
                "Tipo":{"select":{"name":"Intervalli"}},
                "RPE":{"number":7},
                "Note":{"rich_text":[{"plain_text":"Gambe buone"}]},
                "Distanza km":{"number":3.97},
                "Sessione pianificata":{"relation":[{"id":"40f3377b-6319-8184-851f-e81f24d9fede"}]}}},
              {"id":"r2","properties":{
                "Corsa":{"title":[{"plain_text":"Seconda"}]},
                "Data":{"date":{"start":"2026-11-05T18:00:00.000+01:00"}},
                "Tipo":{"select":null},"RPE":{"number":null},"Distanza km":{"number":2.0}}}
            ]}"""
        )
        val log = NotionPlan.parseLogResults(json)
        assertEquals(2, log.size)
        assertEquals("Intervalli", log[0].kind)
        assertEquals(7, log[0].rpe)
        assertEquals("Gambe buone", log[0].note)
        assertEquals("40f3377b-6319-8184-851f-e81f24d9fede", log[0].plannedId)
        assertEquals("2026-11-05", log[1].date)
        assertNull(log[1].rpe)

        fun w(id: String, km: Double, start: Long) = WorkoutDetail(
            externalId = id, source = "s", date = "2026-11-05", type = "Corsa", exerciseType = 56,
            startMillis = start, endMillis = start + 1000, movingSeconds = 1, distanceM = km * 1000
        )
        val match = RunFeedback.matchLog(listOf(w("mattina", 2.1, 0), w("sera", 4.0, 5000)), log)
        assertEquals("r1", match["sera"]!!.id)
        assertEquals("r2", match["mattina"]!!.id)

        val plan = listOf(PlannedRun("40f3377b631981848 51fe81f24d9fede".replace(" ", ""), "Ritmo test", "2026-11-04", "Intervalli", "Completata"))
        assertEquals("Ritmo test", RunFeedback.plannedFor(w("sera", 4.0, 0), log[0], plan)!!.name)
    }

    @Test
    fun notionWinsOnKindRpeAndNote() {
        val app = WorkoutFeedback(rpe = 5, kind = "Facile", note = "app", distanceM = 3500.0)
        val logged = LoggedRun("r", "x", "2026-11-05", kind = "Intervalli", rpe = 7)
        val m = RunFeedback.merge(app, logged)
        assertEquals("Intervalli", m.kind)
        assertEquals(7, m.rpe)
        assertEquals("app", m.note) // nel registro manca: resta quella dell'app
        assertEquals(3500.0, m.distanceM!!, 0.0) // la correzione resta dell'app
        assertEquals(app, RunFeedback.merge(app, null))
    }
}
