package com.harbour.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.ParcelFileDescriptor
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** One voice in a pack. [id] is its speaker number in the model. */
data class Voice(val id: Int, val name: String, val kind: String) {
    val label: String get() = "$name · $kind"
}

/** Who reads a line: the narrator (caption boxes) or a character voice (speech bubbles). */
enum class VoiceRole(val label: String) { Narrator("Narrator"), Female("Female"), Male("Male") }

/**
 * Free open-source voice models that run on the tablet (through sherpa-onnx), downloaded once
 * from sherpa-onnx's GitHub releases. Quick (Piper) keeps up easily; Natural (Kokoro) sounds
 * best but is several times slower to speak, so it may pause between lines.
 */
enum class VoicePack(
    val label: String,
    val detail: String,
    val archive: String,
    val downloadMb: Int,
    /** The file that must be there once it's unpacked. */
    val modelFile: String,
) {
    Quick("Quick", "Piper voices. Keeps up easily.", "vits-piper-en_US-libritts_r-medium-int8", 22, "en_US-libritts_r-medium.onnx"),
    Natural("Natural", "Kokoro voices. Sounds best, but too slow on this tablet: about 30 s before a line plays.", "kokoro-int8-en-v0_19", 98, "model.int8.onnx"),
    ;

    val voices: List<Voice> get() = if (this == Quick) Voices.piper else Voices.kokoro

    fun byId(id: Int): Voice = voices.firstOrNull { it.id == id } ?: voices.first()

    fun defaultFor(role: VoiceRole): Int = when (this) {
        Quick -> when (role) {
            VoiceRole.Narrator -> Voices.PIPER_NARRATOR
            VoiceRole.Female -> Voices.PIPER_FEMALE
            VoiceRole.Male -> Voices.PIPER_MALE
        }
        Natural -> when (role) {
            VoiceRole.Narrator -> 9 // George
            VoiceRole.Female -> 1 // Bella
            VoiceRole.Male -> 6 // Michael
        }
    }
}

object Voices {
    val kokoro = listOf(
        Voice(1, "Bella", "American woman"),
        Voice(2, "Nicole", "American woman, soft"),
        Voice(3, "Sarah", "American woman"),
        Voice(4, "Sky", "American woman"),
        Voice(0, "Mixed", "American woman"),
        Voice(7, "Emma", "British woman"),
        Voice(8, "Isabella", "British woman"),
        Voice(5, "Adam", "American man"),
        Voice(6, "Michael", "American man"),
        Voice(9, "George", "British man"),
        Voice(10, "Lewis", "British man"),
    )

    // Piper (LibriTTS-R) has 904 unlabelled speakers. These were picked by measuring each one's
    // pitch: clearly men's and clearly women's voices, deep to light.
    val piper = listOf(
        Voice(22, "Man 1", "deep"),
        Voice(132, "Man 2", "deep"),
        Voice(156, "Man 3", "low"),
        Voice(116, "Man 4", "middle"),
        Voice(27, "Man 5", "middle"),
        Voice(70, "Man 6", "lighter"),
        Voice(184, "Woman 1", "low"),
        Voice(160, "Woman 2", "low"),
        Voice(46, "Woman 3", "middle"),
        Voice(80, "Woman 4", "middle"),
        Voice(161, "Woman 5", "light"),
        Voice(77, "Woman 6", "light"),
    )
    const val PIPER_NARRATOR = 22
    const val PIPER_FEMALE = 80
    const val PIPER_MALE = 116


    fun dir(context: Context, pack: VoicePack): File = File(context.filesDir, "voices/${pack.archive}")

    fun installed(context: Context, pack: VoicePack): Boolean = File(dir(context, pack), ".complete").exists()

    fun delete(context: Context, pack: VoicePack) {
        dir(context, pack).deleteRecursively()
    }

    /** Downloads and unpacks a pack. [onProgress] gets 0..1 while downloading, then -1 while unpacking. */
    fun install(context: Context, pack: VoicePack, onProgress: (Float) -> Unit) {
        val root = File(context.filesDir, "voices").apply { mkdirs() }
        if (root.usableSpace < pack.downloadMb * 4L * 1024 * 1024) {
            throw IOException("Not enough free space (needs about ${pack.downloadMb * 4} MB)")
        }
        val archive = File(context.cacheDir, "${pack.archive}.tar.bz2")
        try {
            download(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${pack.archive}.tar.bz2",
                pack.downloadMb,
                archive,
                onProgress,
            )
            onProgress(-1f)
            val tmp = File(root, "${pack.archive}.tmp")
            tmp.deleteRecursively()
            unpack(archive, tmp)
            if (!File(tmp, pack.modelFile).exists() || !File(tmp, "tokens.txt").exists()) {
                tmp.deleteRecursively()
                throw IOException("The voice download was incomplete")
            }
            val final = dir(context, pack)
            final.deleteRecursively()
            if (!tmp.renameTo(final)) throw IOException("Couldn't move the voices into place")
            File(final, ".complete").writeText("ok")
        } finally {
            archive.delete()
        }
    }

