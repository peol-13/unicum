package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnitsTest {

    @Test
    fun weightAndLoads() {
        assertEquals("72,0 kg", Units.weight(72.0, imperial = false))
        assertEquals("158,7 lb", Units.weight(72.0, imperial = true))
        assertEquals("60 kg", Units.load(60.0, imperial = false))
        assertEquals("62,5 kg", Units.load(62.5, imperial = false))
        assertEquals("mezze libbre", "132,5 lb", Units.load(60.1, imperial = true))
        assertEquals(159, Units.weightField(72, imperial = true))
        assertEquals(72, Units.weightFromField(159, imperial = true))
    }

    @Test
    fun heightRoundTrip() {
        assertEquals(69, Units.heightField(175, imperial = true))
        assertEquals(175, Units.heightFromField(69, imperial = true))
        assertEquals("5′9″", Units.height(175, imperial = true))
        assertEquals("175 cm", Units.height(175, imperial = false))
        // Ogni pollice cambia i cm salvati, quindi − / + non restano fermi
        for (inches in 47..91) assertEquals(inches, Units.heightField(Units.heightFromField(inches, true), true))
    }

    @Test
    fun defaultFromCountry() {
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.fromLocale(Locale.US))
        assertEquals(UnitSystem.METRIC, UnitSystem.fromLocale(Locale.ITALY))
        assertEquals(UnitSystem.METRIC, UnitSystem.fromLocale(Locale.UK))
    }
}
