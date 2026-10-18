# Smooth Media — P08-B Artwork Source Reader

## Sonuç

**PASS — source-reader üretim entegrasyonu ve otomatik testler tamamlandı.**

Gerçek cihazda yeni APK kurulmadı ve gerçek kütüphane taranmadı. Bu nedenle cihaz üzerindeki gerçek MP3/FLAC/sidecar dağılımı doğrulanmış sayılmıyor; cihaz durumu aşağıda ayrı belirtilmiştir.

## Uygulananlar

- `ArtworkSourceReader`: gömülü audio artwork ve SAF klasör artwork kaynaklarını salt-okunur ve test edilebilir biçimde sonuçlandırıyor.
- `ArtworkSourceResult`: kaynak türü, locator, availability, format, boyut ve failure reason taşıyor.
- Embedded adaylar mevcut `MediaMetadataRetriever` yolu üzerinden okunuyor; MP3/FLAC için ikinci parser eklenmedi.
- Klasör adayları yalnız aynı SAF klasörünün çocuklarından ve yalnız `cover.jpg`/`folder.jpg` adlarından seçiliyor. `cover.jpg` öncelikli.
- Duplicate locator tek kez okunuyor; klasör adından album kimliği üretilmiyor.
- Maksimum encoded artwork boyutu 8 MiB; bounds-only image inspection ve bozuk/büyük görsel koruması var.
- İzin kaybı, kaynak yokluğu, cancellation, bozuk görsel ve işlem sırasında kaybolan dosya ayrı sonuçlara ayrılıyor.
- `AndroidArtworkSourceBackend`, mevcut `ContentResolver` ve `AndroidSafDocumentTreeReader` bileşenlerini yeniden kullanıyor.
- `AndroidArtworkResolver` embedded okuma için yeni source reader'ı kullanıyor; Room, identity ve override akışına yazım eklenmedi.
- Room persistence, canonical album identity, kullanıcı override'ı ve disk cache bu fazın dışında bırakıldı.

## Test kanıtı

Yeni `ArtworkSourceReaderTest`: **6/6 PASS**.

Kapsanan davranışlar:

- Embedded artwork format/boyut sonucu.
- `cover.jpg` önceliği ve diğer dosyaların yok sayılması.
- Duplicate locator ve bozuk görsel.
- Büyük görselin decode edilmeden reddedilmesi.
- SAF izin kaybı ve cancellation.
- Listeleme sonrası kaybolan klasör dosyasının `MISSING` raporlanması.

Tam JVM suite: **244 PASS / 0 FAIL / 0 SKIP**.

- `assembleDebug`: PASS
- `compileDebugAndroidTestKotlin`: PASS
- `git diff --check`: PASS
- `connectedDebugAndroidTest`: çalıştırılmadı.

## Cihaz doğrulaması

- ADB cihazı yetkili ve paket User 0 altında mevcut olarak doğrulandı.
- Uygulama foreground'da mevcut durumda kaldı.
- Yeni APK kurulmadı; rescan, metadata apply, SAF grant değişikliği, Room yazımı ve ses dosyası değişikliği yapılmadı.
- Gerçek cihaz dosyalarından artwork örneği okunmadı; MP3/FLAC ve sidecar gerçek veri sonucu **PENDING**.

## Değişen dosyalar

- `app/src/main/java/com/theemrock/smoothmedia/artwork/ArtworkSourceReader.kt`
- `app/src/main/java/com/theemrock/smoothmedia/artwork/AndroidArtworkResolver.kt`
- `app/src/test/java/com/theemrock/smoothmedia/artwork/ArtworkSourceReaderTest.kt`
- `docs/reports/SMOOTH_MEDIA_P08_B.md`

P08-A raporu ve ilgisiz untracked kullanıcı dosyaları korunmuştur. Room şeması ve veritabanı değişmedi.

## P08-C'ye kalan işler

- Artwork için canonical album/selection key'i album adı + sanatçıdan bağımsızlaştırmak.
- Source/provenance/MIME/dimensions/checksum ve kullanıcı override persistence tasarımını fixture'larla doğrulamak.
- Aynı görselin farklı albümlerde kullanılmasını identity merge'e dönüştürmeden deduplicate etmek.
- Gerekirse additive Room migration; bu aşamada migration eklenmedi.
