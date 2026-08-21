package com.eventtracker.sdk.internal.repository

import app.cash.turbine.test
import com.eventtracker.sdk.internal.db.FakeEventDao
import com.eventtracker.sdk.internal.util.FakeClock
import com.eventtracker.sdk.internal.util.FakeIdGenerator
import com.eventtracker.sdk.internal.util.PropertiesJsonCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventRepositoryImplTest {

    // 2024-12-20T12:00:00Z in millis, an arbitrary fixed instant so day-bucketing is deterministic.
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
        clock.set(baseMillis) // back to "today" before asking what counts as today

        val stats = repository.getStatistics(dayWindow = 7)

        assertEquals(3, stats.totalCount)
        assertEquals(2, stats.todayCount)
        assertEquals(2, stats.byDay.size)
        assertEquals(2, stats.byDay.first().count) // most recent day first
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
        runCurrent() // let the fire-and-forget markSeen side effect complete

        repository.clearAllEvents()

        assertEquals(0, repository.getStatistics().totalCount)
    }

    @Test
    fun `events never delivered to observeRecentEvents survive clearAllEvents`() = runTest {
        val dao = FakeEventDao()
        val repository: EventRepository = EventRepositoryImpl(dao, FakeClock(baseMillis), FakeIdGenerator(), backgroundScope)

        repository.trackEvent("never_seen")
        // Deliberately never calling observeRecentEvents().

        repository.clearAllEvents()

        assertEquals(1, repository.getStatistics().totalCount)
        assertTrue(dao.allEvents.none { it.isSeen })
    }
}
