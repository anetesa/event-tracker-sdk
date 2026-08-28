package com.eventtracker.sdk.internal.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Сохраняет конфигурацию SDK (период retention, максимальное число событий) между перезапусками
 * процесса, чтобы фоновый [com.eventtracker.sdk.internal.work.CleanupWorker] — который может
 * выполниться в свежем процессе без какого-либо состояния в памяти — всегда читал актуальные
 * значения.
 */
internal class SdkConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Ограничивается снизу значением 1 день — неположительный период retention лишён смысла. */
    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        set(value) = prefs.edit().putInt(KEY_RETENTION_DAYS, value.coerceAtLeast(1)).apply()

    /**
     * Значение `<= 0` означает "без ограничения" (очистка по количеству пропускается) — это
     * полезнее, чем ограничение снизу значением 1, из-за которого каждая очистка удаляла бы всё
     * до одного-единственного события.
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
