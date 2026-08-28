package com.eventtracker.sdk.internal.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eventtracker.sdk.internal.config.SdkConfigStore
import com.eventtracker.sdk.internal.db.EventTrackerDatabase
import java.util.concurrent.TimeUnit

/**
 * Периодическая фоновая очистка: удаляет события старше настроенного периода retention, и
 * отдельно урезает количество до настроенного максимума, если оно превышено.
 *
 * Сознательно **не** учитывает [com.eventtracker.sdk.internal.db.EventEntity.isSeen] — правило
 * "защищать события, которые пользователь ещё не видел" (см. `EventRepositoryImpl.clearAllEvents`)
 * ограничено явным пользовательским действием "clear all", а не автоматическим контролем роста
 * диска; автоматическую очистку не должен молча обходить список событий, который никогда не
 * открывают. Если ревьюер считает, что защита должна распространяться и на автоматическую
 * очистку, в оба запроса ниже нужно добавить `AND isSeen = 1`.
 *
 * Конфиг перечитывается из [SdkConfigStore] при каждом запуске (а не захватывается в момент
 * планирования), поскольку этот воркер может выполниться в свежем процессе без какого-либо
 * состояния SDK в памяти, а значения могли измениться через `EventTrackerSDK.updateConfig` уже
 * после того, как задача была впервые поставлена в очередь.
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
        onFailure = { error ->
            Log.e(TAG, "Cleanup run failed, will retry", error)
            Result.retry()
        },
    )

    companion object {
        private const val TAG = "CleanupWorker"
        const val UNIQUE_WORK_NAME = "event_tracker_cleanup"
    }
}
