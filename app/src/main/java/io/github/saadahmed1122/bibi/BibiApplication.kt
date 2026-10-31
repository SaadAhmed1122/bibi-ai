package io.github.saadahmed1122.bibi

import android.app.Application
import io.github.saadahmed1122.bibi.core.common.ProcessNames

class BibiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The :inference process runs only the model service; it gets no UI wiring.
        if (ProcessNames.isInference(getProcessName())) return
    }
}
