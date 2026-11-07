package dev.motioncare.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.motioncare.data.trip.GuidanceSound
import dev.motioncare.feature.driving.ui.GuidanceSwitch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val NOTICE = "알림이 꺼져 있어 주행 중 안내 카드가 뜨지 않아요"
private const val TURN_ON = "알림 켜기"

/** The switch only appears on automotive devices, so it is checked here on its own. */
class GuidanceSwitchTest {
    @get:Rule val compose = createComposeRule()

    private var turnOnTapped = 0

    private fun show(enabled: Boolean, cardsBlocked: Boolean) = compose.setContent {
        MaterialTheme {
            GuidanceSwitch(
                enabled = enabled,
                sound = GuidanceSound.CHIME,
                cardsBlocked = cardsBlocked,
                onChange = {},
                onSound = {},
                onTurnOnNotifications = { turnOnTapped += 1 },
            )
        }
    }

    @Test fun blockedNotificationsAreExplainedNextToTheSwitch() {
        show(enabled = true, cardsBlocked = true)
        compose.onNodeWithText(NOTICE).assertIsDisplayed()
        compose.onNodeWithText(TURN_ON).performClick()
        assertEquals(1, turnOnTapped)
    }

    @Test fun noNoticeWhileGuidanceIsOff() {
        show(enabled = false, cardsBlocked = true)
        compose.onNodeWithText(NOTICE).assertDoesNotExist()
        compose.onNodeWithText(TURN_ON).assertDoesNotExist()
    }

    @Test fun noNoticeWhileCardsCanShow() {
        show(enabled = true, cardsBlocked = false)
        compose.onNodeWithText(NOTICE).assertDoesNotExist()
        compose.onNodeWithText(TURN_ON).assertDoesNotExist()
    }
}
