# Smooth Media

Yerel müzik koleksiyonları için Android müzik çalar. Büyük kütüphaneleri (7000+ parça) hızlı tarar, etiketleri düzgün okur, şarkı sözlerini çevrimdışı saklar ve ses çıkışını EQ ile şekillendirir. Reklam, hesap ve bulut zorunluluğu yoktur.

- Paket: `com.theemrock.smoothmedia`
- Android 13+ (minSdk 33, targetSdk 35)
- Dil: Kotlin, arayüz Jetpack Compose, arayüz metinleri Türkçe

## Özellikler

### Kütüphane
- Klasör (SAF) kaynakları ekleme; kalıcı izin, yeniden tarama, kaynak başına parça sayısı.
- Room tabanlı katalog; tarama kesilse de tutarlı kalır, kaybolan dosyalar "erişilemiyor" olarak işaretlenir.
- Ana sayfada özet, hızlı erişim, görünüm modu (liste/ızgara), alfabe dizini.
- Şarkılar, sanatçılar, albümler, türler ve klasör ağacı görünümleri.
- Otomatik tür listeleri ve tür hiyerarşisi.
- Akıllı (kurallı) çalma listeleri ve normal çalma listeleri.

### Arama
- Sabit sekme şeridi: Şarkılar, Sanatçılar, Albümler, Türler, Klasörler, Şarkı sözleri.
- Her sekme kendi sonuç sayısını gösterir.
- Şarkı sözü aramasında eşleşen satır, zaman damgası olmadan önizlenir; dokunulunca şarkı çalar.

### Etiketler (ID3 / Vorbis / MP4)
- TagLib ile okuma/yazma; bozuk karakter onarımı (UTF-8'in Latin-1 okunması, Türkçe ve CJK kodlama hataları).
- Etiket düzenleyici: tek parça veya seçili koleksiyon, kapak resmi dahil.
- Ayarlar → "ID3 etiketlerini yenile": tüm parçaların etiketlerini istediğiniz an dosyalardan yeniden okur.
- Dosyaya dokunmadan geçersiz kılma (override) katmanı.

### Şarkı sözleri
- LRCLIB'den tek tek veya toplu çekme ("Şarkı sözlerini toplu çek").
- Yerel önbellek ve "bulunamadı" kaydı; tekrar tekrar aranmaz.
- Senkronize (LRC) sözlerde çalan satırı takip eden ekran.

### Oynatma
- Media3 ExoPlayer + MediaSession: bildirim, kilit ekranı, kulaklık/Bluetooth tuşları.
- Kuyruk ekranı; "Kuyruğu temizle" çalan parçayı korur.
- Mini oynatıcı ve Şimdi Çalıyor ekranı.
- Akıllı öneri ile kuyruk besleme.

### Ekolayzer
- Elle bantlı EQ ve AutoEQ profilleri (parametrik filtreler), ses işlemcisi içinde uygulanır.
- Profil kaydetme.

## Kurulum

İmzalı APK, GitHub Actions ile her sürüm etiketinde üretilir: [Releases](https://github.com/theemrock/Smooth-Media/releases).

Yeni sürüm için `app/build.gradle.kts` içinde `versionCode`/`versionName` artırılır, commit edilir ve etiket atılır:

```
git tag smooth-media-p01-10c-interim-v0.0.01.NNN
git push origin main smooth-media-p01-10c-interim-v0.0.01.NNN
```

Not: İmza anahtarı debug yapıdan farklıdır. Debug sürümden imzalıya geçerken önce debug kaldırılmalıdır (uygulama verisi silinir, kütüphane yeniden taranır).

## Geliştirme

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Gereksinimler: JDK 21, Android SDK 36.

## Mimari

| Paket | Görev |
|---|---|
| `catalog` | Room veritabanı, DAO'lar, tarama/içe aktarma |
| `source`, `saf` | Klasör kaynakları ve SAF izinleri |
| `library` | Sorgular, arama, akıllı listeler |
| `metadata` | Etiket okuma/yazma, zenginleştirme, bozuk metin onarımı |
| `lyrics` | LRCLIB istemcisi, LRC ayrıştırıcı, önbellek |
| `playback` | Media3 servis, kuyruk, konum çözümleme |
| `eq` | Parametrik ve elle EQ, AutoEQ işlemcisi |
| `genre`, `playlist` | Tür hiyerarşisi, çalma listeleri |
| `ui` | Compose ekranları |

## Kullanılan kütüphaneler

Jetpack Compose, Media3 (ExoPlayer, Session), Room, Kyant0 TagLib.
