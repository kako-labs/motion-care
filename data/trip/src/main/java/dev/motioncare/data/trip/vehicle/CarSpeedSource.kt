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

/** Requires android.car.permission.CAR_SPEED. Load only on automotive devices. */
internal class CarSpeedSource(private val context: Context) : VehicleSignalSource {
    override fun speeds(): Flow<SpeedReading> = callbackFlow {
        val car = Car.createCar(context)
        val manager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                trySend(speedReadingOf(value.timestamp, value.status == CarPropertyValue.STATUS_AVAILABLE,
                    (value.value as? Number)?.toDouble()))
            }
            override fun onErrorEvent(propertyId: Int, zone: Int) {
                trySend(speedReadingOf(SystemClock.elapsedRealtimeNanos(), available = false, metersPerSecond = null))
            }
        }
        check(manager.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, SPEED_RATE_HZ)) {
            "Vehicle speed subscription failed"
        }
        awaitClose {
            manager.unregisterCallback(callback)
            car.disconnect()
        }
    }.buffer(READING_BUFFER)
}
