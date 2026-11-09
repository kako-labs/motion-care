package dev.motioncare.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
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
                    onRecordingStarted = {
                        ContextCompat.startForegroundService(this, Intent(this, TripRecordingService::class.java))
                    },
                )
            }
        }
    }
}
