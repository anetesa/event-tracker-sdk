package com.eventtracker.sdk.internal.db

/** Форма строки, возвращаемой [EventDao.getGroupedByDay]. */
data class DayCountRow(
    val createdDate: String,
    val count: Int,
)
