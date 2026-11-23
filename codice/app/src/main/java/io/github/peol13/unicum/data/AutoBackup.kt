package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** Quando serve un nuovo backup e quando è il caso di avvisare l'utente (logica pura, con test). */
object AutoBackupPolicy {
    /** Un backup al giorno: si rifà se l'ultimo riuscito ha più di 20 ore (così non slitta di un'ora ogni giorno). */
    const val MIN_INTERVAL_MS = 20 * 3_600_000L

    /** Oltre questo tempo senza un backup riuscito l'app avvisa. */
    const val STALE_AFTER_MS = 3 * 86_400_000L

    fun isDue(lastSuccessMillis: Long, nowMillis: Long): Boolean = nowMillis - lastSuccessMillis >= MIN_INTERVAL_MS

    enum class Health { OFF, OK, STALE, ERROR, HELD }

    /**
     * Stato da mostrare: spento, a posto, fermo da troppo (nessun backup riuscito negli ultimi giorni) oppure con un
     * errore o un blocco. [setUpMillis] serve a non avvisare subito dopo averlo attivato.
     */
    fun health(
        enabled: Boolean, lastSuccessMillis: Long, setUpMillis: Long, nowMillis: Long, error: String?, held: String?
    ): Health = when {
        !enabled -> Health.OFF
        held != null -> Health.HELD
        nowMillis - maxOf(lastSuccessMillis, setUpMillis) > STALE_AFTER_MS -> Health.STALE
        error != null && lastSuccessMillis == 0L -> Health.ERROR
        error != null && nowMillis - lastSuccessMillis > MIN_INTERVAL_MS * 2 -> Health.ERROR
        else -> Health.OK
    }
}

/**
 * Backup automatico: una volta al giorno il file di backup (lo stesso dell'esportazione manuale) viene riscritto in un
 * posto scelto dall'utente una sola volta con il selettore di Android. Se nel selettore si sceglie Google Drive il
 * file sta nel suo Drive (che tiene anche le versioni precedenti); va bene anche una cartella del telefono.
 *
 * Prima di dichiarare riuscito un backup il file viene riletto e deve essere un backup valido, con gli stessi pasti.
 * Se i dati sono molto meno di quelli dell'ultimo backup (es. un database svuotato per sbaglio) il file buono non
 * viene sovrascritto: l'utente deve confermare ([run] con `overrideHold`).
 */
object AutoBackup {
    private const val PREFS = "autobackup"
    private const val MAX_READ_BYTES = 64 * 1024 * 1024
    const val SUGGESTED_NAME = "unicum-backup.json"
    private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    data class State(
        val uri: String? = null,
        val setUpMillis: Long = 0L,
        val lastSuccessMillis: Long = 0L,
        val lastBytes: Long = 0L,
        val lastError: String? = null,
        /** Il backup nuovo è fermo perché ha molti meno dati dell'ultimo: serve la conferma dell'utente. */
        val held: String? = null,
        /**
         * Nel file anche il collegamento a Notion (token e database): su un telefono nuovo l'app si ricollega da sola.
         * Acceso di base; si spegne dalla pagina del backup.
         */
        val includeNotion: Boolean = true
    ) {
        val enabled: Boolean get() = uri != null
    }

    sealed interface Outcome {
        data object NotConfigured : Outcome
        data object NotDue : Outcome
        data class Saved(val bytes: Long) : Outcome
        data class Held(val message: String) : Outcome
        data class Failed(val message: String) : Outcome
    }

    var state by mutableStateOf(State())
        private set

