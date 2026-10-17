# Smooth Media — Sprint 04 devam raporu

Tarih: 2026-10-17

## Durum

Bu devam oturumunda mevcut tracked/untracked kullanıcı dosyaları korunmuştur. Stage, commit, tag ve push yapılmamıştır. Uninstall, `pm clear`, rescan, SAF izin değişikliği ve `connectedDebugAndroidTest` çalıştırılmamıştır.

## Uygulanan entegrasyonlar

- `SafDocumentEntity` provider URI'sini saklayacak şekilde genişletildi.
- Room v5→v6 migration ve migration regresyon testi eklendi; eski track/document kayıtları korunuyor.
- Discovery callback'indeki URI `ManualScanController` tarafından `saf_documents` tablosuna yazılıyor.
- `SafDocumentDao` gerçek klasör düğümleri/çocukları sorguluyor; repository graph mevcutsa Folders listesini bu kaynaktan besliyor.
- Folder Detail graph çocuklarını gösteriyor ve klasör URI'si üzerinden mevcut track/location filtresine bağlanıyor.
- `LyricsResolver` cache önceliği, plain/synced ayrımı ve track identity ile eklendi. Canlı ağ çağrısı yok.
- `TagTextDecoder` BOM/charset temelli byte decoder olarak eklendi; genel mojibake replacement yapılmadı.

## Faz durumu

| Faz | IMPLEMENTATION/TEST | DEVICE VALIDATION |
|---|---|---|
| SAF graph / Folders | PARTIAL: discovery → Room v6 → repository/UI bağı kuruldu; root document ID ve canlı klasör verisi tam doğrulanmadı | PASS: veri korumalı kurulum ve uygulama başlatma; klasör etkileşimleri PENDING |
| Genre DAG | PARTIAL: mevcut domain/testler var; Room node/edge ve UI entegrasyonu yapılmadı | PENDING |
| Lyrics resolver | PARTIAL: resolver/cache fixture testi var; embedded/SAF Android adapterı tamamlanmadı | PENDING |
| Metadata decoder | PARTIAL: UTF-8/UTF-16 byte fixture testleri var; MP3/FLAC ham decoder kökü doğrulanmadı | PENDING |
| Canonical album Room route | PARTIAL: mevcut route korunuyor; yeni in-memory Room route fixture'i bu oturumda eklenmedi | PENDING |
| Playback/EQ | PARTIAL: mevcut deterministic domain altyapısı korunuyor; gerçek ses/DSP kanıtı yok | PENDING |

## Test ve cihaz kanıtı

- `testDebugUnitTest`: **185 PASS / 0 FAIL / 0 SKIP**; 46 XML dosyası.
- `compileDebugAndroidTestKotlin`: **PASS**.
- `assembleDebug`: **PASS**.
- `git diff --check`: **PASS**.
- Cihaz: Samsung Galaxy S24 Ultra, ADB `authorized`.
- `adb install -r app-debug.apk`: **Success**.
- `am start -W -n com.theemrock.smoothmedia/.MainActivity`: **Status: ok**; Activity resumed ve process görünür.
- Paket `firstInstallTime` korunmuş, yalnızca `lastUpdateTime` değişmiştir; uninstall/clear yapılmadı.
- Katalog DB dosyaları yalnızca salt-okunur listelendi; Room sorgusu veya kullanıcı verisi değişikliği yapılmadı.

## Kesin kalan işler

1. SAF root/document ID eşleşmesini fake provider ve cihazdaki mevcut source ile test etmek.
2. Genre node/edge/assignment migration, repository ve Genre Detail state entegrasyonunu tamamlamak.
3. Embedded lyrics ve izinli sidecar `.lrc` Android adapterını gerçek okuyuculara bağlamak.
4. Albums ve Artist Detail için aynı fixture DB üzerinde canonical Room route regresyonu eklemek.
5. Cihazda canlı katalogla Folders, Genre, Lyrics, playback ve EQ davranışlarını güvenli smoke olarak doğrulamak.

## Gizlilik ve güvenlik

Gerçek secret, token, private key, SAF grant değişikliği veya kullanıcı medya dosyası işlemi yapılmadı. Rapor cihaz seri numarası ve yerel kullanıcı yollarını içermiyor.
