# TODO / Known Gaps

Listed per the exam's "list of TODOs in case you do not finish the task" requirement.

## Judgment calls worth a second look

- **"Seen" protection scope**: `clearAllEvents()` only deletes events the UI has displayed
  (`isSeen = true`); the automatic WorkManager retention/count cleanup deliberately does *not*
  check `isSeen` (see `CleanupWorker` doc comment for the reasoning). If a reviewer intends the
  protection to also cover automatic cleanup, it's a one-line change: add `AND isSeen = 1` to
  `deleteOlderThan`/`deleteExceedingCount` in `EventDao`.
- The demo's displayed event list is capped to the configured max-event-count value (not an
  independent "page size"), so the "seen" rule has something real to demonstrate. Spec's exact
  wording ("display all events") could be read either way — see `EventsViewModel` doc comment.
- **The "seen" invariant is a convention, not a structural guarantee.** It's enforced by exactly
  one place (`EventRepositoryImpl.observeRecentEvents`'s `onEach{}`), and `EventDao` is `internal`
  rather than private to that class — `CleanupWorker` already reaches `EventDao` directly,
  bypassing `EventRepository` (by design, since it deliberately skips seen-checking). If a future
  change adds another read path against `EventDao` (a debug screen, a new SDK method), nothing in
  the type system stops it from silently never marking rows seen. Not fixed now — would mean
  wrapping `EventDao` behind a decorator or moving seen-tracking state into the repository itself,
  which felt like more architecture than this exam's scope warranted — but worth flagging for
  anyone extending this code.
- **MVVM, not strict MVI, in the demo app.** `EventsViewModel` exposes a single `StateFlow<EventsUiState>`
  (the "state" half of MVI) but plain public methods (`onTrackEventClicked()`, `onApplyConfigClicked()`,
  etc.) instead of a `sealed interface EventsIntent` + one `onIntent(intent)` dispatch entry point,
  and there's no one-shot effect channel (no navigation, and no error/snackbar events are surfaced
  today). MVI's usual payoff — intents as replayable/loggable data, one funnel for every state
  mutation, an effect channel for exactly-once events — has little to bite on here: one screen, no
  navigation, no multi-step flows, no effects to sequence. Each handler is already independently
  unit-tested (`EventsViewModelTest`), so MVI's testability argument doesn't add much either. Adding
  the ceremony would be process for its own sake rather than solving a real problem this app has.
  Would reconsider if the screen grows navigation targets or needs one-shot UI events (e.g. a
  snackbar on a failed action).
- **Package-level layering, not a domain/data Gradle module split, inside `:eventtrackersdk`.**
  The SDK separates concerns via packages (`model`, `internal.repository`, `internal.db`,
  `internal.work`, `internal.config`/`internal.util`) rather than further splitting into e.g.
  `:eventtrackersdk:domain` / `:eventtrackersdk:data` submodules the way a *feature* inside a larger
  multi-module consumer app might. Reasoning: `:eventtrackersdk` **is** the single required
  deliverable ("the SDK must be an Android Library module," singular) — turning it into several
  glued-together modules would fragment that one artifact rather than clarify it, for a codebase
  small enough (~15 Kotlin files) that packages already give the same isolation (an `EventRepository`
  interface for DIP, `internal` visibility keeping Room out of the public API) without the extra
  Gradle wiring. Would reconsider only if the SDK grew enough independent features to want separate
  build/test cycles per layer.

## Not implemented / out of scope for the exam's time budget

- No Compose UI instrumented tests (`androidTest`) — covered instead by ViewModel unit tests with
  a fake `SdkGateway`.
- No full WorkManager scheduling integration test on a real device/emulator (`enqueueUniquePeriodicWork`'s
  actual 24h/1h timing is a framework guarantee, not app logic); only unit-tested that a second
  `init()` doesn't create a duplicate job entry.
- No custom launcher icon/adaptive icon — the demo app builds and runs without one (AGP doesn't
  require it), but a polished submission would add one.
- No localization — UI strings are English-only (out of scope for the spec).
- No explicit user-facing validation error message when config input is non-numeric — it silently
  falls back to the previously persisted value rather than showing an inline field error.
- No proguard/R8 keep-rules audit for the release build variant beyond the defaults AGP provides;
  only the debug build was manually smoke-tested.
- Doze/battery-optimization impact on the periodic cleanup job's real-world timing is understood
  theoretically but not verified on a physical device.
