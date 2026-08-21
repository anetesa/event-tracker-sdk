package com.eventtracker.sdk.internal.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists SDK configuration (retention period, max event count) across process restarts so the
 * background [com.eventtracker.sdk.internal.work.CleanupWorker] — which may run in a fresh
 * process with no in-memory state — always reads the latest values.
 */
internal class SdkConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Clamped to a minimum of 1 day — a non-positive retention period is not meaningful. */
    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        set(value) = prefs.edit().putInt(KEY_RETENTION_DAYS, value.coerceAtLeast(1)).apply()

    /**
     * A value `<= 0` means "unlimited" (count-based trimming is skipped) — more useful than
     * clamping to 1, which would make every cleanup pass delete down to a single event.
     */
    var maxEventCount: Int
        get() = prefs.getInt(KEY_MAX_EVENT_COUNT, DEFAULT_MAX_EVENT_COUNT)
        set(value) = prefs.edit().putInt(KEY_MAX_EVENT_COUNT, value).apply()

    companion object {
        private const val PREFS_NAME = "event_tracker_sdk_config"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_MAX_EVENT_COUNT = "max_event_count"
        const val DEFAULT_RETENTION_DAYS = 7
        const val DEFAULT_MAX_EVENT_COUNT = 100
    }
}
