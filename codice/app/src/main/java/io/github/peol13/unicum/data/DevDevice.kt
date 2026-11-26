package io.github.peol13.unicum.data

import android.os.Build
import io.github.peol13.unicum.BuildConfig

/**
 * Dispositivi di chi sviluppa, che non devono mai entrare nei numeri: le build di debug (il telefono di chi
 * sviluppa) e gli emulatori (le prove, anche con la release). Da lì non partono né il conteggio anonimo
 * ([AnonCount]) né le statistiche dettagliate ([UsageStats]).
 */
object DevDevice {
    val isEmulator: Boolean by lazy {
        looksLikeEmulator(Build.FINGERPRINT.orEmpty(), Build.MODEL.orEmpty(), Build.HARDWARE.orEmpty(), Build.PRODUCT.orEmpty())
    }

    /** true = non si invia niente da questo dispositivo. */
    val notCounted: Boolean get() = BuildConfig.DEBUG || isEmulator

    /** Emulatore di Android Studio (e simili): logica pura, con test. */
    fun looksLikeEmulator(fingerprint: String, model: String, hardware: String, product: String): Boolean =
        fingerprint.startsWith("generic") || fingerprint.contains("emulator") || fingerprint.contains("sdk_gphone") ||
            model.startsWith("sdk_gphone") || model.contains("Emulator") || model.contains("Android SDK built for") ||
            hardware == "goldfish" || hardware == "ranchu" ||
            product.startsWith("sdk") || product.contains("emulator") || product.contains("simulator")
}
