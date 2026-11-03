# Devir notu: söz çekme, otomatik tür listeleri, AutoEQ

Çalışma kopyası: `C:\Projects\SmoothMedia-perf` (dal: `perf/startup-dashboard`, `origin/ghost-context-lazy-resolution` üzerine kurulu). Commit yok, push yok.

## Tamamlanan
- **Şarkı sözleri:** lrclib istemcisi (`lyrics/LrclibClient.kt`), tek parça çekme ("Söz getir"), toplu çekme (`lyrics/LyricsBulkFetcher.kt`, Ayarlar > "Şarkı sözlerini toplu çek"). Aralık 300 ms, keep-alive, 10 ardışık hatada durur. Kaldığı yerden devam eder; bulunamayanlar tekrar denenir.
- **Smart Playlist 1. aşama (çekirdek):** kural modeli ve SQL derleyici (`library/SmartRule.kt`, birim testleri `SmartRuleCompilerTest`). Tür kuralı `IN (SELECT ...)` biçiminde, parça başına değil.
- **Otomatik tür listeleri:** Türler ekranında yatay şerit (`ui/AutoGenreStrip.kt`). Yalnızca kök türler, en az 5 parça, sayıya göre sıralı; "Türsüz" listesi ayrı. Metal listesi (374 parça) doğrulandı.

## Açık işler (sırayla)
1. Kullanıcının kural kaydetmesi: şema v13 (`smart_playlists`, kural JSON'u), kayıt/düzenleme ekranı.
2. Smart Playlist'lerin listesi ve sabit listeye dönüştürme.
3. AutoEQ ve canlı EQ: DSP katmanı, cihazda düşük sesle (%0–5) test. Tasarım notları: export'taki "EQ detayları" sohbeti (10 bant + preamp V1, ±2 dB canlı düzeltme sınırı, tek EQ denetleyicisi).
4. Smart Mix (dinleme geçmişine dayalı, tür çeşitliliği %35 sınırı): ilk sürüm dışında.

## Bilinenler
- "*****" adında bozuk bir tür var (metadata); otomatik listelerden filtreleniyor, kaynağı temizlenmedi.
- Toplu söz çekmede hata oranı yüksekti (ilk 23 parçada 6 hata); sebebi (servis yoğunluğu mu, parçaya özgü mü) henüz net değil.
- `SMOOTH_PERF` zamanlama logları kodda duruyor; temizlenebilir.
- Cihazdaki uygulama debug sürümü.

## Devam için
- Telefon bağlı değilse: önce `./gradlew :app:testDebugUnitTest` ile testleri doğrulayın.
- Toplu söz çekmeyi yeniden başlatmak için: uygulamayı açın > menü > Ayarlar > "Başlat".

## Sonraki konu
- ID3 / metadata sorununa bakılacak (kullanıcı sorunu tarif edecek).
- Remix fallback (parantezli ek atılıp orijinal aranıyor) derlendi; cihazda doğrulanmalı.
