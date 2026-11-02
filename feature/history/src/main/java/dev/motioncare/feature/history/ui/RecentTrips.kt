package dev.motioncare.feature.history.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.motioncare.core.model.*
import dev.motioncare.core.ui.ScaffoldSection
import java.text.DateFormat
import java.util.Date

@Composable
fun RecentTrips(records: List<TripRecord>, onSelect: (TripId) -> Unit) {
    ScaffoldSection("저장된 샘플 기록") {
        if (records.isEmpty()) Text("아직 기록이 없습니다.")
        records.forEach { trip ->
            TextButton(onClick = { onSelect(trip.id) }) {
                val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(trip.startedAtEpochMs))
                Text("$time · ${trip.status.name} · ${trip.origin.name}")
            }
        }
    }
}
