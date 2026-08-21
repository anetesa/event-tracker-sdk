# AI Communication Log

## Tools used

- **Claude Code** (Anthropic CLI agent), running model **Claude Sonnet 5**, in an interactive
  terminal session with file read/write/edit access plus shell (Gradle) tooling. Every line of
  source code, every test, and this document itself came out of that single tool across one
  session.

### Specific Claude Code capabilities used

- **Plan Mode** (`EnterPlanMode`/`ExitPlanMode`) — invoked once, before a single line of code
  existed, to draft the full architecture and get explicit sign-off on it: module layout, public
  API, Room schema, the "seen event" mechanism, WorkManager wiring, edge cases, a test plan, and
  a build order.
- **A "Plan" sub-agent** (the `Agent` tool, `subagent_type: "Plan"`) — spawned once during that
  plan-mode pass with the specific job of drafting the architecture and checking it against the
  spec's requirements before it went up for approval; what it produced became the approved plan
  almost word-for-word.
- **`AskUserQuestion`** — used exactly once, before any scaffolding began, to settle a genuine
  fork the assistant had no way to infer on its own: build the exam as a brand-new project, or
  repurpose an unrelated, already-existing Android practice repo on the same GitHub account
  (different `minSdk`, unrelated modules). The user chose "new project."
- **`Monitor`** — an asynchronous log/condition watcher, called on repeatedly to wait out
  long-running local Gradle test runs and GitHub Actions CI runs without sitting in a blocking
  polling loop, freeing the assistant to keep working and get notified only when something
  actually changed or finished.
- Ordinary **Bash** (Gradle, `git`, the GitHub CLI `gh`) plus the editor's **Read/Write/Edit**
  file tools handled everything else: writing source files, running builds and tests, tracking
  down the ViewModel-test hang with `jstack`, standing up the private GitHub repo, and managing
  commits and pushes.

### What was *not* used, and why

This machine's Claude Code setup also exposes a sizable "Ruflo" MCP toolset globally — swarm
orchestration, a shared memory/agent-coordination server, dozens of `mcp__ruflo__*` tools — plus a
library of pre-built Skills. None of that got touched for this task. The exam boils down to a
single developer, a single session, a same-day deadline, and a mostly linear dependency chain
(schema → repository → facade → worker → app → tests); there was nothing here that was genuinely
parallel or complex enough to justify multi-agent coordination, nor any repeatable workflow that
would have made reaching for a packaged Skill worth more than just writing the code directly.
Pulling in swarm/multi-agent tooling would have added coordination overhead with nothing to show
for it — a call worth stating outright rather than quietly leaving out.

## Process

The spec went in whole, and the instruction was to plan before writing any code — a "plan mode"
pass where the assistant worked out a full architecture (module layout, public API surface, Room
schema, the "seen event" mechanism, WorkManager wiring, edge cases, a test plan, a build order),
which then got reviewed and signed off before implementation began. From there, work followed
roughly that planned order: SDK models, then the Room layer, the repository, the public facade,
the WorkManager worker, SDK unit tests, the demo app skeleton, DI, UI, the ViewModel, ViewModel
tests, and finally the docs.

## Key prompts

- The complete exam spec, pasted verbatim, together with an instruction to plan first rather
  than jump straight to implementation.
