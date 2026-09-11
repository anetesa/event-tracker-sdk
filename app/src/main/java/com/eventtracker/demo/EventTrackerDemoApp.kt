package com.eventtracker.demo

import android.app.Application
import com.eventtracker.demo.data.DemoConfigRepository
import com.eventtracker.sdk.EventTrackerSDK
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The SDK auto-initializes itself on process start via its bundled `ContentProvider` (see
 * `EventTrackerInitProvider` in the `:eventtrackersdk` module), using
 * `app/src/main/assets/event_tracker_config.json` if present — so this class no longer calls
 * `EventTrackerSDK.init()` directly; by the time `onCreate()` runs here, that has already
 * happened. What this class still needs to do on every process wake-up is push whatever retention
 * period / event limit the *user* has actually configured through the demo's settings screen,
 * since that lives in [DemoConfigRepository] and can't be known to the SDK ahead of time — a live
 * [EventTrackerSDK.updateConfig] call, not a fresh `init()`.
 */
@HiltAndroidApp
class EventTrackerDemoApp : Application() {

    @Inject
    lateinit var configRepository: DemoConfigRepository

    override fun onCreate() {
        super.onCreate()
        EventTrackerSDK.updateConfig(
            retentionDays = configRepository.retentionDays,
            maxEventCount = configRepository.eventLimit,
        )
        EventTrackerSDK.track("process_wake_up")
    }
}
