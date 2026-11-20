package io.github.peol13.unicum

import android.app.Application
import io.github.peol13.unicum.i18n.I18n

/** Prima di qualunque schermata, widget, notifica o lavoro in background: la lingua dell'app. */
class UnicumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.init(this)
    }
}
