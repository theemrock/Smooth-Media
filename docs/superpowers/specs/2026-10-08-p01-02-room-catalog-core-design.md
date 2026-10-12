# P01-02 Room Catalog Core Tasarım Spesifikasyonu

## Amaç

P01-01 Strict Identity Core üzerine, Room'u tek canonical katalog otoritesi yapan ilk kalıcı şemayı kurmak. Bu paket tarama, metadata ayrıştırma, UI veya playback içermez; veri bütünlüğü, kesintiden devam, migration güvenliği ve transaction sınırlarını hazırlar.

## Bağlayıcı sınırlar

- `docs/architecture/FINAL_ARCHITECTURE.md` tek mimari otoritedir.
- Track ve TrackLocation farklı stable kimliklerdir; MediaStore ID veya locator TrackID olamaz.
- Fiziksel hash kimliği ile mantıksal Track merge ayrıdır.
- Hash üzerinde global `UNIQUE` yoktur; aynı byte içeriği farklı Location'larda bulunabilir.
- `originalFileHash` normal DAO işlemleriyle ve DB trigger'ıyla değiştirilemez.
- `currentFileHash` yalnız beklenen mevcut hash'i doğrulayan hedefli optimistic update ile değişir.
- `REPLACE`, destructive migration fallback ve otomatik DB silme kullanılmaz.
- Availability değişikliği Track silmez; EXCLUDED ayrı tombstone'dur.
- Partial scan Missing reconciliation yetkisi kazanmaz.
- Belirsiz exclusion eşleşmesi otomatik exclusion üretmez; `ReviewRequired` olur.
- Production scanner, metadata parser, UI, playback, Paging/FTS ve artwork kapsam dışıdır.
- Telefon portrait-only sapması açık kalır ve bu pakette değiştirilmez.

## Toolchain

- Room runtime/compiler/testing ve Room Gradle plugin: `2.8.5`
- KSP: `2.2.21-2.0.5`, mevcut Kotlin `2.2.21` ile eşleşir.
- AndroidX SQLite framework: `2.7.1`
- Mevcut AGP `8.11.1`, Gradle `8.13`, JDK 21 ve JVM target 17 korunur.
- Room schema export: `app/schemas`, version control altında.

## Şema v1

### Canonical katalog

- `tracks`: `track_id` primary key, katalog yaşam döngüsü, oluşturma/güncelleme zamanı. Locator içermez.
- `library_sources`: stable source ID, tür, root URI/volume, persisted permission flags/state ve source availability.
- `track_locations`: stable location ID; Track ve Source foreign key'leri; original/current hash algoritma, sürüm ve digest alanları; format, availability, opsiyonel path/MediaStore ID ve teknik alanlar.
- `locator_bindings`: aktif locator'ı LocationID'ye bağlar. Locator primary key, LocationID unique olur; TrackID taşımaz.

Foreign key silme davranışı `NO ACTION` olur. Track veya Source silindiğinde gelecekteki playlist/history gibi ilişkileri zincirleme kaybetmek yerine açık transaction gerekir.

### Scan state

- `scan_sessions`: stable session ID ve durum.
- `active_scan_leases`: hard-coded tek global slot ile bir aktif/resumable scan invariant'ı.
- `source_scan_checkpoints`: session/source bazında cursor, sayaçlar ve `PARTIAL/COMPLETE` coverage.
- `scan_candidates`: session/source/candidate anahtarında discovery/processing/completed ledger.

Stale lease recovery yalnız açık çağrıyla yapılır. Lease'in session'ı terminal değilse geri alınmaz. Terminal session'a bağlı veya session'ı bulunmayan stale lease transaction içinde temizlenebilir; ardından yeni scan başlatılabilir. Process death sonrasında RUNNING/PAUSED session'ın lease'i korunur ve resume edilir.

Missing reconciliation yalnız session `COMPLETED` ve ilgili source checkpoint `COMPLETE` olduğunda izinlidir.

### Exclusion

- `catalog_exclusions`: stable exclusion ID, source, locator ve hash kanıtı snapshot'ı.
- `(source_id, locator)` unique; hash global unique değildir.
- `CatalogExclusionPolicy` sonuçları: `Allowed`, `Excluded`, `ReviewRequired`.
- Yalnız aynı source + aynı locator + aynı full hash kesin eşleşmesi otomatik `Excluded` olabilir.
- Locator veya source ayrışan hash eşleşmeleri ve locator/hash çatışmaları `ReviewRequired` olur.

## DAO ve repository sınırları

- `TrackDao`: insert/read, Track→Locations, hedefli availability/current-hash update.
- `LibrarySourceDao`: insert/read ve permission/availability update.
- `LocatorBindingDao`: abort-on-conflict binding ve lookup.
- `ScanDao`: session/checkpoint/candidate/lease işlemleri.
- `CatalogExclusionDao`: kayıt ve eşleşme adaylarını bulma.
- `CatalogRepository`, `LibrarySourceRepository`, `ScanStateRepository`, `CatalogExclusionRepository` minimum persistence API'lerini sunar.
- `CatalogIdentityTransaction` Track/Location/Binding/Candidate completion yazımlarını tek Room transaction'ında uygular.

Genel `@Update TrackLocationEntity` bulunmaz. Böylece original hash normal update API'sinden değiştirilemez. Ek DB trigger'ı üç original hash sütunundaki değişikliği `ABORT` eder.

## Migration ve recovery

- `CatalogDatabase` version `1`, `exportSchema = true`.
- `CatalogMigrations.ALL` başlangıçta boştur; builder yalnız bu listeyi kullanır ve destructive fallback çağırmaz.
- DB invariant callback'i immutable hash trigger'ını her açılışta `CREATE TRIGGER IF NOT EXISTS` ile garanti eder.
- Corruption halinde otomatik silme veya boş DB'ye dönüş yoktur. Bu pakette recovery backup entegrasyonu yapılmaz.

## Test tasarımı

- Saf exclusion policy testleri JVM'de çalışır; P01-01 identity testleri regresyon olarak korunur.
- Room/foreign key/index/transaction/scan/migration davranışları Android instrumentation ile gerçek SQLite üzerinde sınanır.
- Migration fixture, export edilen v1 JSON şemasını kullanır.
- Cihaz yoksa androidTest APK derlenir ancak instrumentation `NOT RUN`; PASS tag oluşturulmaz.
