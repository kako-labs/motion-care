package dev.motioncare.app

import android.content.Context
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.data.trip.createSampleTripRepository

class AppGraph(context: Context) {
    val trips: TripRepository = createSampleTripRepository(context)
}
