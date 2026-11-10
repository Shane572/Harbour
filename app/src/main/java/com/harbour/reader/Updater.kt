package com.harbour.reader

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val build: Int, val apkUrl: String, val sizeBytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** Handed to Android's installer; the user confirms there. */
    data class Waiting(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo?) : UpdateState
}

/**
 * Checks this app's GitHub releases for a newer build and installs it in place.
 * Each GitHub build is tagged build-N and its versionCode is N, so a higher N means newer.
 */
class Updater(private val context: Context) {

    val currentBuild: Int = runCatching {
        PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0),
        ).toInt()
    }.getOrDefault(0)

    /** Returns the latest release if it's newer than this build, or null when up to date. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        toUpdate(JSONObject(get("$API/releases/latest")))?.takeIf { it.build > currentBuild }
    }

    private fun toUpdate(release: JSONObject): UpdateInfo? {
        val build = release.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return null
        val assets = release.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?: return null
        return UpdateInfo(build, apk.getString("browser_download_url"), apk.optLong("size"))
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) throw IOException("GitHub replied ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the APK straight into Android's installer, then asks the user to confirm. */
    suspend fun install(info: UpdateInfo, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            try {
                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                try {
                    if (conn.responseCode != 200) throw IOException("Download failed (${conn.responseCode})")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                    conn.inputStream.use { input ->
                        session.openWrite("harbour.apk", 0, if (total > 0) total else -1).use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                            }
                            session.fsync(out)
                        }
                    }
                } finally {
                    conn.disconnect()
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val result = PendingIntent.getBroadcast(
                    context, sessionId, Intent(context, InstallResultReceiver::class.java), flags,
                )
                session.commit(result.intentSender)
            } catch (e: Exception) {
                session.abandon()
                throw e
            }
        }
    }

    companion object {
        private const val API = "https://api.github.com/repos/Shane572/Harbour"
    }
}

/** Receives Android's answer about an update install. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Android restarts the app on the new version.
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                Toast.makeText(context, "Update cancelled", Toast.LENGTH_SHORT).show()
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(context, "Update didn't install" + (why?.let { ": $it" } ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
}
