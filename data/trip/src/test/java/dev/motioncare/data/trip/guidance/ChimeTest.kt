package dev.motioncare.data.trip.guidance

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class ChimeTest {
    private val rate = 48_000
    private val pcm = chimePcm(rate)
    private fun samples(ms: Int) = rate * ms / 1000

    @Test fun `it starts with the silence Pleos recommends for its output path`() {
        val firstSound = pcm.indexOfFirst { it != 0.toShort() }
        assertTrue("silence was $firstSound samples", firstSound in samples(150)..samples(200))
    }

    @Test fun `it is short`() {
        assertTrue("length was ${pcm.size} samples", pcm.size in samples(400)..samples(1000))
    }

    @Test fun `it has two notes that go up`() {
        val start = pcm.indexOfFirst { it != 0.toShort() }
        val firstNote = pcm.copyOfRange(start, start + samples(100))
        val secondNote = pcm.copyOfRange(start + samples(150), start + samples(250))
        assertTrue(crossings(secondNote) > crossings(firstNote) * 1.2)
    }

    @Test fun `it is audible without clipping`() {
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak was $peak", peak in (Short.MAX_VALUE / 5)..(Short.MAX_VALUE / 2))
    }

    @Test fun `it fades out instead of ending in a click`() {
        assertTrue(abs(pcm.last().toInt()) < 200)
    }

    private fun crossings(part: ShortArray) =
        part.toList().zipWithNext().count { (a, b) -> (a < 0) != (b < 0) }
}
