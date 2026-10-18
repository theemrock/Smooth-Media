# Smooth Media — P07-K Autonomous Apply Orchestrator

## Sonuç

**PARTIAL.** P07-K için v8→v9 additive apply altyapısı, beş alanlı dry-run coordinator’ı, Room session/item/lease/change-log tabloları ve koşullu executor hazırlandı. Gerçek metadata apply bu turda çalıştırılmadı; apply varsayılan olarak kapalıdır.

## Doğrulanan audit temeli

P07-I cihaz snapshot’ındaki Room kayıtları salt-okunur SQL ile:

- `1690/1690` track işlendi.
- `AUTO_REPAIR=59`, `NO_CHANGE=15142`, `READ_ERROR=9`.
- `AUTO_REPAIR` 47 benzersiz track’e ait.
- Alan dağılımı: `TITLE=19`, `ARTIST=15`, `ALBUM=12`, `ALBUM_ARTIST=11`, `GENRE=2`.
- Çelişkili AUTO karar: `0`.
- AUTO kararlarında kullanıcı override: `0`.
- READ_ERROR: `1` benzersiz FLAC track / 9 alan; source/location erişilebilir görünür, karar nedeni `READ_FAILED`.

## Room v8→v9

Yıkıcı olmayan migration ile şu tablolar eklendi:

- `metadata_apply_sessions`
- `metadata_apply_items`
- `metadata_apply_change_log`
- `metadata_apply_leases`

`CatalogDatabase` sürümü 9 oldu; eski katalog, audit session/decision kayıtları ve kullanıcı tabloları korunuyor. `CatalogMigrationTest` v8→v9 fixture’ında track, location, binding, audit session/decision korunmasını ve apply item yazımını doğruluyor. Android migration testi yalnız derlendi; `connectedDebugAndroidTest` çalıştırılmadı.

## Apply altyapısı

- `MetadataApplyCoordinator`: yalnız `COMPLETED` audit session ve `AUTO_REPAIR` kararlarını kabul eder; en fazla 100 item batch üretir. `READ_ERROR`, `REVIEW_REQUIRED` ve `UNRECOVERABLE` atlanır.
- `RoomMetadataApplySessionStore`: dry-run session/item kayıtlarını Room’a yazar; metadata alanlarına yazmaz.
- `RoomMetadataApplyExecutor`: lease, pending item, stale ilişki kontrolü, field-level gate, change-log ve checkpoint güncelleme akışını içerir.
- Apply yalnız `applyEnabled=true` ve `backupVerified=true` birlikte sağlanırsa çalışabilir; varsayılan değerler `false`.
- `ControlledMetadataFieldApplyGate` ve `ControlledMetadataRoomWriter`: title, artist, album, albumArtist, genre için eski değer, source/location/binding, erişilebilirlik ve override koşullarını yeniden doğrular; koşullu update ve transaction rollback kullanır.
- Koşullu undo için change-log’daki eski/yeni değerler ters çevrilerek aynı gate üzerinden doğrulanabilir; güncel değer beklenen yeni değer değilse yazım reddedilir.

Gerçek UI’da etkin bir Apply butonu bağlanmadı; mevcut ekran açıkça “Otonom onarım: devre dışı (yalnız dry-run)” gösteriyor.

## Test ve build

- TDD kırmızı aşaması: coordinator sınıfları yokken hedef test beklenen sembol hatalarıyla başarısız oldu.
- Hedefli coordinator/field gate testleri: PASS.
- Tam JVM: **234 test, 0 fail, 0 skip**.
- `assembleDebug`: PASS.
- `compileDebugAndroidTestKotlin`: PASS.
- `git diff --check`: PASS.
- Gerçek Room migration/instrumentation testi: çalıştırılmadı; yalnız derlendi.

## Cihaz ve veri güvenliği

ADB cihazı önceki doğrulamalarda yetkili ve paket kurulu olarak görülmüştü. P07-K APK’sı cihaza kurulmadı; v9 migration canlı veritabanında çalıştırılmadı. Bu nedenle cihaz durumu **DEVICE PENDING**.

P07-I snapshot kanıtı: Room `integrity_check=ok`, `tracks=1690`, `locations=1691`, `bindings=1691`, `track_metadata=1689`; audit öncesi/sonrası metadata hash aynıydı. P07-K’de gerçek metadata yazımı, undo, DB restore, rescan, SAF değişikliği ve ses dosyası yazımı yapılmadı.

## Kalan engeller

1. v8→v9 migration’ın gerçek cihazda doğrulanması gerekiyor; bunu yapmak için güvenli `adb install -r` ve açılış/logcat kontrolü gerekir.
2. Executor’ın gerçek Room transaction + change-log akışı yalnız derleme ve domain testleriyle doğrulandı; cihazda apply çalıştırılmadı.
3. Gerçek apply için WAL uyumlu yedek, integrity/restore doğrulaması ve açık kullanıcı etkinleştirmesi zorunlu.
4. Barış Manço ve 7 Kocalı Hürmüz için merge yapılmadı; metadata değişikliği sonrası grup görünümü apply sonrasında ayrıca doğrulanmalı.

Gerçek katalog metadata’sına bu turda **sıfır yazma** yapılmıştır. Otonom apply henüz etkinleştirilmemeli; gerçek apply için açık kullanıcı onayı ve cihaz doğrulaması beklenmelidir.
