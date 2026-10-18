# Smooth Media — P07-J Otonom Metadata Recovery Apply

## Sonuç

**PARTIAL.** Gerçek katalog metadata yazımı bu turda yapılmadı. P07-I audit kararları salt-okunur SQL ile doğrulandı ve beş desteklenen alan için koşullu, atomik Room writer kapısı genişletildi. Otonom apply coordinator'ın kalıcı apply günlüğü ve kullanıcı tarafından etkinleştirme akışı henüz devreye alınmadı.

## Audit karar denetimi

P07-I cihaz snapshot'ı (`user_version=8`, integrity `ok`) üzerinden:

| Ölçüm | Sonuç |
|---|---:|
| İşlenen track | 1690 / 1690 |
| Toplam karar | 15210 |
| AUTO_REPAIR | 59 |
| AUTO_REPAIR benzersiz track | 47 |
| NO_CHANGE | 15142 |
| REVIEW_REQUIRED | 0 |
| UNRECOVERABLE | 0 |
| READ_ERROR | 9 |
| AUTO_REPAIR çelişkili karar | 0 |
| AUTO_REPAIR üzerinde override | 0 |

`AUTO_REPAIR` alan dağılımı: `TITLE=19`, `ARTIST=15`, `ALBUM=12`, `ALBUM_ARTIST=11`, `GENRE=2`. Kararlar alan bazında tutuluyor; aynı track için güvenilir alanlar bağımsız uygulanabilecek, belirsiz alanlar korunacak şekilde modellenmiştir. Örneklerde kanıt türü bounded UTF-8 round-trip repair olarak kaydedilmiştir; rapora ham URI veya tam kimlik yazılmamıştır.

## READ_ERROR

Dokuz karar tek benzersiz track’e ve dokuz metadata alanına aittir. Konum `FLAC`, location ve source `AVAILABLE`, permission `GRANTED`, selection `ACTIVE` ve locator binding mevcut görünmektedir. Karar kanıtı dokuz satırın tamamında `metadata read failed: READ_FAILED` olduğundan, snapshot tek başına bunun geçici SAF erişim hatası mı yoksa kalıcı decoder/dosya hatası mı olduğunu ayırmıyor. Bu track güvenli biçimde atlanmalıdır; yeniden tarama veya izin değişikliği yapılmadı.

## Uygulanan güvenli kapı

- `ControlledMetadataFieldApplyGate`: `title`, `artist`, `album`, `albumArtist`, `genre` için field-level doğrulama.
- Her alan için immutable track/source/location eşleşmesi, erişilebilirlik, beklenen eski değer, kanıt ve override kontrolü.
- Aynı track+alan için duplicate karar reddi; mevcut yeni değer için idempotent `NO_CHANGE`.
- `ControlledMetadataRoomWriter.writeFieldsAtomically`: Room transaction içinde koşullu SQL update; track lifecycle, location/binding, source availability/permission/selection ve override koşulları SQL içinde tekrar doğrulanıyor. Her update tam bir satır etkilemezse transaction başarısız kabul ediliyor ve rollback uygulanıyor.
- Mevcut `title` kapısı geriye dönük korundu; gerçek apply çağrısı veya kullanıcıya otomatik yazma bağlantısı eklenmedi.

Değişen dosyalar:

- `app/src/main/java/com/theemrock/smoothmedia/metadata/ControlledMetadataFieldApplyGate.kt`
- `app/src/main/java/com/theemrock/smoothmedia/metadata/ControlledMetadataRoomWriter.kt`
- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/dao/TrackMetadataDao.kt`
- `app/src/test/java/com/theemrock/smoothmedia/metadata/ControlledMetadataFieldApplyGateTest.kt`
- Bu rapor.

## Güvenlik ve bütünlük

P07-I sonrası snapshot ile final audit snapshot karşılaştırmasında `tracks=1690`, `track_locations=1691`, `locator_bindings=1691`, `track_metadata=1689` ve metadata hash değişmemiştir; Room `integrity_check=ok`, schema version `8` kalmıştır. Audit sırasında tracks metadata alanlarına yazım kanıtı yoktur. P07-J writer çalıştırılmadı; ses dosyaları, hash/identity, location/binding, artwork, override ve SAF izinleri değiştirilmedi.

Gerçek apply için ayrıca WAL uyumlu güncel yedek, geri yükleme doğrulaması, kalıcı apply progress/change-log, tek worker lease ve kullanıcı tarafından açıkça etkinleştirilen apply akışı gerekir. Bu koşullar sağlanmadan `AUTO_REPAIR` kararları yazılmamalıdır.

## Testler

- TDD kırmızı aşaması: alan kapısı sınıfları yokken derleme beklenen şekilde başarısız oldu.
- Hedefli `ControlledMetadata*` unit testleri: **PASS**.
- Beş alan kabulü, override/stale/unavailable reddi, dry-run sıfır yazımı ve idempotent `NO_CHANGE` testleri: **PASS**.
- `:app:testDebugUnitTest`: **PASS — 233 test, 0 fail, 0 skip**.
- `:app:assembleDebug`: **PASS**.
- `:app:compileDebugAndroidTestKotlin`: **PASS**.
- `git diff --check`: **PASS**.
- `connectedDebugAndroidTest`: çalıştırılmadı.

Gerçek cihazdaki P07-I salt-okunur audit tamamlanmış ve `1690/1690`, `59/15142/9` sayaçları UI/Room snapshot ile doğrulanmıştır. P07-J’de yeni APK kurulmadı ve metadata apply çalıştırılmadı; bu nedenle yeni writer’ın cihaz üzerinde davranışı **DEVICE PENDING** durumundadır.

## Kalan kesin engeller

1. Apply değişiklik günlüğü ve kalıcı apply cursor/lease modeli henüz recovery karar tablolarına bağlanmadı.
2. Dokuz `READ_ERROR` için ham dosya/decoder kök nedeni ayrı salt-okunur forensik inceleme gerektiriyor.
3. Gerçek apply için kullanıcı tarafından açıkça etkinleştirme ve güncel WAL yedeği zorunlu.
4. Barış Manço ve 7 Kocalı Hürmüz grupları birleştirilmedi; metadata yazımı sonrası grup invalidation davranışı henüz canlı doğrulanmadı.

Bu nedenle gerçek katalogda güvenli metadata apply **henüz çalıştırılmadı ve onay bekliyor**.
