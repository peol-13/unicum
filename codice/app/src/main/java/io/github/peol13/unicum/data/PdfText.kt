package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tr

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Testo di un PDF scelto dall'utente, riga per riga in ordine di lettura (PdfBox-Android, tutto sul telefono). */
object PdfText {

    sealed interface Outcome {
        data class Ok(val name: String?, val lines: List<String>) : Outcome
        data class Error(val message: String) : Outcome
    }

    /** Più di così non è un programma di allenamento: si evita di bloccare il telefono su un libro intero. */
    private const val MAX_PAGES = 60

    suspend fun read(context: Context, uri: Uri): Outcome = withContext(Dispatchers.IO) {
        val name = displayName(context, uri)
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext Outcome.Error(tr("Il file non si apre."))
            input.use {
                PDDocument.load(it).use { doc ->
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                        endPage = minOf(doc.numberOfPages, MAX_PAGES)
                    }
                    val lines = stripper.getText(doc).lines().map { l -> l.trim() }.filter { l -> l.isNotEmpty() }
                    if (lines.isEmpty()) Outcome.Error(
                        tr("Nel PDF non c'è testo da leggere: probabilmente è una foto o una scansione. Aggiungi le sedute a mano, oppure esporta il programma come PDF con il testo.")
                            
                    ) else Outcome.Ok(name, lines)
                }
            }
        } catch (e: InvalidPasswordException) {
            Outcome.Error(tr("Il PDF è protetto da password: toglila e riprova."))
        } catch (e: OutOfMemoryError) {
            Outcome.Error(tr("Il PDF è troppo grande da leggere sul telefono."))
        } catch (e: Exception) {
            Outcome.Error(tr("Il file non sembra un PDF leggibile (%s).", e.javaClass.simpleName))
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.removeSuffix(".pdf")?.removeSuffix(".PDF")
}
