package com.eventtracker.sdk.internal.repository

import app.cash.turbine.test
import com.eventtracker.sdk.internal.db.DayCountRow
import com.eventtracker.sdk.internal.db.EventDao
import com.eventtracker.sdk.internal.db.EventEntity
import com.eventtracker.sdk.internal.db.FakeEventDao
import com.eventtracker.sdk.internal.util.FakeClock
import com.eventtracker.sdk.internal.util.FakeIdGenerator
import com.eventtracker.sdk.internal.util.PropertiesJsonCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventRepositoryImplTest {

    // 2024-12-20T12:00:00Z в миллисекундах, произвольный фиксированный момент, чтобы группировка по дням была детерминированной.
    private val baseMillis = 1734696000000L

    @Test
    fun `trackEvent persists event with generated id and current timestamp`() = runTest {
        val dao = FakeEventDao()
        val clock = FakeClock(baseMillis)
        val repository: EventRepository = EventRepositoryImpl(dao, clock, FakeIdGenerator(), backgroundScope)

        repository.trackEvent("button_clicked", mapOf("screen" to "home"))

        val stored = dao.allEvents.single()
        assertEquals("id-1", stored.id)
        assertEquals("button_clicked", stored.name)
        assertEquals(baseMillis, stored.timestamp)
        assertEquals(mapOf("screen" to "home"), PropertiesJsonCodec.decode(stored.propertiesJson))
    }

    @Test
    fun `blank event name is normalized instead of stored as-is`() = runTest {
        val dao = FakeEventDao()
        val repository: EventRepository = EventRepositoryImpl(dao, FakeClock(baseMillis), FakeIdGenerator(), backgroundScope)

        repository.trackEvent("   ", emptyMap())

        assertEquals("unnamed_event", dao.allEvents.single().name)
    }

    @Test
    fun `getStatistics reports total today and grouped-by-day counts`() = runTest {
        val dao = FakeEventDao()
        val clock = FakeClock(baseMillis)
        val repository: EventRepository = EventRepositoryImpl(dao, clock, FakeIdGenerator(), backgroundScope)

        repository.trackEvent("today_1")
        repository.trackEvent("today_2")
        clock.advanceByDays(-1)
        repository.trackEvent("yesterday_1")
        clock.set(baseMillis) // возвращаемся к "сегодня" перед тем, как спрашивать, что считается сегодняшним днём

        val stats = repository.getStatistics(dayWindow = 7)

        assertEquals(3, stats.totalCount)
        assertEquals(2, stats.todayCount)
        assertEquals(2, stats.byDay.size)
        assertEquals(2, stats.byDay.first().count) // сначала самый новый день
    }

    @Test
    fun `events delivered to observeRecentEvents become seen and are removed by clearAllEvents`() = runTest {
        val dao = FakeEventDao()
        val repository: EventRepository = EventRepositoryImpl(dao, FakeClock(baseMillis), FakeIdGenerator(), backgroundScope)

        repository.trackEvent("seen_event")

        repository.observeRecentEvents(limit = 10).test {
            val first = awaitItem()
            assertEquals(1, first.size)
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent() // даём завершиться fire-and-forget побочному эффекту markSeen

        repository.clearAllEvents()

        assertEquals(0, repository.getStatistics().totalCount)
    }

    @Test
    fun `events never delivered to observeRecentEvents survive clearAllEvents`() = runTest {
        val dao = FakeEventDao()
        val repository: EventRepository = EventRepositoryImpl(dao, FakeClock(baseMillis), FakeIdGenerator(), backgroundScope)

        repository.trackEvent("never_seen")
        // Сознательно ни разу не вызываем observeRecentEvents().

        repository.clearAllEvents()

        assertEquals(1, repository.getStatistics().totalCount)
        assertTrue(dao.allEvents.none { it.isSeen })
    }

    @Test
    fun `does not re-write markSeen for rows a later emission already reports as seen`() = runTest {
        // Реальный Flow от Room переэмиттит при ЛЮБОЙ записи в наблюдаемую таблицу, включая
        // собственный UPDATE от markSeen — а не только те эмиссии, где результат запроса
        // действительно изменился. MutableSharedFlow (в отличие от MutableStateFlow в
        // FakeEventDao, который дедуплицирует одинаковые значения) это воспроизводит: каждый
        // emit() долетает до подписчика независимо от содержимого — точно так же, как реальный
        // сценарий бага, от которого защищает этот тест.
        val dao = RecordingReemittingDao()
        val repository: EventRepository = EventRepositoryImpl(dao, FakeClock(baseMillis), FakeIdGenerator(), backgroundScope)

        val notYetSeen = EventEntity(id = "e1", name = "n", propertiesJson = "{}", timestamp = 1L, createdDate = "20/12/2024")
        // Вторая эмиссия имитирует то, что вернул бы реальный повторный запрос после того, как
        // первая запись markSeen уже закоммитилась: та же строка, теперь с isSeen=true.
        val alreadySeenAfterFirstWrite = notYetSeen.copy(isSeen = true)

        repository.observeRecentEvents(limit = 10).test {
            dao.emit(listOf(notYetSeen))
            assertEquals(1, awaitItem().size)
            dao.emit(listOf(alreadySeenAfterFirstWrite))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent() // даём завершиться всем fire-and-forget запускам markSeen

        assertEquals(1, dao.markSeenCalls.size) // только для первой, по-настоящему непросмотренной эмиссии
        assertEquals(listOf("e1"), dao.markSeenCalls.single())
    }

    /** Минимальный дубль [EventDao], чей [observeRecent] никогда не дедуплицирует эмиссии, в отличие от [FakeEventDao]. */
    private class RecordingReemittingDao : EventDao {
        val markSeenCalls = mutableListOf<List<String>>()
        private val flow = MutableSharedFlow<List<EventEntity>>(replay = 0, extraBufferCapacity = 8)

        suspend fun emit(rows: List<EventEntity>) = flow.emit(rows)

        override suspend fun insert(event: EventEntity) = error("not used in this test")
        override fun observeRecent(limit: Int): Flow<List<EventEntity>> = flow
        override suspend fun getTotalCount(): Int = error("not used in this test")
        override suspend fun getTodayCount(today: String): Int = error("not used in this test")
        override suspend fun getGroupedByDay(days: Int): List<DayCountRow> = error("not used in this test")
        override suspend fun deleteOlderThan(cutoffMillis: Long) = error("not used in this test")
        override suspend fun deleteExceedingCount(keepCount: Int) = error("not used in this test")
        override suspend fun markSeen(ids: List<String>) {
            markSeenCalls.add(ids)
        }
        override suspend fun deleteAllSeen() = error("not used in this test")
    }
}
