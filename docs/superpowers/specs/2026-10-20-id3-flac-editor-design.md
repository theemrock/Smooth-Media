# MP3/FLAC Metadata Editor Design

## Decision

Smooth Media may write embedded metadata and artwork to user-selected MP3 and
FLAC files. This supersedes the earlier Room-only metadata-edit rule in
`FINAL_ARCHITECTURE.md`.

Room remains the canonical catalog and user-override store. A successful file
write is followed by a controlled read-back and a catalog refresh; a failed or
unverified write must not update canonical file metadata or silently change
track identity.

## Scope

- Supported physical formats: MP3 and FLAC only.
- Supported fields for the first writer: title, artist, album, album artist,
  genre values, track number, disc number, year/date, comment and embedded
  artwork.
- Single-track and bounded batch editing are supported.
- Existing user-selected SAF roots remain the only write scope.
- No write is allowed for excluded, unavailable, permission-lost or stale
  locations.
- No automatic whole-library tag rewrite, background rewrite, or write during
  ordinary scanning.

## Components

### File tag adapter

Add a small application-owned interface that accepts a seekable file
descriptor and returns a normalized read-back snapshot. The first adapter uses
Kyant0/taglib `1.0.6` for MP3 ID3 and FLAC Vorbis Comment/Picture operations.
The adapter is isolated so a later implementation can replace it without
changing Room, SAF, or UI code.

The adapter must:

- open the descriptor read/write only after the caller has proven write
  permission;
- preserve unrelated metadata and all existing pictures unless the user
  explicitly replaces artwork;
- use UTF-8 Kotlin strings and preserve Turkish characters in round-trip tests;
- return a failure rather than claim success when save or read-back fails;
- never call `close` on a descriptor owned by the caller.

### SAF write session

The write session resolves a `TrackLocation` to its locator binding, checks
source selection, permission, availability, exclusion and expected current
hash, then obtains a writable `ParcelFileDescriptor`. It writes one file at a
time, reads the result back, computes a fresh full-file hash and only then
updates Room. A batch is resumable and bounded; completed items remain
completed and failed items are reported individually.

If the provider cannot provide a seekable writable descriptor, the item is
blocked with a user-visible reason. No copy/replace fallback is introduced in
this phase because it could alter provider semantics or file identity.

### Room reconciliation

After verified write-back, Room stores the new raw/normalized file metadata,
the new current hash, and a write audit record. User overrides remain intact.
If the new hash cannot be read back or does not match the descriptor result,
Room is not updated and the location is reported as write verification failed.

## Existing metadata flows

- `MetadataEnrichment`: retain as the read-only catalog refresh primitive and
  reuse it after a successful physical write; it is not a separate user
  feature.
- `GenreMetadataBackfill`: retain only as a migration/repair operation for
  already stored Room metadata. Remove it from the normal Source screen if no
  remaining database migration needs it.
- `MetadataAudit`, `dry-run`, autonomous apply and the current Room-only apply
  UI: remove from the user flow. Their safety responsibilities move into the
  per-file write session, precondition checks, read-back verification and
  durable per-item result log.
- Existing Room metadata override tables and relations: preserve and revise so
  a file write can be explicitly selected as “write to file and keep Room
  override” or “write file values and clear selected override”. The default is
  to keep the Room override until the user chooses otherwise.

## Safety and integrity

- Never mutate production music files in tests or development checks.
- Test fixtures are copied to app cache or an isolated temporary directory.
- A write requires an explicit user action and a selected target set.
- Batch size is bounded at 100 items per execution page.
- Cancellation stops before the next file; the current file completes or is
  reported failed, never half-reported.
- The 1011 scan path remains read-only with respect to physical tags and does
  not invoke the writer.

## Test matrix

- MP3 ID3 title/artist/genre/artwork round trip.
- FLAC Vorbis Comment/Picture round trip.
- Turkish characters and malformed legacy encoding fixtures.
- Existing unrelated fields and multiple pictures are preserved.
- SAF writable descriptor, non-seekable descriptor, denied permission and
  excluded location.
- Stale hash rejection and read-back mismatch.
- Partial batch completion, cancellation and retry.
- Room transaction preserves overrides and updates current hash only after
  verified success.
- Existing 1011 scan performance tests remain unchanged.

## Non-goals

- Automatic online metadata enrichment.
- Audio fingerprinting or cross-format merge.
- File rename/move/copy/delete.

## External reference review

- **BobbyESP/Metadator:** strongest product reference for the editor workflow:
  changed-fields-only writes, backup-before-save, undo, batch album operations
  and broad TagLib format coverage. Its repository is AGPL-3.0, so Smooth
  Media may use the behavior as a reference but must not copy implementation
  code into this project.
- **PauLopNun/MP3-Cover-Editor:** useful small-scope reference for Compose
  editor UX and image preview. It is MP3-only and documents jaudiotagger
  2.2.3 plus a temporary-copy/write-back workflow; it does not satisfy the
  FLAC or canonical Room/SAF requirements by itself.
- **mvxGREEN/Tagger:** useful reference for a compact multi-format editor
  surface. Its README claims MP3/FLAC/WAV/M4A/AAC support and is WTFPL, but
  the public description does not provide enough evidence for its underlying
  write/SAF/integrity implementation. It will not be adopted as a library.

The implementation therefore adopts the workflow ideas from Metadator,
notably backup/undo and changed-field preservation, while using an isolated
Kyant0/taglib adapter for the first MP3/FLAC writer. Kyant0/taglib's public
Android tests demonstrate descriptor-based metadata and picture save calls,
including UTF-8 and FLAC picture cases, but Smooth Media still requires its
own SAF, stale-hash, read-back and partial-batch tests.
- Production physical-file writes during scan or background refresh.
