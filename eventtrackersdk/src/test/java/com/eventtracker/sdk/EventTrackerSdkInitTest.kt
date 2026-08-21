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
 * Robolectric gives each test its own classloader sandbox, so [EventTrackerSDK]'s object-level
 * state is naturally reset between test methods here — no manual reflection-based reset needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class EventTrackerSdkInitTest {

    @Test
    fun `second init call is a no-op and does not duplicate the periodic cleanup job`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        EventTrackerSDK.init(context, retentionDays = 3, maxEventCount = 50)
        EventTrackerSDK.init(context, retentionDays = 999, maxEventCount = 999) // must be ignored

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CleanupWorker.UNIQUE_WORK_NAME)
            .get()
        assertEquals(1, workInfos.size)
    }

    @Test
    fun `track before init drops the event without throwing`() {
        // No init() call in this test at all.
        EventTrackerSDK.track("too_early", mapOf("k" to "v"))
        // Reaching this line without an exception is the assertion.
    }
}
