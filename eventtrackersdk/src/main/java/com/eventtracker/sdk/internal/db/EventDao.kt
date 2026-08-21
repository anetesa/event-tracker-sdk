package com.eventtracker.sdk.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM events WHERE createdDate = :today")
    suspend fun getTodayCount(today: String): Int

    /**
     * Groups by the precomputed [EventEntity.createdDate] bucket (no per-row date math), but
     * orders by the numeric [EventEntity.timestamp] aggregate rather than the date string itself
     * — `DD/MM/YYYY` sorts wrong lexicographically (e.g. "01/12/2025" < "25/11/2025" as strings,
     * even though 25 Nov predates 01 Dec).
     */
    @Query(
        """
        SELECT createdDate, COUNT(*) AS count
        FROM events
        GROUP BY createdDate
        ORDER BY MAX(timestamp) DESC
        LIMIT :days
        """,
    )
    suspend fun getGroupedByDay(days: Int): List<DayCountRow>

    @Query("DELETE FROM events WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query(
        """
        DELETE FROM events
        WHERE id NOT IN (SELECT id FROM events ORDER BY timestamp DESC LIMIT :keepCount)
        """,
    )
    suspend fun deleteExceedingCount(keepCount: Int)

    @Query("UPDATE events SET isSeen = 1 WHERE id IN (:ids)")
    suspend fun markSeen(ids: List<String>)

    @Query("DELETE FROM events WHERE isSeen = 1")
    suspend fun deleteAllSeen()
}
