package dev.motioncare.data.environment.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.MissingReason
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Environment"
private const val CALL_TIMEOUT_MS = 3_000L
private const val CONNECT_WAIT_MS = 1_000L
private const val CONNECT_ATTEMPTS = 5

/**
 * Outside temperature from CarPropertyManager (android.car.permission.CAR_EXTERIOR_ENVIRONMENT, normal) and
 * cabin climate from the Vehicle SDK (pleos.car.permission.CAR_CLIMATE, normal). CarPropertyManager's own
 * climate properties need signature|privileged permissions. Load only on automotive devices.
 */
internal class CarClimateSource(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ClimateSource {
    private val mutex = Mutex()
    private var car: Car? = null
    private var vehicle: Vehicle? = null
    private var bound = false

    override suspend fun read(): ClimateReadings = mutex.withLock {
        ClimateReadings(
            // Binder calls; kept off the main thread.
            outside = withContext(io) { outside() },
            // The whole-cabin area when the car offers one, else the first area.
            cabin = areaReading(
                areas = { hvac, ok, fail ->
                    hvac.checkHvacTemperatureCurrentCapabilities(
                        { config: CapabilityConfig, areas: List<SeatArea> -> ok(readable(config, areas)) }, fail)
                },
                pick = { areas -> areas.maxByOrNull { it.values.size } },
                value = { hvac, area, ok, fail ->
                    hvac.getHvacTemperatureCurrent(area, TemperatureUnit.CELSIUS, { ok(celsiusOf(it)) }, fail)
                },
            ),
            // Set temperatures are per seat; the driver's seat stands for the car.
            cabinSet = areaReading(
                areas = { hvac, ok, fail ->
                    hvac.checkHvacTemperatureCapabilities(
                        { config: CapabilityConfig, areas: Map<SeatArea, *> -> ok(readable(config, areas.keys.toList())) }, fail)
                },
                pick = { areas -> areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: areas.firstOrNull() },
                value = { hvac, area, ok, fail ->
                    hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, { ok(celsiusOf(it)) }, fail)
                },
            ),
            recirculation = areaReading(
                areas = { hvac, ok, fail ->
                    hvac.checkHvacRecirculationCapabilities(
                        { config: CapabilityConfig, areas: List<SeatArea> -> ok(readable(config, areas)) }, fail)
                },
                pick = { it.firstOrNull() },
                value = { hvac, area, ok, fail -> hvac.getHvacRecirculationState(area, ok, fail) },
            ),
            airCleaner = areaReading(
                areas = { hvac, ok, fail ->
                    hvac.checkHvacAirCleanerCapabilities(
                        { config: CapabilityConfig, areas: List<SeatArea> -> ok(readable(config, areas)) }, fail)
                },
                pick = { it.firstOrNull() },
                value = { hvac, area, ok, fail -> hvac.getHvacAirCleanerState(area, ok, fail) },
            ),
        )
    }

    private fun outside(): EnvironmentReading<Double> = try {
        // A Car without a lifecycle listener kills the app when the car service crashes. This one reconnects by
        // itself; until then getCarManager returns null and the reading is missing.
        val connected = car ?: Car.createCar(context, null, CALL_TIMEOUT_MS) { _, _ -> }.also { car = it }
        val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        if (manager.getCarPropertyConfig(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE) == null) {
            unsupported()
        } else {
            val value = manager.getProperty<Float>(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, 0)
            val celsius = celsiusOf(value.value)
            if (value.status != CarPropertyValue.STATUS_AVAILABLE || celsius == null) missing()
            else available(celsius, epochOf(value.timestamp))
        }
    } catch (error: Exception) {
        Log.w(TAG, "Outside temperature not read", error)
        missing()
    }

    private fun epochOf(elapsedNanos: Long): Long {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - elapsedNanos) / 1_000_000
        return clock() - ageMs.coerceAtLeast(0)
    }

    private suspend fun <T : Any> areaReading(
        areas: (HVAC, (List<SeatArea>) -> Unit, (Exception) -> Unit) -> Unit,
        pick: (List<SeatArea>) -> SeatArea?,
        value: (HVAC, SeatArea, (T?) -> Unit, (Exception) -> Unit) -> Unit,
    ): EnvironmentReading<T> {
        val supported = hvacCall<List<SeatArea>> { hvac, ok, fail -> areas(hvac, ok, fail) }?.getOrNull()
            ?: return missing()
        val area = pick(supported) ?: return unsupported()
        val result = hvacCall<T?> { hvac, ok, fail -> value(hvac, area, ok, fail) } ?: return missing()
        return result.getOrNull()?.let { available(it, clock()) } ?: missing()
    }

    /** Null when the SDK failed or did not answer; a wrapped null when it answered without a value. */
    private suspend fun <T> hvacCall(call: (HVAC, (T) -> Unit, (Exception) -> Unit) -> Unit): Result<T>? {
        val sdk = vehicle ?: Vehicle(context).also { it.initialize(); vehicle = it }
        repeat(CONNECT_ATTEMPTS) { attempt ->
            when (val answer = ask(sdk, call)) {
                is Answer.Value -> { bound = true; return Result.success(answer.value) }
                is Answer.Failed -> { bound = true; Log.w(TAG, "Climate value not read", answer.error); return null }
                // Right after the process starts the first call can wait for the binding and time out without an
                // error; it is asked once more before the value counts as missing.
                Answer.NoAnswer -> if (bound || attempt > 0) return null
                is Answer.NotBound -> {
                    // The SDK binds in the background and throws until then. Wait only before the first answer.
                    if (bound || attempt == CONNECT_ATTEMPTS - 1) {
                        Log.w(TAG, "Vehicle SDK call failed", answer.error)
                        return null
                    }
                    delay(CONNECT_WAIT_MS)
                }
            }
        }
        return null
    }

    private suspend fun <T> ask(sdk: Vehicle, call: (HVAC, (T) -> Unit, (Exception) -> Unit) -> Unit): Answer<T> =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                try {
                    call(sdk.getHVAC(),
                        { if (continuation.isActive) continuation.resume(Answer.Value(it)) },
                        { if (continuation.isActive) continuation.resume(Answer.Failed(it)) })
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resume(Answer.NotBound(error))
                }
            }
        } ?: Answer.NoAnswer

    private sealed interface Answer<out T> {
        data class Value<T>(val value: T) : Answer<T>
        data class Failed(val error: Exception) : Answer<Nothing>
        data class NotBound(val error: Exception) : Answer<Nothing>
        data object NoAnswer : Answer<Nothing>
    }
}

private fun readable(config: CapabilityConfig, areas: List<SeatArea>): List<SeatArea> =
    if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) areas else emptyList()

private fun <T : Any> available(value: T, at: Long) =
    EnvironmentReading(value, Availability.AVAILABLE, EnvironmentSource.VEHICLE, observedAtEpochMs = at)

private fun <T : Any> unsupported() = EnvironmentReading<T>(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE)

private fun <T : Any> missing() = EnvironmentReading<T>(
    null, Availability.UNAVAILABLE, EnvironmentSource.VEHICLE, missingReason = MissingReason.NO_DATA)
