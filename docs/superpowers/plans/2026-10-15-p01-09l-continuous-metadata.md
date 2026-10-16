# P01-09L Continuous Metadata Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a user-started, non-persistent continuous metadata enrichment session that reuses the existing ten-track batch/page coordinators and remains safe to stop or close.

**Architecture:** Add a pure Kotlin session coordinator above `MetadataMissingTrackPageCoordinator`. It advances by the returned keyset cursor, re-queries the missing count after every page, aggregates page counters, and stops on cancellation, exhaustion, or error. `MainActivity` owns the in-memory session lifecycle and posts immutable UI state; the existing one-page action remains available.

**Tech Stack:** Kotlin/JVM unit tests, Android Compose UI, Room DAOs, single background executor, existing Android SAF metadata reader.

**Spec:** `docs/architecture/FINAL_ARCHITECTURE.md` and the approved P01-09L design in chat.

## Global Constraints

- Room remains the canonical library and user overrides remain Room-only.
- No file tag writes, hash work, identity changes, scheduler, service, persistent cursor, or new Room migration.
- A user action is required to start; application recreation never auto-resumes.
- The existing ten-track batch and “10 Parça İşle” action remain bounded.
- SAF permission loss, inaccessible files, and cancellation preserve existing metadata and overrides.
- Real production enrichment must not be started during this turn.

## Review Focus

- A page containing only failures must advance and terminate rather than loop forever.
- Stop requested between pages must not start another Track.
- A second start while active must not create a second session.
- An exception must surface `ERROR` without losing aggregate counters.
- A new Activity instance must begin idle and never resume an old in-memory cursor.

### Task 1: Session coordinator

**Files:**
- Create: `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataContinuousSessionCoordinator.kt`
- Test: `app/src/test/java/com/theemrock/smoothmedia/metadata/MetadataContinuousSessionCoordinatorTest.kt`

**Interfaces:**
- Consumes: `MetadataMissingTrackPageCoordinator.runPage`, `MetadataBatchProgress`, and a missing-count query.
- Produces: immutable session status/progress/result types and `runSession(cancellationRequested, onPage, onState)`.

- [ ] Write failing tests for multi-page progression, failure-only termination, cancellation, and error aggregation.
- [ ] Run the new tests and observe the expected missing-symbol failure.
- [ ] Implement the minimal cursor-driven coordinator; advance after every non-empty page and stop when the next page is empty, cancelled, or throws.
- [ ] Run the coordinator tests and the existing metadata unit tests.

### Task 2: Activity/UI lifecycle integration

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/MainActivity.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/ui/SourceManagementScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/theemrock/smoothmedia/metadata/MetadataContinuousSessionCoordinatorTest.kt`

**Interfaces:**
- Consumes: Task 1 session types.
- Produces: user-started continuous control, stop guard, aggregate UI counters/status, and unchanged one-page action.

- [ ] Add failing assertions for state labels and stop/start lifecycle behavior at the coordinator boundary.
- [ ] Implement in-memory Activity state and one background executor job; no state/cursor persistence.
- [ ] Keep the one-page “10 Parça İşle” button and expose a separate “Başlat”/“Durdur” continuous action.
- [ ] Render completed/stopped/error plus page and aggregate success/skip/failure counters.
- [ ] Run JVM tests and inspect the UI wiring for no main-thread DB/file work.

### Task 3: Verification

**Files:**
- No product file changes expected.

- [ ] Run targeted JVM/UI-related tests available in the project.
- [ ] Run `./gradlew.bat testDebugUnitTest`.
- [ ] Run `./gradlew.bat assembleDebug`.
- [ ] Run `git diff --check`.
- [ ] Optionally install with `adb install -r` only if APK/device versions need alignment; verify screen opening only and do not press metadata start.
