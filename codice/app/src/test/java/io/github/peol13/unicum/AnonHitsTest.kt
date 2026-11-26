package io.github.peol13.unicum

import io.github.peol13.unicum.data.AnonHits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AnonHitsTest {
    private val today = LocalDate.of(2026, 11, 26)
    private val now = Instant.parse("2026-11-26T08:15:30.123Z")
    private val info = AnonHits.Info(
        version = "1.2.0", androidSdk = 35, language = "it",
        features = listOf("health_connect", "programma_notion", "home_nutrizione", "tema_scuro"),
        installDay = LocalDate.of(2026, 11, 20),
    )

    @Test
    fun ageBandsAreBroad() {
        val inst = LocalDate.of(2026, 11, 1)
        assertEquals("nuova", AnonHits.ageBand(inst, inst))
        assertEquals("nuova", AnonHits.ageBand(inst, inst.plusDays(1)))
        assertEquals("2-7-giorni", AnonHits.ageBand(inst, inst.plusDays(7)))
        assertEquals("8-30-giorni", AnonHits.ageBand(inst, inst.plusDays(30)))
        assertEquals("oltre-30-giorni", AnonHits.ageBand(inst, inst.plusDays(31)))
        assertEquals("sconosciuta", AnonHits.ageBand(null, inst))
    }

    @Test
    fun screensCountOncePerDay() {
        var s = AnonHits.addScreen(null, today, "Home")
        s = AnonHits.addScreen(s, today, "Home")
        s = AnonHits.addScreen(s, today, "Profilo/aspetto")
        assertEquals("2026-11-26|Home;2026-11-26|Profilo/aspetto", s)
    }

    @Test
    fun todayIsSentWithItsDetailsButNotItsScreens() {
        val screens = AnonHits.addScreen(AnonHits.addScreen(null, today.minusDays(1), "Diario"), today, "Home")
        val b = AnonHits.build("2026-11-26", screens, today, info, now)
        val paths = b.hits.map { it.path }
        assertEquals(
            listOf(
                "/aperta", "/versione/1.2.0", "/anzianita/2-7-giorni", "/lingua/it", "/android/35",
                "/funzione/health_connect", "/funzione/programma_notion", "/schermata-principale/nutrizione", "/tema/scuro",
                "/schermata/diario",
            ),
            paths
        )
        assertEquals(listOf("2026-11-26"), b.sentDays)
        assertEquals(listOf("2026-11-25"), b.sentScreenDays)
        // Oggi con l'ora vera, i giorni passati a mezzogiorno UTC
        assertEquals(now, b.hits.first().createdAt)
        assertEquals(Instant.parse("2026-11-25T12:00:00Z"), b.hits.last().createdAt)
    }

    @Test
    fun missedDaysAreSentLateButAtMostSeven() {
        val days = (0L..9L).map { today.minusDays(it).toString() }.sorted().joinToString(",")
        val b = AnonHits.build(days, null, today, info, now)
        assertEquals(7, b.sentDays.size)
        assertEquals("2026-11-20", b.sentDays.first())
        assertEquals(7, b.hits.count { it.path == "/aperta" })
        // L'anzianità segue il giorno: il 20/11 l'installazione era nuova
        assertTrue(b.hits.any { it.path == "/anzianita/nuova" && it.createdAt == Instant.parse("2026-11-20T12:00:00Z") })
    }

    @Test
    fun sentItemsAreRemovedTheRestStays() {
        assertEquals("2026-11-27", AnonHits.without("2026-11-26,2026-11-27", listOf("2026-11-26")))
        assertEquals(
            "2026-11-26|Home",
            AnonHits.screensWithout("2026-11-25|Diario;2026-11-26|Home", listOf("2026-11-25"))
        )
    }

    @Test
    fun everyHitHasItsOwnThrowawaySession() {
        val b = AnonHits.build("2026-11-26", null, today, info, now)
        val json = Json.parseToJsonElement(AnonHits.body(b.hits)).jsonObject
        assertFalse("no_sessions" in json.keys)
        val hits = json["hits"]!!.jsonArray.map { it.jsonObject }
        assertEquals(setOf("path", "title", "created_at", "session"), hits.first().keys)
        assertEquals("2026-11-26T08:15:30Z", hits.first()["created_at"]!!.jsonPrimitive.content)
        // Una sessione diversa per ogni conteggio: nessuna lega due voci tra loro
        assertEquals(hits.size, hits.map { it["session"]!!.jsonPrimitive.content }.toSet().size)
        // E cambia a ogni invio
        val again = Json.parseToJsonElement(AnonHits.body(b.hits)).jsonObject["hits"]!!.jsonArray.first().jsonObject
        assertTrue(again["session"] != hits.first()["session"])
    }

    @Test
    fun oddValuesBecomeSafePaths() {
        val b = AnonHits.build("2026-11-26", null, today, info.copy(version = "1.2.0 beta/2", language = "", features = emptyList()), now)
        assertTrue(b.hits.any { it.path == "/versione/1.2.0-beta-2" })
        assertTrue(b.hits.any { it.path == "/lingua/sistema" })
    }
}
