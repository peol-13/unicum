package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthStructureTest {

    private fun one(text: String): Exercise = StrengthStructure.parse(text).single()

    @Test
    fun setsRepsAndLoad() {
        assertEquals(Exercise("Panca piana", 4, "8", kg = 60.0), one("Panca piana 4x8 @ 60 kg"))
        assertEquals(Exercise("Rematore", 3, "8-10", kg = 42.5), one("Rematore 3 × 8–10 42,5kg"))
        assertEquals(Exercise("Trazioni", 3, "max"), one("Trazioni 3xmax"))
        assertEquals(Exercise("Squat", 3, "12"), one("Squat 3 serie da 12 ripetizioni"))
        assertEquals(Exercise("Stacco", 5, "5", kg = 80.0), one("Stacco 80 kg 5x5"))
    }

    @Test
    fun timedExercisesAndRest() {
        assertEquals(Exercise("Plank", 3, seconds = 45), one("Plank 3x45''"))
        assertEquals(Exercise("Wall sit", 2, seconds = 60), one("Wall sit 2×1′"))
        assertEquals(Exercise("Squat", 5, "5", kg = 70.0, restS = 120), one("Squat 5x5 70kg, rec 2'"))
        assertEquals(90, one("Panca 4x8 rec 90\"").restS)
        assertEquals(90, one("Panca 4x8 riposo 1'30").restS)
        assertEquals(120, one("Panca 4x8 rec 2").restS)
    }

    @Test
    fun manyExercisesOnLinesOrInOneLine() {
        val list = StrengthStructure.parse("1. Panca piana 4x8 60kg\n2. Trazioni 3x6\n3) Plank 3x45\"")
        assertEquals(listOf("Panca piana", "Trazioni", "Plank"), list.map { it.name })
        // Testo di un PDF: le righe della seduta arrivano unite, senza separatori
        val joined = StrengthStructure.parse("Palestra: Panca 4x8 60kg Trazioni 3xmax Plank 3x45''")
        assertEquals(listOf("Panca", "Trazioni", "Plank"), joined.map { it.name })
        assertEquals(60.0, joined[0].kg!!, 1e-9)
        val semicolons = StrengthStructure.parse("Affondi 3x10; Military press 4x6 @ 35 kg; Crunch 3x20")
        assertEquals(listOf("Affondi", "Military press", "Crunch"), semicolons.map { it.name })
        assertEquals(35.0, semicolons[1].kg!!, 1e-9)
    }

    @Test
    fun sameExerciseHeavierSets() {
        val list = StrengthStructure.parse("Panca 4x8 60kg + 2x5 70kg")
        assertEquals(2, list.size)
        assertEquals("Panca", list[1].name)
        assertEquals(70.0, list[1].kg!!, 1e-9)
    }

    @Test
    fun circuitRoundsBecomeSets() {
        val list = StrengthStructure.parse("Circuito 3 giri: 10 piegamenti, 15 squat, 30'' plank")
        assertEquals(listOf("Piegamenti", "Squat", "Plank"), list.map { it.name })
        assertTrue(list.all { it.sets == 3 })
        assertEquals("10", list[0].reps)
        assertEquals(30, list[2].seconds)
    }

    @Test
    fun textWithoutExercises() {
        assertTrue(StrengthStructure.parse("Palestra: forza gambe").isEmpty())
        assertTrue(StrengthStructure.parse(null).isEmpty())
    }

    @Test
    fun textsAndFields() {
        assertEquals("4 × 8", StrengthStructure.volumeText(Exercise("Panca", 4, "8")))
        assertEquals("3 × 1′30″", StrengthStructure.volumeText(Exercise("Plank", 3, seconds = 90)))
        assertEquals("60 kg", StrengthStructure.kgText(60.0))
        assertNull(StrengthStructure.kgText(null))
        assertEquals(null to 45, StrengthStructure.parseAmount("45\""))
        assertEquals(null to 90, StrengthStructure.parseAmount("1'30"))
        assertEquals("8-10" to null, StrengthStructure.parseAmount("8 - 10"))
        assertEquals(62.5, StrengthStructure.parseKg("62,5")!!, 1e-9)
        assertNull(StrengthStructure.parseKg(""))
    }

    @Test
    fun sportFromNamesAndTypes() {
        assertEquals(Sport.WEIGHTS, Sport.of("Pesi"))
        assertEquals(Sport.BODYWEIGHT, Sport.of("corpo libero"))
        assertEquals(Sport.RUN, Sport.of("Corsa"))
        assertNull(Sport.of("Nuoto"))
        assertEquals(Sport.WEIGHTS, Sport.infer("Forza", "Gambe"))
        assertEquals(Sport.BODYWEIGHT, Sport.infer(null, "Circuito a corpo libero"))
        assertEquals(Sport.RUN, Sport.infer("Facile", "Facile 6 km"))
        assertEquals("salite per la forza = corsa", Sport.RUN, Sport.infer("Salite", "Salite per la forza"))
        assertEquals(Sport.WEIGHTS, Sport.ofWorkout("Palestra"))
        assertNull(Sport.ofWorkout("Ciclismo"))
    }
}
