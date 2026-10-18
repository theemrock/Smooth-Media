# SMOOTH MEDIA | P08-D | Artwork Cache & UI Integration

Durum: PASS (PC kapsamı)

## Thumbnail ve cache

- `ArtworkDiskCache` app-private cache dizininde encoded artwork saklıyor; Room’a binary yazmıyor.
- Atomic temp-file rename, SHA-256 doğrulama, bozuk giriş silme ve bounded LRU eviction kullanılıyor.
- Varsayılan disk kapasitesi 32 MiB; tek giriş kapasiteyi aşarsa saklanmıyor.
- Cache key artwork identity/request key, thumbnail hedef boyutu ve source version içeriyor.
- Persisted P08-C asset’lerinde asset ID ve `updatedAtEpochMs` kaynak sürümü olarak kullanılıyor.
- Memory LruCache korunuyor; disk cache ikinci katman olarak çalışıyor.
- Mevcut `LibraryArtwork` zaten `Dispatchers.IO` üzerinde resolver çağırdığı için decode UI thread’inde yapılmıyor.
- Hedef boyutu `BitmapFactory.inSampleSize` ile downsample ediliyor; aspect ratio `ContentScale.Crop` ile korunuyor.

## P08-C entegrasyonu

- Albüm istekleri mevcut collection `row.key` değerlerini kullandığında P08-C `ArtworkRepository` üzerinden persisted asset aranıyor.
- Kullanıcı override’ı repository katmanında keşfedilmiş binding’i ezmiyor.
- Embedded persisted asset ve folder-image persisted asset okuyucudan güvenli biçimde yükleniyor.
- Kaynak kaybı, bozuk cache veya bozuk artwork placeholder’a düşüyor; katalog/override silinmiyor.
- Embedded/folder önceliği için yeni ürün kararı verilmedi; mevcut explicit binding priority ve mimari karar korunuyor.

## UI entegrasyonu

Mevcut Compose bileşenleri korunarak artwork resolver bağlantısı şu akışlarda güncellendi:

- Dashboard album kartları
- Albums liste/grid kartları
- Album Detail başlığı
- Artist Detail albüm bölümü
- Artists önizleme akışı
- All Songs track artwork akışı

Yeni ekran, playback davranışı, Room migration veya artwork editörü eklenmedi.

## Testler

- Final JVM suite: 256/256 PASS, 0 fail, 0 skip.
- Disk cache testleri: 5/5 PASS.
- Artwork source reader testleri: 7/7 PASS.
- Existing artwork selection testleri: 5/5 PASS.
- `assembleDebug`: PASS.
- `compileDebugAndroidTestKotlin`: PASS.
- `git diff --check`: PASS.
- `connectedDebugAndroidTest` çalıştırılmadı.

## Cihaz ve güvenlik

- Gerçek cihaz kurulumu, Room migration, katalog yazımı, rescan ve metadata apply yapılmadı.
- P08-E’ye bırakılan gerçek cihaz doğrulaması dışında PC kapsamı tamamlandı.
- İlgisiz kullanıcı dosyaları, raporlar ve sample görselleri stage edilmedi.

## P08-E

Gerçek cihazda güvenli APK kurulumu, artwork cache davranışı, override önceliği, kaynak kaybı/geri dönüşü ve Room verisinin korunması doğrulanacak.
