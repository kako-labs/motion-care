package dev.soundprobe

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private const val TAG = "SOUNDPROBE"

/** Measures what can make sound on Pleos. Driven by adb; every result goes to logcat. */
class ProbeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var tts: TextToSpeech? = null
    private var mode: Mode? = null
    private var speakAt = 0L
    private var rmsCount = 0
    private var rmsMax = Double.NEGATIVE_INFINITY
    private var rmsMin = Double.POSITIVE_INFINITY
    private val holds = mutableMapOf<String, AudioFocusRequest>()
    private var nextId = 10

    private val listener = object : EventListener {
        override fun onReady() = log("tts onReady +${since()}ms")
        override fun onStart() = log("tts onStart +${since()}ms")
        override fun onUpdatedRms(rms: Double) {
            rmsCount++
            if (rms > rmsMax) rmsMax = rms
            if (rms < rmsMin) rmsMin = rms
        }
        override fun onDone() = log("tts onDone +${since()}ms rms count=$rmsCount min=$rmsMin max=$rmsMax")
        override fun onStop() = log("tts onStop +${since()}ms rms count=$rmsCount")
        override fun onError(errorType: ErrorType) = log("tts onError $errorType +${since()}ms")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("low", "low", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "low")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("SOUNDPROBE").build())
        audio.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>) {
                log("playback active=" + configs.joinToString { "usage=${it.audioAttributes.usage} content=${it.audioAttributes.contentType}" })
            }
        }, main)
        log("created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, Notification.Builder(this, "low")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("SOUNDPROBE").build())
        val act = intent?.getStringExtra("act") ?: return START_NOT_STICKY
        log("act $act")
        runCatching { handle(act, intent.getStringExtra("text").orEmpty()) }.onFailure { log("act failed: $it") }
        return START_NOT_STICKY
    }

    private fun handle(act: String, text: String) {
        val parts = act.split(":")
        when (parts[0]) {
            "tts" -> initTts(Mode.valueOf(parts[1]))
            "attrs" -> tts?.setAudioAttributes(attributes(parts[1].toInt()))
            "speak" -> speak(text)
            "stop" -> tts?.stop()
            "release" -> releaseTts()
            "focus" -> requestFocus(parts[1], parts[2].toInt(), parts[3].toInt())
            "abandon" -> holds.remove(parts[1])?.let { log("abandon ${parts[1]} result=${audio.abandonAudioFocusRequest(it)}") }
            "tone" -> tone(parts[1].toInt(), parts[2].toInt())
            "card" -> card(parts[1], text)
            "volumes" -> volumes()
            "ptts" -> platformTts(text, parts[1].toInt())
        }
    }

    private fun initTts(selected: Mode) {
        releaseTts()
        mode = selected
        val instance = TextToSpeech(applicationContext, selected)
        tts = instance
        val startedAt = SystemClock.elapsedRealtime()
        instance.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                log("tts init success mode=$selected +${SystemClock.elapsedRealtime() - startedAt}ms")
            }
            override fun onFailure(errorType: ErrorType) = log("tts init failure mode=$selected $errorType")
        })
        instance.addEventListener(listener)
        instance.setLocale(Locale.KOREA)
    }

    private fun speak(text: String) {
        rmsCount = 0
        rmsMax = Double.NEGATIVE_INFINITY
        rmsMin = Double.POSITIVE_INFINITY
        speakAt = SystemClock.elapsedRealtime()
        log("speak len=${text.length}")
        tts?.speak(text, true) ?: log("speak without tts")
    }

    private fun releaseTts() {
        tts?.removeEventListener(listener)
        tts?.release()
        tts = null
    }

    private fun attributes(usage: Int) = AudioAttributes.Builder()
        .setUsage(usage)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun requestFocus(name: String, usage: Int, gain: Int) {
        val request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(attributes(usage))
            .setOnAudioFocusChangeListener({ change -> log("focus $name change=$change") }, main)
            .build()
        val result = audio.requestAudioFocus(request)
        log("focus $name usage=$usage gain=$gain result=$result")
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) holds[name] = request
    }

    /** Plays a tone through an app-owned AudioTrack with the given usage. */
    private fun tone(usage: Int, millis: Int) {
        val rate = 48_000
        val samples = rate * millis / 1000
        val pcm = ShortArray(samples) { i ->
            val fade = minOf(1.0, minOf(i, samples - i) / (rate * 0.01))
            (sin(2 * PI * 880 * i / rate) * fade * 0.4 * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples * 2)
            .build()
        track.write(pcm, 0, samples)
        track.play()
        val startedAt = SystemClock.elapsedRealtime()
        log("tone play usage=$usage ms=$millis state=${track.playState}")
        main.postDelayed({
            log("tone end head=${track.playbackHeadPosition}/$samples after ${SystemClock.elapsedRealtime() - startedAt}ms")
            track.release()
        }, millis + 300L)
    }

    /** kind: default (channel default sound), silent, or explicit (channel sound set to the system notification tone). */
    private fun card(kind: String, text: String) {
        val id = "card_$kind"
        val channel = NotificationChannel(id, id, NotificationManager.IMPORTANCE_HIGH)
        when (kind) {
            "silent" -> channel.setSound(null, null)
            "explicit" -> channel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: Uri.EMPTY,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        log("card $kind sound=${manager.getNotificationChannel(id).sound} default=${RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)}")
        manager.notify(nextId++, Notification.Builder(this, id)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("모션케어")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setTimeoutAfter(15_000)
            .build())
    }

    private fun volumes() {
        listOf(
            "music" to AudioManager.STREAM_MUSIC,
            "notification" to AudioManager.STREAM_NOTIFICATION,
            "ring" to AudioManager.STREAM_RING,
            "alarm" to AudioManager.STREAM_ALARM,
        ).forEach { (name, stream) ->
            log("volume $name ${audio.getStreamVolume(stream)}/${audio.getStreamMaxVolume(stream)}")
        }
    }

    private var platform: android.speech.tts.TextToSpeech? = null

    /** The plain Android API against the same engine, with no SDK involved. */
    private fun platformTts(text: String, usage: Int) {
        val startedAt = SystemClock.elapsedRealtime()
        platform?.shutdown()
        var engine: android.speech.tts.TextToSpeech? = null
        engine = android.speech.tts.TextToSpeech(applicationContext, { status ->
            log("ptts init status=$status engines=${engine?.engines?.map { it.name }} default=${engine?.defaultEngine} +${SystemClock.elapsedRealtime() - startedAt}ms")
            val e = engine ?: return@TextToSpeech
            log("ptts language=${e.setLanguage(Locale.KOREA)}")
            e.setAudioAttributes(attributes(usage))
            e.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) = log("ptts onStart +${SystemClock.elapsedRealtime() - startedAt}ms")
                override fun onDone(id: String?) = log("ptts onDone +${SystemClock.elapsedRealtime() - startedAt}ms")
                @Deprecated("required") override fun onError(id: String?) = log("ptts onError")
            })
            log("ptts speak=${e.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "u1")}")
        }, "ai.pleos.playground.caas")
        platform = engine
    }

    private fun since() = SystemClock.elapsedRealtime() - speakAt

    private fun log(message: String) { Log.i(TAG, "[$packageName] $message") }

    override fun onDestroy() {
        releaseTts()
        super.onDestroy()
    }
}
