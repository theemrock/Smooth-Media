# P01-02 Room Catalog Core Uygulama Planı

> **Agentic çalışanlar için:** Bu planı görev görev uygulamak için `superpowers:executing-plans` zorunludur. Adımlar checkbox (`- [ ]`) biçimindedir.

**Hedef:** P01-01 kimlik çekirdeği üzerinde, veri koruyucu Room v1 canonical katalog ve test edilebilir transaction/repository sınırları kurmak.

**Mimari:** Tek `:app` modülünde normalize Room tabloları, hedefli DAO güncellemeleri ve Room transaction kullanan repository implementasyonları oluşturulur. Physical identity, locator binding, source state, resumable scan ve exclusion policy birbirinden ayrıdır.

**Teknoloji:** Kotlin 2.2.21, Room 2.8.5, KSP 2.2.21-2.0.5, AndroidX SQLite 2.7.1, JUnit4 ve Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-10-08-p01-02-room-catalog-core-design.md`

## Global Kısıtlar

- Anayasa değiştirilmez; `EX_FINAL_ARCHITECTURE.md` kullanılmaz.
- Production scanner, metadata parser, UI, playback, Paging/FTS ve yeni Gradle modülü yoktur.
- `REPLACE`, destructive migration fallback, local.properties ve global hash unique constraint yoktur.
- Original hash DAO ve DB seviyesinde immutable; ambiguous exclusion `ReviewRequired`.
- Tek aktif scan lease'i process death'te korunur; yalnız terminal/orphan stale lease açık recovery ile temizlenir.
- Ara TDD dilimleri commit edilmez; kullanıcı talebine göre tek P01-02 milestone commit oluşturulur.

## Review Focus

1. Hash algoritması veya digest alanlarından yalnız birinin değişmesi immutable trigger tarafından engellenmeli.
2. RUNNING/PAUSED session lease'i stale recovery tarafından çalınmamalı.
3. Terminal ya da orphan lease temizliği ile yeni session oluşturma aynı transaction'da atomik olmalı.
4. Aynı hash farklı locator/source olduğunda exclusion otomatik uygulanmamalı.
5. Partial checkpoint hiçbir durumda Missing reconciliation izni vermemeli.

---

### Görev 1: Room/KSP ve schema export altyapısı

**Dosyalar:**
- Değiştir: `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`

**Üretir:** Room/KSP/SQLite dependency ve schema export Gradle altyapısı.

- [ ] Version catalog/plugin/dependency ve `app/schemas` export ayarını ekle.
- [ ] `gradlew help` ve mevcut `compileDebugKotlin` ile plugin/dependency çözümlemesini doğrula.

### Görev 2: Canonical Track/Location/Source/Binding bütünlüğü

**Dosyalar:**
- Oluştur: `catalog/database/CatalogDatabase.kt`, `CatalogDatabaseFactory.kt`, `CatalogMigrations.kt`
- Oluştur: `catalog/database/entity/TrackEntity.kt`, `TrackLocationEntity.kt`, `LibrarySourceEntity.kt`, `LocatorBindingEntity.kt`
- Oluştur: `catalog/database/dao/TrackDao.kt`, `LibrarySourceDao.kt`, `LocatorBindingDao.kt`
- Oluştur: `catalog/database/CatalogDatabaseCallback.kt`
- Oluştur: `catalog/repository/CatalogRepository.kt`, `RoomCatalogRepository.kt`, `CatalogIdentityTransaction.kt`
- Test: `CatalogIntegrityTest.kt`

**Üretir:** Room v1 database/factory, stable IDs, FK/index'ler, Track→Locations, immutable original hash, optimistic current hash ve atomik identity yazımı.

- [ ] 01–10 ve 17–18 numaralı DB davranışları için instrumentation testlerini yaz; RED gözle.
- [ ] Minimum entity/DAO/repository/trigger implementasyonunu ekle.
- [ ] Test APK derlemesini ve mümkünse hedefli device testini GREEN doğrula.
- [ ] `app/schemas/.../1.json` üretildiğini doğrula.

### Görev 3: Scan state, stale lease ve exclusion policy

**Dosyalar:**
- Oluştur: `catalog/database/entity/ScanEntities.kt`, `CatalogExclusionEntity.kt`
- Oluştur: `catalog/database/dao/ScanDao.kt`, `CatalogExclusionDao.kt`
- Oluştur: `catalog/repository/ScanStateRepository.kt`, `RoomScanStateRepository.kt`
- Oluştur: `catalog/repository/CatalogExclusionRepository.kt`, `RoomCatalogExclusionRepository.kt`
- Oluştur: `catalog/exclusion/CatalogExclusionPolicy.kt`
- Test: `ScanStateRepositoryTest.kt`, `CatalogExclusionPolicyTest.kt`, `CatalogExclusionDatabaseTest.kt`

**Üretir:** Kalıcı checkpoint/ledger, tek lease, güvenli stale recovery, partial reconciliation gate ve non-automatic ambiguous exclusion.

- [ ] 11–16 numaralı davranışlar ile stale lease senaryolarının testlerini önce yaz; RED gözle.
- [ ] Minimum Room ve policy implementasyonunu ekle.
- [ ] JVM policy testlerini ve androidTest derlemesini GREEN doğrula.

### Görev 4: Migration fixture ve tam doğrulama

**Dosyalar:**
- Oluştur: `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/CatalogMigrationTest.kt`
- Version control: `app/schemas/com.theemrock.smoothmedia.catalog.database.CatalogDatabase/1.json`

**Üretir:** v1 schema fixture ve sonraki veri-koruyucu migration'lar için çalışan test harness'i.

- [ ] MigrationTestHelper testini yaz; fixture/config eksikliği nedeniyle RED gözle.
- [ ] Schema test source wiring ve migration harness'ini tamamla.
- [ ] `clean assembleDebug testDebugUnitTest assembleDebugAndroidTest` çalıştır.
- [ ] Cihaz görünüyorsa `connectedDebugAndroidTest`; görünmüyorsa `NOT RUN` kaydet.
- [ ] Schema JSON, `git diff --check`, staged diff ve tracked artifact sızıntılarını doğrula.

### Görev 5: P01-02 checkpoint

- [ ] Tüm uygulanabilir test kanıtlarını tekrar değerlendir.
- [ ] Yalnız P01-02 dosyalarını tek milestone commit olarak kaydet ve `main` push et.
- [ ] Instrumentation gerçek cihazda PASS ise `smooth-media-p01-02-room-catalog-pass` tag'ini aynı SHA'ya oluştur/push et; değilse tag oluşturma.
- [ ] Remote main/tag SHA ve temiz working tree durumunu doğrula.
