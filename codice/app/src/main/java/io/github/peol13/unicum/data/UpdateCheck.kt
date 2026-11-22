package io.github.peol13.unicum.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.peol13.unicum.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Avviso "nuova versione disponibile" per chi ha installato l'APK da GitHub (fuori dal Play Store
 * nessuno avvisa degli aggiornamenti).
 *
 * - Al massimo una volta al giorno l'app chiede a GitHub qual è l'ultima release pubblicata
 *   ([LATEST_URL], una richiesta senza dati dell'utente: solo lo User-Agent con la versione dell'app).
 * - Se è più nuova di quella installata, la Home mostra un banner che porta alla pagina di download.
 * - "×" nasconde l'avviso per quella versione; alla release successiva ricompare.
 * - La versione trovata resta salvata: il banner si vede anche senza rete, e sparisce da solo
 *   quando l'utente installa la nuova versione.
 */
object UpdateCheck {
    private const val LATEST_URL = "https://api.github.com/repos/peol-13/unicum/releases/latest"
    const val RELEASES_PAGE = AppInfo.PROJECT_URL + "/releases/latest"

    private const val PREFS = "update_check"
    private const val K_LAST_CHECK = "last_check"
    private const val K_LATEST = "latest_version"
    private const val K_URL = "latest_url"
    private const val K_DISMISSED = "dismissed_version"

    private lateinit var prefs: SharedPreferences

    /** Ultima versione pubblicata, se più nuova di quella installata e non nascosta; altrimenti null. */
    var available by mutableStateOf<String?>(null)
        private set

    /** Pagina della release da aprire per scaricare l'APK. */
    var downloadUrl by mutableStateOf(RELEASES_PAGE)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        refreshState()
    }

    /** Nasconde l'avviso fino alla prossima release. */
    fun dismiss() {
        val v = available ?: return
        prefs.edit().putString(K_DISMISSED, v).apply()
        available = null
    }

    /** Chiede a GitHub l'ultima release, al massimo una volta al giorno. Gli errori si ignorano: si riprova domani. */
    suspend fun checkIfDue(today: LocalDate = LocalDate.now()) {
        if (!::prefs.isInitialized) return
        if (prefs.getString(K_LAST_CHECK, null) == today.toString()) return
        val release = fetchLatest() ?: return
        prefs.edit()
            .putString(K_LAST_CHECK, today.toString())
            .putString(K_LATEST, release.version)
            .putString(K_URL, release.url)
            .apply()
        refreshState()
    }

    private fun refreshState() {
        val latest = prefs.getString(K_LATEST, null)
        available = latest?.takeIf {
            isNewer(it, BuildConfig.VERSION_NAME) && it != prefs.getString(K_DISMISSED, null)
        }
        downloadUrl = prefs.getString(K_URL, null)?.takeIf { it.startsWith(AppInfo.PROJECT_URL) } ?: RELEASES_PAGE
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", AppInfo.USER_AGENT)
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                if (conn.responseCode != 200) null
                else parseRelease(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    data class Release(val version: String, val url: String)

    /** Risposta di `/releases/latest`: "tag_name" (es. "v1.0.3") e "html_url". Bozze e pre-release si saltano. */
    fun parseRelease(json: String): Release? {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val version = root.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        if (parseVersion(version) == null) return null
        return Release(version, root.optString("html_url").ifBlank { RELEASES_PAGE })
    }

    /** "1.0.3" > "1.0.2"; "1.1" = "1.1.0". Suffissi come "-debug" si ignorano. Versioni illeggibili: mai più nuove. */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parseVersion(remote) ?: return false
        val c = parseVersion(current) ?: return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parseVersion(v: String): List<Int>? {
        val core = v.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }
}
