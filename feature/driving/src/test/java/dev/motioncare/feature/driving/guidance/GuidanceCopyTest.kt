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

    @Test fun `cards say what was observed and never give orders or medical claims`() {
        DrivingPattern.entries.forEach { pattern ->
            val copy = guidanceCopy(pattern)
            assertTrue(copy, copy.endsWith("있어요"))
            listOf("해보세요", "하세요", "멀미", "위험", "운전자", "습관").forEach { word ->
                assertFalse("$copy contains $word", word in copy)
            }
        }
    }
}
