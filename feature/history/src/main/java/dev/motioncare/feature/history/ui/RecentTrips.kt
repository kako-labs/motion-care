package dev.motioncare.feature.history.ui

import dev.motioncare.core.ui.motionType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.motioncare.core.ui.MotionSpace
import dev.motioncare.core.ui.glass
import androidx.compose.material3.Icon
import dev.motioncare.core.ui.MotionIcons
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
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MotionSpace.Medium),
            verticalArrangement = Arrangement.spacedBy(MotionSpace.Large)) {
            ScreenTitle("전체 운행 기록", period ?: "이 기기 기록", onBack)
            if (people.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MotionSpace.Medium)) {
                FilterTile(filter == null, { filter = null }) { Text("전체", fontSize = motionType.body, fontWeight = FontWeight.Bold) }
                people.forEach { person ->
                    FilterTile(filter == person.id, { filter = if (filter == person.id) null else person.id }) {
                        PersonAvatar(person, Modifier.size(MotionSpace.AvatarSmall)); Spacer(Modifier.width(MotionSpace.Medium))
                        Text(person.displayName, fontSize = motionType.body, fontWeight = FontWeight.Bold)
                    }
                }
            }
            RecentTrips(shown, onSelect, riders)
        }
    }
}

@Composable private fun FilterTile(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = if (selected) MotionSelected else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MotionCyan.copy(.5f) else MotionLine), modifier = Modifier.heightIn(min = MotionSpace.Touch)) {
        Row(Modifier.padding(horizontal = MotionSpace.Large, vertical = MotionSpace.Small), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/**
 * Trips grouped by day, one panel per day, so a row needs only the time. A row spreads its time, length, riders and
 * source across the width as columns instead of pushing them to both ends.
 */
@Composable fun RecentTrips(records: List<TripRecord>, onSelect: (TripId) -> Unit, riders: (TripId) -> List<PersonProfile> = { emptyList() }) {
    if (records.isEmpty()) {
        GlassPanel(Modifier.fillMaxWidth()) { Text("아직 기록이 없습니다.", color = MotionSubtle) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(MotionSpace.Large)) {
        records.groupBy { tripDate(it.startedAtEpochMs) }.forEach { (day, trips) ->
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp)).padding(vertical = MotionSpace.Base)) {
                SectionTitle(day, Modifier.padding(horizontal = MotionSpace.Large, vertical = MotionSpace.Small))
                trips.forEachIndexed { index, trip -> TripRow(trip, riders(trip.id), onSelect, divider = index < trips.lastIndex) }
            }
        }
    }
}

@Composable private fun TripRow(trip: TripRecord, people: List<PersonProfile>, onSelect: (TripId) -> Unit, divider: Boolean) {
    val minutes = trip.endedAtEpochMs?.let { ((it - trip.startedAtEpochMs) / 60_000).coerceAtLeast(0) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MotionSpace.RowSingle)
                .clickable(role = Role.Button) { onSelect(trip.id) }
                .padding(horizontal = MotionSpace.Large, vertical = MotionSpace.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MotionSpace.Large),
        ) {
            Text(tripClock(trip.startedAtEpochMs), Modifier.width(200.dp), fontSize = motionType.body, fontWeight = FontWeight.Bold)
            Text(when (trip.status) {
                TripStatus.COMPLETED -> minutes?.let { if (it == 0L) "1분 미만" else "${it}분" } ?: "완료"
                TripStatus.RECORDING -> "● 기록 중"
                TripStatus.INTERRUPTED -> "중단됨"
            }, Modifier.width(140.dp), color = MotionSubtle, fontSize = motionType.label)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MotionSpace.Base)) {
                if (people.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                        people.forEach { PersonAvatar(it, Modifier.size(MotionSpace.AvatarSmall)) }
                    }
                    Text(people.joinToString(" · ") { it.displayName }, fontSize = motionType.label, color = MotionText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Text("탑승자 미지정", fontSize = motionType.label, color = MotionMuted)
            }
            Text(if (trip.origin == DataOrigin.VEHICLE) "차량 기록" else "합성 주행", color = MotionCyan, fontSize = motionType.label)
            Icon(MotionIcons.ChevronRight, contentDescription = null, tint = MotionSubtle, modifier = Modifier.size(MotionSpace.IconMedium))
        }
        if (divider) HorizontalDivider(Modifier.padding(horizontal = MotionSpace.Large), color = MotionLine)
    }
}
