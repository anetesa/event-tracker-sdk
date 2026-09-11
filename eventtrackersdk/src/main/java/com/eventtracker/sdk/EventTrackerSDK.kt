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
import kotlinx.coroutines.CoroutineExceptionHandler
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
 * [init] itself normally never needs to be called by the host app: it runs automatically on
 * process start via `com.eventtracker.sdk.internal.init.EventTrackerInitProvider`, a
 * `ContentProvider` declared in this library's own manifest — see that class's doc comment for
 * how it resolves the initial `retentionDays`/`maxEventCount`. To change those values at runtime
 * instead (e.g. from a settings screen), call [updateConfig].
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
     * Initializes the SDK. In a normal app process this has already been called automatically —
     * by the auto-init `ContentProvider`, before any host app code ran — by the time any code
     * could possibly call this method, so an explicit call from host app code is essentially
     * always a no-op in practice. It stays public and idempotent regardless, for tests and for
     * advanced setups that disable the auto-init provider via a manifest merge override.
     *
     * The first call in a process wins and performs setup (schedules the periodic cleanup job,
     * persists config); every subsequent call in the same process — including concurrent racing
     * calls — is a no-op.
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

            // A SupervisorJob alone does not swallow exceptions — it only stops a failing child
            // from cancelling its siblings. Without this handler, an exception from a background
            // write (e.g. a disk-full Room failure during a burst of track() calls) would
            // propagate uncaught and crash the host app, violating the "track() never crashes
            // the caller" guarantee.
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Unhandled exception in EventTrackerSDK background work", throwable)
            }
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
            repository = EventRepositoryImpl(dao, SystemClock, UuidGenerator, scope)

            // Tracking/reading must keep working even if this fails (e.g. a host app's own
            // Configuration.Provider replaces WorkManager's default auto-init and hasn't run yet
            // at this point in provider startup) — scheduling is retried on every future init()
            // call, i.e. every future process start, so a one-off failure here is not permanent.
            runCatching { scheduleCleanupWork(appContext) }
                .onFailure { e -> Log.w(TAG, "Failed to schedule periodic cleanup work; will retry on next process start", e) }

            // Published last: any thread that observes `initialized == true` is guaranteed (by
            // the JVM memory model's volatile happens-before rule) to see the fully-constructed
            // `repository`/`configStore`/`scope` written above.
            initialized = true
        }
    }

    /** Live-updates retention/limit config without requiring re-initialization. Either parameter may be omitted to leave it unchanged. */
    fun updateConfig(retentionDays: Int? = null, maxEventCount: Int? = null): Unit = guarded("updateConfig()", Unit) {
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
    fun track(name: String, properties: Map<String, String> = emptyMap()): Unit = guarded("track(\"$name\")", Unit) {
        scope.launch { repository.trackEvent(name, properties) }
    }

    /** Recent events, newest first, capped to [limit] (clamped to a minimum of 1). */
    fun getRecentEvents(limit: Int = 50): Flow<List<TrackedEvent>> =
        guarded("getRecentEvents()", emptyFlow()) { repository.observeRecentEvents(limit) }

    /** Total/today/day-grouped counts. Always executes on a background thread. */
    suspend fun getStatistics(dayWindow: Int = 7): EventStatistics =
        guardedSuspend("getStatistics()", EventStatistics(totalCount = 0, todayCount = 0, byDay = emptyList())) {
            repository.getStatistics(dayWindow)
        }

    /**
     * Deletes all events the UI has already displayed via [getRecentEvents]. Events never yet
     * delivered to that flow are left untouched — see `EventRepositoryImpl` for the exact
     * "seen" semantics.
     */
    suspend fun clearAllEvents() = guardedSuspend("clearAllEvents()", Unit) {
        repository.clearAllEvents()
    }

    /**
     * Every public method needs the same "did init() actually run yet?" check before touching
     * the lateinit `repository`/`configStore`/`scope` — kept as these two small helpers instead
     * of a hand-copied `if (!initialized) {...}` per method, so a future method can't compile
     * while forgetting the guard.
     */
    private inline fun <T> guarded(methodName: String, fallback: T, block: () -> T): T {
        if (!initialized) {
            Log.w(TAG, "$methodName called before init() — SDK not initialized, using fallback.")
            return fallback
        }
        return block()
    }

    private suspend inline fun <T> guardedSuspend(methodName: String, fallback: T, block: suspend () -> T): T {
        if (!initialized) {
            Log.w(TAG, "$methodName called before init() — SDK not initialized, using fallback.")
            return fallback
        }
        return block()
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
