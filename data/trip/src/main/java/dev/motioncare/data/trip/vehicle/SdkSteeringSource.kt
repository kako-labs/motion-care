package dev.motioncare.data.trip.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.content.Context
import android.os.SystemClock
import dev.motioncare.data.trip.internal.SteeringReading
import dev.motioncare.data.trip.internal.SteeringSignalSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

private const val READING_BUFFER = 256
private const val CONNECT_WAIT_MS = 1_000L
private const val CONNECT_ATTEMPTS = 5

/**
 * The Vehicle SDK is the only steering path open to a normal app: CarPropertyManager needs
 * android.car.permission.READ_CAR_STEERING, which is signature|privileged. The SDK needs
 * pleos.car.permission.CAR_DRIVE, declared in this module's manifest. Events arrive only when the angle
 * changes and carry no time. Load only on automotive devices.
 */
internal class SdkSteeringSource(private val context: Context) : SteeringSignalSource {
    override fun steering(): Flow<SteeringReading> = callbackFlow {
        val vehicle = Vehicle(context)
        vehicle.initialize()
        val listener = object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(p0: Float?) {
                steeringReadingOf(SystemClock.elapsedRealtimeNanos(), p0)?.let { trySend(it) }
            }
            override fun onFailed(p0: Exception) {
                close(p0)
            }
        }
        registerWhenConnected(vehicle, listener)
        awaitClose {
            runCatching { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(listener) }
            vehicle.release()
        }
    }.buffer(READING_BUFFER)

    // The SDK binds to its service in the background, and offers no callback for when that finishes.
    private suspend fun registerWhenConnected(vehicle: Vehicle, listener: SteeringWheelAngleListener) {
        repeat(CONNECT_ATTEMPTS - 1) {
            delay(CONNECT_WAIT_MS)
            if (runCatching { vehicle.getSteeringWheel().registerSteeringWheelAngle(listener) }.isSuccess) return
        }
        delay(CONNECT_WAIT_MS)
        vehicle.getSteeringWheel().registerSteeringWheelAngle(listener)
    }
}
