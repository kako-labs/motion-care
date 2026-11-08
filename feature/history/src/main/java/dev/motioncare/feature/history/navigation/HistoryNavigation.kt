package dev.motioncare.feature.history.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.TripId
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.advice.AdviceRoute
import dev.motioncare.feature.history.departure.DepartureRoute
import dev.motioncare.feature.history.feedback.FeedbackRoute
import dev.motioncare.feature.history.profile.PersonDetailRoute
import dev.motioncare.feature.history.report.TripDetailRoute
import dev.motioncare.feature.history.ui.HistoryListRoute

object HistoryRoutes {
    /** Opened by the app's home button; shows seating and departure advice. */
    const val PROFILES = "profiles"
    const val RECORDS = "records"
    fun profile(id: PersonId) = "profile/${id.value}"
    fun advice(id: PersonId?) = "advice/${id?.value ?: "none"}"
    fun trip(id: TripId) = "trip/${id.value}"
    fun feedback(id: TripId) = "feedback/${id.value}"
}
private const val TRIP_ID = "tripId"
private const val PERSON_ID = "personId"

fun NavGraphBuilder.historyGraph(
    navigation: NavController,
    trips: TripRepository,
    profiles: ProfileRepository,
    onBackToHome: () -> Unit,
) {
    composable(HistoryRoutes.PROFILES) {
        DepartureRoute(trips, profiles,
            onBack = { navigation.popBackStack() },
            onProfile = { navigation.navigate(HistoryRoutes.profile(it)) },
            onAdvice = { navigation.navigate(HistoryRoutes.advice(it)) },
            onRecords = { navigation.navigate(HistoryRoutes.RECORDS) })
    }
    composable(HistoryRoutes.RECORDS) {
        HistoryListRoute(trips, onBack = { navigation.popBackStack() }, onSelect = { navigation.navigate(HistoryRoutes.trip(it)) }, profiles = profiles)
    }
    composable("profile/{$PERSON_ID}") { entry ->
        val id = PersonId(requireNotNull(entry.arguments?.getString(PERSON_ID)))
        PersonDetailRoute(id, trips, profiles, onBack = { navigation.popBackStack() },
            onAdvice = { navigation.navigate(HistoryRoutes.advice(it)) })
    }
    composable("advice/{$PERSON_ID}") { entry ->
        val raw = entry.arguments?.getString(PERSON_ID)
        AdviceRoute(raw?.takeUnless { it == "none" }?.let(::PersonId), trips, profiles, onBack = { navigation.popBackStack() }, onTrip = { id, event -> navigation.navigate(HistoryRoutes.trip(id) + "?event=$event") })
    }
    composable("trip/{$TRIP_ID}?event={event}", arguments = listOf(navArgument("event") { type = NavType.IntType; defaultValue = 0 })) { entry ->
        val id = TripId(requireNotNull(entry.arguments?.getString(TRIP_ID)))
        TripDetailRoute(id, trips, profiles, onBack = { navigation.popBackStack() }, onBackToHome = onBackToHome,
            onFeedback = { navigation.navigate(HistoryRoutes.feedback(id)) }, initialEvent = entry.arguments?.getInt("event") ?: 0)
    }
    composable("feedback/{$TRIP_ID}") { entry ->
        val id = TripId(requireNotNull(entry.arguments?.getString(TRIP_ID)))
        FeedbackRoute(id, trips, profiles) { navigation.popBackStack() }
    }
}