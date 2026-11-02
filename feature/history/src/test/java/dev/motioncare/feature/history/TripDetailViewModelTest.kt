package dev.motioncare.feature.history

import androidx.lifecycle.viewModelScope
import dev.motioncare.core.model.*
import dev.motioncare.data.trip.TripRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private class FakeRepository : TripRepository {
        override val records = MutableStateFlow<List<TripRecord>>(emptyList())
        var failRead = false
        override suspend fun initialize() = Unit
        override suspend fun get(id: TripId): TripSnapshot? {
            if (id.value == "slow") withContext(NonCancellable) { delay(1000) }
            check(!failRead)
            if (id.value == "missing") return null
            return TripSnapshot(TripRecord(id, 1000, origin = DataOrigin.SYNTHETIC))
        }
        override suspend fun start(vehicleId: VehicleId?) = error("Not used by history")
        override suspend fun captureSample() = error("Not used by history")
        override suspend fun finish() = error("Not used by history")
    }
    @Test fun `new selection wins even if the older read ignores cancellation`() = runTest(dispatcher) {
        val model = TripDetailViewModel(FakeRepository())
        try {
            model.load(TripId("slow")); runCurrent()
            model.load(TripId("new")); advanceUntilIdle()
            val state = model.uiState.value as TripDetailUiState.Content
            assertEquals("new", state.snapshot.trip.id.value)
        } finally { model.viewModelScope.cancel() }
    }
    @Test fun `failure retry and missing record have distinct states`() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failRead = true }
        val model = TripDetailViewModel(repository)
        try {
            model.load(TripId("one")); runCurrent()
            assertEquals(TripDetailUiState.Error, model.uiState.value)
            repository.failRead = false
            model.retry(); runCurrent()
            assertTrue(model.uiState.value is TripDetailUiState.Content)
            model.load(TripId("missing")); runCurrent()
            assertEquals(TripDetailUiState.NotFound, model.uiState.value)
        } finally { model.viewModelScope.cancel() }
    }
}
