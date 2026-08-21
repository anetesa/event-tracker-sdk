package com.eventtracker.sdk.internal.db

/** Row shape returned by [EventDao.getGroupedByDay]. */
data class DayCountRow(
    val createdDate: String,
    val count: Int,
)
