package dev.motioncare.app

import dev.motioncare.core.model.TripId
import dev.motioncare.data.environment.EnvironmentRepository
import dev.motioncare.data.profile.ProfileRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps who sat where and the environment at the start of a trip. The home screen and the recording service may
 * both prepare the same trip, so the lock and the checks keep it to once.
 */
class TripPreparation(private val profiles: ProfileRepository, private val environment: EnvironmentRepository) {
    private val lock = Mutex()

    // Either part may fail without blocking the trip or the other part.
    suspend fun prepare(tripId: TripId) = withContext(NonCancellable) {
        lock.withLock {
            runCatching {
                profiles.initialize()
                if (profiles.participants.value.none { it.tripId == tripId }) profiles.assignCurrentSeating(tripId)
            }
            runCatching {
                if (environment.history(tripId).isEmpty()) environment.capture(tripId)
            }
        }
    }
}
