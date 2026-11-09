package dev.motioncare.feature.history.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.HistoryViewModel

@Composable fun RecentTripsRoute(repository: TripRepository, onSelect: (TripId) -> Unit) {
    val history: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repository) } })
    val state by history.uiState.collectAsStateWithLifecycle()
    RecentTrips(state.records.take(3), onSelect)
}

@Composable fun HistoryListRoute(repository: TripRepository, onBack: () -> Unit, onSelect: (TripId) -> Unit, profiles: ProfileRepository? = null) {
    val history: HistoryViewModel = viewModel(key = "all-history", factory = viewModelFactory { initializer { HistoryViewModel(repository) } })
    val state by history.uiState.collectAsStateWithLifecycle()
    val people = profiles?.people?.collectAsStateWithLifecycle()?.value.orEmpty()
    val links = profiles?.participants?.collectAsStateWithLifecycle()?.value.orEmpty().associateBy { it.tripId }
    var filter by remember { mutableStateOf<PersonId?>(null) }
    val riders: (TripId) -> List<PersonProfile> = { id ->
        val link = links[id]
        listOfNotNull(link?.driverId).plus(link?.passengerIds.orEmpty()).mapNotNull { pid -> people.firstOrNull { it.id == pid } }
    }
    val shown = state.records.filter { record -> filter == null || riders(record.id).any { it.id == filter } }
    // Only this head unit's own records exist; say so, with the span they cover.
    val period = state.records.minOfOrNull { it.startedAtEpochMs }?.let { "이 기기 기록 · ${tripDate(it)}부터 · ${state.records.size}건" }
    MotionCareBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ScreenTitle("전체 운행 기록", period ?: "이 기기 기록", onBack)
            if (people.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterTile(filter == null, { filter = null }) { Text("전체", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                people.forEach { person ->
                    FilterTile(filter == person.id, { filter = if (filter == person.id) null else person.id }) {
                        PersonAvatar(person, Modifier.size(48.dp)); Spacer(Modifier.width(10.dp))
                        Text(person.displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            RecentTrips(shown, onSelect, riders)
        }
    }
}

@Composable private fun FilterTile(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = if (selected) MotionCyan.copy(.1f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MotionCyan.copy(.5f) else MotionLine), modifier = Modifier.heightIn(min = 60.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable fun RecentTrips(records: List<TripRecord>, onSelect: (TripId) -> Unit, riders: (TripId) -> List<PersonProfile> = { emptyList() }) {
    GlassPanel(Modifier.fillMaxWidth()) {
        if (records.isEmpty()) Text("아직 기록이 없습니다.", color = MotionSubtle)
        records.forEach { trip ->
            TextButton(onClick = { onSelect(trip.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(tripTime(trip.startedAtEpochMs), fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    val minutes = trip.endedAtEpochMs?.let { ((it - trip.startedAtEpochMs) / 60_000).coerceAtLeast(0) }
                    Text(when (trip.status) {
                        TripStatus.COMPLETED -> minutes?.let { "${it}분" } ?: "완료"
                        TripStatus.RECORDING -> "● 기록 중"
                        TripStatus.INTERRUPTED -> "중단됨"
                    }, color = MotionSubtle, fontSize = 17.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    riders(trip.id).forEach { PersonAvatar(it, Modifier.size(44.dp)) }
                }
                Spacer(Modifier.width(20.dp))
                Text(if (trip.origin == DataOrigin.VEHICLE) "차량 기록" else "합성 주행", color = MotionCyan)
                Spacer(Modifier.width(12.dp)); Text("›", fontSize = 28.sp)
            }
            HorizontalDivider(color = MotionLine)
        }
    }
}
