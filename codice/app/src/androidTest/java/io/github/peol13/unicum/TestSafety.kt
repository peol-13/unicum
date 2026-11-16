package io.github.peol13.unicum

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peol13.unicum.data.db.UnicumDatabase
import org.junit.Assume

/**
 * Sicurezza dei test strumentati. Alcuni (AppStateTest, RepositoryTest) usano il repository e lo stato dell'app, che
 * scrivono anche su Health Connect, sul widget e sulle preferenze: su un telefono vero toccherebbero i dati dell'utente.
 * Per questo girano solo sull'emulatore (altrove vengono saltati) e su un database in memoria, mai su quello vero.
 *
 * Se si collega anche il telefono, lanciare i test su un dispositivo solo: `ANDROID_SERIAL=emulator-5554 ./gradlew
 * connectedDebugAndroidTest`; senza, Gradle li esegue su tutti i dispositivi collegati.
 */
object TestSafety {
    fun isEmulator(): Boolean =
        Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" ||
            Build.PRODUCT.startsWith("sdk") || Build.FINGERPRINT.contains("emulator")

    /** Salta il test se non è un emulatore. */
    fun requireEmulator() {
        Assume.assumeTrue("Solo su emulatore: il test usa lo stato dell'app", isEmulator())
    }

    /** Fa usare all'app un database in memoria (da chiudere con [release]). */
    fun useInMemoryDatabase(context: Context): UnicumDatabase {
        val db = Room.inMemoryDatabaseBuilder(context, UnicumDatabase::class.java).build()
        UnicumDatabase.useForTests(db)
        return db
    }

    fun release(db: UnicumDatabase?) {
        UnicumDatabase.useForTests(null)
        db?.close()
    }

    fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext
}
