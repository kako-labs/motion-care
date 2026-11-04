package dev.motioncare.data.trip.guidance

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoredGuidanceSettingsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun `guidance starts on`() {
        assertTrue(StoredGuidanceSettings(context).enabled.value)
    }

    @Test fun `turning guidance off is kept for the next launch`() {
        val first = StoredGuidanceSettings(context)
        first.setEnabled(false)
        assertFalse(first.enabled.value)
        assertFalse(StoredGuidanceSettings(context).enabled.value)
    }
}
