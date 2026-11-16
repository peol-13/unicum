package io.github.peol13.unicum.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** L'esempio di database Notion in docs/notion deve restare leggibile dall'app (nomi delle colonne e Struttura). */
class NotionExamplesTest {

    private fun csv(name: String): List<Map<String, String>> {
        val file = listOf("../../docs/notion/$name", "../docs/notion/$name", "docs/notion/$name").map(::File).first { it.exists() }
        val rows = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map(::splitCsv)
        return rows.drop(1).map { r -> rows.first().zip(r).toMap() }
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        line.forEach { c ->
            when {
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
        }
        out += cur.toString()
        return out
    }

    @Test
    fun planExampleHasTheColumnsTheAppReads() {
        val rows = csv("programma-corse.csv")
        val columns = listOf("Allenamento", "Data", "Tipo", "Stato", "Obiettivo km", "Durata min", "Ritmo target", "RPE target", "Struttura", "Focus", "Settimana")
        assertTrue(rows.isNotEmpty() && rows.all { it.keys.containsAll(columns) })
        rows.filter { it["Tipo"] in PlanStructure.STRUCTURED_KINDS }.forEach {
            assertNotNull("Struttura non leggibile: ${it["Struttura"]}", PlanStructure.parse(it["Struttura"]))
        }
    }

    @Test
    fun logExampleHasTheColumnsTheAppReads() {
        val rows = csv("registro-corse.csv")
        val columns = listOf("Corsa", "Data", "Tipo", "RPE", "Note", "Distanza km", "Sessione pianificata")
        assertTrue(rows.isNotEmpty() && rows.all { it.keys.containsAll(columns) })
    }
}
