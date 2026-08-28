package com.eventtracker.sdk.internal.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Прогоняет настоящий SQL против in-memory базы Room — корректность удаления/группировки
 * невозможно достоверно проверить на фейковом DAO, поэтому здесь оправданы расходы на настройку
 * Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class EventDaoTest {

    private lateinit var db: EventTrackerDatabase
    private lateinit var dao: EventDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, EventTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.eventDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(id: String, timestamp: Long, createdDate: String, isSeen: Boolean = false) =
        EventEntity(id = id, name = "evt_$id", propertiesJson = "{}", timestamp = timestamp, createdDate = createdDate, isSeen = isSeen)

    @Test
    fun `deleteOlderThan removes only events strictly before the cutoff`() = runBlocking {
        dao.insert(entity("old", timestamp = 1000L, createdDate = "01/01/2024"))
        dao.insert(entity("new", timestamp = 5000L, createdDate = "02/01/2024"))

        dao.deleteOlderThan(cutoffMillis = 3000L)

        assertEquals(1, dao.getTotalCount())
        assertEquals("new", dao.observeRecent(10).first().single().id)
    }

    @Test
    fun `deleteExceedingCount keeps only the newest N events`() = runBlocking {
        dao.insert(entity("e1", timestamp = 1000L, createdDate = "01/01/2024"))
        dao.insert(entity("e2", timestamp = 2000L, createdDate = "01/01/2024"))
        dao.insert(entity("e3", timestamp = 3000L, createdDate = "01/01/2024"))

        dao.deleteExceedingCount(keepCount = 2)

        assertEquals(2, dao.getTotalCount())
    }

    @Test
    fun `markSeen and deleteAllSeen only remove the marked rows`() = runBlocking {
        dao.insert(entity("seen1", timestamp = 1000L, createdDate = "01/01/2024"))
        dao.insert(entity("unseen", timestamp = 2000L, createdDate = "01/01/2024"))

        dao.markSeen(listOf("seen1"))
        dao.deleteAllSeen()

        assertEquals(1, dao.getTotalCount())
    }

    @Test
    fun `observeRecent breaks timestamp ties by insertion order, newest insert first`() = runBlocking {
        // Плотный цикл (например, "Track 100 Events" в демо) может вставить много строк в
        // пределах одной и той же миллисекунды, поскольку timestamp имеет разрешение
        // System.currentTimeMillis(). Без тай-брейкера ORDER BY timestamp DESC в SQLite не даёт
        // никакой гарантии порядка среди совпадающих значений.
        dao.insert(entity("first", timestamp = 5000L, createdDate = "01/01/2024"))
        dao.insert(entity("second", timestamp = 5000L, createdDate = "01/01/2024"))
        dao.insert(entity("third", timestamp = 5000L, createdDate = "01/01/2024"))

        val recent = dao.observeRecent(10).first()

        assertEquals(listOf("third", "second", "first"), recent.map { it.id })
    }

    @Test
    fun `getGroupedByDay orders by most recent day even when date strings sort lexicographically wrong`() = runBlocking {
        // "01/12/2025" отсортировалась бы раньше "25/11/2025" как обычные строки, хотя
        // хронологически 25 ноября 2025 раньше 1 декабря 2025 — DAO обязан сортировать по
        // числовому timestamp, а не по строке createdDate.
        dao.insert(entity("nov1", timestamp = 1000L, createdDate = "25/11/2025"))
        dao.insert(entity("nov2", timestamp = 1500L, createdDate = "25/11/2025"))
        dao.insert(entity("dec1", timestamp = 9000L, createdDate = "01/12/2025"))

        val grouped = dao.getGroupedByDay(days = 7)

        assertEquals(2, grouped.size)
        assertEquals("01/12/2025", grouped.first().createdDate)
        assertEquals(1, grouped.first().count)
        assertEquals("25/11/2025", grouped[1].createdDate)
        assertEquals(2, grouped[1].count)
    }
}
