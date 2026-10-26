package com.kaona.bach

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log

class BachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("BACH", "Uncaught exception in thread ${thread.name}", throwable)
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                getSharedPreferences("bach_crash", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", stackTrace)
                    .commit()

                val intent = Intent(this, CrashReportActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("error", stackTrace)
                }
                startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (e: Throwable) {
                Log.e("BACH", "Failed to launch CrashReportActivity", e)
                oldHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        lateinit var instance: BachApplication
            private set
    }
}
