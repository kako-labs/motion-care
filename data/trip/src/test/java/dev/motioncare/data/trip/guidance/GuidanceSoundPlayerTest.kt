package dev.motioncare.data.trip.guidance

import dev.motioncare.data.trip.GuidanceSound
import org.junit.Assert.*
import org.junit.Test

private const val COPY = "정지 지점보다 조금 일찍 감속해보세요"

class GuidanceSoundPlayerTest {
    private class FakeOutput : SoundOutput {
        var otherVoice = false
        var focusGranted = true
        var focusHeld = false
        var focusRequests = 0
        var chimes = 0
        var spoken = mutableListOf<String>()
        var silenced = 0
        var released = 0
        var watching = false
        var failOnSound = false
        var failOnSilence = false
        var failOnRelease = false
        var failWithLinkError = false
        var onFocusLost: (() -> Unit)? = null
        var onOtherVoice: (() -> Unit)? = null
        var finishSound: (() -> Unit)? = null
        var failSpeech: (() -> Unit)? = null
        var limit: (() -> Unit)? = null
        var limitMs = 0L

        override fun otherVoiceActive() = otherVoice
        override fun requestFocus(onLost: () -> Unit): Boolean {
            focusRequests++
            if (!focusGranted) return false
            focusHeld = true
            onFocusLost = onLost
            return true
        }
        override fun abandonFocus() { focusHeld = false }
        override fun watchOtherVoice(onStarted: () -> Unit) { watching = true; onOtherVoice = onStarted }
        override fun stopWatching() { watching = false }
        override fun chime(onFinished: () -> Unit) {
            if (failWithLinkError) throw UnsatisfiedLinkError("no native library")
            if (failOnSound) error("no audio track")
            chimes++
            finishSound = onFinished
        }
        override fun speak(message: String, onFinished: () -> Unit, onFailed: () -> Unit) {
            if (failOnSound) error("no speech service")
            spoken += message
            finishSound = onFinished
            failSpeech = onFailed
        }
        override fun silence() {
            if (failOnSilence) error("already released")
            silenced++
        }
        override fun after(delayMs: Long, block: () -> Unit) { limitMs = delayMs; limit = block }
        override fun release() {
            if (failOnRelease) error("already released")
            released++
        }
    }

    private val output = FakeOutput()
    private var sound = GuidanceSound.CHIME
    private val player = GuidanceSoundPlayer({ sound }, output)

    @Test fun `the chime setting plays the chime and not the words`() {
        player.play(COPY)
        assertEquals(1, output.chimes)
        assertTrue(output.spoken.isEmpty())
    }

    @Test fun `the voice setting reads the card's words`() {
        sound = GuidanceSound.VOICE
        player.play(COPY)
        assertEquals(listOf(COPY), output.spoken)
        assertEquals(0, output.chimes)
    }

    @Test fun `the setting is read when the card appears`() {
        player.play(COPY)
        output.finishSound!!()
        sound = GuidanceSound.VOICE
        player.play(COPY)
        assertEquals(1, output.chimes)
        assertEquals(1, output.spoken.size)
    }

    @Test fun `focus is held only while the sound plays`() {
        player.play(COPY)
        assertTrue(output.focusHeld)
        assertTrue(output.watching)
        output.finishSound!!()
        assertFalse(output.focusHeld)
        assertFalse(output.watching)
    }

    @Test fun `nothing plays over a call or navigation voice`() {
        output.otherVoice = true
        sound = GuidanceSound.VOICE
        player.play(COPY)
        assertEquals(0, output.focusRequests)
        assertTrue(output.spoken.isEmpty())
    }

    @Test fun `nothing plays when focus is refused`() {
        output.focusGranted = false
        player.play(COPY)
        assertEquals(0, output.chimes)
        assertFalse(output.watching)
    }

    @Test fun `losing focus stops the sound`() {
        sound = GuidanceSound.VOICE
        player.play(COPY)
        output.onFocusLost!!()
        assertEquals(1, output.silenced)
        assertFalse(output.focusHeld)
        assertFalse(output.watching)
    }

    @Test fun `navigation starting to speak stops the sound`() {
        sound = GuidanceSound.VOICE
        player.play(COPY)
        output.onOtherVoice!!()
        assertEquals(1, output.silenced)
        assertFalse(output.focusHeld)
    }

    @Test fun `a card during a sound does not overlap it`() {
        player.play(COPY)
        player.play(COPY)
        assertEquals(1, output.chimes)
        assertEquals(1, output.focusRequests)
    }

    @Test fun `a sound can play again after one was stopped`() {
        player.play(COPY)
        output.onFocusLost!!()
        player.play(COPY)
        assertEquals(2, output.chimes)
    }

    @Test fun `when speech fails the chime plays instead and focus is kept for it`() {
        sound = GuidanceSound.VOICE
        player.play(COPY)
        output.failSpeech!!()
        assertEquals(1, output.chimes)
        assertTrue(output.focusHeld)
        output.finishSound!!()
        assertFalse(output.focusHeld)
    }

    @Test fun `speech failing after it was stopped plays nothing`() {
        sound = GuidanceSound.VOICE
        player.play(COPY)
        output.onFocusLost!!()
        output.failSpeech!!()
        assertEquals(0, output.chimes)
    }

    @Test fun `a sound that never reports its end gives focus back`() {
        sound = GuidanceSound.VOICE
        player.play(COPY)
        assertTrue(output.limitMs in 1..SOUND_LIMIT_MS)
        output.limit!!()
        assertEquals(1, output.silenced)
        assertFalse(output.focusHeld)
    }

    @Test fun `the limit from an old sound does not stop a newer one`() {
        player.play(COPY)
        val oldLimit = output.limit!!
        output.finishSound!!()
        player.play(COPY)
        oldLimit()
        assertEquals(0, output.silenced)
        assertTrue(output.focusHeld)
    }

    @Test fun `an audio failure stays inside the player and gives focus back`() {
        output.failOnSound = true
        player.play(COPY)
        assertFalse(output.focusHeld)
        assertFalse(output.watching)
        output.failOnSound = false
        player.play(COPY)
        assertEquals(1, output.chimes)
    }

    @Test fun `the voice setting also survives speech and chime both failing`() {
        sound = GuidanceSound.VOICE
        player.play(COPY)
        output.failOnSound = true
        output.failSpeech!!()
        assertFalse(output.focusHeld)
    }

    @Test fun `a failure while stopping does not escape and still gives focus back`() {
        player.play(COPY)
        output.failOnSilence = true
        output.onFocusLost!!()
        assertFalse(output.focusHeld)
    }

    @Test fun `a missing native library does not escape`() {
        output.failWithLinkError = true
        player.play(COPY)
        assertFalse(output.focusHeld)
    }

    @Test fun `a failure while releasing does not escape`() {
        output.failOnRelease = true
        player.release()
        assertEquals(0, output.released)
    }

    @Test fun `release stops the sound and frees the output`() {
        sound = GuidanceSound.VOICE
        player.play(COPY)
        player.release()
        assertEquals(1, output.silenced)
        assertFalse(output.focusHeld)
        assertEquals(1, output.released)
    }
}
