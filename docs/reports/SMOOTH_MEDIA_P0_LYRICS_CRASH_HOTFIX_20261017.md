# Smooth Media — P0 Lyrics Crash Hotfix

Tarih: 2026-10-17

## Stack trace ve kök neden

Bu oturumdaki mevcut logcat'te yeni FATAL kaydı yoktu. Kod akışında doğrulanan crash nedeni, `NowPlayingScreen` içindeki `onLyrics` callback'inin ana thread'de `RoomLyricsCache.get()` çağırmasıdır. Bu çağrı doğrudan Room DAO'suna gider ve UI callback'i içinde senkron database okuması yapar. Önceki logcat'te görülen Room migration crash'inden farklı, lyrics düğmesine özgü akış budur.

## Düzeltme

`MainActivity` içinde lyrics cache okuması mevcut executor'a taşındı. Sonuç UI thread'ine döndüğünde yalnızca istenen track hâlâ aktifse lyrics ekranı açılıyor. Böylece hem main-thread database erişimi hem de track değişiminde eski lyrics uygulanması engelleniyor.

Değişen dosyalar:

- `app/src/main/java/com/theemrock/smoothmedia/MainActivity.kt`
- `app/src/main/java/com/theemrock/smoothmedia/lyrics/LyricsNavigationPolicy.kt`
- `app/src/test/java/com/theemrock/smoothmedia/lyrics/LyricsNavigationPolicyTest.kt`

## Doğrulama

- JVM: **186 PASS / 0 FAIL / 0 SKIP**
- `assembleDebug`: **PASS**
- `git diff --check`: **PASS**
- `adb install -r`: **Success**
- Process canlı: `pidof com.theemrock.smoothmedia` sonuç verdi.
- `MainActivity` foreground olarak gözlendi.
- Son logcat'te uygulamaya ait `FATAL EXCEPTION`, `ANR` veya `Cannot access database` bulunmadı.

## Cihaz sınırı

Şarkı satırına dokunma ve uygulama açılışı güvenli biçimde yapıldı. Ancak cihazdaki `uiautomator dump` playback sonrasında idle durumu alamadığı için `Sözler` düğmesine ikinci kez güvenilir otomatik tıklama yapılamadı. Bu nedenle gerçek cihazda lyrics ekranının açılması ve sözsüz parça boş durumu **DEVICE PENDING** olarak bırakıldı; PASS ilan edilmedi.

Uninstall, `pm clear`, rescan, SAF izin değişikliği, connectedDebugAndroidTest, commit, tag ve push yapılmadı. Sprint 05 çalışma ağacı korunmuştur.
