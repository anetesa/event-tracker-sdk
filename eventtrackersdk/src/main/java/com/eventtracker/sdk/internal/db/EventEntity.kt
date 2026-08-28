package com.eventtracker.sdk.internal.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сохраняемая строка отслеженного события.
 *
 * [createdDate] вычисляется заранее, в момент вставки (а не выводится в SQL), чтобы запросы
 * группировки по дням были обычным `GROUP BY` без пересчёта даты построчно — соответствует
 * требованию схемы.
 *
 * [isSeen] реализует правило "событие не подлежит очистке, пока UI его не показал": начинается
 * со значения `false` и переключается в `true` только после того, как строка была доставлена в
 * наблюдаемый UI-flow списка событий (см. `EventRepositoryImpl`). `clearAllEvents()` удаляет
 * только строки, где это значение `true`; автоматическая очистка по retention/количеству
 * сознательно игнорирует этот флаг (см. описание `CleanupWorker`).
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val propertiesJson: String,
    val timestamp: Long,
    val createdDate: String,
    val isSeen: Boolean = false,
)
