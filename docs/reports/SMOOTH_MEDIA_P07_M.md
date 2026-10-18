# Smooth Media — P07-M Cihaz Apply Dry-Run Doğrulaması

## Sonuç

**PARTIAL.** P07-K için Room-backed, salt-okunur dry-run bağlayıcısı eklendi ve derlendi. Gerçek cihaz Room snapshot’ı üzerinde aynı fail-closed kontrollerle 59 kararın tamamı değerlendirildi. Ancak uygulama içinde bu runner’ı tetikleyen kullanıcı arayüzü/komut yolu bulunmadığından, uygulama coordinator’ının cihaz üzerinde doğrudan çalıştırıldığı iddia edilmemelidir.

## Cihaz ve yedek

- Galaxy S24 Ultra / SM-S928B, ADB yetkili, User 0.
- `com.theemrock.smoothmedia` çalışır durumda kaldı.
- Güncel APK `adb install -r` ile kuruldu; uninstall, `pm clear` ve rescan yapılmadı.
- P07-M öncesi snapshot: `C:\Projects\SmoothMedia_P07M_DeviceSnapshot_20261018_160900`.
- Kurulum sonrası snapshot: `C:\Projects\SmoothMedia_P07M_DevicePostInstall_20261018_161100`.
- Her iki snapshot için SQLite `integrity_check=ok`, Room `user_version=9`.

## Katalog ve metadata korunumu

| Sayaç | Önce | Sonra |
|---|---:|---:|
| tracks | 1690 | 1690 |
| locations | 1691 | 1691 |
| bindings | 1691 | 1691 |
| sources | 13 | 13 |
| track_metadata | 1689 | 1689 |
| recovery sessions | 2 | 2 |
| recovery decisions | 17910 | 17910 |
| apply sessions/items/log/leases | 0/0/0/0 | 0/0/0/0 |

Metadata içerik hash’i iki snapshot’ta da aynıdır: `d0342a75c9185c50791915806266d57d84868266e3ba43b024ea6dd22a3cace7`.

## 59 kararın salt-okunur değerlendirmesi

Tamamlanmış audit session’ı ve `AUTO_REPAIR` kararları SQL ile doğrulandı:

- Değerlendirilen: **59/59**.
- `ELIGIBLE`: **59**.
- `NO_CHANGE`: 0.
- `SKIPPED_STALE`: 0.
- `SKIPPED_OVERRIDE`: 0.
- `READ_ERROR`: 0.
- `BLOCKED`: 0.

Alan dağılımı:

- TITLE: 19 eligible
- ARTIST: 15 eligible
- ALBUM: 12 eligible
- ALBUM_ARTIST: 11 eligible
- GENRE: 2 eligible

Her karar için tekil location, eşleşen locator binding, `AVAILABLE` location/source, `GRANTED + ACTIVE` kaynak durumu, beklenen eski değer ve sıfır kullanıcı override doğrulandı. Duplicate decision key bulunmadı.

Bu değerlendirme gerçek metadata yazmadı; `metadata_apply_*` tabloları boş kaldı.

## Kod ve test değişiklikleri

Değişen/eklenen P07-M dosyaları:

- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/MetadataRecoveryDao.kt`
- `app/src/main/java/com/theemrock/smoothmedia/metadata/RoomMetadataApplyDryRun.kt`
- `docs/reports/SMOOTH_MEDIA_P07_M.md`

Yeni Room bağlayıcısı yalnız tamamlanmış audit kararlarını okur, source/location/binding/availability/override/stale kontrollerini uygular ve metadata writer çağırmaz. Schema veya migration değişmedi.

Doğrulamalar:

- JVM: **234 PASS / 0 FAIL / 0 SKIP**.
- `assembleDebug`: PASS.
- `compileDebugAndroidTestKotlin`: PASS; connected instrumentation çalıştırılmadı.
- `git diff --check`: PASS.

## Apply durumu ve kalan engel

Apply varsayılan olarak kapalıdır. Gerçek metadata update, undo, apply planı yazımı, rescan veya SAF değişikliği yapılmadı. `ELIGIBLE` sonucu uygulanabilirlik ön kontrolüdür; gerçek apply PASS anlamına gelmez.

Kalan tek teknik adım, mevcut Metadata Recovery ekranından bu Room dry-run bağlayıcısını kullanıcı başlatmalı salt-okunur akışa bağlayıp 59/59 sonucunu uygulama içi kanıtla göstermektir. Gerçek apply için ayrıca açık kullanıcı onayı, başarılı WAL uyumlu yedek ve ayrı bir cihaz doğrulaması gerekir.
