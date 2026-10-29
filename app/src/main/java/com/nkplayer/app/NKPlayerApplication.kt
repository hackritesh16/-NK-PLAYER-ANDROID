package com.nkplayer.app

import android.app.Application
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

class NKPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val writer = StringWriter()
                throwable.printStackTrace(PrintWriter(writer))
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_LAST_CRASH, "Thread: ${thread.name}\n" + writer.toString())
                    .putLong(KEY_CRASH_TIME, System.currentTimeMillis())
                    .commit()
            }.onFailure { Log.e(TAG, "Could not save crash report", it) }

            Log.e(TAG, "Uncaught app exception", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val PREFS = "nk_player_crash_reports"
        const val KEY_LAST_CRASH = "last_crash"
        const val KEY_CRASH_TIME = "last_crash_time"
        private const val TAG = "NKPlayerCrash"
    }
}
