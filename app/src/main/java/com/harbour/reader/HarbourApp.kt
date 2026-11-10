package com.harbour.reader

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.harbour.reader.ui.AccentChoices
import com.harbour.reader.ui.HarbourColors
import java.io.File

/**
 * Saves the details of any crash so Harbour can show them (and you can copy them) next launch,
 * and applies the saved colours before anything is drawn.
 */
class HarbourApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
        HarbourColors.accent = AccentChoices.firstOrNull { it.name == settings.getString("accent", null) }
            ?: AccentChoices.first()
        HarbourColors.black = settings.getBoolean("black", false)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { CrashLog.write(this, error) }
            previous?.uncaughtException(thread, error)
        }
    }
}

object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun write(context: Context, error: Throwable) {
        val build = runCatching {
            PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        }.getOrDefault(0L)
        val header = "Harbour build $build · ${Build.MANUFACTURER} ${Build.MODEL} · " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n"
        file(context).writeText(header + error.stackTraceToString().take(12_000))
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }
}
