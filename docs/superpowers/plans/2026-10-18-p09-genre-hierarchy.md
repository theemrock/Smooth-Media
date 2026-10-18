# P09 Genre Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mevcut Room v10 Genre altyapısını atomik graph/assignment işlemleri ve SQL tabanlı Smart Playlist uyumlu filtre sözleşmesiyle tamamlamak.

**Architecture:** Saf graph doğrulaması `GenreHierarchy` içinde kalacak. Room repository, tam snapshot ve kısmi graph güncellemelerini ayrı açık API’lerle transaction içinde yürütecek; assignment invariant’larını da aynı katmanda koruyacak. Nested `GenreQuery`, aktif Track evreni üzerinde parametreli `EXISTS`/`NOT EXISTS` SQL’e çevrilecek.

**Tech Stack:** Kotlin, Android Room 2.8.5, SQLite recursive CTE, JUnit 4, Room instrumentation tests, Jetpack Compose mevcut Genre UI.

**Spec:** `docs/superpowers/specs/2026-10-18-p09-genre-hierarchy-design.md`

## Global Constraints

- Room v10 korunacak; migration yalnızca gerçekten zorunlu additive schema değişikliği çıkarsa değerlendirilecek.
- `Track ↔ Genre` many-to-many kalacak; Primary seçimi diğer üyelikleri silmeyecek.
- Açık Primary değişiminde eski assignment korunup `isPrimary=false` yapılacak; istem dışı ikinci Primary reddedilecek.
- Edge temizliği yalnızca açıkça tam snapshot olarak tanımlanmış ve bütünüyle doğrulanmış graph güncellemesinde yapılacak.
- SQL parametreleştirilecek; tüm katalog assignment’ları RAM’e yüklenmeyecek.
- Gerçek cihaz kataloğu, metadata apply, rescan, playback ve MiniPlayer değiştirilmeyecek.
- Issue #8, #9, #11 açık; P08 PARTIAL korunacak.

## Review Focus

1. Aynı Track için açık olmayan ikinci Primary isteği reddedilmeli; assignment tablosu değişmemeli. (Task 2)
2. Geçersiz tam snapshot veya kısmi edge değişikliği mevcut graph’ı korumalı. (Task 1)
3. Çok ebeveynli descendant traversal aynı Track’i yalnızca bir kez döndürmeli. (Task 3)
4. NOT, tüm aktif Track evreni üzerinde değerlendirilmelidir; yalnızca pozitif eşleşme adaylarını tamamlayıcı almamalıdır. (Task 3)
5. Metadata upsert kullanıcı override’ını ezmemeli ve stable ID’yi korumalı. (Task 2)

## Dosya haritası

- Modify: `app/src/main/java/com/theemrock/smoothmedia/genre/GenreHierarchy.kt` — graph model validation, query model ve SQL compiler sınırları.
- Modify: `app/src/main/java/com/theemrock/smoothmedia/genre/RoomGenreRepository.kt` — transaction-aware graph/assignment façade.
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/GenreDao.kt` — atomic graph/assignment writes and raw query execution.
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/database/CatalogDatabase.kt` — repository için transaction erişimi gerekiyorsa mevcut DB factory/DAO bağlantısını koruyacak minimal değişiklik.
- Modify: `app/src/main/java/com/theemrock/smoothmedia/ui/LibraryDetailScreen.kt`, `LibraryCollectionScreen.kt` veya mevcut Genre state wiring — yalnız canonical Genre sonuçlarını gösterecek minimal entegrasyon.
- Test: `app/src/test/java/com/theemrock/smoothmedia/genre/GenreHierarchyTest.kt` — validation and descendant semantics.
- Test: `app/src/test/java/com/theemrock/smoothmedia/genre/GenreFilterTest.kt` — nested query semantics and empty/unknown behavior.
- Create/Modify: `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/GenreRoomRepositoryTest.kt` — Room transaction, rollback, assignment, override and query integration.
- Verify: `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/CatalogMigrationTest.kt` — existing v6→v7 compatibility remains green; no new migration unless schema changes.

