package com.eventtracker.sdk.internal.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eventtracker.sdk.internal.config.SdkConfigStore
import com.eventtracker.sdk.internal.db.EventTrackerDatabase
import java.util.concurrent.TimeUnit

/**
 * Periodic background cleanup: deletes events older than the configured retention period, and
 * separately trims down to the configured max event count when exceeded.
 *
 * Deliberately does **not** consult [com.eventtracker.sdk.internal.db.EventEntity.isSeen] — the
 * "protect events the user has never seen" rule (see `EventRepositoryImpl.clearAllEvents`) is
 * scoped to the explicit user-initiated "clear all" action, not automatic disk-growth control;
 * automatic cleanup must not be silently defeated by an event list that's never opened. If a
 * reviewer intends the protection to also cover automatic cleanup, both queries below need an
 * added `AND isSeen = 1`.
 *
 * Config is re-read from [SdkConfigStore] on every run (not captured at schedule time) since
 * this worker may execute in a fresh process with no in-memory SDK state, and the values may
 * have changed via `EventTrackerSDK.updateConfig` since the job was first enqueued.
 */
internal class CleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val appContext = applicationContext
        val config = SdkConfigStore(appContext)
        val dao = EventTrackerDatabase.getInstance(appContext).eventDao()

        val retentionMillis = TimeUnit.DAYS.toMillis(config.retentionDays.toLong())
        val cutoff = System.currentTimeMillis() - retentionMillis
        dao.deleteOlderThan(cutoff)

        val maxEventCount = config.maxEventCount
        if (maxEventCount > 0) {
            dao.deleteExceedingCount(maxEventCount)
        }
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )

    companion object {
        const val UNIQUE_WORK_NAME = "event_tracker_cleanup"
    }
}
