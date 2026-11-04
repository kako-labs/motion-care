package dev.motioncare.feature.driving.guidance

import dev.motioncare.core.model.DrivingPattern
import org.junit.Assert.*
import org.junit.Test

class GuidanceCopyTest {
    /** Counts what the driver reads. NHTSA excludes punctuation; spaces stay in to be conservative. */
    private fun readLength(text: String) = text.count { it.isLetterOrDigit() || it.isWhitespace() }

    @Test fun `every card fits the 30 character limit with its title`() {
        DrivingPattern.entries.forEach { pattern ->
            val length = readLength(GUIDANCE_TITLE) + readLength(guidanceCopy(pattern))
            assertTrue("$pattern is $length characters", length <= 30)
        }
    }

    @Test fun `every pattern has its own message`() {
        val copies = DrivingPattern.entries.map(::guidanceCopy)
        assertEquals(copies.size, copies.toSet().size)
    }

    @Test fun `each card recommends an action for the pattern that repeated`() {
        val subject = mapOf(
            DrivingPattern.RAPID_START to "출발",
            DrivingPattern.HARD_STOP to "정지",
            DrivingPattern.REPEATED_SPEED_CHANGE to "앞차",
            DrivingPattern.FAST_TURN to "커브",
            DrivingPattern.REPEATED_SWAY to "핸들",
        )
        DrivingPattern.entries.forEach { pattern ->
            val copy = guidanceCopy(pattern)
            assertTrue(copy, copy.endsWith("보세요"))
            assertTrue("$pattern says: $copy", subject.getValue(pattern) in copy)
        }
    }

    @Test fun `cards never give orders or medical claims`() {
        DrivingPattern.entries.forEach { pattern ->
            val copy = guidanceCopy(pattern)
            listOf("하세요", "마세요", "멀미", "위험", "운전자", "습관").forEach { word ->
                assertFalse("$copy contains $word", word in copy)
            }
        }
    }
}
