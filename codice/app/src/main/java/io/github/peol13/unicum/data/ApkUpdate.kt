package io.github.peol13.unicum.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aggiornamento in un tocco: scarica l'APK della nuova release da GitHub ([UpdateCheck.apkUrl]) dentro l'app
 * e apre l'installazione di Android, che lo mette sopra quello installato (i dati restano).
 *
 * - Android accetta l'aggiornamento solo se l'APK è firmato con la stessa chiave dell'app installata: una build
 *   di debug non si aggiorna con la release (e viceversa).
 * - La prima volta Android chiede di consentire a UNICUM di installare app: si apre la sua impostazione, poi
 *   si torna e si tocca di nuovo il banner.
 * - Si scaricano solo file da `github.com/peol-13/unicum/releases/download/` (controllato in [UpdateCheck]) e il
 *   file scaricato deve essere un APK di questa app; altrimenti si ripiega sulla pagina della release.
 * - Da non usare nella futura versione del Play Store (là gli aggiornamenti passano solo dallo store).
 */
object ApkUpdate {
    sealed interface State {
        data object Idle : State
        /** [percent] null = dimensione non nota. */
        data class Downloading(val percent: Int?) : State
        data class Ready(val file: File) : State
        data object Failed : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Scarica [apkUrl] e, appena pronto, apre l'installazione. */
    fun start(context: Context, apkUrl: String, version: String) {
        if (state is State.Downloading) return
        val app = context.applicationContext
        scope.launch {
            state = State.Downloading(null)
            // Nella memoria dell'app, non nella cache: Android può svuotare la cache quando lo spazio è poco
            val dir = updatesDir(app).apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val file = File(dir, "unicum-$version.apk")
            val ok = withContext(Dispatchers.IO) { download(apkUrl, file) && isThisApp(app, file) }
            if (!ok) {
                file.delete()
                state = State.Failed
                return@launch
            }
            state = State.Ready(file)
            install(app)
        }
    }

    /**
     * Apre l'installazione del file pronto. Se Android non permette ancora a UNICUM di installare app, apre
     * quell'impostazione e restituisce false (il file resta pronto per il tocco successivo).
     */
    fun install(context: Context): Boolean {
        val file = (state as? State.Ready)?.file?.takeIf { it.exists() } ?: run { state = State.Idle; return false }
        return try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                false
            } else {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }
        } catch (e: Exception) {
            state = State.Failed
            false
        }
    }

    private fun updatesDir(context: Context) = File(context.filesDir, "updates")

    /** Nessun aggiornamento in attesa: via l'APK scaricato (circa 30 MB). Chiamata all'avvio da [UpdateCheck]. */
    fun cleanup(context: Context) {
        if (state is State.Downloading) return
        updatesDir(context.applicationContext).listFiles()?.forEach { it.delete() }
        state = State.Idle
    }

    /** Dopo un errore il banner torna allo stato iniziale (per riprovare o aprire GitHub). */
    fun reset() {
        if (state is State.Failed) state = State.Idle
    }

    private fun download(url: String, target: File): Boolean = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true // GitHub rimanda al server dei file (https → https)
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", AppInfo.USER_AGENT)
        }
        try {
            if (conn.responseCode != 200) false
            else {
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt()
                                if (p != lastPercent) { lastPercent = p; state = State.Downloading(p) }
                            }
                        }
                    }
                }
                total <= 0 || target.length() == total
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** Il file è davvero un APK di UNICUM (stesso nome di pacchetto); la firma la controlla Android all'installazione. */
    private fun isThisApp(context: Context, file: File): Boolean =
        runCatching { context.packageManager.getPackageArchiveInfo(file.path, 0)?.packageName == context.packageName }
            .getOrDefault(false)
}
