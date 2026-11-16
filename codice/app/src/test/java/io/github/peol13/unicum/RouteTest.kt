package io.github.peol13.unicum

import io.github.peol13.unicum.ui.Route
import io.github.peol13.unicum.ui.screens.DetailTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RouteTest {

    private val all: List<Route> = listOf(
        Route.Home, Route.Allenamenti, Route.Statistiche, Route.Diario, Route.Profilo, Route.Consigli,
        Route.HomeEditor, Route.WorkoutList, Route.Notion, Route.Shoes,
        Route.Workout("9b5e709b-04e1-4fe6-836a-9a75c88dad6c"), Route.Shoe("smw6797qm"),
        Route.WeekStory(LocalDate.of(2026, 11, 2))
    ) + DetailTopic.entries.map { Route.Detail(it) }

    @Test
    fun `ogni destinazione si rilegge dalla chiave salvata`() {
        all.forEach { assertEquals(it, Route.decode(it.key)) }
        // Le chiavi sono tutte diverse
        assertEquals(all.size, all.map { it.key }.toSet().size)
    }

    @Test
    fun `le chiavi sono quelle di prima, quindi lo stato salvato resta valido`() {
        assertEquals(Route.Home, Route.decode("Home"))
        assertEquals(Route.WorkoutList, Route.decode("Allenamenti/elenco"))
        assertEquals(Route.Notion, Route.decode("Allenamenti/notion"))
        assertEquals(Route.Shoes, Route.decode("Allenamenti/scarpe"))
        assertEquals(Route.HomeEditor, Route.decode("PersonalizzaHome"))
        assertEquals(Route.Workout("abc"), Route.decode("Allenamento/abc"))
        assertEquals(Route.Shoe("s1"), Route.decode("Scarpa/s1"))
        assertEquals(Route.WeekStory(LocalDate.of(2026, 10, 26)), Route.decode("Settimana/2026-10-26"))
        assertEquals(Route.Detail(DetailTopic.SONNO), Route.decode("Dettaglio/sonno"))
        assertEquals(Route.Detail(DetailTopic.PESO), Route.decode("Dettaglio/peso"))
    }

    @Test
    fun `una chiave sconosciuta o incompleta non porta da nessuna parte`() {
        listOf("", "Casa", "Allenamento/", "Scarpa/", "Settimana/non-una-data", "Dettaglio/boh", "dettaglio/sonno")
            .forEach { assertNull(it, Route.decode(it)) }
    }

    @Test
    fun `il Saver riporta alla Home una chiave che non conosce`() {
        assertEquals(Route.Home, Route.Saver.restore("Casa"))
        assertEquals(Route.Workout("x"), Route.Saver.restore("Allenamento/x"))
        assertEquals(Route.Diario, Route.Saver.restore("Diario"))
    }
}
