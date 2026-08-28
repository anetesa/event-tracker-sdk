package com.eventtracker.sdk.internal.repository

import com.eventtracker.sdk.internal.db.EventDao
import com.eventtracker.sdk.internal.db.EventEntity
import com.eventtracker.sdk.internal.util.Clock
import com.eventtracker.sdk.internal.util.DateFormatting
import com.eventtracker.sdk.internal.util.IdGenerator
import com.eventtracker.sdk.internal.util.PropertiesJsonCodec
import com.eventtracker.sdk.model.DayCount
import com.eventtracker.sdk.model.EventStatistics
import com.eventtracker.sdk.model.TrackedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class EventRepositoryImpl(
    private val dao: EventDao,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val scope: CoroutineScope,
) : EventRepository {

    override suspend fun trackEvent(name: String, properties: Map<String, String>) {
        val safeName = name.ifBlank { "unnamed_event" }
        val now = clock.nowMillis()
        dao.insert(
            EventEntity(
                id = idGenerator.newId(),
                name = safeName,
                propertiesJson = PropertiesJsonCodec.encode(properties),
                timestamp = now,
                createdDate = DateFormatting.dayBucket(now),
            ),
        )
    }

    override fun observeRecentEvents(limit: Int): Flow<List<TrackedEvent>> {
        val safeLimit = limit.coerceAtLeast(1)
        return dao.observeRecent(safeLimit)
            .onEach { rows ->
                // Пишем только по строкам, ещё не помеченным как seen. Flow от Room переэмиттит
                // при ЛЮБОЙ записи в таблицу "events", а не только при той, что меняет результат
                // этого конкретного запроса — безусловный markSeen() здесь писал бы на каждую
                // эмиссию (даже избыточно повторно выставляя isSeen=1 на уже просмотренных
                // строках), а это само по себе триггерит новую инвалидацию/переэмиссию, и так
                // до бесконечности, пока подписчик остаётся подключён. Фильтрация до по-настоящему
                // новых строк делает так, что записи — и сам цикл — в итоге останавливаются.
                val newlySeenIds = rows.filter { !it.isSeen }.map { it.id }
                if (newlySeenIds.isNotEmpty()) {
                    // Запускается в общем scope, чтобы никогда не блокировать/задерживать доставку в UI.
                    scope.launch { dao.markSeen(newlySeenIds) }
                }
            }
            .map { rows -> rows.map(::toPublicModel) }
    }

    override suspend fun getStatistics(dayWindow: Int): EventStatistics {
        val today = DateFormatting.dayBucket(clock.nowMillis())
        val total = dao.getTotalCount()
        val todayCount = dao.getTodayCount(today)
        val byDay = dao.getGroupedByDay(dayWindow.coerceAtLeast(1))
            .map { row -> DayCount(date = row.createdDate, count = row.count) }
        return EventStatistics(totalCount = total, todayCount = todayCount, byDay = byDay)
    }

    override suspend fun clearAllEvents() {
        dao.deleteAllSeen()
    }

    private fun toPublicModel(entity: EventEntity) = TrackedEvent(
        id = entity.id,
        name = entity.name,
        properties = PropertiesJsonCodec.decode(entity.propertiesJson),
        timestamp = entity.timestamp,
    )
}
