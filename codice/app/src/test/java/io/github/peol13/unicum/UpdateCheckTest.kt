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

    @Test
    fun picksTheApkOfTheRelease() {
        val base = "https://github.com/peol-13/unicum/releases/download/v1.2.0/"
        val json = """{"tag_name":"v1.2.0","html_url":"https://github.com/peol-13/unicum/releases/tag/v1.2.0","assets":[
            {"name":"unicum-1.2.0.apk","browser_download_url":"${base}unicum-1.2.0.apk"},
            {"name":"unicum.apk","browser_download_url":"${base}unicum.apk"}]}"""
        assertEquals(base + "unicum.apk", UpdateCheck.parseRelease(json)!!.apkUrl)
        // Senza "unicum.apk" va bene anche quello con la versione; file di altri siti mai
        val solo = """{"tag_name":"v1.2.0","assets":[{"name":"unicum-1.2.0.apk","browser_download_url":"${base}unicum-1.2.0.apk"}]}"""
        assertEquals(base + "unicum-1.2.0.apk", UpdateCheck.parseRelease(solo)!!.apkUrl)
        val altrove = """{"tag_name":"v1.2.0","assets":[{"name":"unicum.apk","browser_download_url":"https://example.com/unicum.apk"}]}"""
        assertNull(UpdateCheck.parseRelease(altrove)!!.apkUrl)
        assertNull(UpdateCheck.parseRelease("""{"tag_name":"v1.2.0"}""")!!.apkUrl)
    }
}
