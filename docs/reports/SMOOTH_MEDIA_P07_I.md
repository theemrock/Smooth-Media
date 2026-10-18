# Smooth Media P07-I — Persistent Recovery Infrastructure

Tarih: 2026-10-18

## Sonuç

Genel sonuç: **PARTIAL**.

Kalıcı recovery session/decision altyapısı, v7→v8 additive migration, bounded keyset audit, lease ve kullanıcı başlatmalı ekran akışı uygulandı ve gerçek cihazda audit tamamlandı. Gerçek metadata alanlarına yazma yapılmadı. `connectedDebugAndroidTest` çalıştırılmadı.

## Uygulama

- Room schema v7→v8 migration eklendi; iki recovery tablosu ve gerekli index/foreign key tanımları mevcut.
- `MetadataRecoverySessionCoordinator` en fazla 100 track’lik batch, keyset cursor, session lease, pause/resume/cancel/fail geçişleri ve idempotent karar kaydı kullanıyor.
- `MetadataRecoveryEngine` mevcut SAF metadata okuyucusu ve Room metadata repository’sine bağlandı.
- Audit yalnız `metadata_recovery_sessions` ve `metadata_recovery_decisions` tablolarına yazıyor; `tracks`, `track_metadata`, hash, identity, location, binding ve override kayıtlarına yazmıyor.
- Kaynak Yönetimi ekranına kullanıcı başlatmalı, salt-okunur audit durumu ve karar özeti eklendi. Son session uygulama yeniden açıldığında geri yükleniyor.

## Migration ve cihaz kanıtı

- Migration ilk cihaz denemesinde Room index şema uyuşmazlığı verdi; kök neden migration’da oluşturulan `status` index’inin entity’de ilan edilmemesiydi.
- Entity index’i eşleştirildi, yeniden derlendi ve `adb install -r` ile veri korunarak kuruldu.
- Cihazda son doğrulama: `user_version=8`, SQLite `integrity_check=ok`.
- Başlangıç/son sayaçlar: `tracks=1690`, `track_locations=1691`, `locator_bindings=1691`; değişmedi.
- `track_metadata` satır sayısı 1689 ve audit öncesi/sonrası içerik hash’i aynı; metadata alanlarına sıfır yazma kanıtı.

## Gerçek audit

- İşlenen: **1690/1690 track**.
- Session: tamamlandı, lease temizlendi.
- Karar kayıtları: **15210** (1690 × 9 alan).

| Karar | Alan sayısı |
|---|---:|
| AUTO_REPAIR | 59 |
| NO_CHANGE | 15142 |
| REVIEW_REQUIRED | 0 |
| UNRECOVERABLE | 0 |
| READ_ERROR | 9 |

AUTO_REPAIR alan dağılımı: `TITLE=19`, `ARTIST=15`, `ALBUM=12`, `ALBUM_ARTIST=11`, `GENRE=2`. Örnekler ham tag round-trip kanıtıyla Türkçe karakterlerin geri kazanılabildiğini gösterdi; apply çalıştırılmadı.

Barış Manço örneklerinde katalogda 67 doğru kayıt ile 7 ve 2 kayıtlık mojibake varyantları ve 20 kayıtlık Çince karakterli varyant görüldü. Audit bunları yalnız karar olarak kaydetti; sanatçı merge yapılmadı.

`7 Kocalı Hürmüz` için 1 doğru kayıt, 4 bozuk varyant ve ayrı adlandırılmış 1 kayıt görüldü. Otomatik merge veya tahmini düzeltme yapılmadı.

## Test ve build

- JVM: **229 PASS / 0 FAIL / 0 SKIP**.
- `assembleDebug`: PASS.
- `compileDebugAndroidTestKotlin`: PASS; instrumentation testleri çalıştırılmadı.
- v7→v8 migration fixture testi ve recovery Room pause/resume testi Android test kaynaklarına eklendi; derleme kanıtı var, cihaz test politikası nedeniyle `connectedDebugAndroidTest` sonucu yok.
- `git diff --check`: PASS.
- Cihaz açılışı ve migration sonrası foreground doğrulaması: PASS; düzeltme sonrası yeni FATAL EXCEPTION görülmedi.

## Değiştirilen P07-I dosyaları

- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/entity/MetadataRecoveryEntities.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/MetadataRecoveryDao.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/CatalogDatabase.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/CatalogMigrations.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/TrackDao.kt`
- `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataRecoverySessionCoordinator.kt`
- `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataRecoveryEngine.kt`
- `app/src/main/java/com/theemrock/smoothmedia/MainActivity.kt`
- `app/src/main/java/com/theemrock/smoothmedia/ui/SourceManagementScreen.kt`
- `app/src/androidTest/java/com/theemrock/smoothmedia/catalog/database/CatalogMigrationTest.kt`
- `app/src/androidTest/java/com/theemrock/smoothmedia/metadata/MetadataRecoverySessionRoomTest.kt`

Önceden var olan tracked/untracked kullanıcı çalışmaları korunmuştur. Commit, tag ve push yapılmamıştır. Cihaz veritabanı snapshot’ları proje dışında tutulmuştur.

## Açık kalanlar

- `READ_ERROR=9` kayıt için SAF erişim/okuma nedeni ayrıntılı alan bazında ayrıca incelenmelidir; metadata apply yapılmadı.
- Recovery kararlarının cihazdaki Room testleri derlendi, fakat bağlı cihaz instrumentation testi çalıştırılmadı.
- `AUTO_REPAIR` kararları yalnız audit kanıtıdır; gerçek katalog apply için ayrı kullanıcı onayı, yedek ve kontrollü apply kapısı gerekir.

- Bu rapor, sonraki P07 apply kapıları için salt-okunur başlangıç kanıtıdır.
