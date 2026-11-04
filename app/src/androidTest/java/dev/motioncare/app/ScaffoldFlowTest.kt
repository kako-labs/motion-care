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

    @Test fun departureScreenSurvivesActivityRecreationWithoutAStartButton() {
        awaitText("출발 전 안내")
        compose.onAllNodesWithText("운행 시작", substring = true).assertCountEquals(0)

        compose.activityRule.scenario.recreate()

        awaitText("출발 전 안내")
        compose.onNodeWithText("전체 기록").assertExists()
    }

    @Test fun aPersonCanBeAddedAndOpenedAsAnIndividualProfile() {
        awaitText("출발 전 안내")
        click("＋ 사람 추가")
        compose.onNode(hasSetTextAction()).performTextInput("테스트 운전자")
        click("추가")
        awaitText("테스트 운전자")

        val people = compose.onAllNodesWithText("테스트 운전자").fetchSemanticsNodes()
        compose.onAllNodesWithText("테스트 운전자")[people.lastIndex].performClick()

        awaitText("운전할 때")
        compose.onNodeWithText("동승할 때").assertExists()
        compose.onNodeWithText("최근 운행").assertExists()
    }
}