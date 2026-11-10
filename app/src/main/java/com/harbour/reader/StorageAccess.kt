package com.harbour.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Plain file access for Harbour's folder browser and library. Android's system folder picker
 * is broken on Shane's tablet, so Harbour uses All files access instead.
 */
object StorageAccess {

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** True on Android 11+, where access is granted on a Settings screen rather than a popup. */
    val usesSettingsScreen: Boolean get() = Build.VERSION.SDK_INT >= 30

    /** Intents to try, most specific first, for the "Allow access to all files" screen. */
    fun settingsIntents(context: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= 30) {
            add(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
            add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    data class Root(val label: String, val dir: File)

    /** Internal storage plus any SD cards or USB drives Android reports. */
    fun roots(context: Context): List<Root> {
        val out = mutableListOf<Root>()
        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory()
        out += Root("Internal storage", primary)
        // App-specific dirs exist on every volume: /storage/XXXX-XXXX/Android/data/<pkg>/files.
        context.getExternalFilesDirs(null).drop(1).filterNotNull().forEachIndexed { i, appDir ->
            val volume = appDir.path.substringBefore("/Android/").takeIf { it.isNotEmpty() } ?: return@forEachIndexed
            out += Root(if (i == 0) "SD card" else "SD card ${i + 1}", File(volume))
        }
        return out
    }

    data class Listing(val folders: List<File>, val comicsHere: Int, val readable: Boolean)

    fun list(dir: File): Listing {
        val children = dir.listFiles() ?: return Listing(emptyList(), 0, readable = false)
        val folders = children
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        val comics = children.count { it.isFile && it.name.endsWith(".cbz", ignoreCase = true) }
        return Listing(folders, comics, readable = true)
    }
}
