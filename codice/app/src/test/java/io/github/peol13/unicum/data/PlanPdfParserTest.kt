package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanPdfParserTest {

    private val today = LocalDate.of(2026, 11, 13)

    @Test
    fun datedPlanWithContinuationLines() {
        val r = PlanPdfParser.parse(
            listOf(
                "PROGRAMMA 10 KM - NOVEMBRE",
                "Lun 16/11 Corsa facile 6 km a 6:40/km, RPE 4",
                "Mar 17/11 Riposo",
                "Mer 18/11 Ripetute: 10' riscaldamento + 5x(3' a 5:50 + 2' lento) + 5' defaticamento",
                "RPE 7-8",
                "Gio 19/11 Palestra: forza gambe",
                "Sab 21 novembre Lungo 12 km",
                "Pagina 1 di 2"
            ),
            today
        )
        assertEquals(1, r.restDays)
        assertEquals(1, r.otherActivities)
        assertFalse(r.needsStart)
        assertEquals(3, r.drafts.size)

        val easy = r.drafts[0]
        assertEquals(LocalDate.of(2026, 11, 16), easy.date)
        assertEquals("Facile", easy.kind)
        assertEquals(6.0, easy.km!!, 1e-9)
        assertEquals("6:40/km", easy.pace)
        assertEquals(4, easy.rpe)

        val reps = r.drafts[1]
        assertEquals("Intervalli", reps.kind)
        assertNull("i km/minuti delle prove non sono l'obiettivo", reps.km)
        assertNull(reps.minutes)
        assertEquals(8, reps.rpe)
        assertTrue("la riga dopo continua la seduta", reps.text.endsWith("RPE 7-8"))
        // La struttura si legge come quella di Notion
        assertEquals(12, PlanStructure.parse(reps.text)!!.size)

        val long = r.drafts[2]
        assertEquals(LocalDate.of(2026, 11, 21), long.date)
        assertEquals("Lungo facile", long.kind)
        assertEquals(12.0, long.km!!, 1e-9)
    }

    @Test
    fun weekAndDayPlanNeedsStart() {
        val r = PlanPdfParser.parse(
            listOf(
                "Settimana 1",
                "Lunedì: 30 minuti lento",
                "Giovedì: progressivo 15' progressivi: 5' a 7:10, 5' a 6:50, 5' a 6:30",
                "Settimana 2",
                "Martedì - fartlek 8x1' veloce / 1' piano",
                "Domenica - lungo 1h10'"
            ),
            today
        )
        assertTrue(r.needsStart)
        assertEquals(listOf(0, 3, 8, 13), r.drafts.map { it.offset })
        assertEquals(30, r.drafts[0].minutes)
        assertEquals("Progressivo", r.drafts[1].kind)
        assertEquals(15, r.drafts[1].minutes)
        assertEquals("Intervalli", r.drafts[2].kind)
        assertEquals(70, r.drafts[3].minutes)

        val start = LocalDate.of(2026, 11, 16)
        val runs = PlanPdfParser.toRuns(r.drafts, start)
        assertEquals(listOf("2026-11-16", "2026-11-19", "2026-11-24", "2026-11-29"), runs.map { it.date })
        assertEquals("Facile 30′", runs[0].name)
        assertEquals(1, runs[0].week)
        // Senza primo giorno restano senza data
        assertNull(PlanPdfParser.toRuns(r.drafts, null)[0].date)
    }

    @Test
    fun distancesAndRangesAreNotDates() {
        val r = PlanPdfParser.parse(
            listOf(
                "12/01 Facile",
                "5.5 km a ritmo libero",
                "6-8 km",
                "12 settimane di preparazione"
            ),
            LocalDate.of(2026, 12, 20)
        )
        assertEquals(1, r.drafts.size)
        // A dicembre "12/01" è il gennaio successivo
        assertEquals(LocalDate.of(2027, 1, 12), r.drafts[0].date)
        assertEquals(5.5, r.drafts[0].km!!, 1e-9)
    }

    @Test
    fun warmUpPlusEasyAddsUpMinutes() {
        val d = PlanPdfParser.parse(listOf("20/11 10' riscaldamento + 30' corsa lenta + 5' defaticamento"), today).drafts.single()
        assertEquals(45, d.minutes)
        assertEquals("Facile", d.kind)
    }

    @Test
    fun pieceInKmLeavesDurationUnknown() {
        val d = PlanPdfParser.parse(listOf("24/11 Test 5 km: 10' riscaldamento + 5 km al massimo + 5' defaticamento"), today).drafts.single()
        assertEquals("Test", d.kind)
        assertEquals(5.0, d.km!!, 1e-9)
        assertEquals(null, d.minutes)
        assertEquals("Test 5 km", PlanPdfParser.nameOf(d.kind, d.km, d.minutes))
    }

    @Test
    fun totalKmCountsForIntervals() {
        val d = PlanPdfParser.parse(listOf("20/11 Ripetute 6x800 m rec. 2' - totale 9 km"), today).drafts.single()
        assertEquals("Intervalli", d.kind)
        assertEquals(9.0, d.km!!, 1e-9)
    }
}
