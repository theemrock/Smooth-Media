# ID3-12 Production Metadata Write Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Connect the editor to a gate-protected SAF-to-cache staging flow without enabling physical production writes.

**Architecture:** A small adapter will mirror a test-owned/fake SAF source into cache, run `IsolatedMetadataWriteCoordinator` and `KyantTagLibAdapter` only on staging, and return explicit preparation/verification/blocked/recovery results. The existing `MetadataEditorWriteGate` remains the final write boundary; blocked flows never reconcile Room or report committed.

**Tech Stack:** Kotlin, Android `ContentResolver`/`ParcelFileDescriptor`, Room reconciliation, Kyant TagLib adapter, JVM tests, androidTest compilation.

**Spec:** `docs/superpowers/specs/2026-10-20-id3-flac-editor-design.md`

## Global Constraints

- Physical production metadata writes remain disabled; `MetadataEditorWriteGate` stays `BLOCKED`.
- No device, APK, production SAF/Room, MediaStore, rescan, audio, network, or user music access.
- Test-owned/fake URI and temporary files only.
- Existing changed-field-only, FLAC audio, and PICTURE preservation behavior must remain intact.
- No unapproved dependencies or changes to `AGENTS.md`/`artifacts/`.

## Review Focus

- Gate bypass through a direct `MetadataFileWriteSession.write()` call — test boundary rejection in `MetadataEditorCoordinatorTest`.
- Stale source hash — test-owned source mutation rejects before staging commit.
- Provider capability uncertainty — fake provider commit refusal returns non-committed recovery state.
- Short backup/truncate behavior — staged rollback preserves backup and verifies full bytes.
- Room reconciliation after blocked/failed write — assert no reconciliation callback is invoked.

### Task 1: Define the gate-protected integration result and adapter contract

**Files:**
- Create: `app/src/main/java/com/theemrock/smoothmedia/metadata/SafMetadataWriteAdapter.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataEditorCoordinator.kt`
- Test: `app/src/test/java/com/theemrock/smoothmedia/metadata/SafMetadataWriteAdapterTest.kt`

- [ ] Write failing tests for mirror/preflight, blocked gate, stale source, provider capability refusal, and recovery backup retention.
- [ ] Run the focused tests and confirm the new contract fails for the missing adapter.
- [ ] Implement a test-injectable adapter that mirrors bytes to cache, invokes the existing coordinator on staging, verifies metadata/artifact invariants, and never calls a physical commit while the gate is blocked.
- [ ] Return explicit non-committed states for blocked, rejected, verification failure, and recovery-required outcomes.
- [ ] Run focused tests to green.

### Task 2: Preserve editor field semantics and Room safety

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataEditorCoordinator.kt`
- Test: `app/src/test/java/com/theemrock/smoothmedia/metadata/MetadataEditorCoordinatorTest.kt`

- [ ] Add tests proving only touched fields reach the staged adapter, unchanged properties/artwork remain, and blocked/failed results do not call reconciliation.
- [ ] Route the existing batch runner through the gate-protected adapter while retaining the current batch limit and cancellation behavior.
- [ ] Run the focused coordinator tests.

### Task 3: Final verification and checkpoint

**Files:** only the files from Tasks 1–2 and this plan if required by the checkpoint.

- [ ] Run full JVM tests and androidTest Kotlin compilation.
- [ ] Run `git diff --check` and a focused privacy scan.
- [ ] Confirm `MetadataEditorWriteGate` remains blocked and no direct write path bypasses it.
- [ ] Stage only ID3-12 implementation/tests and create a local checkpoint commit.
