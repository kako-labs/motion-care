package dev.motioncare.feature.history.feedback

import dev.motioncare.core.model.FeedbackReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackReasonsTest {
    @Test fun `a new answer offers feelings instead of the retired cabin choice`() {
        val offered = offeredReasons(emptySet())

        assertFalse(FeedbackReason.CABIN_ENVIRONMENT in offered)
        assertEquals(
            listOf("출발·정차", "반복 가감속", "커브", "더웠어요", "공기가 답답했어요", "잘 모르겠어요"),
            offered.map { it.label() },
        )
    }

    @Test fun `an older answer keeps the retired choice so it can be cleared`() {
        assertTrue(FeedbackReason.CABIN_ENVIRONMENT in offeredReasons(setOf(FeedbackReason.CABIN_ENVIRONMENT)))
    }
}
