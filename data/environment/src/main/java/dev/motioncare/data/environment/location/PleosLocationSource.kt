package dev.motioncare.data.environment.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.motioncare.core.model.MissingReason
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Environment"
private const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
private const val CALL_TIMEOUT_MS = 3_000L
private const val CONNECT_WAIT_MS = 1_000L
private const val CONNECT_ATTEMPTS = 5

/**
 * The Pleos FusedLocation SDK. Its permission is dangerous, so the app asks for it at trip start.
 * The last known position is used as is; the car keeps it current while it drives.
 */
internal class PleosLocationSource(private val context: Context) : LocationSource {
    private val mutex = Mutex()
    private var fused: FusedLocation? = null

    override suspend fun current(): Position = mutex.withLock {
        if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return Position.Missing(MissingReason.NO_PERMISSION)
        }
        val location = fused ?: FusedLocation(context).also { it.initialize(); fused = it }
        // Only checks that the location service is installed, so it is safe before the SDK has bound.
        if (!location.isSupported()) return Position.Unsupported
        repeat(CONNECT_ATTEMPTS) { attempt ->
            when (val result = lastKnown(location)) {
                is LastKnown.Found -> return Position.Found(result.data.latitude, result.data.longitude)
                LastKnown.Missing -> return Position.Missing(MissingReason.NO_LOCATION)
                LastKnown.NotBound -> if (attempt < CONNECT_ATTEMPTS - 1) delay(CONNECT_WAIT_MS)
            }
        }
        Position.Missing(MissingReason.NO_LOCATION)
    }

    private suspend fun lastKnown(location: FusedLocation): LastKnown =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : FusedLocationListener {
                    override fun onChanged(location: LocationData?) {
                        if (continuation.isActive) continuation.resume(location?.let(LastKnown::Found) ?: LastKnown.Missing)
                    }
                    override fun onError(exception: Exception) {
                        Log.w(TAG, "Location error", exception)
                        if (continuation.isActive) continuation.resume(LastKnown.Missing)
                    }
                }
                try {
                    location.getLastKnownLocation(listener)
                } catch (error: Exception) {
                    // The SDK throws until its service connection is ready.
                    if (continuation.isActive) continuation.resume(LastKnown.NotBound)
                    Log.d(TAG, "Location service not ready", error)
                }
            }
        } ?: LastKnown.Missing

    private sealed interface LastKnown {
        data class Found(val data: LocationData) : LastKnown
        data object Missing : LastKnown
        data object NotBound : LastKnown
    }
}
