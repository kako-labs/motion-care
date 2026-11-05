package dev.motioncare.data.trip.guidance

import android.content.Context
import androidx.core.content.edit
import dev.motioncare.data.trip.GuidanceSettings
import dev.motioncare.data.trip.GuidanceSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FILE_NAME = "motion-care-guidance"
private const val ENABLED_KEY = "enabled"
private const val SOUND_KEY = "sound"

internal class StoredGuidanceSettings(context: Context) : GuidanceSettings {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getBoolean(ENABLED_KEY, true))
    private val soundState = MutableStateFlow(
        GuidanceSound.entries.firstOrNull { it.name == preferences.getString(SOUND_KEY, null) } ?: GuidanceSound.CHIME)
    override val enabled: StateFlow<Boolean> = state.asStateFlow()
    override val sound: StateFlow<GuidanceSound> = soundState.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(ENABLED_KEY, enabled) }
        state.value = enabled
    }

    override fun setSound(sound: GuidanceSound) {
        preferences.edit { putString(SOUND_KEY, sound.name) }
        soundState.value = sound
    }
}
