# P01-04B Real SAF Instrumentation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicitly enabled connected-device test that runs the existing SAF discovery against one preselected, persisted test tree.

**Architecture:** The AndroidJUnit4 test reads one `ACTIVE` `SAF_TREE` row from the existing Room database file using SQLite `OPEN_READONLY` (bypassing Room callbacks/migrations), then checks its persisted read grant through `AndroidTreePermissionGateway`. It calls `SelectedAudioDiscovery` with `AndroidSafDocumentTreeReader`, asserts controlled fixture results and repeat-scan uniqueness, and never writes Room or files.

**Tech Stack:** Kotlin, AndroidJUnit4, Android SAF `DocumentsContract`, existing Room source registry, JUnit assumptions.

**Spec:** User request P01-04B; authority `docs/architecture/FINAL_ARCHITECTURE.md` §§8–11, 89–91, 101, 119, 127; P01-03 source-registry spec.

## Global Constraints

- Test scope is only the explicitly user-selected `SmoothMediaSafDiscoveryTestRoot` tree and its descendants.
- Do not edit production code/UI, user audio files, Room data, `local.properties`, or Git history/remotes.
- Missing opt-in, source, test-root identity, or persisted grant reports SKIP, never PASS.
- Once the marked root and grant are present and opt-in is enabled, missing/misplaced fixtures fail; they are not classified as a product PASS.
- Do not use MediaStore or whole-device discovery.

## Review Focus

- Accidental run without consent → JUnit assumption skip unless `enableRealSafDiscovery=true`.
- Wrong source selected → skip unless provided source ID resolves to an active SAF row whose root display name is the dedicated test marker.
- Permission revoked/provider unavailable, including between preflight and listing → skip with an explicit reason.
- Scope escape or duplicate results → assert all discovered document URIs retain the selected tree ID; assert unique document IDs and identical repeated-run identities.
- Missing/misplaced MP3/FLAC fixtures after opt-in → fail with the exact missing fixture; test root/grant absence remains SKIP.

## Files

- Create: `app/src/androidTest/java/com/theemrock/smoothmedia/discovery/SelectedAudioDiscoveryRealSafTest.kt`
- Create: this implementation plan for in-work checkpoints.

### Task 1: Opt-in real-provider SAF discovery test

**Interfaces:** Read the existing source row via SQLite `OPEN_READONLY`; consume `AndroidTreePermissionGateway`, `AndroidSafDocumentTreeReader`, and `SelectedAudioDiscovery`; instrumentation arguments `enableRealSafDiscovery=true` and `testSafSourceId=<existing source ID>`.

- [x] Add one AndroidJUnit4 test with assumptions for opt-in, source row/state/type, root marker and persisted permission; assert the required fixture set.
- [x] Verify through real SAF child queries that the MP3 is a direct root child and the FLAC is in `nested`; discover twice and assert formats/URIs, no duplicate document IDs, stable identities across runs, and selected tree ID on every result.
- [x] Run `testDebugUnitTest` and `assembleDebugAndroidTest`.
- [ ] No-argument connected invocation was not run; explicit opt-in was used for the real-provider check, and absent-source/permission conditions remain guarded by JUnit assumptions.
- [x] Run with the prepared test root and existing source ID; real-device result: PASS.

### Task 2: P01-04C nested SAF document ID correction

- [x] Resolve a child tree-document URI with its document ID, falling back to the tree ID only for the selected root URI.
- [x] Add root, nested and deeply nested URI regression tests.
- [x] Verify root MP3 and nested FLAC through the real provider and repeat discovery on the S24 Ultra; both formats were found with no duplicate document IDs.
