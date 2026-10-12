# P01-03 Source Registry & Permissions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kullanıcının seçtiği SAF tree kaynaklarını kalıcı, idempotent ve veri-koruyucu biçimde kaydeden; izin ve erişim yaşam döngüsünü yöneten minimal production akışını oluşturmak.

**Architecture:** Saf tree identity saf Kotlin policy ile authority + tree document ID üzerinden üretilir. Android permission gateway platform grant/probe işlemlerini, Room repository kalıcı source state'ini yönetir; `SourceRegistry` iki sınırı kontrollü sonuç tipleriyle koordine eder. Room v2 yalnız `selection_state` ekler ve kaynak kaldırıldığında katalog ilişkilerini korur.

**Tech Stack:** Kotlin 2.2, Android SDK 33–36, Jetpack Compose/Activity Result API, Room 2.8, SQLite, JUnit 4, AndroidX instrumentation.

**Spec:** `docs/superpowers/specs/2026-10-12-p01-03-source-registry-design.md`

## Global Constraints

- Teknik otorite yalnız `docs/architecture/FINAL_ARCHITECTURE.md`; `EX_FINAL_ARCHITECTURE.md` arşivdir.
- Room canonical kalır; destructive migration, source/Track/TrackLocation silme yoktur.
- Yalnız kullanıcı seçimi; otomatik discovery/scan ve alt klasör exclude sistemi yoktur.
- SAF yalnız persisted read grant kullanır; `MANAGE_EXTERNAL_STORAGE`, file write ve fiziksel silme yoktur.
- Android tarafından engellenen root, Download, `Android/data` ve `Android/obb` alanları zorlanmaz.
- Production kodu P01X debug koduna bağlanmaz; yeni framework/dependency yalnız zorunluysa eklenir.
- Kullanıcı metinleri English default + Turkish Android resources; Smooth token'ları korunur.
- Telefon `<600dp` portrait-only; tablet `>=600dp` orientation serbesttir.
- `applicationId = com.theemrock.smoothmedia`; sürüm kabul öncesinde `v0.0.01.002 / 1002` kalır.
- `local.properties`, kullanıcı verileri ve P01-02 checkpoint'i değiştirilmez.

## Review Focus

1. Aynı tree URI farklı string encoding/visible name ile dönerse authority + decoded tree document ID aynı source ID'yi üretmeli; duplicate oluşmamalı.
2. Room removal başarılı, grant release başarısız olduğunda sonuç kısmi başarısızlığı söylemeli ve source `REMOVED` kalmalı.
3. Grant mevcut fakat provider geçici yoksa `UNAVAILABLE`; grant yok/SecurityException ise `PERMISSION_LOST` olmalı.
4. Removed source yeniden seçildiğinde `createdAt`, source ID ve bütün Track/TrackLocation/locator ilişkileri değişmemeli.
5. Orientation policy telefon sınırının hemen altında portrait, 600dp ve üstünde unspecified üretmeli.

---

### Task 1: Saf tree identity ve orientation policy

**Files:**
- Create: `app/src/main/java/com/theemrock/smoothmedia/source/SafTreeIdentity.kt`
- Create: `app/src/main/java/com/theemrock/smoothmedia/ui/DeviceOrientationPolicy.kt`
- Create: `app/src/test/java/com/theemrock/smoothmedia/source/SafTreeIdentityTest.kt`
- Create: `app/src/test/java/com/theemrock/smoothmedia/ui/DeviceOrientationPolicyTest.kt`

**Interfaces:**
- Produces: `SafTreeIdentity(sourceId: String, authority: String, treeDocumentId: String, canonicalKey: String)`.
- Produces: `SafTreeIdentityFactory.from(authority: String?, treeDocumentId: String?): SafTreeIdentityResult` with `Valid` and `Invalid`.
- Produces: `DeviceOrientationPolicy.requestedOrientation(smallestScreenWidthDp: Int): Int`.

- [ ] **Step 1: Write failing identity tests**

Test valid deterministic SHA-256 identity, same identity for the same decoded authority/document ID, different provider/document identities, and rejection of blank/null components. Expected literals are independently computed fixed digests.

- [ ] **Step 2: Run identity tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "*.SafTreeIdentityTest"`
Expected: FAIL because the identity API does not exist.

- [ ] **Step 3: Implement minimal identity API**

Use UTF-8 SHA-256 over `saf-tree-v1\u0000<authority>\u0000<treeDocumentId>` and lowercase hex. Do not accept display name or filesystem path.

- [ ] **Step 4: Write and run failing orientation boundary tests**

Assert `599 → ActivityInfo.SCREEN_ORIENTATION_PORTRAIT` and `600/720 → ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED`; verify RED before implementation.

- [ ] **Step 5: Implement orientation policy and verify GREEN**

Run the two test classes, then full `testDebugUnitTest`; expected all PASS.

- [ ] **Step 6: Commit**

Commit: `feat: add source identity and orientation policies`

### Task 2: Room v2 source lifecycle ve veri-koruyucu repository

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/database/entity/LibrarySourceEntity.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/LibrarySourceDao.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/database/CatalogDatabase.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/database/CatalogMigrations.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/repository/LibrarySourceRepository.kt`
- Modify: `app/src/main/java/com/theemrock/smoothmedia/catalog/repository/RoomLibrarySourceRepository.kt`
- Modify existing fixtures that construct `LibrarySourceEntity`.
- Create: `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/LibrarySourceRepositoryTest.kt`
- Modify: `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/CatalogMigrationTest.kt`
- Create/update: `app/schemas/com.theemrock.smoothmedia.catalog.database.CatalogDatabase/2.json`

