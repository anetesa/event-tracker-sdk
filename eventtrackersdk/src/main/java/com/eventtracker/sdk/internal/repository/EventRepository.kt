package com.eventtracker.sdk.internal.repository

import com.eventtracker.sdk.model.EventStatistics
import com.eventtracker.sdk.model.TrackedEvent
import kotlinx.coroutines.flow.Flow

/**
 * Владеет всеми бизнес-правилами по событиям (генерация id/timestamp, жизненный цикл флага
 * "seen", формирование статистики). Оформлено как интерфейс — отдельно от
 * [com.eventtracker.sdk.internal.db.EventDao], который остаётся чистым SQL — чтобы его можно
 * было подменить фейком в тестах, не трогая Room, и чтобы [com.eventtracker.sdk.EventTrackerSDK]
 * оставался тонким фасадом без собственной бизнес-логики (инверсия зависимостей + единственная
 * ответственность).
 */
internal interface EventRepository {

    /** Вставляет новую строку события. Приостанавливается только на время записи в БД; выбор потока — на совести вызывающего кода. */
    suspend fun trackEvent(name: String, properties: Map<String, String> = emptyMap())

    /**
     * Живой "список событий", за которым наблюдает UI. Каждая порция, которую эмиттит этот
     * [Flow], помечается как seen в момент эмиссии — почему выбрана именно такая семантика
     * "seen", смотри в описании класса у поля `isSeen` в `EventEntity`.
     */
    fun observeRecentEvents(limit: Int): Flow<List<TrackedEvent>>

    /** [dayWindow] ограничивает, сколько последних различных дней попадёт в [EventStatistics.byDay]. */
    suspend fun getStatistics(dayWindow: Int = 7): EventStatistics

    /** Удаляет только те события, которые UI уже показал (см. `observeRecentEvents`). */
    suspend fun clearAllEvents()
}
