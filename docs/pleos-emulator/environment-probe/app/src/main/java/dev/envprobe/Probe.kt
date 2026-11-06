package dev.envprobe

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.HvacTemperatureValueListener
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/*
 * Measures which environment values a normal app can read on the Pleos emulator: outside temperature,
 * climate through the Vehicle SDK, FusedLocation, Android Geocoder and an HTTPS request. Results go to
 * logcat with the ENVPROBE tag. See docs/pleos-emulator/README.md for the commands.
 */
private const val TAG = "ENVPROBE"
private const val AIR_URL =
    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=37.5665&longitude=126.978&current=pm10,pm2_5&timezone=Asia%2FSeoul"

private fun log(message: String) { Log.i(TAG, message) }

class PermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(
            arrayOf("pleos.car.permission.FUSED_LOCATION", "android.car.permission.CAR_EXTERIOR_ENVIRONMENT", "pleos.car.permission.CAR_CLIMATE"),
            1,
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissions.forEachIndexed { index, name -> log("permission result $name=${grantResults[index]}") }
    }
}

class ProbeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var car: Car? = null
    private var vehicle: Vehicle? = null
    private var location: FusedLocation? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("env", "env", NotificationManager.IMPORTANCE_LOW))
            startForeground(1, Notification.Builder(this, "env").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("env").build())
            listOf(
                "android.car.permission.CAR_EXTERIOR_ENVIRONMENT",
                "pleos.car.permission.CAR_CLIMATE",
                "pleos.car.permission.FUSED_LOCATION",
                "android.permission.INTERNET",
            ).forEach { log("checkSelf $it=${checkSelfPermission(it)}") }
            readCarProperties()
            readHvac()
            readLocation()
            readNetwork()
        }
        when (intent?.getStringExtra("act")) {
            "again" -> { readCarPropertyOnce(); readHvacValues(); readLocationOnce(); readNetwork(); readGeocoder() }
        }
        return START_STICKY
    }

    private fun readCarProperties() {
        runCatching {
            val c = Car.createCar(this)
            car = c
            val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val listed = cpm.propertyList.map { VehiclePropertyIds.toString(it.propertyId) }
            log("cpm visible count=${listed.size} list=$listed")
            val config = cpm.getCarPropertyConfig(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE)
            log("ENV_OUTSIDE config=$config")
            readCarPropertyOnce()
            if (config != null) {
                val ok = cpm.registerCallback(
                    object : CarPropertyManager.CarPropertyEventCallback {
                        override fun onChangeEvent(value: CarPropertyValue<*>) {
                            log("ENV_OUTSIDE event value=${value.value} status=${value.status} ts=${value.timestamp}")
                        }
                        override fun onErrorEvent(propId: Int, zone: Int) { log("ENV_OUTSIDE error $propId $zone") }
                    },
                    VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
                    0.1f,
                )
                log("ENV_OUTSIDE registerCallback=$ok")
            }
        }.onFailure { log("cpm failed $it") }
    }

    private fun readCarPropertyOnce() {
        runCatching {
            val cpm = car?.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val v = cpm.getProperty<Float>(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, 0)
            log("ENV_OUTSIDE get value=${v.value} status=${v.status} ts=${v.timestamp}")
        }.onFailure { log("ENV_OUTSIDE get failed $it") }
    }

    private fun readHvac() {
        val v = Vehicle(this)
        vehicle = v
        v.initialize()
        handler.postDelayed({
            runCatching { readHvacCapabilities() }.onFailure { log("hvac capabilities threw $it") }
            runCatching { readHvacValues() }.onFailure { log("hvac values threw $it") }
            runCatching { listenHvac() }.onFailure { log("hvac listen threw $it") }
        }, 3_000)
    }

    private fun readHvacCapabilities() {
        val hvac = vehicle!!.getHVAC()
        fun failed(name: String) = { e: Exception -> log("cap $name failed $e") }
        hvac.checkHvacTemperatureCurrentCapabilities({ c, a -> log("cap tempCurrent access=${c.accessType} areas=${a.map { it.values.toList() }}") }, failed("tempCurrent"))
        hvac.checkHvacTemperatureCapabilities({ c, a -> log("cap temp access=${c.accessType} areas=$a") }, failed("temp"))
        hvac.checkHvacRecirculationCapabilities({ c, a -> log("cap recirc access=${c.accessType} areas=${a.map { it.values.toList() }}") }, failed("recirc"))
        hvac.checkHvacAutoRecirculationCapabilities({ c, a -> log("cap autoRecirc access=${c.accessType} areas=${a.map { it.values.toList() }}") }, failed("autoRecirc"))
        hvac.checkHvacAirCleanerCapabilities({ c, a -> log("cap airCleaner access=${c.accessType} areas=${a.map { it.values.toList() }}") }, failed("airCleaner"))
        hvac.checkHvacFanSpeedCapabilities({ c, a -> log("cap fan access=${c.accessType} areas=$a") }, failed("fan"))
        hvac.checkHvacPowerCapabilities({ c, a -> log("cap power access=${c.accessType} areas=${a.map { it.values.toList() }}") }, failed("power"))
    }

    private fun readHvacValues() {
        val hvac = vehicle!!.getHVAC()
        val cabin = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
        fun failed(name: String) = { e: Exception -> log("$name failed $e") }
        hvac.getHvacRecirculationState(cabin, { log("recirc cabin=$it") }, failed("recirc"))
        hvac.getHvacAutoRecirculationState(cabin, { log("autoRecirc cabin=$it") }, failed("autoRecirc"))
        hvac.getHvacAirCleanerState(cabin, { log("airCleaner cabin=$it") }, failed("airCleaner"))
        hvac.getHvacPowerState(cabin, { log("power cabin=$it") }, failed("power"))
        hvac.getHvacTemperatureCurrent(SeatArea(SeatValue.ROW_1_LEFT), TemperatureUnit.CELSIUS, { log("tempCurrent left=$it") }, failed("tempCurrent left"))
        hvac.getHvacTemperatureCurrent(cabin, TemperatureUnit.CELSIUS, { log("tempCurrent cabin=$it") }, failed("tempCurrent cabin"))
        hvac.checkHvacTemperatureCapabilities({ _, areas ->
            areas.keys.forEach { area ->
                hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, { log("tempSet ${area.values.toList()}=$it") }, failed("tempSet ${area.values.toList()}"))
            }
        }, failed("cap temp"))
        hvac.checkHvacFanSpeedCapabilities({ _, areas ->
            areas.keys.forEach { area ->
                hvac.getHvacFanSpeed(area, { log("fan ${area.values.toList()}=$it") }, failed("fan ${area.values.toList()}"))
            }
        }, failed("cap fan"))
    }

    private fun listenHvac() {
        vehicle!!.getHVAC().registerHvacTemperatureCurrent(
            SeatArea(SeatValue.ROW_1_LEFT),
            object : HvacTemperatureValueListener {
                override fun onHvacTemperatureUpdated(p0: Float?, p1: Float?) { log("tempCurrent event $p0 $p1") }
                override fun onFailed(p0: Exception) { log("tempCurrent event failed $p0") }
            },
        )
    }

    private fun readLocation() {
        val l = FusedLocation(this)
        location = l
        runCatching { l.initialize() }.onFailure { log("location init failed $it") }
        handler.postDelayed({
            readLocationOnce()
            runCatching {
                l.registerFusedLocationCallback(object : FusedLocationListener {
                    override fun onChanged(p0: LocationData?) { log("location event ${describe(p0)}") }
                    override fun onError(p0: Exception) { log("location event error $p0") }
                })
            }.onFailure { log("location register threw $it") }
        }, 3_000)
    }

    private fun readLocationOnce() {
        val l = location ?: return
        runCatching {
            log("location isSupported=${l.isSupported()}")
            l.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(p0: LocationData?) { log("location last ${describe(p0)}") }
                override fun onError(p0: Exception) { log("location last error $p0") }
            })
        }.onFailure { log("location threw $it") }
    }

    private fun describe(d: LocationData?): String =
        if (d == null) "null" else "lat=${d.latitude} lon=${d.longitude} confidence=${d.confidence} ts=${d.timestampMillis} ageMs=${System.currentTimeMillis() - d.timestampMillis}"

    private fun readGeocoder() {
        thread {
            log("geocoder present=${android.location.Geocoder.isPresent()}")
            runCatching {
                @Suppress("DEPRECATION")
                val found = android.location.Geocoder(this, java.util.Locale.KOREA).getFromLocation(37.5665, 126.978, 1)
                log("geocoder result=${found?.firstOrNull()?.getAddressLine(0)}")
            }.onFailure { log("geocoder failed $it") }
        }
    }

    private fun readNetwork() {
        thread {
            val startedAt = System.currentTimeMillis()
            runCatching {
                val connection = URL(AIR_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                val code = connection.responseCode
                val body = connection.inputStream.bufferedReader().readText()
                log("net code=$code ms=${System.currentTimeMillis() - startedAt} body=${body.take(240)}")
            }.onFailure { log("net failed ms=${System.currentTimeMillis() - startedAt} $it") }
        }
    }
}