    private lateinit var prefs: SharedPreferences
    private val runLock = Mutex()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        state = State(
            uri = prefs.getString("uri", null),
            setUpMillis = prefs.getLong("set_up", 0L),
            lastSuccessMillis = prefs.getLong("last_success", 0L),
            lastBytes = prefs.getLong("last_bytes", 0L),
            lastError = prefs.getString("error", null),
            held = prefs.getString("held", null),
            includeNotion = prefs.getBoolean("include_notion", true)
        )
    }

    private fun save(new: State) {
        state = new
        prefs.edit()
            .putString("uri", new.uri).putLong("set_up", new.setUpMillis)
            .putLong("last_success", new.lastSuccessMillis).putLong("last_bytes", new.lastBytes)
            .putString("error", new.lastError).putString("held", new.held)
            .putBoolean("include_notion", new.includeNotion)
            .apply()
    }

    /** Collegamento a Notion nel backup automatico sì / no (vale dal prossimo backup). */
    fun setIncludeNotion(context: Context, on: Boolean) {
        init(context)
        save(state.copy(includeNotion = on))
    }

    fun health(nowMillis: Long = System.currentTimeMillis()) = state.let {
        AutoBackupPolicy.health(it.enabled, it.lastSuccessMillis, it.setUpMillis, nowMillis, it.lastError, it.held)
    }

    /**
     * Sceglie dove salvare. false se quel posto non permette di tenere l'accesso (senza, i backup di notte
     * fallirebbero): l'utente ne sceglie un altro.
     */
    fun setTarget(context: Context, uri: Uri): Boolean {
        init(context)
        val resolver = context.contentResolver
        val granted = runCatching { resolver.takePersistableUriPermission(uri, FLAGS) }.isSuccess
        if (!granted) return false
        state.uri?.takeIf { it != uri.toString() }?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), FLAGS) }
        }
        save(State(uri = uri.toString(), setUpMillis = System.currentTimeMillis()))
        return true
    }

    fun disable(context: Context) {
        init(context)
        state.uri?.let { runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(it), FLAGS) } }
        save(State())
    }

    /** "Google Drive · unicum-backup.json" o il nome del file/posto scelto. */
    fun placeLabel(context: Context): String? {
        val uri = state.uri?.let(Uri::parse) ?: return null
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: SUGGESTED_NAME
        val place = when {
            uri.authority?.contains("com.google.android.apps.docs") == true -> "Google Drive"
            uri.authority == "com.android.providers.downloads.documents" -> "Download"
            uri.authority == "com.android.externalstorage.documents" -> tr("Memoria del telefono")
            else -> null
        }
        return listOfNotNull(place, name).joinToString(" · ")
    }

    /**
     * Fa il backup se è il momento (o sempre con [force]). Non solleva eccezioni: l'esito è nel valore restituito e
     * nello [state], da cui lo leggono Profilo e pagina del backup.
     */
    suspend fun run(
        context: Context,
        force: Boolean = false,
        overrideHold: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): Outcome = runLock.withLock {
        withContext(Dispatchers.IO) {
            init(context)
            val uri = state.uri?.let(Uri::parse) ?: return@withContext Outcome.NotConfigured
            if (!force && !AutoBackupPolicy.isDue(state.lastSuccessMillis, nowMillis)) return@withContext Outcome.NotDue
            try {
                val resolver = context.contentResolver
                val text = UnicumRepository.exportBackup(context, includeNotion = state.includeNotion)
                val fresh = BackupCodec.decode(text) ?: error("backup non valido")

                // Il file che c'è già: se ha molti più dati di questo, qualcosa è andato storto
                val old = readText(context, uri)?.let(BackupCodec::decode)
                if (!overrideHold && BackupCodec.shrunk(old, fresh)) {
                    val msg = tr("Il backup di adesso ha %d alimenti nei pasti contro i %d dell'ultimo: non l'ho sovrascritto. Se è giusto così, salva comunque.", fresh.meals.size, old!!.meals.size)
                        
                    save(state.copy(held = msg, lastError = null))
                    return@withContext Outcome.Held(msg)
                }

                val bytes = text.toByteArray(Charsets.UTF_8)
                val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri, "w")
                ?: throw FileNotFoundException()
                out.use { it.write(bytes); it.flush() }

                // Riletto: deve essere un backup valido con gli stessi pasti
                val back = readText(context, uri)?.let(BackupCodec::decode)
                if (back == null || back.meals.size != fresh.meals.size) error("il file scritto non è leggibile")

                save(state.copy(lastSuccessMillis = nowMillis, lastBytes = bytes.size.toLong(), lastError = null, held = null))
                Outcome.Saved(bytes.size.toLong())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = friendlyError(e)
                save(state.copy(lastError = msg))
                Outcome.Failed(msg)
            }
        }
    }

    private fun readText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            // Al massimo MAX_READ_BYTES (readNBytes esiste solo da Android 13: su quelli prima darebbe sempre null)
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (out.size() < MAX_READ_BYTES) {
                val n = input.read(buf, 0, minOf(buf.size, MAX_READ_BYTES - out.size()))
                if (n < 0) break
                out.write(buf, 0, n)
            }
            val bytes = out.toByteArray()
            if (bytes.isEmpty()) null else String(bytes, Charsets.UTF_8)
        }
    }.getOrNull()

    internal fun friendlyError(e: Throwable): String = when (e) {
        is SecurityException -> tr("Non ho più il permesso di scrivere in quel posto: scegli di nuovo dove salvare.")
        is FileNotFoundException -> tr("Il file non c'è più (cancellato o spostato): scegli di nuovo dove salvare.")
        else -> tr("Backup non riuscito (%s).", e.message ?: e.javaClass.simpleName)
    }
}