**Interfaces:**
- Produces: `SourceSelectionState.ACTIVE | REMOVED` on `LibrarySourceEntity`.
- Produces: repository methods `findByRootUri`, `listAll`, `registerOrReactivate`, `updateAccessState`, `markRemoved`.
- `registerOrReactivate(source: LibrarySourceEntity): SourceRegistrationWriteResult` preserves existing `sourceId` and `createdAtEpochMs`.

- [ ] **Step 1: Write failing Room/repository tests**

Cover insert, duplicate idempotency, removed reactivation, list/reopen persistence, removal without catalog deletion, and source state updates. Add a fixture with Track + TrackLocation + LocatorBinding and assert all IDs remain after removal/recovery.

- [ ] **Step 2: Write failing v1→v2 migration test**

Create v1 fixture rows, migrate to v2, assert `selection_state=ACTIVE` and all nine-table data remains readable. Verify RED because migration/schema v2 is absent.

- [ ] **Step 3: Implement entity/DAO/repository and migration**

Add only `selection_state`; use Room transactions for register/reactivate and mark/remove state updates. Do not delete source or catalog rows.

- [ ] **Step 4: Build/run targeted instrumentation and verify schema**

Run `assembleDebugAndroidTest`; if device is connected, run the two targeted test classes. Confirm schema v2 export and no unexpected schema field changes.

- [ ] **Step 5: Commit**

Commit: `feat: persist source selection lifecycle`

### Task 3: Kontrollü SourceRegistry orchestration

**Files:**
- Create: `app/src/main/java/com/theemrock/smoothmedia/source/TreePermissionGateway.kt`
- Create: `app/src/main/java/com/theemrock/smoothmedia/source/SourceRegistryResult.kt`
- Create: `app/src/main/java/com/theemrock/smoothmedia/source/SourceRegistry.kt`
- Create: `app/src/test/java/com/theemrock/smoothmedia/source/SourceRegistryTest.kt`

**Interfaces:**
- Consumes: Task 1 identity types and Task 2 `LibrarySourceRepository`.
- Produces: `TreePermissionGateway.takeAndVerify`, `inspect`, and `release` methods returning platform-neutral sealed results.
- Produces: `SourceRegistry.register(selection: SelectedTree, nowEpochMs: Long): SourceRegistrationResult`.
- Produces: `SourceRegistry.refresh(sourceId: String, nowEpochMs: Long): SourceRefreshResult`.
- Produces: `SourceRegistry.remove(sourceId: String, nowEpochMs: Long): SourceRemovalResult` including `RemovedWithPermissionReleaseFailure`.

- [ ] **Step 1: Write failing registry tests**

