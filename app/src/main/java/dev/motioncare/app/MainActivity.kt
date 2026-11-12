package dev.motioncare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.motioncare.core.ui.MotionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as MotionCareApplication).graph
        setContent {
            MotionTheme {
                MainHost(
                    trips = graph.trips,
                    profiles = graph.profiles,
                    environment = graph.environment,
                    vehicleConnected = graph.vehicleConnected,
                    guidance = graph.guidance,
                    startRecordingService = { startRecordingService(this) },
                    autoRecord = graph.autoRecord,
                    preparation = graph.preparation,
                )
            }
        }
    }

    // The service ends with its process; opening the app starts watching the gear again.
    override fun onStart() {
        super.onStart()
        val graph = (application as MotionCareApplication).graph
        if (graph.autoRecord?.enabled?.value == true && speedPermissionGranted(this)) startRecordingService(this)
    }
}
