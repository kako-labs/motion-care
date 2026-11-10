package dev.motioncare.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import dev.motioncare.feature.history.demo.HistoryDemoActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Debug replay regression. Reuses the feature's debug repositories, never AppGraph. */
class HistoryDemoReadinessTest {
    @get:Rule val compose = createAndroidComposeRule<HistoryDemoActivity>()
    private fun click(text: String, substring: Boolean = false) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onAllNodesWithText(text, substring).onFirst()
        val scrollable = hasText(text, substring = substring) and hasAnyAncestor(hasScrollAction())
        if (compose.onAllNodes(scrollable).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        node.performClick()
    }
    private fun scenario(extra: String) {
        compose.runOnUiThread { compose.activity.intent.putExtra(extra, true) }
        compose.activityRule.scenario.recreate()
    }

    @Test fun selectedObservationOpensTheSameTripAndEvent() {
        click("프로필")
        click("자세히")
        val thirdEvent = hasText("3 ·", substring = true) and hasClickAction()
        compose.waitUntil(20_000) { compose.onAllNodes(thirdEvent).fetchSemanticsNodes().isNotEmpty() }
        // Scroll the outer page first: the chip's nearest scroll parent only moves horizontally.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(thirdEvent)
        val event = compose.onAllNodes(thirdEvent).onFirst()
        event.performScrollTo()
        event.performClick()
        val selected = hasText("정차 직전 큰 감속 3 ·", substring = true)
        assertTrue(compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 80),
            compose.onAllNodes(selected).fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(selected)
        compose.onNode(selected).assertIsDisplayed()
        val expectedEvent = compose.onNode(selected).fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString("") { it.text }
        captureReadinessScreen("08-demo-selected-event")
        click("운행 상세에서 보기")
        compose.onNodeWithText("운행 상세").assertIsDisplayed()
        compose.onNodeWithText("완료된 운행").assertExists()
        compose.onAllNodesWithText("11월 4일", substring = true).onFirst().assertExists()
        try {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(expectedEvent))
            compose.onNodeWithText(expectedEvent).assertIsDisplayed()
            captureReadinessScreen("09-demo-same-event-detail")
        } catch (error: AssertionError) {
            throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 80), error)
        }
    }

    @Test fun ratingUsesExistingSaveAndSurvivesRecreation() {
        val preferences = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("history_b_debug_only", Context.MODE_PRIVATE)
        val existed = preferences.contains("rating-j1")
        val before = preferences.getInt("rating-j1", 4)
        val rating = if (before == 3) 4 else 3
        val expected = if (rating == 3) "보통이었어요" else "편안했어요"
        try {
            click("완료 평가")
            val star = compose.onNodeWithContentDescription("${rating}점")
            if (!star.isDisplayed()) star.performScrollTo()
            star.performClick()
            click("나의 응답 남기기")
            compose.waitUntil(20_000) { preferences.getInt("rating-j1", before) == rating }
            click("완료 평가")
            compose.onNodeWithText(expected).assertExists()
            compose.activityRule.scenario.recreate()
            click("완료 평가")
            compose.onNodeWithText(expected).assertExists()
        } finally {
            val editor = preferences.edit()
            if (existed) editor.putInt("rating-j1", before) else editor.remove("rating-j1")
            check(editor.commit())
        }
    }

    @Test fun skippingAnUnansweredTripDoesNotCreateARating() {
        scenario("unanswered")
        click("완료 평가")
        compose.onNodeWithText("이동은 어땠나요?").assertExists()
        click("건너뛰기")
        click("완료 평가")
        compose.onNodeWithText("이동은 어땠나요?").assertExists()
    }

    @Test fun noHistoryStaysEmptyAndInterruptedStatusRemainsVisible() {
        scenario("empty")
        click("운행 기록")
        compose.onNodeWithText("아직 기록이 없습니다.").assertExists()
        compose.runOnUiThread { compose.activity.intent.removeExtra("empty"); compose.activity.intent.putExtra("interrupted", true) }
        compose.activityRule.scenario.recreate()
        click("운행 기록")
        click("11월 4일", substring = true)
        compose.onNodeWithText("중단된 운행").assertExists()
    }

    @Test fun rearSeatsHaveSeparateTouchTargetsAndRatingsStayLarge() {
        val left = compose.onNodeWithContentDescription("뒤 왼쪽", substring = true)
        if (!left.isDisplayed()) left.performScrollTo()
        val first = left.fetchSemanticsNode().boundsInRoot
        val middle = compose.onNodeWithContentDescription("뒤 가운데", substring = true).fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithContentDescription("뒤 오른쪽", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(first.right <= middle.left)
        assertTrue(middle.right <= right.left)
        captureReadinessScreen("07-demo-home")
        click("완료 평가")
        val star = compose.onNodeWithContentDescription("4점")
        if (!star.isDisplayed()) star.performScrollTo()
        assertTrue(star.fetchSemanticsNode().boundsInRoot.width / compose.density.density >= 55.5f)
    }
}