Use deterministic in-memory fakes at the gateway/repository boundaries. Cover Added, AlreadyRegistered, Reactivated, InvalidUri, PermissionDenied/Lost, Unavailable, StorageFailure, Removed, partial removal, refresh recovery, and removal DB failure that must not release permission.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "*.SourceRegistryTest"`
Expected: FAIL because registry contracts do not exist.

- [ ] **Step 3: Implement minimal orchestration**

Keep exception mapping at boundaries; never report success after failed grant verification or failed Room write. On removal, persist `REMOVED` before release; surface release failure explicitly.

- [ ] **Step 4: Verify targeted and full JVM suite GREEN**

Run targeted class and `testDebugUnitTest`; expected all PASS.

- [ ] **Step 5: Commit**

Commit: `feat: coordinate source permission lifecycle`

### Task 4: Android SAF gateway ve picker contract

**Files:**
- Create: `app/src/main/java/com/theemrock/smoothmedia/source/AndroidTreePermissionGateway.kt`
- Create: `app/src/main/java/com/theemrock/smoothmedia/source/TreePickerIntentFactory.kt`
- Create: `app/src/androidTest/java/com/theemrock/smoothmedia/source/AndroidTreePermissionGatewayTest.kt`
- Create: `app/src/androidTest/java/com/theemrock/smoothmedia/source/TreePickerIntentFactoryTest.kt`

**Interfaces:**
- Consumes: Task 1 identity and Task 3 gateway contract.
- Produces: picker `Intent` with `ACTION_OPEN_DOCUMENT_TREE`, read, persistable and prefix flags.
- Produces: ContentResolver-backed take/list/probe/release behavior.

- [ ] **Step 1: Write failing picker/gateway instrumentation tests**

Assert exact action/required flags, malformed/non-tree URI rejection, read flag requirement, persisted grant verification, permission loss mapping, provider-unavailable mapping and idempotent release. Use an instrumentation-owned DocumentsProvider/test URI where platform permission behavior is required; do not assert on mocks alone.

- [ ] **Step 2: Build/run tests and verify RED**

Run `assembleDebugAndroidTest`, then targeted connected test when the S24 Ultra is visible.

- [ ] **Step 3: Implement Android adapter**

Parse with `DocumentsContract.isTreeUri/getTreeDocumentId`, intersect returned flags with read, call `takePersistableUriPermission`, check `persistedUriPermissions`, query only the selected root document, and release only held read grants.

- [ ] **Step 4: Verify targeted device tests GREEN**

Record exact test count and device serial/model. If provider limitations prevent automation, keep the automated contract tests and record the manual real-picker step separately as NOT RUN until executed.

- [ ] **Step 5: Commit**

Commit: `feat: implement Android SAF permission gateway`

### Task 5: Minimal production kaynak yönetimi UI ve orientation

**Files:**
- Modify: `app/src/main/java/com/theemrock/smoothmedia/MainActivity.kt`
- Create: `app/src/main/java/com/theemrock/smoothmedia/source/SourceRegistryFactory.kt`
- Create: `app/src/main/java/com/theemrock/smoothmedia/ui/SourceManagementScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-tr/strings.xml`
- Modify: `app/src/androidTest/java/com/theemrock/smoothmedia/MainActivitySmokeTest.kt`
- Create: `app/src/androidTest/java/com/theemrock/smoothmedia/SourceManagementActivityTest.kt`

**Interfaces:**
- Consumes: Task 3 registry and Task 4 picker intent.
- Produces: source list, add, refresh and remove UI; no scanner/player behavior.

- [ ] **Step 1: Write failing UI/orientation instrumentation tests**

Assert phone Activity requests portrait, tablet policy remains unspecified, add action launches correct picker intent, persisted sources render after database reopen, removal state renders distinctly, and controlled failures produce non-success messages.

- [ ] **Step 2: Build/run tests and verify RED**

Run `assembleDebugAndroidTest`; execute targeted connected tests where available.

- [ ] **Step 3: Implement Activity integration and screen**

Apply orientation policy before Compose content. Run Room/platform work off the main thread with existing Android/JDK primitives; close owned executor/database resources. Keep all user copy in English/Turkish resources and retain Smooth color tokens.

- [ ] **Step 4: Verify UI/instrumentation GREEN**

Run targeted tests and MainActivity smoke test on S24 Ultra. Confirm reopening does not start discovery or scan.

- [ ] **Step 5: Commit**

Commit: `feat: add source management screen`

### Task 6: Tam doğrulama, sürüm ve P01-03 checkpoint

**Files:**
- Modify after pre-version acceptance only: `app/build.gradle.kts`
- Update if Room export changes: `app/schemas/com.theemrock.smoothmedia.catalog.database.CatalogDatabase/2.json`
- Update plan checkboxes as work evidence.

**Interfaces:**
- Produces: verified P01-03 artifact and Git checkpoint.

- [ ] **Step 1: Run pre-version acceptance sequence**

Run in order:

1. `./gradlew.bat testDebugUnitTest`
2. `./gradlew.bat clean assembleDebug`
3. `./gradlew.bat assembleDebugAndroidTest`
4. `./gradlew.bat connectedDebugAndroidTest` on the real S24 Ultra
5. Inspect Room schema v1/v2 and migration output
6. `git diff --check`
7. Inspect built APK `versionName/versionCode`

Any FAIL/NOT RUN stops version bump, GREEN claim and pass tag.

- [ ] **Step 2: Perform manual real-device SAF scenarios not automatable through instrumentation**

On S24 Ultra select a real test folder, reopen app, select same folder again, temporarily revoke/remove access when platform permits, recover by reselecting, remove source, and confirm catalog IDs remain. Record each PASS/FAIL/NOT RUN.

- [ ] **Step 3: Bump version only after every mandatory acceptance item PASS**

Set `versionName = "v0.0.01.003"` and `versionCode = 1003` in `app/build.gradle.kts` only.

- [ ] **Step 4: Repeat the complete verification sequence with final version**

All mandatory commands and device scenarios must PASS again; verify APK manifest metadata is `v0.0.01.003 / 1003`.

- [ ] **Step 5: Create final checkpoint and publish**

Commit remaining version/evidence changes with a meaningful P01-03 commit. Push `main`, create and push `smooth-media-p01-03-source-registry-pass`, then verify remote main SHA equals remote tag SHA and working tree is clean.

- [ ] **Step 6: Produce final Turkish report**

Report changed files, SAF lifecycle, availability/recovery, every test as PASS/FAIL/NOT RUN, Room/constitution status, APK version, commit/push/tag SHAs, open risks and P01-04 readiness. Do not call GREEN if any mandatory item is not PASS.
