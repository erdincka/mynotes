package com.mynotes

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MyNotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // BuildConfig is generated, so we might need to use a safe way to check it if it's not yet generated
        // For now, we'll plant a DebugTree in all builds or use a better check
        Timber.plant(Timber.DebugTree())
    }
}
