# Event Tracker SDK

A take-home technical exam: an Android event-tracking SDK (`:eventtrackersdk`, packaged as an
Android Library module) alongside a demo app (`:app`) that puts it through its paces.

## Modules

- **`:eventtrackersdk`** — the SDK itself. No Hilt, no Compose, no DI-framework dependency of any
  kind — a self-contained, drop-in library. Its only dependencies are Room, WorkManager, and
  kotlinx-coroutines.
- **`:app`** — the demo app: Kotlin, Jetpack Compose (Material 3), Hilt, a single screen.

## SDK public API

```kotlin
EventTrackerSDK.init(context, retentionDays = 7, maxEventCount = 100) // once per process; later calls are no-ops
EventTrackerSDK.updateConfig(retentionDays = 14, maxEventCount = 200) // live update, no re-init needed
EventTrackerSDK.track("button_clicked", mapOf("button_id" to "submit")) // non-blocking, thread-safe
EventTrackerSDK.getRecentEvents(limit = 50) // Flow<List<TrackedEvent>>, newest first
EventTrackerSDK.getStatistics() // suspend, total/today/by-day counts
EventTrackerSDK.clearAllEvents() // suspend, deletes only events the UI has displayed
```

## Design notes

- **Thread safety and a non-blocking `track()`**: the SDK keeps its own
  `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. Calling `track()` just launches a coroutine
  on that scope and hands control back to the caller immediately — Room takes care of serializing
  the actual writes underneath.
- **Idempotent `init()`**: double-checked locking (a `synchronized` block plus a `@Volatile`
  flag published last) means whichever call reaches the process first wins; every subsequent
  call — even racing ones from other threads — is simply ignored.
- **The "seen" rule**: `EventEntity.isSeen` starts out `false` and only becomes `true` once a
  batch of rows has actually reached `getRecentEvents()`'s collector — in other words, once the
  UI has displayed them. `clearAllEvents()` deletes exclusively the rows where `isSeen = true`.
  Because the demo's displayed list is capped to the configured max event count, a stress-tracked
  burst that pushes the total past that ceiling leaves the oldest overflow rows genuinely unseen,
  and therefore provably shielded from "Clear All." The full reasoning — including why the
  automatic retention/count cleanup deliberately ignores this flag — lives in the doc comments on
  `EventsViewModel` and `CleanupWorker`.
- **WorkManager**: `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)` on a
  24-hour period with a 1-hour initial delay. The `KEEP` policy guarantees that re-initializing
  the SDK across process restarts never spawns a duplicate job.
- **JSON encoding**: properties get encoded through a small hand-rolled codec
  (`PropertiesJsonCodec`) rather than `org.json.JSONObject`. Properties are always a flat
  `Map<String, String>`, and `org.json`'s real Android implementation throws under plain JUnit
  (it only cooperates with Robolectric), which would mean dragging in a heavier test setup for no
  real payoff here.

## Building & testing

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

For the AI-assisted development log, see `AI_COMMUNICATION.md`; known gaps are tracked in
`TODO.md`.
