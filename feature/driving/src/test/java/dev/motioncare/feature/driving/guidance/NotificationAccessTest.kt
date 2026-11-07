package dev.motioncare.feature.driving.guidance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rationale flags are what shouldShowRequestPermissionRationale returned before and after asking. */
class NotificationAccessTest {
    @Test fun `allowing in the prompt needs no settings`() {
        assertFalse(opensSettingsAfterAsking(granted = true, rationaleBefore = false, rationaleAfter = false))
    }

    @Test fun `denying the first prompt is respected`() {
        assertFalse(opensSettingsAfterAsking(granted = false, rationaleBefore = false, rationaleAfter = true))
    }

    @Test fun `choosing do not ask again in the prompt is respected`() {
        assertFalse(opensSettingsAfterAsking(granted = false, rationaleBefore = true, rationaleAfter = false))
    }

    @Test fun `denying a later prompt is respected`() {
        assertFalse(opensSettingsAfterAsking(granted = false, rationaleBefore = true, rationaleAfter = true))
    }

    @Test fun `settings open when the system no longer shows the prompt`() {
        assertTrue(opensSettingsAfterAsking(granted = false, rationaleBefore = false, rationaleAfter = false))
    }
}
