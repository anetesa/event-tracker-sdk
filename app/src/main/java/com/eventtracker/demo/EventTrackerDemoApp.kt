package com.eventtracker.demo

import android.app.Application
import com.eventtracker.demo.data.DemoConfigRepository
import com.eventtracker.sdk.EventTrackerSDK
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Initializes the SDK on every process wake-up (not just first install/run), passing whatever
 * retention period / event limit the user has configured previously — satisfies the "app must
 * initialize the SDK on each process wake-up... with the configured retention period and the
 * event limit count" requirement.
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
