package com.reality2.devtool

import android.app.Application
import timber.log.Timber

/**
 * Application class for Reality2 DevTool
 */
class Reality2DevToolApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("Reality2 DevTool started")
    }
}
