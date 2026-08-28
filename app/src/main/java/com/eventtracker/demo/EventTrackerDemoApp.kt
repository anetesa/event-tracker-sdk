package com.eventtracker.demo

import android.app.Application
import com.eventtracker.demo.data.DemoConfigRepository
import com.eventtracker.sdk.EventTrackerSDK
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Инициализирует SDK при каждом пробуждении процесса (не только при первой установке/запуске),
 * передавая тот период retention / лимит событий, который пользователь ранее настроил —
 * удовлетворяет требованию "приложение должно инициализировать SDK при каждом пробуждении
 * процесса... с настроенным периодом retention и лимитом количества событий".
 */
@HiltAndroidApp
class EventTrackerDemoApp : Application() {

    @Inject
    lateinit var configRepository: DemoConfigRepository

    override fun onCreate() {
        super.onCreate()
        EventTrackerSDK.init(
            context = this,
            retentionDays = configRepository.retentionDays,
            maxEventCount = configRepository.eventLimit,
        )
        EventTrackerSDK.track("process_wake_up")
    }
}
