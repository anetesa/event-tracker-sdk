package com.eventtracker.demo.ui

import com.eventtracker.demo.data.SdkGateway
import com.eventtracker.sdk.model.EventStatistics
import com.eventtracker.sdk.model.TrackedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Подход: рукописный фейк [SdkGateway] (не мок) для тестов [EventsViewModel].
 *
 * Почему фейк, а не MockK: во всех тестах [EventsViewModelTest] от этой зависимости нужно не
 * застабленное значение на один вызов, а накапливаемое поведение во времени — список всех
 * `track()`-вызовов для последующих проверок (`trackCalls`), управляемый вручную Flow событий
 * (`emitEvents`), настраиваемая статистика (`statisticsToReturn`) и счётчик очисток. Выразить это
 * через `every {}`/`verify {}` на моке было бы менее наглядно, чем прочитать простое состояние
 * маленького реального класса. Для соседней зависимости, `DemoConfigRepository`, наоборот,
 * достаточно `mockk(relaxed = true)` — там нужно лишь вернуть два числа и проверить пару присвоений,
 * без накопления истории и без Flow.
 */
internal class FakeSdkGateway : SdkGateway {

    data class TrackCall(val name: String, val properties: Map<String, String>)
    data class ConfigUpdate(val retentionDays: Int?, val maxEventCount: Int?)

    val trackCalls = mutableListOf<TrackCall>()
    val configUpdates = mutableListOf<ConfigUpdate>()
    var clearAllCallCount = 0
        private set

    private val eventsFlow = MutableStateFlow<List<TrackedEvent>>(emptyList())
    var statisticsToReturn: EventStatistics = EventStatistics(0, 0, emptyList())

    override fun track(name: String, properties: Map<String, String>) {
        trackCalls += TrackCall(name, properties)
    }

    override fun recentEvents(limit: Int): Flow<List<TrackedEvent>> = eventsFlow

    override suspend fun statistics(dayWindow: Int): EventStatistics = statisticsToReturn

    override suspend fun clearAllEvents() {
        clearAllCallCount++
    }

    override fun updateConfig(retentionDays: Int?, maxEventCount: Int?) {
        configUpdates += ConfigUpdate(retentionDays, maxEventCount)
    }

    fun emitEvents(events: List<TrackedEvent>) {
        eventsFlow.value = events
    }
}
