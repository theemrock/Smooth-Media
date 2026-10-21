# Ghost Context Lazy Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Start a large Ghost Context without eagerly resolving every track location, while retaining full queue semantics.

**Architecture:** The domain queue remains complete in `PlaybackQueueController`. A new bounded playback-window projector resolves only a centered look-ahead slice for Media3; transitions and direct selections request a new window when needed.

**Tech Stack:** Kotlin, AndroidX Media3, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-21-ghost-context-lazy-resolution-design.md`

## Global Constraints

- Preserve the entire logical Ghost Context; never shorten it to hide latency.
- Preserve Next, Previous, repeat, shuffle, direct queue selection, and Ghost-to-User takeover.
- Do not touch production media, catalog, metadata, app version, audio volume, ADB volume, APK installation, or source permissions.
- Commit and push only this isolated branch.

## Review Focus

- Current entry absent or inaccessible must preserve domain queue state and report an error.
- A direct selected entry outside the active Media3 window must re-center safely.
- Repeat-all and shuffle must select from the full domain queue, not the window.
- A queue mutation must invalidate any stale window mapping.
- Media3 callbacks must map entry ids back to the canonical queue.

---

### Task 1: Establish a runnable focused-test command

**Files:**
- Modify only if required by a documented local Gradle configuration: project Gradle configuration.
- Test: `app/src/test/java/com/theemrock/smoothmedia/playback/PlaybackQueueTest.kt`

**Interfaces:**
- Produces: a documented command that runs the focused playback unit tests locally.

- [ ] Run the focused playback test command with the current configuration and capture the loopback failure.
- [ ] Try the minimum non-destructive Gradle daemon/client configuration needed to avoid the loopback failure.
- [ ] Re-run `PlaybackQueueTest` and record the command and result.

### Task 2: Specify and test bounded playback-window selection

**Files:**
- Create: `app/src/main/java/com/theemrock/smoothmedia/playback/PlaybackWindow.kt`
- Create: `app/src/test/java/com/theemrock/smoothmedia/playback/PlaybackWindowTest.kt`

**Interfaces:**
- Consumes: `List<QueueItem>`, selected entry id, previous/look-ahead bounds.
- Produces: `PlaybackWindow(items: List<QueueItem>, selectedIndex: Int)`.

- [ ] Write failing unit tests proving a selected middle entry yields exactly one predecessor, itself, and two successors in domain order.
- [ ] Write failing unit tests proving first/last entries clamp at boundaries without altering queue order.
- [ ] Run the focused test and observe expected missing-type failure.
- [ ] Implement the smallest immutable `PlaybackWindow` selector.
- [ ] Run the focused test and confirm it passes.
- [ ] Commit the tested window selector.

### Task 3: Use the bounded window when synchronizing Ghost Context playback

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/MainActivity.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/playback/PlaybackSessionClient.kt`
- Test: `app/src/test/java/com/theemrock/smoothmedia/playback/PlaybackWindowTest.kt`
- Test: `app/src/test/java/com/theemrock/smoothmedia/playback/PlaybackQueueTest.kt`

**Interfaces:**
- Consumes: `PlaybackWindow.select(items, entryId)`.
- Produces: a Media3 projection containing only the selected bounded window while the queue controller persists the full domain queue.

- [ ] Write a failing unit test that proves a full Ghost queue and a selected entry produce a bounded playback projection with the selected entry index preserved.
- [ ] Run focused tests and observe the failure before production changes.
- [ ] Update synchronization to resolve locations only for the bounded projection and retain the complete queue in `PlaybackQueueController`.
- [ ] Update Media3 queue state reconciliation to retain entry-id mapping for the active window.
- [ ] Run focused playback tests and the project unit-test suite.
- [ ] Commit the tested bounded synchronization.

### Task 4: Re-center on explicit queue navigation and record limitations

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/MainActivity.kt`
- Modify: `app/src/test/java/com/theemrock/smoothmedia/playback/PlaybackWindowTest.kt`
- Modify: `docs/superpowers/specs/2026-10-21-ghost-context-lazy-resolution-design.md`

**Interfaces:**
- Consumes: a domain-selected entry from Next, Previous, shuffle, or direct queue selection.
- Produces: a newly centered Media3 window without changing domain queue order.

- [ ] Write failing tests for re-centering from an arbitrary selected entry and for preserving full queue navigation order.
- [ ] Implement re-centering through the existing sync path; do not mutate queue order.
- [ ] Run focused and full unit tests.
- [ ] Record any Media3 transition-prefetch limitation honestly in the design document.
- [ ] Commit the tested navigation integration.

### Task 5: Whole-branch verification and review

**Files:**
- Verify: playback tests, full unit-test suite, diff, and Git status.

- [ ] Run the complete unit-test suite and inspect output.
- [ ] Review the branch diff against the spec’s invariants.
- [ ] Push the isolated branch after verification.
