package dev.motioncare.data.trip.drive

import android.content.Context
import androidx.core.content.edit
import dev.motioncare.data.trip.AutoRecordSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FILE_NAME = "motion-care-auto-record"
private const val ENABLED_KEY = "enabled"

internal class StoredAutoRecordSettings(context: Context) : AutoRecordSettings {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getBoolean(ENABLED_KEY, false))
    override val enabled: StateFlow<Boolean> = state.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(ENABLED_KEY, enabled) }
        state.value = enabled
    }
}
