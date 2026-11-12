package dev.motioncare.data.trip.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import dev.motioncare.data.trip.drive.Gear
import dev.motioncare.data.trip.drive.GearSource
import dev.motioncare.data.trip.drive.gearOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

private const val GEAR_BUFFER = 16

/**
 * Requires android.car.permission.CAR_POWERTRAIN. Load only on automotive devices. The gear is an on-change
 * property, so the current gear comes once on subscribing and again on every change. Restarts like [CarSpeedSource].
 */
internal class CarGearSource(private val context: Context) : GearSource {
    override fun gears(): Flow<Gear> = callbackFlow {
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                trySend(if (value.status == CarPropertyValue.STATUS_AVAILABLE) gearOf(value.value as? Int) else Gear.UNKNOWN)
            }
            override fun onErrorEvent(propertyId: Int, zone: Int) {
                trySend(Gear.UNKNOWN)
            }
        }
        val subscription = RestartingSubscription<CarPropertyManager>(
            subscribe = { it.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) },
            unsubscribe = { it.unregisterCallback(callback) },
            onLost = { trySend(Gear.UNKNOWN) },
            onFailed = { close(it) },
        )
        val car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
            if (ready) subscription.onReady(connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)
            else subscription.onLost()
        }
        awaitClose {
            subscription.close()
            car.disconnect()
        }
    }.buffer(GEAR_BUFFER)
}
