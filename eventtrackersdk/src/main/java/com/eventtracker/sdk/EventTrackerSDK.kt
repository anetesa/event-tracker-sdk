package com.eventtracker.sdk

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eventtracker.sdk.internal.config.SdkConfigStore
import com.eventtracker.sdk.internal.db.EventTrackerDatabase
import com.eventtracker.sdk.internal.repository.EventRepository
import com.eventtracker.sdk.internal.repository.EventRepositoryImpl
import com.eventtracker.sdk.internal.util.SystemClock
import com.eventtracker.sdk.internal.util.UuidGenerator
import com.eventtracker.sdk.internal.work.CleanupWorker
import com.eventtracker.sdk.model.EventStatistics
import com.eventtracker.sdk.model.TrackedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Public entry point for the Event Tracker SDK.
 *
 * Deliberately framework-agnostic: this facade does not require Hilt, Dagger, or any other DI
 * framework from the host app — it builds its own collaborators internally the first time
 * [init] runs. This keeps the SDK a true drop-in artifact rather than one that imposes the
 * host's architecture choices.
 *
 * All public methods are safe to call from any thread. [track] never blocks the caller.
 */
object EventTrackerSDK {

    private const val TAG = "EventTrackerSDK"

    private val initLock = Any()

    @Volatile
    private var initialized = false

    private lateinit var repository: EventRepository
    private lateinit var configStore: SdkConfigStore
    private lateinit var scope: CoroutineScope

    /**
     * Initializes the SDK. Safe to call from app startup on every process wake-up: the first
     * call in a process wins and performs setup (schedules the periodic cleanup job, persists
     * config); every subsequent call in the same process — including concurrent racing calls —
     * is a no-op.
     *
     * @param retentionDays events older than this are eligible for automatic cleanup. Clamped to
     *   a minimum of 1.
     * @param maxEventCount if the stored event count exceeds this, the oldest excess events are
     *   eligible for automatic cleanup. A value `<= 0` disables count-based trimming.
     */
    fun init(context: Context, retentionDays: Int = 7, maxEventCount: Int = 100) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            val appContext = context.applicationContext
            val dao = EventTrackerDatabase.getInstance(appContext).eventDao()

            configStore = SdkConfigStore(appContext).apply {
                this.retentionDays = retentionDays
                this.maxEventCount = maxEventCount
            }

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = EventRepositoryImpl(dao, SystemClock, UuidGenerator, scope)

            scheduleCleanupWork(appContext)

            // Published last: any thread that observes `initialized == true` is guaranteed (by
            // the JVM memory model's volatile happens-before rule) to see the fully-constructed
            // `repository`/`configStore`/`scope` written above.
            initialized = true
        }
    }

    /** Live-updates retention/limit config without requiring re-initialization. Either parameter may be omitted to leave it unchanged. */
    fun updateConfig(retentionDays: Int? = null, maxEventCount: Int? = null) {
        if (!initialized) {
            Log.w(TAG, "updateConfig() called before init() — ignored.")
            return
        }
        retentionDays?.let { configStore.retentionDays = it }
        maxEventCount?.let { configStore.maxEventCount = it }
    }

    /**
     * Tracks an event. Returns immediately — the write happens asynchronously on a background
     * dispatcher, so this never blocks the calling thread regardless of caller. Safe to call
     * concurrently from many threads in rapid succession without data loss.
     *
     * If called before [init], the event is dropped and a warning is logged: a mistakenly-early
     * call must not crash the caller, but there is no queue-until-init behavior in this SDK.
     */
    fun track(name: String, properties: Map<String, String> = emptyMap()) {
        if (!initialized) {
            Log.w(TAG, "track(\"$name\") called before init() — event dropped.")
            return
        }
        scope.launch { repository.trackEvent(name, properties) }
    }

    /** Recent events, newest first, capped to [limit] (clamped to a minimum of 1). */
    fun getRecentEvents(limit: Int = 50): Flow<List<TrackedEvent>> {
        if (!initialized) {
            Log.w(TAG, "getRecentEvents() called before init() — returning empty flow.")
            return emptyFlow()
        }
        return repository.observeRecentEvents(limit)
    }

    /** Total/today/day-grouped counts. Always executes on a background thread. */
    suspend fun getStatistics(dayWindow: Int = 7): EventStatistics {
        if (!initialized) {
            Log.w(TAG, "getStatistics() called before init() — returning empty statistics.")
            return EventStatistics(totalCount = 0, todayCount = 0, byDay = emptyList())
        }
        return repository.getStatistics(dayWindow)
    }

    /**
     * Deletes all events the UI has already displayed via [getRecentEvents]. Events never yet
     * delivered to that flow are left untouched — see `EventRepositoryImpl` for the exact
     * "seen" semantics.
     */
    suspend fun clearAllEvents() {
        if (!initialized) {
            Log.w(TAG, "clearAllEvents() called before init() — ignored.")
            return
        }
        repository.clearAllEvents()
    }

    private fun scheduleCleanupWork(appContext: Context) {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            CleanupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
