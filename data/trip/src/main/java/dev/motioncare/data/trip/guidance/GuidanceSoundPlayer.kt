package dev.motioncare.data.trip.guidance

import android.util.Log
import dev.motioncare.data.trip.GuidanceSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "GuidanceSound"

/** Longer than the longest card read aloud (about 3 s, 4.5 s when the engine is cold). */
internal const val SOUND_LIMIT_MS = 15_000L

/** The device side of playing a sound. All calls and callbacks run on one thread. */
internal interface SoundOutput {
    /** A call, navigation voice or assistant is speaking and must not be talked over. */
    fun otherVoiceActive(): Boolean
    fun requestFocus(onLost: () -> Unit): Boolean
    fun abandonFocus()
    fun watchOtherVoice(onStarted: () -> Unit)
    fun stopWatching()
    fun chime(onFinished: () -> Unit)
    fun speak(message: String, onFinished: () -> Unit, onFailed: () -> Unit)
    fun silence()
    fun after(delayMs: Long, block: () -> Unit)
    fun release()
}

/**
 * Decides whether a card's sound plays. The sound is optional, so it gives way to everything else:
 * another voice, a refused focus request or any failure leaves the card alone and stays silent.
 * The rules and their sources are in docs/guidance-card.md.
 */
internal class GuidanceSoundPlayer(
    private val sound: () -> GuidanceSound,
    private val output: SoundOutput,
) {
    private class Playing

    private var playing: Playing? = null

    fun play(message: String) {
        if (playing != null) return report("skipped: already playing")
        val job = Playing()
        contain(job) {
            if (output.otherVoiceActive()) return@contain report("skipped: other voice")
            if (!output.requestFocus { stop(job, "focus lost") }) return@contain report("skipped: no focus")
            playing = job
            output.watchOtherVoice { stop(job, "other voice") }
            output.after(SOUND_LIMIT_MS) { stop(job, "time limit") }
            when (sound()) {
                GuidanceSound.CHIME -> chime(job)
                GuidanceSound.VOICE -> speak(job, message)
            }
        }
    }

    fun release() {
        playing?.let { stop(it, "released") }
        runCatching { output.release() }.onFailure { report("release failed: $it") }
    }

    private fun chime(job: Playing) {
        output.chime { finish(job) }
        report("started: ${GuidanceSound.CHIME}")
    }

    private fun speak(job: Playing, message: String) {
        output.speak(message, { finish(job) }, { chimeInstead(job) })
        report("started: ${GuidanceSound.VOICE}")
    }

    private fun chimeInstead(job: Playing) {
        if (playing !== job) return
        report("speech failed")
        contain(job) { chime(job) }
    }

    private fun stop(job: Playing, reason: String) {
        if (playing !== job) return
        report("stopped: $reason")
        runCatching { output.silence() }.onFailure { report("silence failed: $it") }
        finish(job)
    }

    private fun finish(job: Playing) {
        if (playing !== job) return
        playing = null
        runCatching { output.stopWatching() }.onFailure { report("stop watching failed: $it") }
        runCatching { output.abandonFocus() }.onFailure { report("abandon failed: $it") }
        report("finished")
    }

    /** These run on the main thread beside recording, so a failing sound must never become a crash. */
    private inline fun contain(job: Playing, block: () -> Unit) {
        runCatching(block).onFailure {
            report("failed: $it")
            finish(job)
        }
    }

    private fun report(message: String) {
        runCatching { Log.i(TAG, message) }
    }
}

/**
 * A short rising two-note chime. It is generated so no audio file has to be licensed or shipped.
 * Pleos recommends 150 to 200 ms of silence inside the audio data so the output path can open
 * without cutting off the start of the sound.
 */
internal fun chimePcm(sampleRate: Int): ShortArray {
    val silence = sampleRate * 180 / 1000
    val first = note(783.99, 140, 0.35, sampleRate)
    val second = note(1046.50, 320, 0.35, sampleRate)
    return ShortArray(silence) + first + second
}

private fun note(frequency: Double, millis: Int, loudness: Double, sampleRate: Int): ShortArray {
    val samples = sampleRate * millis / 1000
    val edge = sampleRate * 5 / 1000
    return ShortArray(samples) { i ->
        val time = i.toDouble() / sampleRate
        val edges = min(1.0, min(i, samples - 1 - i).toDouble() / edge)
        val decay = exp(-3.0 * i / samples)
        (sin(2 * PI * frequency * time) * edges * decay * loudness * Short.MAX_VALUE).toInt().toShort()
    }
}
