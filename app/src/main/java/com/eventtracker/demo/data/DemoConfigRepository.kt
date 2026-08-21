package com.eventtracker.demo.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The demo app's own persisted copy of the user-configured retention period / event limit, so
 * [com.eventtracker.demo.EventTrackerDemoApp] can pass the current values to
 * `EventTrackerSDK.init` on every process wake-up (the SDK itself only remembers config once
 * initialized in-process; this is what survives across process restarts on the app side).
 */
@Singleton
class DemoConfigRepository @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        set(value) = prefs.edit().putInt(KEY_RETENTION_DAYS, value.coerceAtLeast(1)).apply()

    var eventLimit: Int
        get() = prefs.getInt(KEY_EVENT_LIMIT, DEFAULT_EVENT_LIMIT)
        set(value) = prefs.edit().putInt(KEY_EVENT_LIMIT, value).apply()

    companion object {
        private const val PREFS_NAME = "event_tracker_demo_config"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_EVENT_LIMIT = "event_limit"
        const val DEFAULT_RETENTION_DAYS = 7
        const val DEFAULT_EVENT_LIMIT = 100
    }
}