    private fun download(url: String, sizeMb: Int, to: File, onProgress: (Float) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) throw IOException("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: (sizeMb * 1024L * 1024)
            conn.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - last > 512 * 1024) {
                            last = done
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Unpacks the .tar.bz2 with libarchive (already here for CBR), dropping the top folder. */
    private fun unpack(file: File, into: File) {
        into.mkdirs()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val a = Archive.readNew()
            try {
                Archive.setCharset(a, "UTF-8".toByteArray())
                Archive.readSupportFilterAll(a)
                Archive.readSupportFormatAll(a)
                Archive.readOpenFd(a, pfd.fd, 64 * 1024)
                while (true) {
                    val entry = Archive.readNextHeader(a)
                    if (entry == 0L) break
                    if (ArchiveEntry.filetype(entry) != ArchiveEntry.AE_IFREG) continue
                    val name = ArchiveEntry.pathnameUtf8(entry)
                        ?: ArchiveEntry.pathname(entry)?.let { String(it, Charsets.UTF_8) }
                        ?: continue
                    val rel = name.substringAfter('/', "")
                    if (rel.isEmpty() || rel.split('/').any { it == ".." }) continue
                    val out = File(into, rel)
                    out.parentFile?.mkdirs()
                    ParcelFileDescriptor.open(
                        out,
                        ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
                    ).use { Archive.readDataIntoFd(a, it.fd) }
                }
            } finally {
                runCatching { Archive.readFree(a) }
            }
        }
    }
}

/** The loaded voice model. Loading takes a few seconds, so it's kept while Harbour is open. */
class VoiceEngine private constructor(val pack: VoicePack, private val tts: OfflineTts) {
    val sampleRate: Int = tts.sampleRate()

    /** Speaks [text] as voice [voice]; returns the audio. One line at a time (the model isn't shared). */
    @Synchronized
    fun speak(text: String, voice: Int, speed: Float): FloatArray = tts.generate(text, voice, speed).samples

    fun release() {
        runCatching { tts.release() }
    }

    companion object {
        fun load(context: Context, pack: VoicePack): VoiceEngine {
            val d = Voices.dir(context, pack)
            val model = when (pack) {
                VoicePack.Natural -> OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = File(d, pack.modelFile).path,
                        voices = File(d, "voices.bin").path,
                        tokens = File(d, "tokens.txt").path,
                        dataDir = File(d, "espeak-ng-data").path,
                    ),
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                )
                VoicePack.Quick -> OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(d, pack.modelFile).path,
                        tokens = File(d, "tokens.txt").path,
                        dataDir = File(d, "espeak-ng-data").path,
                    ),
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                )
            }
            return VoiceEngine(pack, OfflineTts(null, OfflineTtsConfig(model = model, maxNumSentences = 1)))
        }
    }
}

/** Plays speech through an AudioTrack, a little at a time so pausing and stopping are instant. */
class SpeechPlayer(private val sampleRate: Int) {
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
                sampleRate * 4 / 4,
            ),
        )
        .build()

    private var written = 0L

    init {
        track.play()
    }

    /**
     * Plays [samples] followed by [gapSeconds] of quiet, and returns once it's been heard. While
     * [paused] is true, it waits. The quiet also makes sure even a very short line fills the
     * buffer, since some devices only start playing once it's full.
     */
    suspend fun play(input: FloatArray, paused: StateFlow<Boolean>, gapSeconds: Float = 0.35f) {
        val samples = input.copyOf(input.size + (sampleRate * gapSeconds).toInt())
        var pos = 0
        while (pos < samples.size) {
            if (!coroutineContext.isActive) return
            if (paused.value) waitWhilePaused(paused)
            val n = track.write(samples, pos, minOf(samples.size - pos, sampleRate / 10), AudioTrack.WRITE_NON_BLOCKING)
            if (n < 0) return
            if (n == 0) delay(15) else pos += n
        }
        written += samples.size
        // Wait until it's actually been heard, so the highlight moves on at the right time. If
        // playback stops moving (it shouldn't), carry on rather than wait forever.
        var last = head()
        var still = 0
        while (coroutineContext.isActive && head() < written) {
            if (paused.value) {
                waitWhilePaused(paused)
                still = 0
                continue
            }
            delay(20)
            val now = head()
            if (now == last) {
                still += 20
                if (still > 1500) break
            } else {
                still = 0
                last = now
            }
        }
    }

    private fun head(): Long = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    private suspend fun waitWhilePaused(paused: StateFlow<Boolean>) {
        track.pause()
        paused.first { !it }
        track.play()
    }

    fun release() {
        runCatching {
            track.pause()
            track.flush()
            track.release()
        }
    }
}
