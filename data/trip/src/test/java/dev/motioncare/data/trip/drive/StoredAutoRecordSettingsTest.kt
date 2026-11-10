package dev.motioncare.data.trip.drive

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StoredAutoRecordSettingsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun `automatic recording starts off`() {
        assertFalse(StoredAutoRecordSettings(context).enabled.value)
    }

    @Test fun `turning it on is kept for the next launch`() {
        val first = StoredAutoRecordSettings(context)
        first.setEnabled(true)
        assertTrue(first.enabled.value)
        assertTrue(StoredAutoRecordSettings(context).enabled.value)
    }
}
