package dev.motioncare.feature.history.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.motioncare.core.model.TripId
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.feedback.FeedbackSlot
import dev.motioncare.feature.history.profile.ProfileRoute
import dev.motioncare.feature.history.report.TripDetailRoute

object HistoryRoutes {
    const val PROFILES = "profiles"
    fun trip(id: TripId) = "trip/${id.value}"
    fun feedback(id: TripId) = "feedback/${id.value}"
}

private const val TRIP_ID = "tripId"

/** Destinations owned by this feature. Add new history screens here; the app only calls this once. */
fun NavGraphBuilder.historyGraph(
    navigation: NavController,
    trips: TripRepository,
    profiles: ProfileRepository,
    onBackToHome: () -> Unit,
) {
    composable(HistoryRoutes.PROFILES) {
        ProfileRoute(profiles, onBack = { navigation.popBackStack() })
    }
    composable("trip/{$TRIP_ID}") { entry ->
        val id = TripId(requireNotNull(entry.arguments?.getString(TRIP_ID)))
        TripDetailRoute(
            id = id,
            repository = trips,
            onBack = { navigation.popBackStack() },
            onBackToHome = onBackToHome,
            onFeedback = { navigation.navigate(HistoryRoutes.feedback(id)) },
        )
    }
    composable("feedback/{$TRIP_ID}") { entry ->
        FeedbackSlot(entry.arguments?.getString(TRIP_ID)) { navigation.popBackStack() }
    }
}
