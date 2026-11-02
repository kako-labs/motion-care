package dev.motioncare.feature.driving

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
class DrivingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class FakeRepository : TripRepository {
        override val records = MutableStateFlow<List<TripRecord>>(emptyList())
        var failInit = false
        var starts = 0
        override suspend fun initialize() { check(!failInit) }
        override suspend fun get(id: TripId): TripSnapshot? = null
        override suspend fun start(vehicleId: VehicleId?): CommandResult {
            starts++
            delay(100)
            return CommandResult.Success(TripId("sample"))
        }
        override suspend fun captureSample() = CommandResult.Rejected("지원하지 않는 입력")
        override suspend fun finish() = CommandResult.Success(TripId("sample"))
    }

    @Test fun `failed initialization is retryable and commands do not race`() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failInit = true }
        val model = DrivingViewModel(repository)
        try {
            runCurrent()
            assertFalse(model.uiState.value.ready)
            assertNotNull(model.uiState.value.error)
            repository.failInit = false
            model.initialize()
            runCurrent()
            assertTrue(model.uiState.value.ready)
            model.start(); model.start()
            advanceUntilIdle()
            assertEquals(1, repository.starts)
            assertFalse(model.uiState.value.busy)
            model.capture()
            runCurrent()
            assertEquals("지원하지 않는 입력", model.uiState.value.error)
        } finally { model.viewModelScope.cancel() }
    }
}
