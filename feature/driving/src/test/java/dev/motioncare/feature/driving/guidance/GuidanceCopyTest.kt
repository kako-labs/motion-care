package dev.motioncare.feature.driving.guidance

import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.EnvironmentAdvice
import dev.motioncare.core.model.GuidanceTopic
import org.junit.Assert.*
import org.junit.Test

class GuidanceCopyTest {
    private val topics = DrivingPattern.entries.map { GuidanceTopic.Pattern(it) } +
        EnvironmentAdvice.entries.map { GuidanceTopic.Environment(it) } + GuidanceTopic.LongDrive

    /** Counts what the driver reads. NHTSA excludes punctuation; spaces stay in to be conservative. */
    private fun readLength(text: String) = text.count { it.isLetterOrDigit() || it.isWhitespace() }

    @Test fun `every card fits the 30 character limit with its title`() {
        topics.forEach { topic ->
            val length = readLength(GUIDANCE_TITLE) + readLength(guidanceCopy(topic))
            assertTrue("$topic is $length characters", length <= 30)
        }
    }

    @Test fun `every topic has its own message`() {
        val copies = topics.map(::guidanceCopy)
        assertEquals(copies.size, copies.toSet().size)
    }

    @Test fun `every topic has its own icon`() {
        val icons = topics.map(::guidanceIcon)
        assertEquals(icons.size, icons.toSet().size)
    }

    @Test fun `each card recommends an action for its topic`() {
        val subject = mapOf(
            GuidanceTopic.Pattern(DrivingPattern.RAPID_START) to "출발",
            GuidanceTopic.Pattern(DrivingPattern.HARD_STOP) to "정지",
            GuidanceTopic.Pattern(DrivingPattern.REPEATED_SPEED_CHANGE) to "앞차",
            GuidanceTopic.Pattern(DrivingPattern.FAST_TURN) to "커브",
            GuidanceTopic.Pattern(DrivingPattern.REPEATED_SWAY) to "핸들",
            GuidanceTopic.Environment(EnvironmentAdvice.REDUCE_OUTSIDE_AIR) to "외기 유입을 줄이고",
            GuidanceTopic.Environment(EnvironmentAdvice.TRY_OUTSIDE_AIR) to "외기 모드",
            GuidanceTopic.LongDrive to "쉬어가",
        )
        topics.forEach { topic ->
            val copy = guidanceCopy(topic)
            assertTrue(copy, copy.endsWith("보세요"))
            assertTrue("$topic says: $copy", subject.getValue(topic) in copy)
        }
    }

    @Test fun `cards never give orders or medical claims`() {
        topics.forEach { topic ->
            val copy = guidanceCopy(topic)
            listOf("하세요", "마세요", "멀미", "위험", "운전자", "습관").forEach { word ->
                assertFalse("$copy contains $word", word in copy)
            }
        }
    }
}
