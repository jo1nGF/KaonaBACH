package com.kaona.bach

import android.app.Application

class BachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: BachApplication
            private set
    }
}
