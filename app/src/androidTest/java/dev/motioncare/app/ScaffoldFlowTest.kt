package dev.motioncare.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ScaffoldFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun click(text: String) {
        awaitText(text)
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    @Test fun profileScreenReadsTheProfileStoreAndReturnsHome() {
        click("프로필 연결 자리")
        awaitText("등록된 사람이 없습니다.")
        click("돌아가기")
        awaitText("샘플 주행 시작")
    }

    @Test fun sampleTripSurvivesRecreationAndFeedbackReturnsToItsDetail() {
        click("샘플 주행 시작")
        for (version in 1..3) {
            click("샘플 신호 추가")
            awaitText("원본 버전: $version")
        }
        compose.activityRule.scenario.recreate()
        awaitText("원본 버전: 3")
        click("샘플 주행 종료")
        click("주행 상세")
        awaitText("샘플 3개")
        click("평가 연결 자리")
        awaitText("연결된 주행:")
        click("돌아가기")
        awaitText("샘플 3개")
        compose.onNodeWithText("출처: SYNTHETIC · COMPLETED").assertExists()
    }
}
