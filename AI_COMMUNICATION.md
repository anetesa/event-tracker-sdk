# AI Communication Log

## Tools used

- **Claude Code** (Anthropic CLI agent), model **Claude Sonnet 5**, in an interactive terminal
  session with file read/write/edit and shell (Gradle) tool access. All source code, tests, and
  this document were produced through this single tool across one session.

## Process

The spec was pasted in full, then explicitly planned before any code was written (a "plan mode"
pass: the assistant drafted a full architecture — module layout, public API surface, Room schema,
the "seen event" mechanism, WorkManager wiring, edge cases, test plan, build order — and it was
reviewed and approved before implementation started). Implementation then proceeded roughly in
that planned order: SDK models → Room layer → repository → public facade → WorkManager worker →
SDK unit tests → demo app skeleton → DI → UI → ViewModel → ViewModel tests → docs.

## Key prompts

- The full exam spec, pasted verbatim, with the instruction to first plan rather than implement
  immediately.
- A detailed architecture-design prompt (used internally, not visible in the final code) covering:
  module/package layout, exact public API shapes, Room schema and query design, the "seen event"
  end-to-end mechanism, WorkManager scheduling details, a concrete edge-case list, a pragmatic
  testing plan given the same-day deadline, and an implementation build order — with an explicit
  instruction to flag close judgment calls rather than silently pick one.
- Follow-up implementation prompts were mostly direct file-by-file execution of the approved plan,
  not open-ended "figure it out" requests.

## Where AI helped successfully

- **Architecture / SOLID decomposition**: splitting the SDK into `EventTrackerSDK` (thin facade),
  `EventRepository`/`EventRepositoryImpl` (business rules), `EventDao` (pure SQL), `SdkConfigStore`,
  `CleanupWorker`, and small single-purpose seams (`Clock`, `IdGenerator`, `PropertiesJsonCodec`)
  was proposed and justified up front, which kept the implementation phase mechanical rather than
  requiring redesign partway through.
- **A real correctness bug caught before it was ever written**: the day-grouping SQL query. The
  schema requires a precomputed `createdDate` string in `DD/MM/YYYY` format for grouping. A naive
  `GROUP BY createdDate ORDER BY createdDate DESC` looks correct but is wrong — `"01/12/2025"`
  sorts *before* `"25/11/2025"` as a plain string, even though 25 Nov predates 1 Dec chronologically.
  This was identified during planning (before any SQL was written) and the query instead orders by
  `MAX(timestamp)` while still grouping on the string column. A dedicated test
  (`EventDaoTest.getGroupedByDay orders by most recent day even when date strings sort lexicographically wrong`)
  encodes this exact scenario against a real in-memory Room database.
- **The "seen event" protection mechanism**: the spec's subtlest requirement ("an event is not
  eligible for clearing if the user never saw it") was translated into a concrete, testable design:
  an `isSeen` column flipped only when a row is delivered to the UI's observed `Flow`, with
  `clearAllEvents()` scoped to `isSeen = true` rows only, deliberately kept separate from the
  automatic retention/count cleanup path. This was cross-checked with a dedicated test asserting
  that an event never delivered to `observeRecentEvents()` survives `clearAllEvents()`.
- Boilerplate that would otherwise cost real time — Gradle module/version-catalog wiring, Room
  DAO/entity scaffolding, Hilt module wiring, Compose layout — was generated quickly and
  consistently against the plan.

## Where AI got it wrong (verified and fixed)

