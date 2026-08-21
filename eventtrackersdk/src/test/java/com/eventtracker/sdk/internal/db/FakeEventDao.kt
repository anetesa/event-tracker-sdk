package com.eventtracker.sdk.internal.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [EventDao] fake for repository-level unit tests — no Room/Robolectric required. */
internal class FakeEventDao : EventDao {

    private val state = MutableStateFlow<List<EventEntity>>(emptyList())

    val allEvents: List<EventEntity> get() = state.value

    override suspend fun insert(event: EventEntity) {
        state.update { list -> list.filterNot { it.id == event.id } + event }
    }

    override fun observeRecent(limit: Int): Flow<List<EventEntity>> =
        state.map { list -> list.sortedByDescending { it.timestamp }.take(limit) }

    override suspend fun getTotalCount(): Int = state.value.size

    override suspend fun getTodayCount(today: String): Int =
        state.value.count { it.createdDate == today }

    override suspend fun getGroupedByDay(days: Int): List<DayCountRow> =
        state.value.groupBy { it.createdDate }
            .entries
            .map { (date, rows) -> DayCountRow(date, rows.size) to rows.maxOf { it.timestamp } }
            .sortedByDescending { it.second }
            .take(days)
            .map { it.first }

    override suspend fun deleteOlderThan(cutoffMillis: Long) {
        state.update { list -> list.filter { it.timestamp >= cutoffMillis } }
    }

    override suspend fun deleteExceedingCount(keepCount: Int) {
        state.update { list -> list.sortedByDescending { it.timestamp }.take(keepCount) }
    }

    override suspend fun markSeen(ids: List<String>) {
        state.update { list -> list.map { if (it.id in ids) it.copy(isSeen = true) else it } }
    }

    override suspend fun deleteAllSeen() {
        state.update { list -> list.filterNot { it.isSeen } }
    }
}