- A detailed architecture-design prompt (used internally — it doesn't appear in the final code)
  spelling out: module/package layout, exact public API shapes, Room schema and query design, the
  "seen event" mechanism end to end, WorkManager scheduling details, a concrete list of edge
  cases, a pragmatic testing plan sized to the same-day deadline, and a build order — with an
  explicit instruction to flag any close judgment call rather than quietly pick a side.
- Everything after that was mostly direct, file-by-file execution of the approved plan, not
  open-ended "figure it out yourself" prompting.

## Where AI helped successfully

- **Architecture and SOLID decomposition**: the SDK was split into `EventTrackerSDK` (a thin
  facade), `EventRepository`/`EventRepositoryImpl` (business rules), `EventDao` (pure SQL),
  `SdkConfigStore`, `CleanupWorker`, and a handful of small single-purpose seams (`Clock`,
  `IdGenerator`, `PropertiesJsonCodec`). Having this proposed and justified up front kept the
  implementation phase mechanical rather than needing a mid-course redesign.
- **A real correctness bug caught before it was ever written**: the day-grouping SQL query. The
  schema calls for a precomputed `createdDate` string in `DD/MM/YYYY` format, used for grouping. A
  naive `GROUP BY createdDate ORDER BY createdDate DESC` looks fine but isn't —
  `"01/12/2025"` sorts *before* `"25/11/2025"` as plain text, even though 25 Nov comes first
  chronologically. This surfaced during planning, before any SQL existed, and the query instead
  orders by `MAX(timestamp)` while still grouping on the string column. A dedicated test
  (`EventDaoTest.getGroupedByDay orders by most recent day even when date strings sort lexicographically wrong`)
  locks in that exact scenario against a real in-memory Room database.
- **The "seen event" protection mechanism**: the spec's subtlest requirement — "an event is not
  eligible for clearing if the user never saw it" — turned into a concrete, testable design: an
  `isSeen` column that flips only once a row has actually reached the UI's observed `Flow`, with
  `clearAllEvents()` scoped to `isSeen = true` rows and deliberately kept apart from the automatic
  retention/count cleanup path. A dedicated test cross-checks this by asserting that an event
  never delivered to `observeRecentEvents()` survives `clearAllEvents()`.
- Boilerplate that would otherwise have eaten real time — Gradle module and version-catalog
  wiring, Room DAO/entity scaffolding, Hilt module wiring, Compose layout — came together quickly
  and stayed consistent with the plan throughout.

## Where AI got it wrong (verified and fixed)

1. **A literal control byte in place of an escape sequence.** While writing the escape-sequence
   handling for the hand-rolled JSON decoder (`PropertiesJsonCodec.kt`), the source generated for
   the `\f` (form-feed) JSON escape case ended up holding an actual raw form-feed *byte* between
   the quotes instead of the intended two-character Kotlin escape — an artifact of how that one
   escape sequence got transcribed while the file was written, not a logic mistake:

   ```kotlin
   // AI-generated (this is what it looked like visually, but byte-for-byte the file did NOT
   // contain this text — the character between the quotes was a literal 0x0C byte):
   'f' -> sb.append('\f')
   ```

   ```kotlin
   // Corrected — found by scanning every .kt file for stray control bytes (anything below
   // 0x20 other than \t \n \r), then repaired with a byte-level replacement, since a plain
   // text-based find/replace kept failing to match:
   'f' -> sb.append('\u000C')
   ```

   This is precisely the sort of subtle, easy-to-miss defect the exam's "address edge cases" and
   "verify AI-generated code" instructions are pointing at — left unchecked, it would have been
   either an invisible compile error or, worse, a silently wrong character at runtime.
   **Verification**: after fixing it, a scan of the whole repository confirmed no other `.kt`/`.kts`
   file carried stray control bytes, and `PropertiesJsonCodecTest` — including a round-trip test
   covering quotes, backslashes, newlines, and Unicode — passed.

2. **Tautological, dead test assertions on the first pass.** Two early rounds of test-writing
   produced assertions that compiled and "passed" without actually checking anything:

   ```kotlin
   // AI-generated first pass (EventRepositoryImplTest) — this always passes no matter what
   // `stored.timestamp` actually holds, since both sides of the comparison are the same
   // string literal:
   assertEquals("button_clicked", stored.timestamp.let { "button_clicked" })
   ```

   ```kotlin
   // Corrected — actually asserts on the field, plus the properties round-trip:
   assertEquals("button_clicked", stored.name)
   assertEquals(baseMillis, stored.timestamp)
   assertEquals(mapOf("screen" to "home"), PropertiesJsonCodec.decode(stored.propertiesJson))
   ```

   A near-identical dead-assertion pattern turned up once more in `EventDaoTest` and got the same
   treatment — asserting on an actual queried value rather than a self-referential `.let {}`
   chain. **Verification**: this was only caught by re-reading each test file right after writing
   it, before ever running the suite — the test framework itself didn't flag it, since both
   versions compile and the broken one "passes" trivially. It's a good reminder that a green test
   suite on its own isn't proof of anything; someone still has to read the assertions, not just
   run them.

## How AI-generated code was verified

- `./gradlew :eventtrackersdk:compileDebugKotlin :app:compileDebugKotlin`, run once the SDK and
  demo app skeletons were in place, to surface wiring/type errors early rather than at the very
  end.
- `./gradlew testDebugUnitTest` across both modules — repository logic checked against a fake
  in-memory DAO, real SQL correctness (day-grouping, deletion, the seen flag) checked against an
  in-memory Room database via Robolectric, JSON codec round-trips, SDK init-idempotency checked
  against a real (test-mode) WorkManager instance, and ViewModel behavior checked against a fake
  `SdkGateway`.
- `./gradlew assembleDebug`, to confirm both modules actually build the way they're configured to
  — among other things, that the SDK module carries no accidental Hilt/Compose dependency, which
  would have quietly undermined the whole framework-agnostic design.
- A manual smoke test on the demo app: all four action buttons, config changes taking effect
  live, "Clear All Events" leaving genuinely-unseen rows untouched, and a process kill/relaunch
  neither duplicating the WorkManager job nor losing any tracked events.
- A repository-wide sweep for stray control-byte artifacts (see bug #1 above), run after the
  first one turned up, to rule out the same transcription glitch elsewhere.

## Time saved vs. time spent debugging AI output

Hand-scaffolding a two-module Gradle project — the version catalog, both `build.gradle.kts`
files, Room/DAO boilerplate, Hilt wiring, Compose layout — would reasonably take longer than
reviewing and lightly correcting AI-generated equivalents of the same. The architecture-planning
pass stands out in particular: it surfaced both the date-string sort bug and the seen/clear
design before any code existed, and that class of bug is normally expensive to catch later via a
failing test rather than during design review, so it likely paid for itself several times over.
On the other side of the ledger, the two bugs documented above — the stray form-feed byte and the
tautological test assertions — both needed a manual re-read to catch; neither "the code compiles"
nor, in the tautological-assertion case, "the tests pass" would have caught them. That re-reading
step is the actual cost of relying on AI-generated code here, and it was treated as mandatory
rather than optional for exactly that reason.
