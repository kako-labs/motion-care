package dev.motioncare.app

import android.content.Context
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.profile.createProfileRepository
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.data.trip.createSampleTripRepository

class AppGraph(context: Context) {
    val trips: TripRepository = createSampleTripRepository(context)
    val profiles: ProfileRepository = createProfileRepository(context)
}
