package com.eventtracker.demo.ui

import com.eventtracker.sdk.model.DayCount
import com.eventtracker.sdk.model.TrackedEvent

data class EventsUiState(
    val totalCount: Int = 0,
    val todayCount: Int = 0,
    val byDay: List<DayCount> = emptyList(),
    val events: List<TrackedEvent> = emptyList(),
    val retentionDaysInput: String = "",
    val eventLimitInput: String = "",
)
