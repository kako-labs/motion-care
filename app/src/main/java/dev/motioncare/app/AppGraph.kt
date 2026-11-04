package dev.motioncare.app

import android.content.Context
import android.content.pm.PackageManager
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.profile.createProfileRepository
import dev.motioncare.data.trip.GuidanceSettings
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.data.trip.VehicleRecorder
import dev.motioncare.data.trip.createSampleTripRepository
import dev.motioncare.data.trip.createVehicleTripData

class AppGraph(context: Context) {
    val vehicleConnected: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    private val vehicle = if (vehicleConnected) createVehicleTripData(context) else null
    val trips: TripRepository = vehicle?.trips ?: createSampleTripRepository(context)
    val recorder: VehicleRecorder? = vehicle?.recorder
    val guidance: GuidanceSettings? = vehicle?.guidance
    val profiles: ProfileRepository = createProfileRepository(context)
}
