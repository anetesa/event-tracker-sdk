package com.eventtracker.demo.ui

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.eventtracker.demo.data.DemoConfigRepository
import com.eventtracker.sdk.model.EventStatistics
import com.eventtracker.sdk.model.TrackedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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

/**
 * Подход: plain JVM-юнит-тест ViewModel с [UnconfinedTestDispatcher], фейком [FakeSdkGateway]
 * (см. его doc-комментарий, почему это фейк, а не мок) и `mockk(relaxed = true)` для
 * `DemoConfigRepository` (простой геттер/сеттер поверх SharedPreferences — Robolectric ради
 * него не нужен, достаточно двух `every {}` и `verify {}` на присвоение). Ни Robolectric, ни
 * реальный `EventTrackerSDK` здесь не задействованы вовсе: [EventsViewModel] зависит только от
 * интерфейсов ([SdkGateway], `DemoConfigRepository`), поэтому Android SDK/Room/WorkManager до
 * этого теста просто не доходят.
 *
 * ViewModel запускает в `viewModelScope` неограниченный цикл
 * `while (isActive) { refreshStatistics(); delay(3s) }` для секции статистики.
 * `Dispatchers.setMain(aTestDispatcher)` связывает понятие "времени" у `Dispatchers.Main` с тем,
 * какой `runTest` сейчас активен (это осознанная интеграция kotlinx-coroutines-test, она не
 * привязана к структуре parent/child job) — поэтому без явного сигнала остановки финальный
 * "холостой прогон" `runTest` пытается бесконечно проматывать этот бесконечный цикл вперёд, и
 * тест зависает, утилизируя одно ядро CPU. Решение, используемое по всему файлу: явно отменять
 * `viewModel.viewModelScope` последним шагом каждого теста — точно так же, как это делает
 * реальный жизненный цикл ViewModel через `onCleared()` при настоящем уничтожении. Это не костыль,
 * а воспроизведение продакшен-поведения внутри теста.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {

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
        runCurrent() // даём один раз отработать запущенным в init{} корутинам (обновление статистики, сбор событий)
        return viewModel
    }

    @Test
    fun `init tracks a screen_viewed event`() = runTest {
        val viewModel = createViewModel()
        assertTrue(gateway.trackCalls.any { it.name == "screen_viewed" })
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `initial state seeds config inputs from the persisted repository`() = runTest {
        val viewModel = createViewModel()
        assertEquals("7", viewModel.state.value.retentionDaysInput)
        assertEquals("100", viewModel.state.value.eventLimitInput)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `onTrackEventClicked tracks a uniquely named test event`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTrackEventClicked()

        val call = gateway.trackCalls.last()
        assertTrue(call.name.startsWith("test_event_"))
        assertTrue(call.properties.isEmpty())
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `onTrackWithPropertiesClicked tracks button_click with sample properties`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTrackWithPropertiesClicked()

        val call = gateway.trackCalls.last()
        assertEquals("button_click", call.name)
        assertEquals(mapOf("screen" to "home", "action" to "submit"), call.properties)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `onTrack100EventsClicked tracks exactly one hundred events`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTrack100EventsClicked()
        runCurrent()

        assertEquals(100, gateway.trackCalls.count { it.name.startsWith("stress_test_event_") })
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `onClearAllClicked delegates to the gateway`() = runTest {
        val viewModel = createViewModel()
        viewModel.onClearAllClicked()
        runCurrent()

        assertEquals(1, gateway.clearAllCallCount)
        viewModel.viewModelScope.cancel()
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
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `onApplyConfigClicked falls back to persisted values on invalid input`() = runTest {
        val viewModel = createViewModel()
        viewModel.onRetentionDaysInputChanged("not a number")

        viewModel.onApplyConfigClicked()

        verify { configRepository.retentionDays = 7 } // откатывается к замоканному сохранённому значению
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `event list state reflects what the gateway emits`() = runTest {
        val viewModel = createViewModel()
        val events = listOf(TrackedEvent(id = "1", name = "e", properties = emptyMap(), timestamp = 1L))

        viewModel.state.test {
            skipItems(1) // начальное состояние
            gateway.emitEvents(events)
            runCurrent()
            assertEquals(events, awaitItem().events)
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `statistics are populated from the gateway on the first refresh tick`() = runTest {
        gateway.statisticsToReturn = EventStatistics(totalCount = 5, todayCount = 2, byDay = emptyList())
        val viewModel = createViewModel()

        assertEquals(5, viewModel.state.value.totalCount)
        assertEquals(2, viewModel.state.value.todayCount)
        viewModel.viewModelScope.cancel()
    }
}
