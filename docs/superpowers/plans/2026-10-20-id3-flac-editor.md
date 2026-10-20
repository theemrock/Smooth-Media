# MP3/FLAC Metadata Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a safe, explicit MP3/FLAC tag and artwork writer while preserving Room as canonical catalog and keeping scanning read-only.

**Architecture:** An application-owned descriptor-based file-tag interface isolates Kyant0/taglib from SAF, Room and UI. A write session validates location identity and permissions, performs one bounded write, reads back and hashes the result, then reconciles Room in a transaction.

**Tech Stack:** Kotlin, Android SAF/ParcelFileDescriptor, Kyant0/taglib 1.0.6, Room, Jetpack Compose, JUnit/Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-10-20-id3-flac-editor-design.md`

## Global Constraints

- Only user-selected MP3/FLAC locations are writable.
- Scanning and ordinary enrichment never invoke the writer.
- Excluded, unavailable, permission-lost or stale locations are rejected.
- Physical writes require explicit user action and read-back verification.
- Room is updated only after verified write success.
- Existing production music files are never used as write fixtures.
- Batch execution is bounded to 100 items.

## Review Focus

- SAF provider gives a non-seekable or read-only descriptor: reject without Room mutation.
- TagLib save returns success but read-back differs: reject and retain old Room metadata/hash.
- Turkish characters and multiple artwork entries: preserve through round-trip.
- A stale current hash or excluded location: reject before opening for write.
- Partial batch/cancellation: completed items persist, remaining items stay retryable.

### Task 1: Dependency, architecture and license records

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `docs/architecture/FINAL_ARCHITECTURE.md`
- Create: `docs/reports/SMOOTH_MEDIA_ID3_EDITOR_REFERENCE_REVIEW_20261020.md`

Add Kyant0/taglib 1.0.6 and update the normative metadata sections to permit explicit verified file writes while retaining Room canonical semantics. Record upstream TagLib LGPL/MPL and wrapper Apache-2.0 notices plus the three reviewed repositories without copying their code.

### Task 2: Descriptor tag adapter

**Files:**
- Create: `app/src/main/java/com/theemrock/smoothmedia/metadata/FileTagAdapter.kt`
- Create: `app/src/main/java/com/theemrock/smoothmedia/metadata/KyantTagLibAdapter.kt`
- Test: `app/src/test/java/com/theemrock/smoothmedia/metadata/FileTagAdapterTest.kt`

Define `read(fd): FileTagSnapshot` and `write(fd, change): FileTagWriteResult`, preserving unrelated tags and pictures. Add unit coverage for field mapping and failure normalization; keep native descriptor lifecycle with the caller.

### Task 3: SAF write preconditions and verified session

**Files:**
- Create: `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataFileWriteSession.kt`
- Test: `app/src/test/java/com/theemrock/smoothmedia/metadata/MetadataFileWriteSessionTest.kt`

Validate source/location/binding/exclusion/hash preconditions, perform one item at a time, verify read-back and current hash, and return per-item status without touching Room on failure.

### Task 4: Room reconciliation and batch state

**Files:**
- Modify: metadata entities/DAO/database migration files as required
- Create: `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataFileWriteReconciler.kt`
- Test: `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/MetadataFileWriteRoomTest.kt`

Persist verified raw/normalized metadata, current hash and durable per-item write result. Preserve user overrides by default; support an explicit clear-override choice. Add bounded resumable batch behavior and cancellation tests.

### Task 5: Replace obsolete Source metadata controls with Tag Editor entry point

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/ui/SourceManagementScreen.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/MainActivity.kt`
- Modify: localized strings
- Test: existing UI tests plus a new editor state test if needed

Remove audit/dry-run/autonomous-apply controls from the normal Source screen. Keep only read/enrichment status needed by the catalog and expose explicit editor/write progress. Keep genre backfill hidden unless a database repair action still consumes it.

### Task 6: Verification and report

Run focused JVM tests, Android Room/instrumentation tests where available, compile/build, and inspect the diff. Do not install or run a write pilot against the user’s catalog. Record actual results and remaining device-only gaps.
