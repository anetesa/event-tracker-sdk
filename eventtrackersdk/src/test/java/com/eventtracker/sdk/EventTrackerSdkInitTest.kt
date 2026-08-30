package com.eventtracker.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.eventtracker.sdk.internal.work.CleanupWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Подход: Robolectric (не мок, не фейк, не plain unit) для `object` [EventTrackerSDK] целиком,
 * плюс `WorkManagerTestInitHelper` для реального (тестового) [WorkManager].
 *
 * Почему: [EventTrackerSDK] — это framework-agnostic фасад, который сам конструирует внутри
 * себя `Context`-зависимые вещи (Room через `EventTrackerDatabase.getInstance`, `WorkManager`),
 * а не принимает их извне как аргументы — поэтому подменить их фейком/моком в тесте нельзя,
 * не изменив сам продакшен-код. Нужен настоящий `Context` и настоящий `WorkManager`, а
 * Robolectric даёт их на JVM без эмулятора. Дополнительный бонус: Robolectric даёт каждому
 * тесту собственную песочницу classloader'а, поэтому `object`-состояние [EventTrackerSDK]
 * естественным образом сбрасывается между тестовыми методами — ручной сброс через рефлексию
 * не нужен.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class EventTrackerSdkInitTest {

    @Test
    fun `second init call is a no-op and does not duplicate the periodic cleanup job`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        EventTrackerSDK.init(context, retentionDays = 3, maxEventCount = 50)
        EventTrackerSDK.init(context, retentionDays = 999, maxEventCount = 999) // должно быть проигнорировано

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CleanupWorker.UNIQUE_WORK_NAME)
            .get()
        assertEquals(1, workInfos.size)
    }

    @Test
    fun `track before init drops the event without throwing`() {
        // В этом тесте init() вообще не вызывается.
        EventTrackerSDK.track("too_early", mapOf("k" to "v"))
        // Сама проверка — это то, что мы дошли до этой строки без исключения.
    }
}