### Task 1: Graph validation and query model

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/genre/GenreHierarchy.kt`
- Test: `app/src/test/java/com/theemrock/smoothmedia/genre/GenreHierarchyTest.kt`
- Test: `app/src/test/java/com/theemrock/smoothmedia/genre/GenreFilterTest.kt`

**Interfaces:**
- Produces `GenreHierarchy.build(nodes): GenreBuildResult`, `GenreHierarchy.descendantsOf(id): List<String>`, `GenreQuery`, and explicit empty/unknown semantics consumed by Tasks 2–3.

- [ ] Write failing tests for self-parent, indirect cycle, missing parent, multi-parent descendant deduplication, empty AND/OR, unknown Term, and nested AND/OR/NOT semantics.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.theemrock.smoothmedia.genre.*'`; confirm failures are caused by the missing behavior.
- [ ] Implement minimal normalized validation and deterministic traversal; keep stable IDs and current public model compatibility.
- [ ] Implement/adjust pure query semantics without introducing RAM-backed production filtering.
- [ ] Re-run the focused JVM tests and confirm green.

### Task 2: Atomic graph and Track assignment repository

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/GenreDao.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/genre/RoomGenreRepository.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/database/CatalogDatabase.kt` only if required for transaction boundary.
- Test: `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/GenreRoomRepositoryTest.kt`

**Interfaces:**
- Produces explicit `saveFullHierarchySnapshot`, `applyPartialHierarchyUpdate`, `assignGenre`, `changePrimaryGenre`, `clearPrimaryGenre`, `assignGenresBatch`, and read APIs with domain results/errors.

- [ ] Write failing Room tests for full snapshot replacement, partial edge preservation, invalid graph rollback, multiple parents, missing parent, stable node IDs, metadata override preservation, multi-genre assignment, second-primary rejection, explicit primary change, primary clear, and batch rollback.
- [ ] Run the focused instrumentation test; confirm the expected failures before production changes.
- [ ] Add DAO operations for edge replacement, edge insert/delete, assignment updates and parameterized track result queries; keep foreign keys and existing table shapes.
- [ ] Implement repository transaction boundaries. Validate the resulting full graph before destructive edge replacement; never delete edges for partial updates.
- [ ] Implement assignment invariant handling: explicit primary change demotes the old primary, clear only demotes the requested primary, and implicit second primary is rejected.
- [ ] Re-run the focused Room tests and confirm green.

### Task 3: SQL query contract and UI canonical-source integration

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/GenreDao.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/genre/GenreHierarchy.kt` or create a focused `GenreQueryCompiler.kt` if the file boundary is clearer.
- Modify: existing Genre detail/list state wiring in `MainActivity.kt`, `LibraryDetailScreen.kt`, `LibraryCollectionScreen.kt` only where necessary.
- Test: `app/src/test/java/com/theemrock/smoothmedia/genre/GenreFilterTest.kt`
- Test: `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/GenreRoomRepositoryTest.kt`

**Interfaces:**
- Consumes `GenreQuery` from Task 1 and DAO/repository APIs from Task 2.
- Produces a parameterized `GenreTrackQuery` contract with deterministic `track_id` ordering, descendants option, limit and offset.

- [ ] Write failing tests for direct genre SQL, descendant expansion in a multi-parent DAG, duplicate elimination, nested AND/OR/NOT, active-catalog NOT universe, unknown and empty queries, and stable ordering.
- [ ] Run the focused JVM/Room tests; confirm query behavior is not already provided by legacy metadata-string filtering.
- [ ] Implement parameterized SQL compilation with recursive descendant CTE and `EXISTS`/`NOT EXISTS`; use `DISTINCT` and `track_id` tie-break.
- [ ] Replace only Genre-specific UI data reads that currently bypass canonical assignments; preserve existing navigation, empty states and playback behavior.
- [ ] Re-run focused tests and inspect the diff for unrelated UI changes.

### Task 4: Full verification and checkpoint

**Files:**
- Modify only P09 implementation/tests/docs files identified above.

- [ ] Run `./gradlew :app:testDebugUnitTest`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Run `./gradlew :app:compileDebugAndroidTestKotlin`.
- [ ] Do not run `connectedDebugAndroidTest`.
- [ ] Run `git diff --check` and inspect `git diff --stat` plus staged diff.
- [ ] Perform privacy scan for personal paths, emails, tokens, keystore secrets, device identifiers and catalog contents.
- [ ] Stage only P09 files; preserve all pre-existing untracked reports, plan and UI images.
- [ ] Commit with `feat(media): implement p09 genre hierarchy` only if all required checks pass.
- [ ] Create annotated tag `smooth-media-p09-genre-hierarchy-pass` only if PASS conditions are fully met.
- [ ] Push normally and verify `HEAD`, `origin/main`, and tag target; otherwise report PARTIAL/FAIL without the PASS tag.
