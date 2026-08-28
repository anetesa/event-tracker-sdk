package com.eventtracker.sdk.model

/**
 * Количество событий, зафиксированных за один календарный день, ключ [date] в формате `DD/MM/YYYY`.
 */
data class DayCount(
    val date: String,
    val count: Int,
)

/**
 * Агрегированная статистика, возвращаемая [com.eventtracker.sdk.EventTrackerSDK.getStatistics].
 *
 * [byDay] упорядочен так, что сначала идёт самый новый день, и ограничен запрошенным числом
 * последних дней.
 */
data class EventStatistics(
    val totalCount: Int,
    val todayCount: Int,
    val byDay: List<DayCount>,
)
