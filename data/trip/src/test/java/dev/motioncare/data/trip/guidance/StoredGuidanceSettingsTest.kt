package dev.motioncare.data.trip.guidance

import android.content.Context
import dev.motioncare.data.trip.GuidanceSound
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

    @Test fun `the chime is the default sound`() {
        assertEquals(GuidanceSound.CHIME, StoredGuidanceSettings(context).sound.value)
    }

    @Test fun `the chosen sound is kept for the next launch`() {
        val first = StoredGuidanceSettings(context)
        first.setSound(GuidanceSound.VOICE)
        assertEquals(GuidanceSound.VOICE, first.sound.value)
        assertEquals(GuidanceSound.VOICE, StoredGuidanceSettings(context).sound.value)
    }

    @Test fun `an unknown stored sound falls back to the chime`() {
        context.getSharedPreferences("motion-care-guidance", Context.MODE_PRIVATE)
            .edit().putString("sound", "SIREN").commit()
        assertEquals(GuidanceSound.CHIME, StoredGuidanceSettings(context).sound.value)
    }

    @Test fun `choosing a sound leaves the on and off setting alone`() {
        val settings = StoredGuidanceSettings(context)
        settings.setEnabled(false)
        settings.setSound(GuidanceSound.VOICE)
        assertFalse(StoredGuidanceSettings(context).enabled.value)
    }
}
