package dev.motioncare.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedPermissionTest {
    @Test fun grantedNeedsNoNotice() {
        assertEquals(SpeedPermissionNotice.NONE,
            speedPermissionNotice(granted = true, rationaleBefore = false, rationaleAfter = false))
    }

    @Test fun firstDenialInThePromptOnlyExplains() {
        assertEquals(SpeedPermissionNotice.DENIED,
            speedPermissionNotice(granted = false, rationaleBefore = false, rationaleAfter = true))
    }

    @Test fun dontAskAgainChosenInThePromptOnlyExplains() {
        assertEquals(SpeedPermissionNotice.DENIED,
            speedPermissionNotice(granted = false, rationaleBefore = true, rationaleAfter = false))
    }

    @Test fun anotherDenialInThePromptOnlyExplains() {
        assertEquals(SpeedPermissionNotice.DENIED,
            speedPermissionNotice(granted = false, rationaleBefore = true, rationaleAfter = true))
    }

    @Test fun noPromptShownPointsToSettings() {
        assertEquals(SpeedPermissionNotice.ASK_IN_SETTINGS,
            speedPermissionNotice(granted = false, rationaleBefore = false, rationaleAfter = false))
    }
}
