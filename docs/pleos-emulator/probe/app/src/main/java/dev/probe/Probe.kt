package dev.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
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
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

private const val TAG = "PROBE"

open class BaseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this)
        label.text = javaClass.simpleName
        label.textSize = 60f
        label.setTextColor(Color.WHITE)
        label.setBackgroundColor(Color.rgb(0, 44, 95))
        label.gravity = Gravity.CENTER
        setContentView(label)
        Log.i(TAG, "${javaClass.simpleName} onCreate")
    }

    override fun onResume() { super.onResume(); Log.i(TAG, "${javaClass.simpleName} onResume") }
    override fun onPause() { super.onPause(); Log.i(TAG, "${javaClass.simpleName} onPause") }
    override fun onStop() { super.onStop(); Log.i(TAG, "${javaClass.simpleName} onStop") }
}

class PlainActivity : BaseActivity()
class DoActivity : BaseActivity()

/** Plain started service without foreground status, to compare background survival. */
class BgService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var count = 0
    private val tick = object : Runnable {
        override fun run() {
            count += 1
            Log.i(TAG, "bg tick $count")
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() { super.onCreate(); Log.i(TAG, "bg onCreate"); handler.post(tick) }
    override fun onDestroy() { Log.i(TAG, "bg onDestroy"); handler.removeCallbacks(tick); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

/** Foreground service that records ticks and vehicle signals and posts alert variants on request. */
class FgService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var count = 0
    private var started = false
    private var nextId = 100
    private var overlay: TextView? = null
    private var car: Car? = null
    private var vehicle: Vehicle? = null
    private var lastSpeedAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            count += 1
            if (count % 5 == 0) Log.i(TAG, "fg tick $count")
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("probe_low", "probe low", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel("probe_high", "probe high", NotificationManager.IMPORTANCE_HIGH))
            val fg = Notification.Builder(this, "probe_low")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("PROBE recording")
                .build()
            startForeground(1, fg)
            Log.i(TAG, "fg started")
            handler.post(tick)
            subscribeCarProperties()
            subscribeVehicleSdk()
        }
        intent?.getStringExtra("act")?.let(::act)
        return START_STICKY
    }

    private fun act(action: String) {
        Log.i(TAG, "act $action")
        when {
            action.startsWith("hun:") -> postAlert(action.removePrefix("hun:"))
            action == "toast" -> Toast.makeText(applicationContext, "PROBE TOAST FROM SERVICE", Toast.LENGTH_LONG).show()
            action == "overlay" -> showOverlay()
            action == "overlay_off" -> hideOverlay()
        }
    }

    private fun postAlert(category: String) {
        val builder = Notification.Builder(this, "probe_high")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("PROBE HUN $category")
            .setContentText("category=$category importance=HIGH")
        when (category) {
            "nav" -> builder.setCategory(Notification.CATEGORY_NAVIGATION)
            "msg" -> builder.setCategory(Notification.CATEGORY_MESSAGE)
            "call" -> builder.setCategory(Notification.CATEGORY_CALL)
            "status" -> builder.setCategory(Notification.CATEGORY_STATUS)
            "reminder" -> builder.setCategory(Notification.CATEGORY_REMINDER)
        }
        getSystemService(NotificationManager::class.java).notify(nextId++, builder.build())
    }

    private fun showOverlay() {
        if (overlay != null) return
        val view = TextView(this)
        view.text = "PROBE OVERLAY"
        view.textSize = 36f
        view.setPadding(40, 20, 40, 20)
        view.setTextColor(Color.BLACK)
        view.setBackgroundColor(Color.rgb(255, 200, 120))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            overlay = view
            Log.i(TAG, "overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "overlay failed: $e")
        }
    }

    private fun hideOverlay() {
        overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }
        overlay = null
    }

    private fun subscribeCarProperties() {
        try {
            val instance = Car.createCar(this)
            car = instance
            val manager = instance.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val callback = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val now = SystemClock.elapsedRealtime()
                    if (value.propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) {
                        Log.i(TAG, "speed ${value.value} status=${value.status} dtMs=${now - lastSpeedAt}")
                        lastSpeedAt = now
                    } else {
                        Log.i(TAG, "prop ${VehiclePropertyIds.toString(value.propertyId)} ${value.value}")
                    }
                }

                override fun onErrorEvent(propertyId: Int, zone: Int) {
                    Log.e(TAG, "prop error ${VehiclePropertyIds.toString(propertyId)} zone=$zone")
                }
            }
            val config = manager.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED)
            Log.i(TAG, "speed config=$config")
            val speedOk = manager.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 10f)
            Log.i(TAG, "speed registered=$speedOk")
            val gearOk = manager.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, 0f)
            Log.i(TAG, "gear registered=$gearOk")
        } catch (e: Exception) {
            Log.e(TAG, "car property failed: $e")
        }
    }

    private fun subscribeVehicleSdk() {
        try {
            val instance = Vehicle(this)
            instance.initialize()
            vehicle = instance
            handler.postDelayed({
                try {
                    instance.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
                        override fun onSteeringWheelAngleUpdated(p0: Float?) { Log.i(TAG, "sdk steering $p0") }
                        override fun onFailed(p0: Exception) { Log.e(TAG, "sdk steering failed $p0") }
                    })
                    instance.getGear().registerGearSelection(object : GearSelectionListener {
                        override fun onSelected(p0: VehicleGear?) { Log.i(TAG, "sdk gear $p0") }
                        override fun onFailed(p0: Exception) { Log.e(TAG, "sdk gear failed $p0") }
                    })
                    instance.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
                        override fun onBrakePedalStatusUpdated(p0: Boolean?) { Log.i(TAG, "sdk brake $p0") }
                        override fun onFailed(p0: Exception) { Log.e(TAG, "sdk brake failed $p0") }
                    })
                    instance.getOdometer().registerTripInstantaneousDrivingDistance(object : DistanceDrivenListener {
                        override fun onDistanceDrivenUpdated(p0: Float?) { Log.i(TAG, "sdk distance $p0") }
                        override fun onFailed(p0: Exception) { Log.e(TAG, "sdk distance failed $p0") }
                    })
                    Log.i(TAG, "sdk registered")
                } catch (e: Exception) {
                    Log.e(TAG, "sdk register failed: $e")
                }
            }, 3000)
        } catch (e: Exception) {
            Log.e(TAG, "sdk init failed: $e")
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "fg onDestroy")
        handler.removeCallbacksAndMessages(null)
        vehicle?.release()
        car?.disconnect()
        hideOverlay()
        super.onDestroy()
    }
}
