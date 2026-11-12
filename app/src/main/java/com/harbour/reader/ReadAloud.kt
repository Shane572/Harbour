package com.harbour.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads a comic aloud, bubble by bubble and page by page, turning pages as it goes. Each page's
 * lettering is found while the page before is being read, and each line is voiced while the one
 * before is playing. Lives as long as the reader has the comic open.
 */
class ReadAloud(
    private val vm: MainViewModel,
    private val comicKey: String,
    private val archive: ComicArchive,
    private val scope: CoroutineScope,
) {
    var active by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set

    /** What it's busy with before it can speak ("Getting the voices ready…"), or null. */
    var status by mutableStateOf<String?>(null)
        private set

    /** The file's page being read, its lettering, and which bit is being read now. */
    var page by mutableIntStateOf(-1)
        private set
    var bubbles by mutableStateOf<List<Bubble>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set

    val current: Bubble? get() = bubbles.getOrNull(index)
    val currentRole: VoiceRole? get() = current?.let { roleOf(page, index, it) }

    /** Reading direction; changing it re-reads the page order. */
    var rtl = false
        set(value) {
            if (field != value) {
                field = value
                found.clear()
            }
        }

    /** Brings a page (and the half of a split spread holding the bubble) into view. */
    var onShow: (page: Int, bubble: Bubble?) -> Unit = { _, _ -> }

    private val found = HashMap<Int, Deferred<List<Bubble>>>()
    private val pausedFlow = MutableStateFlow(false)
    private var job: Job? = null

    /** Starts (or restarts) reading at a page; [fromBubble] past the end means its last bubble. */
    fun start(fromPage: Int, fromBubble: Int = 0) {
        job?.cancel()
        active = true
        holdPaused(false)
        job = scope.launch { run(fromPage.coerceIn(0, archive.pageCount - 1), fromBubble) }
    }

    fun stop() {
        job?.cancel()
        job = null
        active = false
        index = -1
        status = null
        holdPaused(false)
    }

    fun togglePause() = holdPaused(!paused)

    private fun holdPaused(p: Boolean) {
        paused = p
        pausedFlow.value = p
    }

    fun next() = move(+1)

    fun previous() = move(-1)

    private fun move(by: Int) {
        if (!active || status != null) return
        val target = index + by
        when {
            target < 0 -> start((page - 1).coerceAtLeast(0), if (page > 0) Int.MAX_VALUE else 0)
            target >= bubbles.size -> if (page + 1 < archive.pageCount) start(page + 1) else stop()
            else -> start(page, target)
        }
    }

    /** Gives the bubble being read a different voice (remembered), and reads it again. */
    fun changeVoice(role: VoiceRole) {
        if (current == null) return
        vm.setBubbleVoice(comicKey, page, index, role)
        start(page, index)
    }

    private fun roleOf(p: Int, i: Int, b: Bubble): VoiceRole =
        vm.bubbleVoice(comicKey, p, i) ?: if (b.narrator) VoiceRole.Narrator else vm.characterVoice

    private fun bubblesOn(p: Int): Deferred<List<Bubble>> = found.getOrPut(p) {
        val r = rtl
        scope.async(Dispatchers.IO) {
            runCatching { archive.decodePage(p, 4_000_000L)?.let { BubbleFinder.find(it, r) } }.getOrNull() ?: emptyList()
        }
    }

    private suspend fun speak(engine: VoiceEngine, p: Int, i: Int, b: Bubble): FloatArray {
        val voice = vm.voiceFor(roleOf(p, i, b))
        val speed = vm.speechSpeed
        return withContext(Dispatchers.Default) { engine.speak(b.text, voice, speed) }
    }

    private suspend fun run(startPage: Int, startBubble: Int) {
        var player: SpeechPlayer? = null
        var ahead: Deferred<FloatArray?>? = null
        try {
            if (!vm.voicesLoaded) status = "Getting the voices ready…"
            val engine = vm.voiceEngine()
            player = SpeechPlayer(engine.sampleRate)
            var p = startPage
            var b = startBubble
            while (p < archive.pageCount) {
                val pending = bubblesOn(p)
                if (!pending.isCompleted) status = "Reading the page…"
                val list = pending.await()
                if (p + 1 < archive.pageCount) bubblesOn(p + 1) // get the next page ready meanwhile
                status = null
                page = p
                bubbles = list
                if (list.isEmpty()) {
                    // Nothing to read here: show it briefly and move on.
                    index = -1
                    onShow(p, null)
                    var waited = 0
                    while (waited < 1500) {
                        if (pausedFlow.value) pausedFlow.first { !it }
                        delay(100)
                        waited += 100
                    }
                } else {
                    var i = b.coerceIn(0, list.lastIndex)
                    while (i < list.size) {
                        val audio = ahead?.await() ?: speak(engine, p, i, list[i])
                        val n = i + 1
                        // Never let a failure here escape into the reader's scope; it's just retried.
                        ahead = if (n < list.size) scope.async { runCatching { speak(engine, p, n, list[n]) }.getOrNull() } else null
                        index = i
                        onShow(p, list[i])
                        player.play(audio, pausedFlow)
                        i++
                    }
                }
                p++
                b = 0
            }
            active = false
            index = -1
            vm.message = "Finished reading this issue aloud"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            active = false
            index = -1
            status = null
            vm.message = "Read aloud stopped (${e.message ?: e.javaClass.simpleName})"
        } finally {
            ahead?.cancel()
            player?.release()
        }
    }
}
