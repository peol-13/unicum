package io.github.peol13.unicum

import io.github.peol13.unicum.i18n.AppLanguage
import io.github.peol13.unicum.i18n.I18n
import io.github.peol13.unicum.i18n.tr
import io.github.peol13.unicum.i18n.trp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ogni testo passato a `tr(...)` / `trp(...)` nel codice deve avere una traduzione in tutte le lingue,
 * con gli stessi segnaposto (%d, %s, %.1f…). Le voci del catalogo che il codice non usa più vanno tolte.
 */
class I18nTest {

    @After fun reset() = I18n.setForTest(AppLanguage.IT)

    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    private val literal = "\"((?:[^\"\\\\\\n]|\\\\.)*)\""
    private val trCall = Regex("""\b(?:tr|tk)\(\s*$literal""")
    private val trpCall = Regex("""\btrp\([^,()]*(?:\([^()]*\))?[^,()]*,\s*$literal\s*,\s*$literal""")

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> out.append('\n'); 't' -> out.append('\t')
                    'u' -> { out.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> out.append(n)
                }
                i += 2
            } else { out.append(c); i++ }
        }
        return out.toString()
    }

    /** Chiave → file in cui compare. */
    private fun usedKeys(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (f in sources) {
            // Commenti esclusi: gli esempi della documentazione non sono testi dell'app
            val text = f.readLines().filterNot { it.trimStart().let { l -> l.startsWith("*") || l.startsWith("//") || l.startsWith("/*") } }
                .joinToString("\n")
            trCall.findAll(text).forEach { m ->
                out[m.groupValues[1]] = f.name
            }
            trpCall.findAll(text).forEach { m ->
                out[m.groupValues[1]] = f.name; out[m.groupValues[2]] = f.name
            }
        }
        return out
    }

    @Test fun keysAreConstant() {
        val bad = usedKeys().filterKeys { Regex("""(?<!\\)\$[{a-zA-Z_]""").containsMatchIn(it) }
        assertTrue("Testi con \$ dentro tr(): usa %s/%d come argomenti\n" + bad.entries.joinToString("\n") { "${it.value}: ${it.key}" }, bad.isEmpty())
    }

    @Test fun everyKeyIsTranslated() {
        val catalog = I18n.entries()
        val missing = mutableListOf<String>()
        for ((raw, file) in usedKeys()) {
            val key = unescape(raw)
            val entry = catalog[key]
            if (entry == null) { missing += "$file: «$key» (manca la voce)"; continue }
            val absent = AppLanguage.entries.filter { it !in entry || entry[it].isNullOrBlank() }
            if (absent.isNotEmpty()) missing += "$file: «$key» manca ${absent.joinToString { it.code }}"
        }
        assertTrue("${missing.size} testi senza traduzione:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun placeholdersMatch() {
        val spec = Regex("""%(\d+\$)?[-+ 0,(#]*\d*(?:\.\d+)?([sdfxc%])""")
        fun specs(s: String) = spec.findAll(s).map { it.groupValues[2] }.filter { it != "%" }.sorted().toList()
        val bad = I18n.entries().flatMap { (key, langs) ->
            val expected = specs(key)
            langs.filter { specs(it.value) != expected }.map { "«$key» → ${it.key.code}: «${it.value}»" }
        }
        assertTrue("Segnaposto diversi:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun noUnusedEntries() {
        val used = usedKeys().keys.map { unescape(it) }.toSet()
        val unused = I18n.entries().keys.filter { it !in used }
        assertTrue("Voci del catalogo non usate nel codice:\n" + unused.joinToString("\n"), unused.isEmpty())
    }

    @Test fun noDuplicateEntries() {
        val all = I18n.FILES.flatMap { name ->
            val text = I18n::class.java.getResourceAsStream("/io/github/peol13/unicum/i18n/$name.txt")
                ?.bufferedReader()?.readText() ?: ""
            I18n.parse(text).map { it.first }
        }
        val dup = all.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("Voci ripetute:\n" + dup.joinToString("\n"), dup.isEmpty())
    }

    @Test fun translatesAndFormats() {
        assertEquals("Profilo", tr("Profilo"))
        I18n.setForTest(AppLanguage.EN)
        assertEquals("Profile", tr("Profilo"))
        assertEquals("Testo che non esiste", tr("Testo che non esiste"))
        I18n.setForTest(AppLanguage.FR)
        assertTrue(trp(0, "%d giorno", "%d giorni").startsWith("0 "))
    }
}
