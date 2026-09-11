package com.eventtracker.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.eventtracker.sdk.internal.init.EventTrackerInitProvider
import com.eventtracker.sdk.internal.work.CleanupWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unlike a real device, Robolectric does **not** auto-instantiate manifest-declared
 * `ContentProvider`s before the test body runs — confirmed empirically (an earlier version of
 * this test relied on that and failed with `WorkManager is not initialized`). So
 * [EventTrackerInitProvider] is built explicitly below, the same way
 * `Robolectric.buildContentProvider(...)` is the documented way to test a `ContentProvider` under
 * Robolectric, mirroring how the platform would instantiate it on a real process start.
 * `WorkManagerTestInitHelper` is needed first for the same reason: WorkManager's own
 * `androidx.startup`-based auto-init doesn't reliably run under Robolectric either, and
 * [EventTrackerSDK.init] (called from the provider) calls `WorkManager.getInstance(...)`
 * internally.
 *
 * Also confirmed empirically: [EventTrackerSDK]'s `object`-level state does **not** reliably
 * reset between `@Test` methods of this class under this Robolectric version/config — a prior
 * version of this test split "provider auto-init" and "explicit call after auto-init is a no-op"
 * into two separate test methods, and whichever one JUnit happened to run second saw
 * `EventTrackerSDK` already initialized by the first, silently defeating its own premise (it
 * passed in isolation, and failed only when run together with its sibling). Both scenarios are
 * therefore verified within a single self-contained test method instead of relying on any
 * cross-test reset guarantee.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class EventTrackerSdkInitTest {

    @Test
    fun `provider auto-init schedules the cleanup job exactly once, and a later explicit init call is a no-op`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        Robolectric.buildContentProvider(EventTrackerInitProvider::class.java).create()
        EventTrackerSDK.init(context, retentionDays = 999, maxEventCount = 999) // ignored: already initialized by the provider

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CleanupWorker.UNIQUE_WORK_NAME)
            .get()
        assertEquals(1, workInfos.size)
    }
}
