# Event Tracker SDK

An Android event-tracking SDK (`:eventtrackersdk`, an Android Library module) plus a demo app
(`:app`) that exercises it, built as a take-home technical exam.

## Modules

- **`:eventtrackersdk`** — the SDK. No Hilt, no Compose, no dependency on any DI framework —
  it's a self-contained drop-in library. Depends only on Room, WorkManager, and
  kotlinx-coroutines.
- **`:app`** — demo app. Kotlin + Jetpack Compose (Material 3) + Hilt, single screen.

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

- **Thread safety / non-blocking track()**: the SDK owns a `CoroutineScope(SupervisorJob() +
  Dispatchers.IO)`. `track()` launches a coroutine on that scope and returns immediately; Room
  serializes the actual writes safely under the hood.
- **Idempotent init**: double-checked locking (`synchronized` + a `@Volatile` published-last
  flag) — the first call in a process wins, every later call (including concurrent racing ones)
  is a no-op.
- **The "seen" rule**: `EventEntity.isSeen` starts `false` and flips to `true` the moment a batch
  of rows is delivered to `getRecentEvents()`'s collector (i.e. displayed by the UI).
  `clearAllEvents()` only deletes rows where `isSeen = true`. The demo's displayed list is capped
  to the configured max event count, so a stress-tracked burst that pushes the total past that
  cap leaves the oldest overflow rows genuinely unseen and provably protected from "Clear All" —
  see `EventTrackerViewModel`/`CleanupWorker` doc comments for the full reasoning, including why
  automatic retention/count cleanup deliberately does *not* honor this flag.
- **WorkManager**: `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)`, 24h
  period, 1h initial delay. `KEEP` means re-initializing the SDK across process restarts never
  creates a duplicate job.
- **JSON encoding**: a small hand-rolled codec (`PropertiesJsonCodec`) instead of
  `org.json.JSONObject`, since properties are always a flat `Map<String, String>` and
  `org.json`'s real Android implementation throws under plain JUnit (Robolectric-only), which
  would force a heavier test setup for no real benefit here.

## Building & testing

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

See `AI_COMMUNICATION.md` for the AI-assisted development log and `TODO.md` for known gaps.
