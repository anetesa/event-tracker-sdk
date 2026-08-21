package com.eventtracker.demo.ui

import app.cash.turbine.test
import com.eventtracker.demo.data.DemoConfigRepository
import com.eventtracker.sdk.model.EventStatistics
import com.eventtracker.sdk.model.TrackedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {

    // Unconfined, not Standard: the ViewModel launches an unbounded `while (isActive) { delay(3s) }`
    // statistics-polling loop on viewModelScope. With a shared/Standard test scheduler, runTest's
    // end-of-test idle-drain would try to fast-forward through that loop forever and hang. With
    // Unconfined, viewModelScope coroutines run eagerly to their first real suspension point (the
    // delay), then simply park there — invisible to runTest's own, separate scheduler.
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var gateway: FakeSdkGateway
    private lateinit var configRepository: DemoConfigRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        gateway = FakeSdkGateway()
        configRepository = mockk(relaxed = true) {
            every { retentionDays } returns 7
            every { eventLimit } returns 100
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): EventsViewModel {
        val viewModel = EventsViewModel(gateway, configRepository)
        runCurrent() // let init{}'s launched coroutines (stats refresh, event collection) run once
        return viewModel
    }

    @Test
    fun `init tracks a screen_viewed event`() = runTest {
        createViewModel()
        assertTrue(gateway.trackCalls.any { it.name == "screen_viewed" })
    }

    @Test
    fun `initial state seeds config inputs from the persisted repository`() = runTest {
        val viewModel = createViewModel()
        assertEquals("7", viewModel.state.value.retentionDaysInput)
        assertEquals("100", viewModel.state.value.eventLimitInput)
    }

    @Test
    fun `onTrackEventClicked tracks a uniquely named test event`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTrackEventClicked()

        val call = gateway.trackCalls.last()
        assertTrue(call.name.startsWith("test_event_"))
        assertTrue(call.properties.isEmpty())
    }

    @Test
    fun `onTrackWithPropertiesClicked tracks button_click with sample properties`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTrackWithPropertiesClicked()

        val call = gateway.trackCalls.last()
        assertEquals("button_click", call.name)
        assertEquals(mapOf("screen" to "home", "action" to "submit"), call.properties)
    }

    @Test
    fun `onTrack100EventsClicked tracks exactly one hundred events`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTrack100EventsClicked()
        runCurrent()

        assertEquals(100, gateway.trackCalls.count { it.name.startsWith("stress_test_event_") })
    }

    @Test
    fun `onClearAllClicked delegates to the gateway`() = runTest {
        val viewModel = createViewModel()
        viewModel.onClearAllClicked()
        runCurrent()

        assertEquals(1, gateway.clearAllCallCount)
    }

    @Test
    fun `onApplyConfigClicked persists parsed values and pushes a live config update`() = runTest {
        val viewModel = createViewModel()
        viewModel.onRetentionDaysInputChanged("14")
        viewModel.onEventLimitInputChanged("200")

        viewModel.onApplyConfigClicked()

        verify { configRepository.retentionDays = 14 }
        verify { configRepository.eventLimit = 200 }
        assertEquals(FakeSdkGateway.ConfigUpdate(14, 200), gateway.configUpdates.last())
        assertTrue(gateway.trackCalls.any { it.name == "config_updated" })
    }

    @Test
    fun `onApplyConfigClicked falls back to persisted values on invalid input`() = runTest {
        val viewModel = createViewModel()
        viewModel.onRetentionDaysInputChanged("not a number")

        viewModel.onApplyConfigClicked()

        verify { configRepository.retentionDays = 7 } // falls back to the mocked persisted value
    }

    @Test
    fun `event list state reflects what the gateway emits`() = runTest {
        val viewModel = createViewModel()
        val events = listOf(TrackedEvent(id = "1", name = "e", properties = emptyMap(), timestamp = 1L))

        viewModel.state.test {
            skipItems(1) // initial state
            gateway.emitEvents(events)
            runCurrent()
            assertEquals(events, awaitItem().events)
        }
    }

    @Test
    fun `statistics are populated from the gateway on the first refresh tick`() = runTest {
        gateway.statisticsToReturn = EventStatistics(totalCount = 5, todayCount = 2, byDay = emptyList())
        val viewModel = createViewModel()

        assertEquals(5, viewModel.state.value.totalCount)
        assertEquals(2, viewModel.state.value.todayCount)
    }
}
