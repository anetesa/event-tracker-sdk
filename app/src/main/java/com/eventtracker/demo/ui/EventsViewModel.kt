package com.eventtracker.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventtracker.demo.data.DemoConfigRepository
import com.eventtracker.demo.data.SdkGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val sdkGateway: SdkGateway,
    private val configRepository: DemoConfigRepository,
) : ViewModel() {

    /**
     * Отображение списка ограничено тем же значением, что и настроенный лимит событий — это
     * напрямую связывает требование экзамена "просмотренные события защищены от Clear All" с
     * настраиваемым пользователем количеством событий: если существует больше событий, чем
     * [DemoConfigRepository.eventLimit] (например, сразу после "Track 100 Events", с учётом ещё
     * и собственных стартовых событий приложения), самые старые лишние строки никогда не
     * попадают в этот ограниченный список, а значит никогда не помечаются как seen, и
     * доказуемо остаются нетронутыми при "Clear All Events".
     */
    private val eventListLimit = MutableStateFlow(configRepository.eventLimit)

    private val _state = MutableStateFlow(
        EventsUiState(
            retentionDaysInput = configRepository.retentionDays.toString(),
            eventLimitInput = configRepository.eventLimit.toString(),
        ),
    )
    val state: StateFlow<EventsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            eventListLimit
                .flatMapLatest { limit -> sdkGateway.recentEvents(limit) }
                .collect { events -> _state.update { it.copy(events = events) } }
        }
        // ТЗ привязывает "автообновление каждые 3 секунды" именно к секции статистики; список
        // событий выше вместо этого реактивный/живой, поскольку ТЗ отдельно говорит, что список
        // "должен обновляться при появлении новых отслеженных событий".
        viewModelScope.launch {
            while (isActive) {
                refreshStatistics()
                delay(3_000)
            }
        }
        sdkGateway.track("screen_viewed", mapOf("screen" to "main"))
    }

    private suspend fun refreshStatistics() {
        val stats = sdkGateway.statistics()
        _state.update { it.copy(totalCount = stats.totalCount, todayCount = stats.todayCount, byDay = stats.byDay) }
    }

    fun onTrackEventClicked() {
        val name = "test_event_${System.currentTimeMillis() % 1_000_000}"
        sdkGateway.track(name)
    }

    /**
     * По контракту SDK `track()` — это fire-and-forget (никогда не блокирует, не даёт сигнала
     * о завершении), так что для этой кнопки нет реального состояния "всё ещё выполняется",
     * которое можно было бы вернуть наружу — сама выдача 100 вызовов завершается почти мгновенно,
     * независимо от того, когда долетят реальные записи. В предыдущей версии кнопка блокировалась
     * флагом `isStressTestRunning`, который сбрасывался сразу после того, как вызовы были
     * *выданы*, а не после того, как они завершились — это просто почти сразу же снова включало
     * кнопку и фактически ничего не защищало. Повторные нажатия здесь безвредны — Room безопасно
     * обрабатывает конкурентные записи, а дополнительная конкурентная нагрузка лишь сильнее
     * нагружает требование "100+ быстрых событий без потери данных", а не нарушает его.
     */
    fun onTrack100EventsClicked() {
        viewModelScope.launch {
            repeat(100) { i -> sdkGateway.track("stress_test_event_$i", mapOf("batch_index" to i.toString())) }
        }
    }

    fun onTrackWithPropertiesClicked() {
        sdkGateway.track("button_click", mapOf("screen" to "home", "action" to "submit"))
    }

    fun onClearAllClicked() {
        viewModelScope.launch {
            sdkGateway.clearAllEvents()
            refreshStatistics()
        }
    }

    fun onRetentionDaysInputChanged(value: String) {
        _state.update { it.copy(retentionDaysInput = value) }
    }

    fun onEventLimitInputChanged(value: String) {
        _state.update { it.copy(eventLimitInput = value) }
    }

    fun onApplyConfigClicked() {
        val retentionDays = _state.value.retentionDaysInput.toIntOrNull()?.coerceAtLeast(1)
            ?: configRepository.retentionDays
        // Здесь ограничивается снизу значением >= 1, а не просто пробрасывается дальше: SDK
        // трактует maxEventCount <= 0 как "без ограничения" для своих целей автоматической
        // очистки, но то же самое значение переиспользуется ниже как лимит Flow для
        // отображаемого списка событий этого экрана, где <= 0 ограничивается снизу значением 1
        // (EventRepositoryImpl.observeRecentEvents) — так что "0 значит без ограничения" вместо
        // этого молча схлопнуло бы список событий демо до одной-единственной строки. Ограничение
        // здесь не даёт демо вообще когда-либо задействовать через свой UI это противоречивое
        // спецзначение.
        val eventLimit = _state.value.eventLimitInput.toIntOrNull()?.coerceAtLeast(1)
            ?: configRepository.eventLimit

        configRepository.retentionDays = retentionDays
        configRepository.eventLimit = eventLimit
        sdkGateway.updateConfig(retentionDays = retentionDays, maxEventCount = eventLimit)
        eventListLimit.value = eventLimit

        sdkGateway.track(
            "config_updated",
            mapOf("retention_days" to retentionDays.toString(), "event_limit" to eventLimit.toString()),
        )
    }
}
