package io.github.peol13.unicum

import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.PlannedRun
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NotionPlanTest {

    private val response = """
        {"object":"list","has_more":false,"next_cursor":null,"results":[
          {"object":"page","id":"p1","url":"https://www.notion.so/p1","archived":false,"properties":{
            "Allenamento":{"type":"title","title":[{"plain_text":"Test finale "},{"plain_text":"5 km"}]},
            "Data":{"type":"date","date":{"start":"2026-11-12","end":null}},
            "Tipo":{"type":"select","select":{"name":"Test"}},
            "Stato":{"type":"status","status":{"name":"Da fare"}},
            "Obiettivo km":{"type":"number","number":5},
            "Durata min":{"type":"number","number":65},
            "Ritmo target":{"type":"rich_text","rich_text":[{"plain_text":"Obiettivo 33:00"}]},
            "RPE target":{"type":"number","number":8},
            "Struttura":{"type":"rich_text","rich_text":[]},
            "Focus":{"type":"rich_text","rich_text":[{"plain_text":"5 km in 33:00"}]},
            "Settimana":{"type":"number","number":10}
          }},
          {"object":"page","id":"p2","archived":true,"properties":{
            "Allenamento":{"type":"title","title":[{"plain_text":"Cancellata"}]}
          }},
          {"object":"page","id":"p3","properties":{
            "Allenamento":{"type":"title","title":[]},
            "Data":{"type":"date","date":{"start":"2026-11-05T18:30:00.000+01:00"}},
            "Tipo":{"type":"select","select":null},
            "Obiettivo km":{"type":"number","number":null}
          }}
        ]}
    """.trimIndent()

    @Test
    fun parsesPlannedRuns() {
        val runs = NotionPlan.parseResults(JSONObject(response))
        assertEquals(2, runs.size)
        val test = runs[0]
        assertEquals("Test finale 5 km", test.name)
        assertEquals("2026-11-12", test.date)
        assertEquals("Test", test.kind)
        assertEquals("Da fare", test.status)
        assertEquals(5.0, test.km!!, 0.001)
        assertEquals(65, test.minutes)
        assertEquals(8, test.rpe)
        assertEquals(10, test.week)
        assertEquals("Obiettivo 33:00", test.pace)
        assertNull(test.structure)
        assertEquals("https://www.notion.so/p1", test.url)

        val empty = runs[1]
        assertEquals("Corsa", empty.name)
        assertEquals("2026-11-05", empty.date)
        assertNull(empty.kind)
        assertNull(empty.km)
    }

    @Test
    fun upcomingAndRecent() {
        val today = LocalDate.parse("2026-11-06")
        fun run(id: String, date: String, status: String) = PlannedRun(id, id, date, "Facile", status)
        val plan = listOf(
            run("vecchia", "2026-10-01", "Completata"),
            run("fatta", "2026-11-05", "Completata"),
            run("saltata", "2026-11-03", "Saltata"),
            run("oggi", "2026-11-06", "Completata"),
            run("dopo", "2026-11-12", "Da fare"),
            run("prima", "2026-11-10", "Da fare"),
            run("chiusa futura", "2026-11-11", "Saltata")
        )
        assertEquals(listOf("oggi", "prima", "dopo"), NotionPlan.upcoming(plan, today).map { it.id })
        assertEquals(listOf("fatta", "saltata"), NotionPlan.recent(plan, today).map { it.id })
        assertTrue(NotionPlan.isDone(run("x", "2026-11-04", "Da fare"), setOf("2026-11-04")))
    }

    @Test
    fun databaseIdFromLink() {
        assertNull(NotionPlan.parseDatabaseId(""))
        assertEquals(
            "0a1b2c3d4e5f60718293a4b5c6d7e8f9",
            NotionPlan.parseDatabaseId("https://www.notion.so/0a1b2c3d4e5f60718293a4b5c6d7e8f9?v=0123456789abcdef0123456789abcdef")
        )
        assertEquals(
            "0a1b2c3d4e5f60718293a4b5c6d7e8f9",
            NotionPlan.parseDatabaseId("0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9")
        )
        assertNull(NotionPlan.parseDatabaseId("non è un link"))
    }

    @Test
    fun databaseSourceReadsTitleAndLink() {
        val db = JSONObject(
            "{\"object\":\"database\",\"url\":\"https://www.notion.so/0a1b2c3d\"," +
                "\"title\":[{\"plain_text\":\"Programma \"},{\"plain_text\":\"corse\"}]}"
        )
        val source = NotionPlan.databaseSource("0a1b2c3d", db)
        assertEquals("Programma corse", source.title)
        assertEquals("https://www.notion.so/0a1b2c3d", source.url)
        // Senza titolo né link: restano vuoti e la pagina mostra il nome previsto
        val empty = NotionPlan.databaseSource("x", JSONObject("{\"title\":[]}"))
        assertNull(empty.title)
        assertNull(empty.url)
    }
    @Test
    fun logDatabaseFoundAmongSharedDatabases() {
        fun db(id: String, title: String, vararg props: String) =
            "{\"object\":\"database\",\"id\":\"$id\",\"title\":[{\"plain_text\":\"$title\"}]," +
                "\"properties\":{" + props.joinToString(",") { "\"$it\":{}" } + "}}"
        val plan = "11111111111111111111111111111111"
        val search = JSONObject(
            "{\"results\":[" +
                db("11111111-1111-1111-1111-111111111111", "Programma corse", "RPE target") + "," +
                db("22222222222222222222222222222222", "Spesa") + "," +
                db("33333333333333333333333333333333", "Registro corse", "RPE", "Sessione pianificata") + "]}"
        )
        assertEquals("33333333333333333333333333333333", NotionPlan.pickLogDatabase(search, plan))
        // Nessun indizio: nessun registro
        assertNull(NotionPlan.pickLogDatabase(JSONObject("{\"results\":[" + db("22222222222222222222222222222222", "Spesa") + "]}"), plan))
    }
}
