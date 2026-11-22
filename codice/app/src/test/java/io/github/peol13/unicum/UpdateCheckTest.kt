package io.github.peol13.unicum

import io.github.peol13.unicum.data.UpdateCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(UpdateCheck.isNewer("1.0.3", "1.0.2"))
        assertTrue(UpdateCheck.isNewer("1.0.10", "1.0.9"))
        assertTrue(UpdateCheck.isNewer("1.1", "1.0.9"))
        assertTrue(UpdateCheck.isNewer("2.0.0", "1.9.9"))
        assertFalse(UpdateCheck.isNewer("1.0.2", "1.0.2"))
        assertFalse(UpdateCheck.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateCheck.isNewer("1.0.1", "1.0.2"))
    }

    @Test
    fun ignoresPrefixesSuffixesAndGarbage() {
        assertTrue(UpdateCheck.isNewer("v1.0.3", "1.0.2-debug"))
        assertFalse(UpdateCheck.isNewer("1.0.2", "1.0.2-debug"))
        assertFalse(UpdateCheck.isNewer("ultima", "1.0.2"))
        assertFalse(UpdateCheck.isNewer("", "1.0.2"))
        assertFalse(UpdateCheck.isNewer("1.0.3", "boh"))
    }

    @Test
    fun parsesGithubRelease() {
        val json = """{"tag_name":"v1.0.3","html_url":"https://github.com/peol-13/unicum/releases/tag/v1.0.3",
            "draft":false,"prerelease":false,"assets":[]}"""
        val r = UpdateCheck.parseRelease(json)!!
        assertEquals("1.0.3", r.version)
        assertEquals("https://github.com/peol-13/unicum/releases/tag/v1.0.3", r.url)
    }

    @Test
    fun skipsPrereleasesAndBadTags() {
        assertNull(UpdateCheck.parseRelease("""{"tag_name":"v1.1.0","prerelease":true}"""))
        assertNull(UpdateCheck.parseRelease("""{"tag_name":"v1.1.0","draft":true}"""))
        assertNull(UpdateCheck.parseRelease("""{"tag_name":"prova"}"""))
        assertEquals(UpdateCheck.RELEASES_PAGE, UpdateCheck.parseRelease("""{"tag_name":"1.2.0"}""")!!.url)
    }
}
