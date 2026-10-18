# SMOOTH MEDIA | P08-C | Artwork Identity & Persistence

Durum: PASS (PC kapsamı)

## Yapılanlar

- Room şeması v9’dan v10’a additive olarak yükseltildi.
- `artwork_assets`: binary veri saklamadan asset kimliği, içerik hash’i, kaynak locator’ı, format/boyut, erişilebilirlik ve invalidation alanlarını tutuyor.
- `artwork_album_bindings`: dışarıdan sağlanan canonical collection key ile artwork asset ilişkisini tutuyor. Albüm adı veya track ID’den yeni kimlik türetmiyor.
- `artwork_overrides`: kullanıcı override’ını ayrı tabloda tutuyor; keşfedilen artwork yazımı mevcut override’ı ezmiyor.
- `ArtworkAssetIdentity`, aynı encoded görsel için SHA-256 tabanlı deterministik asset kimliği üretiyor; farklı collection key’ler aynı asset’i paylaşabiliyor.
- `ArtworkSelectionPolicy`, override > keşfedilen binding > placeholder kararını uyguluyor. Embedded/folder önceliği sabit ürün kararı olarak gömülmedi; binding priority değeri mevcut seçim akışına bırakıldı.
- `ArtworkRepository`, P08-B okuyucusundan gelen kaynak sonucunun Room asset/binding sınırına taşınacağı persistence sınırını ve override korumasını sağlıyor.

## Migration ve veri güvenliği

- `FROM_9_TO_10` yalnızca üç yeni tablo ve gerekli index/foreign key’leri oluşturuyor.
- Track, location, locator binding, source ve mevcut recovery/apply tablolarına dokunulmuyor.
- Artwork binary verisi Room’a yazılmıyor; ses dosyalarına ve SAF izinlerine dokunulmadı.
- v9→v10 migration testi eklendi; eski katalog/audit sayaçlarının korunması ve yeni artwork tablolarının yazılabilir şeması doğrulanıyor. Instrumentation testi yalnız derlendi, cihazda çalıştırılmadı.

## Testler

- JVM hedefli artwork testleri: 6/6 PASS.
  - Aynı asset’in farklı collection key’lerde paylaşılması
  - Kullanıcı override önceliği
  - Keşfedilen binding fallback’i
  - Eksik binding placeholder sinyali
  - Deterministik duplicate tie-breaker
  - Aynı artwork byte’larından aynı asset identity
- `testDebugUnitTest`: PASS (6 test, 0 fail, 0 skip).
- Final JVM suite: 250 test, 0 fail, 0 skip.
- İzole geçici SQLite v9→v10 migration harness: PASS (`integrity_check=ok`, legacy counts preserved, artwork foreign keys verified).
- `compileDebugAndroidTestKotlin`: PASS (Room migration test source compiled).
- `assembleDebug`: PASS.
- `git diff --check`: PASS.
- `connectedDebugAndroidTest` çalıştırılmadı; PC-only talimat gereği cihaz migration'ı tetiklenmedi.

## Cihaz durumu

ADB cihazı yetkili göründü. Talimat gereği gerçek katalog migration'ı ve APK kurulumu yapılmadı; cihaz doğrulaması P08-E'ye ertelendi. Kullanıcı kataloğuna yazılmadı.

## Değişen dosyalar

- `app/src/main/java/com/theemrock/smoothmedia/artwork/ArtworkIdentity.kt`
- `app/src/main/java/com/theemrock/smoothmedia/artwork/ArtworkRepository.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/entity/ArtworkEntities.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/ArtworkDao.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/CatalogDatabase.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/CatalogMigrations.kt`
- `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/CatalogMigrationTest.kt`
- `app/src/test/java/com/theemrock/smoothmedia/artwork/ArtworkIdentityPolicyTest.kt`
- `app/schemas/com.theemrock.smoothmedia.catalog.database.CatalogDatabase/10.json`
- `docs/reports/SMOOTH_MEDIA_P08_C.md`

## Kalan işler

P08-D’de artwork persistence’ın mevcut album/artist consumer’larına bağlanması, disk cache ve Library UI kapsamına alınabilir. Embedded/folder kaynak önceliği FINAL_ARCHITECTURE tarafından kesinleştirilmediği için ürün kararı ayrıca netleşmelidir. Gerçek cihaz migration doğrulaması P08-E kapsamındadır.

PC kapsamı PASS olduğu için yalnız P08-C ile ilgili dosyalar checkpoint’e alınacaktır; ilgisiz kullanıcı değişiklikleri ve untracked dosyalar korunacaktır.
