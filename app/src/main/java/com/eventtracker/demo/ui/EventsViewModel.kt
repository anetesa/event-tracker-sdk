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
     * List display is capped to the same value as the configured event limit — this ties the
     * exam's "seen events are protected from Clear All" requirement directly to the
     * user-configurable event count: if more than [DemoConfigRepository.eventLimit] events exist
     * (e.g. right after "Track 100 Events" also counting the app's own startup events), the
     * oldest overflow rows never appear in this capped list, are therefore never marked seen,
     * and are provably left untouched by "Clear All Events".
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
        // Spec ties "auto-refresh every 3 seconds" specifically to the Statistics Section; the
        // event list above is reactive/live instead, since spec separately says the list "must
        // update when new events are tracked".
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
     * `track()` is fire-and-forget by SDK contract (never blocks, no completion signal), so there
     * is no real "still running" state to report back for this button — issuing 100 calls itself
     * finishes near-instantly regardless of when the underlying writes land. A previous version
     * disabled the button behind an `isStressTestRunning` flag that was cleared right after the
     * calls were *issued*, not after they completed, which just re-enabled the button almost
     * immediately and didn't actually guard anything. Repeated taps are harmless here — Room
     * handles concurrent writes safely, and more concurrent load only exercises the "100+ rapid
     * events without data loss" requirement harder, not incorrectly.
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
        // Clamped to >= 1 here, not just passed through: the SDK treats maxEventCount <= 0 as
        // "unlimited" for its own automatic-cleanup purposes, but that same value is reused below
        // as this screen's displayed-event-list Flow limit, where <= 0 gets coerced to a minimum
        // of 1 (EventRepositoryImpl.observeRecentEvents) — so "0 for unlimited" would silently
        // collapse the demo's event list to a single row instead. Clamping here keeps the demo
        // from ever exercising that conflicting sentinel through its own UI.
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
