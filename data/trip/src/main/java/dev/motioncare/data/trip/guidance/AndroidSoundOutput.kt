package dev.motioncare.data.trip.guidance

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.Locale

private const val CHIME_SAMPLE_RATE = 48_000
private const val CHIME_END_MARGIN_MS = 150L

private val OTHER_VOICE_USAGES = setOf(
    AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
    AudioAttributes.USAGE_VOICE_COMMUNICATION,
    AudioAttributes.USAGE_ASSISTANT,
)

/**
 * Plays through the notification audio context. Pleos measures show the speech service does not take
 * audio focus and defaults to media usage, and that focus alone does not keep a card from talking over
 * navigation or a call, so those are handled here. Sources are in docs/guidance-card.md.
 */
internal class AndroidSoundOutput(context: Context) : SoundOutput {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var focus: AudioFocusRequest? = null
    private var watcher: AudioManager.AudioPlaybackCallback? = null
    private var track: AudioTrack? = null
    private var speech: Speech? = null

    override fun otherVoiceActive(): Boolean =
        audio.mode != AudioManager.MODE_NORMAL || audio.activePlaybackConfigurations.hasOtherVoice()

    override fun requestFocus(onLost: () -> Unit): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SONIFICATION))
            .setOnAudioFocusChangeListener({ change -> if (change < 0) onLost() }, main)
            .build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        return true
    }

    override fun abandonFocus() {
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
    }

    override fun watchOtherVoice(onStarted: () -> Unit) {
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                if (configs.hasOtherVoice()) onStarted()
            }
        }
        audio.registerAudioPlaybackCallback(callback, main)
        watcher = callback
    }

    override fun stopWatching() {
        watcher?.let(audio::unregisterAudioPlaybackCallback)
        watcher = null
    }

    override fun chime(onFinished: () -> Unit) {
        val pcm = chimePcm(CHIME_SAMPLE_RATE)
        val player = AudioTrack.Builder()
            .setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SONIFICATION))
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(CHIME_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .build()
        track = player
        player.write(pcm, 0, pcm.size)
        player.play()
        main.postDelayed({
            if (track === player) releaseTrack()
            onFinished()
        }, pcm.size * 1000L / CHIME_SAMPLE_RATE + CHIME_END_MARGIN_MS)
    }

    override fun speak(message: String, onFinished: () -> Unit, onFailed: () -> Unit) {
        val engine = speech ?: Speech(app, main).also { speech = it }
        engine.speak(message, onFinished, onFailed)
    }

    override fun silence() {
        releaseTrack()
        speech?.stop()
    }

    override fun after(delayMs: Long, block: () -> Unit) {
        main.postDelayed(block, delayMs)
    }

    override fun release() {
        releaseTrack()
        speech?.release()
        speech = null
    }

    private fun releaseTrack() {
        track?.release()
        track = null
    }

    private fun List<AudioPlaybackConfiguration>.hasOtherVoice() =
        any { it.audioAttributes.usage in OTHER_VOICE_USAGES }
}

private fun attributes(contentType: Int): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
    .setContentType(contentType)
    .build()

/**
 * The Pleos on-device speech engine. It needs no registration, key or network. The SDK reports from
 * other threads, so every callback moves to [main].
 */
private class Speech(context: Context, private val main: Handler) {
    private val engine = TextToSpeech(context, Mode.ON_DEVICE)
    private var ready = false
    private var failed = false
    private var waiting: (() -> Unit)? = null
    private var onFinished: (() -> Unit)? = null
    private var onFailed: (() -> Unit)? = null

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onStop() = Unit
        override fun onDone() { main.post { onFinished?.invoke() } }
        override fun onError(errorType: ErrorType) { main.post { onFailed?.invoke() } }
    }

    init {
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                main.post {
                    ready = true
                    waiting?.invoke()
                    waiting = null
                }
            }
            override fun onFailure(errorType: ErrorType) {
                main.post {
                    failed = true
                    waiting = null
                    onFailed?.invoke()
                }
            }
        })
        // The SDK requires these after initialize() and before speak().
        engine.addEventListener(events)
        engine.setLocale(Locale.KOREA)
        engine.setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SPEECH))
    }

    fun speak(message: String, finished: () -> Unit, failure: () -> Unit) {
        onFinished = finished
        onFailed = failure
        when {
            failed -> failure()
            ready -> engine.speak(message, true)
            else -> waiting = { engine.speak(message, true) }
        }
    }

    fun stop() {
        waiting = null
        onFinished = null
        onFailed = null
        if (ready) engine.stop()
    }

    fun release() {
        stop()
        engine.removeEventListener(events)
        engine.release()
    }
}
