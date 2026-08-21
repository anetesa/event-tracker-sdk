# TODO / Known Gaps

Provided per the exam's request for "a list of TODOs in case you do not finish the task."

## Judgment calls worth a second look

- **Scope of "seen" protection**: `clearAllEvents()` deletes only events the UI has already
  displayed (`isSeen = true`); the automatic WorkManager retention/count cleanup deliberately
  leaves `isSeen` unchecked (see the `CleanupWorker` doc comment for the full reasoning). If a
  reviewer expects that protection to extend to automatic cleanup too, it's a one-line change —
  add `AND isSeen = 1` to `deleteOlderThan`/`deleteExceedingCount` in `EventDao`.
- The demo caps its displayed event list to the configured max-event-count rather than treating
  it as an independent "page size," which gives the "seen" rule something concrete to
  demonstrate. The spec's literal phrasing ("display all events") is arguably ambiguous either
  way — see the `EventsViewModel` doc comment for the full argument.
- **The "seen" invariant is upheld by convention, not by the type system.** Exactly one place
  enforces it — the `onEach{}` inside `EventRepositoryImpl.observeRecentEvents` — and `EventDao`
  is `internal` rather than private to that class, so `CleanupWorker` already reaches it directly,
  sidestepping `EventRepository` (by design, since it's meant to skip the seen-check). Nothing
  stops a future read path against `EventDao` — a debug screen, a new SDK method — from silently
  never marking rows seen. Left unaddressed here: a proper fix would wrap `EventDao` behind a
  decorator or move seen-tracking into the repository itself, and that felt like more
  architecture than this exam warranted — but it's worth flagging for whoever extends this code
  next.
- **MVVM rather than strict MVI in the demo app.** `EventsViewModel` does expose a single
  `StateFlow<EventsUiState>` — MVI's "state" half — but relies on plain public methods
  (`onTrackEventClicked()`, `onApplyConfigClicked()`, and so on) rather than a
  `sealed interface EventsIntent` funneled through one `onIntent(intent)` entry point, and there's
  no one-shot effect channel (no navigation exists, and nothing surfaces error/snackbar events
  yet). MVI's usual selling points — intents as replayable, loggable data; a single funnel for
  every state mutation; an effect channel for exactly-once events — don't have much to grip onto
  here: it's one screen, no navigation, no multi-step flows, nothing that needs sequencing. Every
  handler already has its own unit test (`EventsViewModelTest`), so MVI's testability argument
  doesn't buy much extra either. Layering it on would be process for its own sake rather than a
  response to an actual problem. Worth revisiting if the screen ever grows navigation targets or
  needs one-shot UI events, like a snackbar after a failed action.
- **Package-level layering inside `:eventtrackersdk` rather than a domain/data Gradle module
  split.** The SDK separates its concerns through packages — `model`, `internal.repository`,
  `internal.db`, `internal.work`, `internal.config`/`internal.util` — instead of further carving
  itself into, say, `:eventtrackersdk:domain` / `:eventtrackersdk:data` submodules, the way a
  *feature* embedded in a larger multi-module app might be structured. The reasoning:
  `:eventtrackersdk` already **is** the one deliverable the spec asks for ("the SDK must be an
  Android Library module," singular) — splitting it into several interdependent modules would
  fragment that single artifact rather than sharpen it, for a codebase small enough (roughly 15
  Kotlin files) that packages already buy the same isolation — an `EventRepository` interface for
  dependency inversion, `internal` visibility keeping Room out of the public surface — without the
  added Gradle wiring. Worth reconsidering only if the SDK eventually grows enough independent
  features to justify separate build/test cycles per layer.

## Not implemented / out of scope given the exam's time budget

- No Compose UI instrumented tests (`androidTest`); ViewModel unit tests against a fake
  `SdkGateway` stand in for that coverage instead.
- No full WorkManager scheduling integration test on a real device or emulator — the actual
  24h/1h timing behind `enqueueUniquePeriodicWork` is a framework guarantee rather than app
  logic, so only the "second `init()` doesn't create a duplicate job" behavior is unit-tested.
- No custom launcher icon or adaptive icon; the demo app builds and runs fine without one (AGP
  doesn't require it), though a more polished submission would include one.
- No localization — UI strings are English-only, which the spec doesn't ask to change.
- No inline validation error shown to the user for non-numeric config input; it quietly falls
  back to the previously persisted value instead of surfacing a field-level error.
- No proguard/R8 keep-rules audit for the release build variant beyond AGP's own defaults — only
  the debug build got a manual smoke test.
- The real-world impact of Doze/battery optimization on the periodic cleanup job's timing is
  understood in theory but hasn't been confirmed on a physical device.
