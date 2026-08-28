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
 * Публичная точка входа Event Tracker SDK.
 *
 * Сознательно framework-agnostic: этот фасад не требует от хост-приложения Hilt, Dagger или
 * любого другого DI-фреймворка — все свои зависимости он строит сам, внутри, при первом
 * вызове [init]. Это делает SDK настоящим drop-in артефактом, а не библиотекой, которая
 * навязывает хосту свои архитектурные решения.
 *
 * Все публичные методы безопасно вызывать из любого потока. [track] никогда не блокирует
 * вызывающий поток.
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
     * Инициализирует SDK. Безопасно вызывать при каждом запуске приложения, на каждое
     * пробуждение процесса: первый вызов в рамках процесса побеждает и выполняет настройку
     * (планирует периодическую задачу очистки, сохраняет конфиг); каждый последующий вызов в
     * том же процессе — включая конкурентно гоняющиеся вызовы — не делает ничего.
     *
     * @param retentionDays события старше этого срока подлежат автоматической очистке.
     *   Ограничивается снизу значением 1.
     * @param maxEventCount если число сохранённых событий превышает это значение, самые старые
     *   лишние события подлежат автоматической очистке. Значение `<= 0` отключает очистку по
     *   количеству.
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

            // Один только SupervisorJob не глотает исключения — он лишь не даёт упавшему
            // дочернему корутину отменить своих соседей. Без этого обработчика исключение из
            // фоновой записи (например, ошибка Room из-за нехватки места на диске во время
            // серии вызовов track()) долетело бы необработанным и уронило бы хост-приложение,
            // нарушив гарантию "track() никогда не роняет вызывающий код".
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Unhandled exception in EventTrackerSDK background work", throwable)
            }
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
            repository = EventRepositoryImpl(dao, SystemClock, UuidGenerator, scope)

            scheduleCleanupWork(appContext)

            // Публикуется последним: любой поток, увидевший `initialized == true`, гарантированно
            // (по правилу happens-before для volatile в модели памяти JVM) увидит и полностью
            // сконструированные `repository`/`configStore`/`scope`, записанные выше.
            initialized = true
        }
    }

    /** Обновляет конфиг retention/limit "на лету", без повторной инициализации. Любой параметр можно опустить, чтобы оставить его без изменений. */
    fun updateConfig(retentionDays: Int? = null, maxEventCount: Int? = null): Unit = guarded("updateConfig()", Unit) {
        retentionDays?.let { configStore.retentionDays = it }
        maxEventCount?.let { configStore.maxEventCount = it }
    }

    /**
     * Трекает событие. Возвращает управление немедленно — запись происходит асинхронно на
     * фоновом диспетчере, поэтому вызывающий поток не блокируется никогда, независимо от того,
     * кто вызвал. Безопасно вызывать конкурентно из множества потоков подряд без потери данных.
     *
     * Если вызвано до [init], событие отбрасывается и пишется предупреждение в лог: ошибочно
     * ранний вызов не должен ронять вызывающий код, но очереди "до инициализации" в этом SDK нет.
     */
    fun track(name: String, properties: Map<String, String> = emptyMap()): Unit = guarded("track(\"$name\")", Unit) {
        scope.launch { repository.trackEvent(name, properties) }
    }

    /** Последние события, сначала новые, ограничено значением [limit] (снизу ограничено 1). */
    fun getRecentEvents(limit: Int = 50): Flow<List<TrackedEvent>> =
        guarded("getRecentEvents()", emptyFlow()) { repository.observeRecentEvents(limit) }

    /** Общее количество / сегодняшнее количество / разбивка по дням. Всегда выполняется в фоновом потоке. */
    suspend fun getStatistics(dayWindow: Int = 7): EventStatistics =
        guardedSuspend("getStatistics()", EventStatistics(totalCount = 0, todayCount = 0, byDay = emptyList())) {
            repository.getStatistics(dayWindow)
        }

    /**
     * Удаляет все события, которые UI уже показал через [getRecentEvents]. События, ещё ни
     * разу не доставленные в этот flow, остаются нетронутыми — точную семантику "seen" смотри
     * в `EventRepositoryImpl`.
     */
    suspend fun clearAllEvents() = guardedSuspend("clearAllEvents()", Unit) {
        repository.clearAllEvents()
    }

    /**
     * Каждому публичному методу нужна одна и та же проверка "а init() вообще уже отработал?"
     * перед обращением к lateinit-полям `repository`/`configStore`/`scope` — вынесено в эти два
     * небольших хелпера вместо копипасты `if (!initialized) {...}` в каждом методе, чтобы будущий
     * метод не мог скомпилироваться, забыв про эту проверку.
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
