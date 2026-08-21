package com.eventtracker.sdk.model

/**
 * Number of events recorded on a single calendar day, keyed by [date] in `DD/MM/YYYY` format.
 */
data class DayCount(
    val date: String,
    val count: Int,
)

/**
 * Aggregate statistics returned by [com.eventtracker.sdk.EventTrackerSDK.getStatistics].
 *
 * [byDay] is ordered newest day first and capped to the most recent days requested.
 */
data class EventStatistics(
    val totalCount: Int,
    val todayCount: Int,
    val byDay: List<DayCount>,
)