1. **A literal control-byte instead of an escape sequence.** While writing the hand-rolled JSON
   decoder's escape-sequence handling (`PropertiesJsonCodec.kt`), the generated source for the
   `\f` (form-feed) JSON escape case ended up containing an actual raw form-feed *byte* between
   the quotes instead of the two-character Kotlin escape sequence — an artifact of how that
   specific escape sequence got transcribed while writing the file, not a logic error:

   ```kotlin
   // AI-generated (visually looked like this, but the file byte-for-byte was NOT this text —
   // the character between the quotes was a literal 0x0C byte):
   'f' -> sb.append('\f')
   ```

   ```kotlin
   // Corrected — found via a script scanning every .kt file for stray control bytes
   // (anything < 0x20 other than \t \n \r), then fixed with a byte-level replacement
   // rather than a text-based find/replace (which kept failing to match):
   'f' -> sb.append('\u000C')
   ```

   This is exactly the kind of subtle, hard-to-spot defect this exam's "address edge cases" and
   "verify AI-generated code" instructions are aimed at — it would have been an invisible,
   easy-to-miss compile error (or worse, a silently-different runtime character) if not checked.
   **Verification**: after the fix, a repository-wide scan confirmed no other `.kt`/`.kts` file
   contained stray control bytes, and `PropertiesJsonCodecTest` (including a round-trip test with
   quotes, backslashes, newlines, and Unicode) passed.

2. **Tautological/dead test assertions written on the first pass.** Two early test-writing passes
   produced assertions that compiled but didn't actually test anything meaningful:

   ```kotlin
   // AI-generated first pass (EventRepositoryImplTest) — this always passes regardless
   // of what `stored.timestamp` actually is, because both sides of the comparison are
   // the same string literal:
   assertEquals("button_clicked", stored.timestamp.let { "button_clicked" })
   ```

   ```kotlin
   // Corrected — actually asserts on the field, plus the properties round-trip:
   assertEquals("button_clicked", stored.name)
   assertEquals(baseMillis, stored.timestamp)
   assertEquals(mapOf("screen" to "home"), PropertiesJsonCodec.decode(stored.propertiesJson))
   ```

   A similar dead-assertion pattern appeared once in `EventDaoTest` and was corrected the same
   way (asserting on an actual queried value instead of a self-referential `.let {}` chain).
   **Verification**: caught by re-reading each test file immediately after writing it, before
   running the suite — not caught by the test framework itself, since both versions compile and
   the broken version "passes" trivially. This is a reminder that a green test suite is not
   sufficient evidence by itself; the assertions have to be read, not just executed.

## How AI-generated code was verified

- `./gradlew :eventtrackersdk:compileDebugKotlin :app:compileDebugKotlin` after the SDK and demo
  app skeletons were in place, to catch wiring/type errors early rather than at the very end.
- `./gradlew testDebugUnitTest` for both modules — repository logic against a fake in-memory DAO,
  real SQL correctness (day-grouping, deletion, seen-flag) against an in-memory Room database via
  Robolectric, JSON codec round-trips, SDK init-idempotency against a real (test-mode) WorkManager
  instance, and ViewModel behavior against a fake `SdkGateway`.
- `./gradlew assembleDebug` to confirm both modules actually build as configured (including that
  the SDK module has no accidental Hilt/Compose dependency leakage, which would defeat the point
  of the framework-agnostic design).
- A manual smoke-test pass on the demo app: all four action buttons, config changes taking live
  effect, "Clear All Events" leaving genuinely-unseen rows intact, process kill/relaunch not
  duplicating the WorkManager job or losing tracked events.
- A repository-wide scan for stray control-byte artifacts (see bug #1 above) after noticing the
  first one, to rule out the same transcription issue elsewhere.

## Time saved vs. time spent debugging AI output

Scaffolding a two-module Gradle project (version catalog, both `build.gradle.kts` files, Room/DAO
boilerplate, Hilt wiring, Compose layout) by hand would reasonably take longer than reviewing and
lightly correcting AI-generated equivalents; the architecture-planning pass in particular — which
surfaced the date-string sort bug and the seen/clear design before any code existed — likely saved
more time than it cost, since that class of bug is the kind that's expensive to find after the
fact via a failing test rather than during design review. Against that: the two bugs documented
above (the stray form-feed byte, and the tautological test assertions) both required a manual
re-read pass to catch — they would not have been caught by "the code compiles" or even, in the
tautological-assertion case, by "the tests pass." That re-reading step is the real cost of using
AI-generated code here, and it was treated as mandatory rather than optional for exactly that
reason.
