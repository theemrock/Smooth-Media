# P01-03 Source Registry & Permissions Design

## Amaç

Kullanıcının Storage Access Framework (SAF) sistem seçicisiyle açıkça seçtiği müzik klasörlerini, kalıcı URI izinlerini ve erişim durumlarını Room canonical kataloğuyla bütünleştirmek. Kaynak izin veya erişim kaybetse ya da kullanıcı kaynak üyeliğini kaldırsa bile `Track` ve `TrackLocation` kayıtları korunur; aynı tree yeniden seçildiğinde aynı source kimliği ve katalog ilişkileriyle devam edilir.

## Kapsam ve anayasal dayanak

Bu çalışma P01 Catalog Foundation içindeki `Permission/Selected Sources` dilimidir. `FINAL_ARCHITECTURE.md` §§2, 9, 25, 66, 71, 95, 99, 101, 119, 121, 123 ve 125 uygulanır.

- Yalnız kullanıcının seçtiği SAF tree root'ları kaydedilir; root'un tüm alt klasörleri kapsamdadır.
- Otomatik kaynak keşfi, otomatik tarama, alt klasör include/exclude sistemi, metadata okuma ve scanner bu fazda yoktur.
- Room katalog için canonical otorite olmaya devam eder.
- Kaynak erişilemezliği, permission loss ve kullanıcı kaldırması ayrı durumlardır.
- Kaynak durum değişiklikleri `Track` veya `TrackLocation` silmez ve katalog kimliklerini değiştirmez.
- `MANAGE_EXTERNAL_STORAGE`, dosya yazma yetkisi ve kullanıcı dosyası silme yoktur.
- Android'in seçilemez yaptığı storage root, `Download`, `Android/data` ve `Android/obb` alanları zorlanmaz.

## Kaynak kimliği

SAF tree identity, görünür klasör adı veya dosya path'inden üretilmez. Girdi URI şu şekilde doğrulanır:

1. Scheme `content` olmalıdır.
2. URI bir DocumentsContract tree URI olmalıdır.
3. Provider authority ve tree document ID alınabilmelidir.

Canonical source key, sürümlü `saf-tree-v1`, provider authority ve tree document ID bileşimidir. `sourceId`, bu key'in SHA-256 digest'inden deterministik olarak üretilir. Böylece aynı tree'nin yeniden seçilmesi aynı `LibrarySource` satırına gider; görünen ad, URI metin sunumu veya uygulama yeniden başlatılması source kimliğini değiştirmez. Persist edilen `rootUri`, sağlayıcının seçici sonucundaki tree URI'sidir ve benzersiz constraint korunur.

## Room modeli ve migration

Mevcut `LibrarySourceEntity` genişletilir:

```text
selection_state: ACTIVE | REMOVED
```

Database schema version `1 → 2` olur. Migration, `library_sources` tablosuna `selection_state TEXT NOT NULL DEFAULT 'ACTIVE'` ekler. Mevcut source, Track, TrackLocation, locator binding, scan state ve exclusion satırları değişmeden kalır. Destructive migration kullanılmaz.

`REMOVED`, yalnız kullanıcının kaynak üyeliğini açıkça kaldırdığını gösterir. Satır silinmez. `permission_state` Android URI grant durumunu, `availability` sağlayıcı/root erişilebilirliğini ifade etmeye devam eder. Böylece aşağıdaki durumlar birbirine karışmaz:

- `ACTIVE + GRANTED + AVAILABLE`: kullanılabilir kayıtlı kaynak.
- `ACTIVE + GRANTED + UNAVAILABLE`: grant var, provider/root geçici erişilemez.
- `ACTIVE + LOST + PERMISSION_LOST`: persisted read grant yok veya güvenlik hatası oluştu.
- `REMOVED`: kullanıcı kaynağı kapsamdan çıkardı; katalog verisi korunur.

## SAF izin yaşam döngüsü

UI, `ACTION_OPEN_DOCUMENT_TREE` intent'ini read, persistable ve prefix grant flag'leriyle başlatır. Sonuç URI'si ve dönen grant flag'leri birlikte işlenir.

Ekleme/yeniden seçme akışı:

1. Sonucun read grant içerdiğini ve URI'nin geçerli tree identity sağladığını doğrula.
2. Yalnız `FLAG_GRANT_READ_URI_PERMISSION` için `takePersistableUriPermission` çağır.
3. `ContentResolver.persistedUriPermissions` içinde aynı tree için read grant bulunduğunu doğrula.
4. Root'a dar bir provider probe uygula; alt dosyaları keşfetme veya scan başlatma.
5. Deterministik `sourceId` ile Room upsert yap. Mevcut satırın `createdAt`, source identity ve katalog ilişkileri korunur; state `ACTIVE` olur.

Permission kontrolü kayıtlı grant listesini source URI'leriyle karşılaştırır. Grant yoksa source `LOST/PERMISSION_LOST`, grant var fakat provider/root geçici açılamıyorsa `GRANTED/UNAVAILABLE`, doğrulanırsa `GRANTED/AVAILABLE` olur. Uygulama açılışındaki bu kontrol yalnız kayıtlı root'lara bakar; müzik discovery veya scan yapmaz.

Kullanıcı kaldırma akışı iki dış sistem arasında atomik olamaz. Bu nedenle kısmi sonuç açıkça modellenir:

