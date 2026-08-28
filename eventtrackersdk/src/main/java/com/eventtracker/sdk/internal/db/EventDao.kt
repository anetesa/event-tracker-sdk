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

    /**
     * `rowid DESC` — это не косметика, а тай-брейкер для `timestamp DESC`: `timestamp` имеет
     * разрешение в миллисекунду (`System.currentTimeMillis()`), и плотный цикл (например,
     * "Track 100 Events") регулярно вставляет несколько строк в пределах одной и той же
     * миллисекунды. Без второго ключа SQLite не гарантирует никакого конкретного порядка среди
     * строк с одинаковым значением, так что список "сначала новые" мог бы отрендериться не в
     * порядке вставки. Неявный `rowid` в SQLite (у этой таблицы нет `INTEGER PRIMARY KEY`, то
     * есть это не WITHOUT ROWID таблица) монотонно растёт вместе с порядком вставки, что делает
     * его надёжным тай-брейкером без каких-либо изменений схемы.
     */
    @Query("SELECT * FROM events ORDER BY timestamp DESC, rowid DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM events WHERE createdDate = :today")
    suspend fun getTodayCount(today: String): Int

    /**
     * Группирует по заранее вычисленному ведру [EventEntity.createdDate] (без пересчёта даты
     * построчно), но сортирует по числовому агрегату [EventEntity.timestamp], а не по самой
     * строке даты — формат `DD/MM/YYYY` лексикографически сортируется неверно (например,
     * "01/12/2025" < "25/11/2025" как строки, хотя 25 ноября раньше 1 декабря).
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
        WHERE id NOT IN (SELECT id FROM events ORDER BY timestamp DESC, rowid DESC LIMIT :keepCount)
        """,
    )
    suspend fun deleteExceedingCount(keepCount: Int)

    @Query("UPDATE events SET isSeen = 1 WHERE id IN (:ids)")
    suspend fun markSeen(ids: List<String>)

    @Query("DELETE FROM events WHERE isSeen = 1")
    suspend fun deleteAllSeen()
}
