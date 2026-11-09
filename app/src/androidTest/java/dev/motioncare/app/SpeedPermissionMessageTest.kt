package dev.motioncare.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val DENIED = "차량 속도 권한이 있어야 운행을 기록할 수 있습니다."
private const val WHERE = "설정 > 권한 > 위치에서 허용하면 기록할 수 있어요"
private const val OPEN = "설정 열기"

/** The speed permission is only asked on automotive devices, so the notice is checked here on its own. */
class SpeedPermissionMessageTest {
    @get:Rule val compose = createComposeRule()

    private var openTapped = 0

    private fun show(notice: SpeedPermissionNotice) = compose.setContent {
        MaterialTheme {
            SpeedPermissionMessage(notice = notice, onOpenSettings = { openTapped += 1 })
        }
    }

    @Test fun aDenialInThePromptOnlyExplains() {
        show(SpeedPermissionNotice.DENIED)
        compose.onNodeWithText(DENIED).assertIsDisplayed()
        compose.onNodeWithText(WHERE).assertDoesNotExist()
        compose.onNodeWithText(OPEN).assertDoesNotExist()
    }

    @Test fun noPromptShowsWhereToAllowAndOpensSettings() {
        show(SpeedPermissionNotice.ASK_IN_SETTINGS)
        compose.onNodeWithText(DENIED).assertIsDisplayed()
        compose.onNodeWithText(WHERE).assertIsDisplayed()
        compose.onNodeWithText(OPEN).performClick()
        assertEquals(1, openTapped)
    }

    @Test fun nothingWhenGranted() {
        show(SpeedPermissionNotice.NONE)
        compose.onNodeWithText(DENIED).assertDoesNotExist()
        compose.onNodeWithText(OPEN).assertDoesNotExist()
    }
}
