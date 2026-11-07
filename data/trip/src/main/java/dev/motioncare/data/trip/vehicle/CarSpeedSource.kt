package dev.motioncare.data.trip.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.SystemClock
import dev.motioncare.data.trip.internal.SpeedReading
import dev.motioncare.data.trip.internal.VehicleSignalSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

private const val SPEED_RATE_HZ = 10f
private const val READING_BUFFER = 256

/**
 * Requires android.car.permission.CAR_SPEED. Load only on automotive devices.
 *
 * A Car created without a lifecycle listener kills the whole app when the car service crashes, so this one
 * passes a listener: the speed is marked unavailable while the service is gone and subscribed again when it is back.
 */
internal class CarSpeedSource(private val context: Context) : VehicleSignalSource {
    override fun speeds(): Flow<SpeedReading> = callbackFlow {
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                trySend(speedReadingOf(value.timestamp, value.status == CarPropertyValue.STATUS_AVAILABLE,
                    (value.value as? Number)?.toDouble()))
            }
            override fun onErrorEvent(propertyId: Int, zone: Int) {
                trySend(unavailableNow())
            }
        }
        val subscription = RestartingSubscription<CarPropertyManager>(
            subscribe = { it.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, SPEED_RATE_HZ) },
            unsubscribe = { it.unregisterCallback(callback) },
            onLost = { trySend(unavailableNow()) },
            onFailed = { close(it) },
        )
        // The listener runs on the main thread, as does this flow when the recording service collects it.
        val car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
            if (ready) subscription.onReady(connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)
            else subscription.onLost()
        }
        awaitClose {
            subscription.close()
            car.disconnect()
        }
    }.buffer(READING_BUFFER)

    private fun unavailableNow() =
        speedReadingOf(SystemClock.elapsedRealtimeNanos(), available = false, metersPerSecond = null)
}
