# Smooth Media — P07-P Otonom Metadata Recovery Activation

## Sonuç

**PARTIAL — güvenli activation gate hazır; gerçek otonom yazma etkinleştirilmedi.**

Mevcut recovery/apply akışına kalıcı kullanıcı ayarı, audit-tetikli coordinator ve fail-closed güvenlik politikası bağlandı. Varsayılan ayar `DISABLED` durumunda kalır. Gerçek metadata apply bu turda çalıştırılmadı.

## Uygulanan entegrasyon

- `MetadataAutonomousApplyPolicy`, `DISABLED`, tamamlanmamış audit, eksik yedek, yeni iş yok ve hazır durumlarını ayırır.
- `SharedPreferencesMetadataAutonomousSettings` ayarı kalıcı olarak saklar; varsayılan değer `false`.
- `MetadataAutonomousApplyCoordinator`, tamamlanmış audit sonrası aynı Room dry-run uygunluklarını kullanır ve yalnız `AUTO_REPAIR` kararlarını değerlendirir.
- Mevcut apply session, bounded executor, lease, checkpoint, transaction ve change-log akışı yeniden kullanılır.
- Aynı audit için tamamlanmış apply session tekrar yazılmaz.
- UI'da otonom onarım açıklaması, anahtar, son audit/karar özeti ve engel durumu gösterilir.
- Uygulama içi doğrulanmış WAL-yedek sağlayıcısı bulunmadığı için anahtar güvenli biçimde devre dışıdır; backup kanıtı olmadan coordinator `BLOCKED_BACKUP` döndürür.
- `READ_ERROR`, `REVIEW_REQUIRED`, `UNRECOVERABLE`, stale veya override kararları otomatik apply kapsamına alınmaz.

## Otomatik trigger

Audit tamamlandığında ve uygulama yeniden açıldığında tamamlanmış audit için trigger değerlendirilir. Aynı anda ikinci trigger `AtomicBoolean` ile engellenir. Ayar kapalıysa yalnız durum mesajı güncellenir; metadata writer çağrılmaz. Yedek kanıtı yoksa apply başlamadan `BLOCKED_BACKUP` sonucuyla durur.

## Gerçek cihaz doğrulaması

- Cihaz User 0 ve paket doğrulandı; APK `adb install -r` ile veri korunarak güncellendi.
- Uygulama foreground'da canlı kaldı.
- Metadata ekranı: `Otonom metadata onarımı` → `DEVRE DIŞI · doğrulanmış yedek gerekli`.
- Mevcut P07-O kararları için dry-run: **59 karar, uygun 0, değişiklik yok 59, stale 0, override 0, okuma 0, engelli 0**.
- Otonom anahtar etkinleştirilmedi; gerçek metadata yazımı: **0**.
- Yeni Smooth Media FATAL EXCEPTION, ANR veya Room validation hatası görülmedi.
- Katalog, SAF izinleri ve ses dosyaları değiştirilmedi; rescan çalıştırılmadı.

## Testler

- Yeni politika testleri: disabled default, backup failure, incomplete audit, no-work/idempotency durumları PASS.
- JVM: **238 PASS / 0 FAIL / 0 SKIP**.
- `assembleDebug`: PASS.
- `compileDebugAndroidTestKotlin`: PASS; `connectedDebugAndroidTest` çalıştırılmadı.
- `git diff --check`: PASS.

## Değişen dosyalar

- `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataAutonomousApplyPolicy.kt`
- `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataAutonomousSettings.kt`
- `app/src/main/java/com/theemrock/smoothmedia/metadata/MetadataAutonomousApplyCoordinator.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/MetadataApplyDao.kt`
- `app/src/main/java/com/theemrock/smoothmedia/MainActivity.kt`
- `app/src/main/java/com/theemrock/smoothmedia/ui/SourceManagementScreen.kt`
- `app/src/test/java/com/theemrock/smoothmedia/metadata/MetadataAutonomousApplyPolicyTest.kt`

## GitHub issue

Yedek doğrulama altyapısı için mükerrer kayıt kontrolü yapıldı ve Issue #9 oluşturuldu: https://github.com/theemrock/Smooth-Media/issues/9

## Kalan kesin engel ve sonraki adım

Uygulama içinde binary-safe, WAL-aware Room yedeği oluşturup `integrity_check` ile doğrulayan ve retention/geri dönüş kanıtı sağlayan bir `MetadataBackupVerifier` yok. Bu sağlayıcı eklenmeden otonom anahtar etkinleştirilemez ve gerçek otomatik yazma başlatılamaz. Sonraki tek adım, mevcut mimariye uygun backup verifier'ı ekleyip migration/katalog verisine dokunmadan test etmektir.

Bu turda commit, tag veya push yapılmadı; mevcut kullanıcı untracked dosyaları korunuyor.