1. Kaynağı Room'da `REMOVED` olarak işaretle; Track/TrackLocation kayıtlarına dokunma.
2. Varsa persisted read grant'i `releasePersistableUriPermission` ile bırak.
3. Her iki adım başarılıysa `Removed` döndür.
4. Room güncellendi fakat grant bırakma başarısızsa `RemovedWithPermissionReleaseFailure` döndür; source kaldırılmış kalır ve UI retry gereğini gösterir.
5. Room güncellemesi başarısızsa grant bırakma denenmez ve `StorageFailure` döndürülür.

Aynı tree yeniden seçilirse deterministic source ID ile `REMOVED` satırı yeniden `ACTIVE` yapılır. Yeni source veya katalog ilişkisi oluşturulmaz.

## API sınırları ve kontrollü sonuçlar

Üç odaklı sınır kullanılacaktır:

- `SafTreeIdentityResolver`: URI doğrulama ve deterministic identity.
- `PersistedTreePermissionGateway`: platform grant alma, listeleme, probe ve bırakma işlemleri.
- `SourceRegistry`: izin ile `LibrarySourceRepository` koordinasyonu.

`SourceRegistry` ekleme, durum yenileme, listeleme ve kullanıcı kaldırma operasyonlarını sealed result tipleriyle sunar. Başarı, idempotent başarı, yeniden etkinleştirme, invalid URI, grant reddi/kaybı, provider unavailable, Room hatası ve kısmi removal ayrı sonuçlardır. Exception'lar UI'ye sızdırılmaz fakat hiçbir hata başarıya çevrilmez.

Room repository şu davranışları sağlamalıdır:

- sourceId veya root URI ile lookup,
- tüm source kayıtlarını kalıcı listeleme,
- insert-or-reactivate işlemini transaction içinde idempotent yürütme,
- permission/availability güncelleme,
- source'u ilişkili katalog satırlarını silmeden `REMOVED` yapma.

## Minimal kaynak yönetimi UI

Production `MainActivity`, Smooth renk token'larını koruyan minimal bir kaynak yönetimi ekranı sunar:

- “Klasör ekle” ile sistem SAF seçicisini açar.
- Kayıtlı source'lar URI/provider bilgisi, seçim state'i ve erişim durumuyla listelenir.
- Aktif source kaldırılabilir; işlem fiziksel dosya veya katalog silmez.
- Durum yenileme yalnız kayıtlı root grant/probe kontrolünü çalıştırır.
- Kullanıcı mesajları English default ve Turkish resources üzerinden gelir; hardcoded kullanıcı metni olmaz.
- Production kodu P01X debug ekranına bağlı olmaz.

## Orientation politikası

Manifest genel olarak orientation kilitlemez. `MainActivity`, cihazın `smallestScreenWidthDp` değerine göre politika uygular:

- `< 600dp`: `SCREEN_ORIENTATION_PORTRAIT`.
- `>= 600dp`: `SCREEN_ORIENTATION_UNSPECIFIED`.

Karar saf bir policy fonksiyonunda test edilir; instrumentation testi Activity'nin telefon boyutunda portrait kilidi uyguladığını doğrular. Tablet eşiği Android adaptive layout teamülündeki 600dp sınıfıdır ve tabletin portrait/landscape serbestliğini korur.

## Test stratejisi

TDD ile önce başarısız testler yazılır.

JVM testleri:

- URI/source identity validation ve determinism.
- Aynı authority/tree document ID için aynı source ID.
- Görünen ad/path değişiminin identity'yi etkilememesi.
- Telefon/tablet orientation policy.
- SourceRegistry controlled result dalları ve partial removal sonucu.

Instrumentation testleri:

- Room v1→v2 migration ve bütün mevcut satırların korunması.
- Source ekleme, duplicate selection ve removed source reactivation.
- Process/database reopen sonrasında kayıt korunması.
- Persisted read permission alma/doğrulama ve permission loss.
- Provider unavailable → available recovery; source ID değişmemesi.
- Kullanıcı kaldırması ve permission release.
- Partial removal failure sonucu.
- Track/TrackLocation/locator/catalog identity satırlarının tüm source durumlarında korunması.
- Hatalı/erişilemeyen URI.
- Telefon portrait ve tablet orientation policy.

Gerçek S24 Ultra testinde sistem picker üzerinden seçilen test klasörüyle grant persistence, uygulama yeniden açılışı, duplicate selection, permission loss/reselect ve kaldırma senaryoları çalıştırılır. Sistem UI otomasyonuna uygun olmayan adımlar açıkça manuel cihaz doğrulaması olarak raporlanır; çalıştırılmayan test PASS sayılmaz.

## Sürümleme ve kabul

Sürüm geliştirme sırasında `v0.0.01.002 / 1002` kalır. JVM testleri, clean debug build, androidTest build, gerçek cihaz testleri, Room schema/migration, diff check ve artifact sürümü PASS olduktan sonra tek Gradle kaynağı `v0.0.01.003 / 1003` yapılır ve bütün zorunlu doğrulamalar yeni sürümle tekrarlanır.

PASS koşulları sağlanmadan GREEN ilan edilmez, pass tag oluşturulmaz veya sürüm yükseltilmez. Başarı halinde main push edilir, `smooth-media-p01-03-source-registry-pass` tag'i oluşturulup remote commit/tag SHA doğrulanır.

## Kapsam dışı

Metadata reader, incremental scanner, MP3/FLAC discovery, production playback, library UI, otomatik merge, fiziksel dosya işlemleri, Room destructive migration ve P01-04+ işleri bu tasarımın parçası değildir.
