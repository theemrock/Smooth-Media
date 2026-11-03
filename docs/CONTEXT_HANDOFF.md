# Bağlam devri: Smooth Media (dün akşamdan bugüne)

Kullanıcı Türkçe konuşuyor. Her yanıt `=====RAPOR=====` ile başlamalı (kullanıcı talebi).

## 1. Klasörler ve dallar
- `C:\Projects\SmoothMedia` — ana çalışma kopyası, `main` (1012). Bu turda dokunulmadı. Yerelde untracked `artifacts/`, `docs/reports/*` var.
- `C:\Projects\SmoothMedia-perf` — **asıl çalışma kopyası**. Worktree, dal `perf/startup-dashboard`, `origin/ghost-context-lazy-resolution` (1013) üzerine kurulu. Uzak dal `origin/perf/startup-dashboard` commit `53fc7a4` (1014). Bu commit'ten sonraki değişiklikler **commit edilmedi**.
- `C:\Projects\SmoothMedia-ghost-context` — 1013 dalının worktree'si. Kullanıcının kendi untracked dosyaları var (`AGENTS.md`, `artifacts/`); dokunma.
- `C:\Projects\SmoothMedia_P07*` (~400 MB, 18 Ekim migrasyon yedekleri ve denetim kayıtları). **Silinmeyecek**; kullanıcı silmek isterse önce arşivlenecek.
- `C:\Projects\Social Media\Chatgpt\cikti\Smooth_Media\` — ChatGPT export'undan çıkarılmış sohbetler (tasarım kararları burada). `C:\Projects\lrclib_codex.xlsx` — Codex'in lrclib test sonuçları (7086 satır).
- `C:\Projects\SmoothMedia-perf\docs\` içinde bu dosya, `PREP_NEXT_STEPS.md`, `HANDOFF_LYRICS_SMART_PLAYLIST.md`.

## 2. Release
- GitHub: `theemrock/Smooth-Media`. Sürüm **v0.0.01.014** (`versionCode 1014`) yayınlandı: prerelease, imzalı APK + SHA-256. APK ve sertifika doğrulandı (cert `aae94f1d…d93c`, Shell ortak anahtarı).
- Yayın iş akışı: `.github/workflows/media-release.yml`, yalnızca `smooth-media-p01-*-interim-v*` etiketiyle tetikleniyor. Etiket `smooth-media-p01-10c-interim-v0.0.01.014` (hafif etiket; kullanıcının kuralı annotated etiket).
- Bilinen sorun: workflow'daki release notu P01-10C'ye ait eski metin; sonraki sürümde düzeltilecek. Aynı etiketle ikinci çalışma `failure` verdi (release zaten var) — zararsız.
- Kurallar: **force push yasak**, commit/push yalnızca açık onayla. Commit mesajına `Co-Authored-By` satırı eklenmeyecek (kullanıcı istemedi).

## 3. Telefon ve test ortamı
- Cihaz seri no: `R5CXA0LXZRB`. Paket: `com.theemrock.smoothmedia` (debug imzalı; release sürümü kaldırıldı, kütüphane 7036 parça ile yeniden taratıldı).
- Ses: müzik stream'i **0** (hoparlör). Kullanıcı "sesi çok açma" dedi: test sırasında 0–5% tut.
- Kurulum: `adb -s R5CXA0LXZRB install -r app\build\outputs\apk\debug\app-debug.apk`.
- Ekran görüntüsü: `adb shell screencap -p /sdcard/x.png` + `adb pull`. Kullanıcı "ss" dediğinde al, sonra incele; **kod yazma** kullanıcı "bitti" diyene kadar ertelenir (o aşamada kullanıcı açıkça böyle istedi).
- Koordinat: ekran görüntüsü 923×2000 gösterilir; dokunma koordinatı = görüntü × 1.56.
- Bash aracı bu ortamda çalışmıyor; PowerShell kullan. Python: `C:\Users\...\Python312`, `python -I` ile. UTF-8 çıktı için `sys.stdout.reconfigure(encoding='utf-8')`.
- Veritabanı incelemesi: `adb exec-out run-as com.theemrock.smoothmedia cat databases/smooth-media-catalog.db` (+ `-wal`, `-shm`) ile çek, sonra sqlite3.
- Derleme: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; $env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain --no-daemon`.
- `gh` CLI bu PC'de **yok**. GitHub durumu için `api.github.com` (herkese açık repo) kullan.

## 4. Dün akşamdan bugüne yapılanlar (durum)

### 4.1 Şarkı sözleri (lrclib)
- `lyrics/LrclibClient.kt`: `/api/search` (artist + track), sonuçları normalize edip eşleştirme (Türkçe harf katlama), tam eşleşme önce, senkron > düz metin, enstrümantal en son. 503/429/ağ hatasında geri çekilme 2 → 5 → 10 → 20 sn.
- `lyrics/LyricsFetchUseCase.kt`: önce önbellek; parantezli sürüm eki varsa (ör. "(Alter Ego Remix)") ek atılıp bir kez daha aranır (sözlü kayıt bulunursa kullanılır). Sonuçlar `lyrics_misses`'e kind ile kaydedilir: `NOT_FOUND`, `INSTRUMENTAL`, `ERROR`. Başarılı kayıtta miss silinir.
- `lyrics/LyricsBulkFetcher.kt`: modlar `NEW` (hiç aranmamış), `NOT_FOUND`, `ERROR`. 250 ms aralık, hata sonrası 30 sn soğuma, 10 ardışık hatada durma. Ayarlar > "Şarkı sözlerini toplu çek" sayfası: Yeni parçalar / Bulunamayanlar / Hatalar düğmeleri, Durdur, ilerleme, kaydırılabilir log; log filtreleri Tümü/İndirildi/Bulunamadı/Hata.
- Veri: `lyrics_cache` ~130 kayıt (SYNCED ağırlıklı), `lyrics_misses` 95+ kayıt. Codex referansı: 7086 parçada %51 bulundu (2965 senkron, 416 düz metin).
- Açık: "Human After All (Alter Ego Remix)" için orijinal sözlü kayıt **cihazda doğrulanmadı** (fallback kodu derlendi, kurulu olabilir).

### 4.2 Veritabanı şeması
- v12: `track_folder_scopes` tablosu + trigger'lar (klasör kapsamı önbelleği; klasör listeleri hızlandı).
- v13: `lyrics_misses` tablosu.
- v14: `lyrics_misses.kind` kolonu.
- Migrasyonlar cihazdaki veriyle test edildi (v14 doğrulandı).

### 4.3 Performans
- Dashboard 16 sn → ~0.5 sn (albüm/sanatçı sayımları ve listeleri 5 sn → 0.1 sn). Kaynak: özyinelemeli klasör CTE'sinin satır başına çalışması.
- Klasör şarkı filtresi `IN (SELECT …)` ile düzeltildi (önce 30+ sn takılıyordu).
- Kapak bellek önbelleği 24 MB; kuyruk ekranı yeniden tasarlandı (kapak, süre, bölümler, Karışık/Tekrar çipleri).

### 4.4 Klasör ağacı ve tür listeleri
- Folders sekmesi: ağaç görünümü (genişlet/daralt, altındaki benzersiz şarkı sayısı). Kök yükleme 4.9 sn → 0.13 sn (`library/FolderTree.kt`, `ui/FolderTreeScreen.kt`).
- Türler ekranında "Otomatik listeler" şeridi: kök türler (en az 5 parça, sayıya göre sıralı) + "Türsüz". Metal listesi (374) açılıyor ve doğru çalışıyor.
- `library/SmartRule.kt`: kural modeli + SQL derleyici (AND/OR/NOT, tür alt türleriyle, sanatçı, albüm, başlık, türsüz). Tür kuralı `IN (SELECT …)` biçiminde (yavaşlık tuzağından kaçınmak için).

### 4.5 Türkçe / bozuk metin (mojibake)
- `metadata/TagTextDecoder.kt`: `normalizeText` — legacy charset round-trip (ISO-8859-2, GBK, Big5, Shift_JIS, 1252, 1254, 8859-1), şüpheli karakterler: Latin işaretleri + CJK aralığı + yarım genişlikli katakana.
- `metadata/TurkishMojibakeTable.kt`: iki yönlü, hesaplanmış eşleme (71 giriş). Kaynak kodlamalar: UTF-8, 1254, 8859-9, 1252.
- `metadata/StoredTextRepair.kt`: mevcut `track_metadata` normalize alanlarını onarır; bozuk parçaları dosyadan (`enrichTrack`) yeniden okur. Tercih anahtarı: `stored_text_repair_v7`. 81 parça yeniden okundu.
- Kalan bozuk değer sayısı: sanatçı 14, albüm sanatçısı 6, albüm 17, başlık 45 (liste sohbette). Bazıları dosya etiketinin **kendisinde** bozuk; yalnızca etiket yazılarak düzelir.
- Birim testleri: `TurkishMojibakeTableTest`, `MetadataNormalizerTest` (Latin-2 ı testi güncellendi: "Yalnız").

### 4.6 Öneri paketleri ve dinleme sinyali (Smart Playlist 2. parça)
- `playback/SmartSuggestionPolicy.kt`: 5'li paket; kuyrukta 2 çalınmamış şarkı kalınca tetik (Soru 94, B seçeneği). `ListenScoring`: 3 sn altı atlama sayılmaz; <%30 erken atlama -1; tamamlanma +1; kullanıcı kararı en güçlü.
- `PlaybackQueue.addSuggestions` + `QueueEntryOrigin.SUGGESTED`. `MainActivity`: `maybeRequestSuggestionPack`, `buildSuggestionPack` (birincil tür, erişilebilir, kuyrukta olmayan, skor ≥ -2), `trackListenSignal`. Puanlar yalnızca bellekte.
- Sınır: **ghost bağlamında** (tüm kütüphane kuyrukta) kuyruk hiç "2 kaldı" olmaz; paket yalnızca kullanıcı kuyruğu bittiğinde tetiklenir.
- Derlendi, telefona kuruldu, açılış kontrolü yapıldı; **çalma sırasında canlı test yapılmadı**.

### 4.7 EQ çekirdeği (entegre değil)
- `eq/ParametricEq.kt`: RBJ peaking biquad, AutoEQ metin ayrıştırma (`Preamp:`, `Filter N: ON PK Fc … Gain … Q …`), manuel ±12 dB sınır, canlı düzeltme ±2 dB sınır. Testler geçiyor.
- Mevcut oynatıcıda zaten bir equalizer var (`EqualizerScreen`, `applyManualEqualizer`, `equalizerBands`); AutoEQ profili bu hatta **bağlanmadı**.

### 4.8 Dosya etiketi yazımı
- `MetadataEditorWriteGate.production()` koordinatörde kullanılıyor, ama ekranda "Fiziksel yazma kilitli…" mesajı görüldü. Sebebi **doğrulanmadı**. Kullanıcı "kapıyı kır" dedi; **kırılmadı**: güvenlik engeli ve geri dönüşsüz yazma. Kapı açılması için kullanıcı onayı + geri alma planı şart.
- Sanatçı adı değişikliği (Seç → Sanatçılar → adı değiştir → kaydet) kaydolmuyor; `ARTIST` override sayısı 0. Büyük olasılıkla yazma kilidinden.

## 5. Açık sorunlar (öncelik sırasıyla)
1. Tag yazımı: kilit sebebini bul; yazmadan önce eski/yeni değerleri listele; onaydan sonra yaz; yazılanın SHA-256'sını ve yeniden okunan etiketi doğrula. (Kullanıcı açık onay verirse.)
2. Kalan bozuk değerler (sanatçı 14 / albüm sanatçısı 6 / albüm 17 / başlık 45): tag yazımına bağlı; kurtarılamayanlar tek tek.
3. Remix fallback'in cihazda doğrulanması ("Human After All (Alter Ego Remix)" → orijinal sözlü kayıt).
4. Toplu söz çekme: lrclib 503 bursts; kullanıcı yavaş ama çalışıyor dedi. Tamamlanınca `lyrics_misses` kind dağılımını kontrol et.
5. Smart playlist: kullanıcı tanımlı kuralları kaydetme (şema v15), kural düzenleyici, Smart Mix (sonra). Ghost bağlamında paket tetikleme kararı.
6. AutoEQ / canlı EQ: profili oynatıcı hattına bağla, düşük sesle (%0–5) test et. Kullanıcı "sesi çok açma" dedi.
7. Çoklu tür verisi: MusicBrainz veri dökümünü (CC0) PC'de işleyip sanatçı bazlı tür haritası çıkarma; telefona küçük dosya olarak aktarma. Dökümün boyutu/indirme planı henüz doğrulanmadı. Canlı API yalnızca yedek (1 istek/sn).
8. Release notu ve annotated etiket düzeltmesi (workflow).
9. `SMOOTH_PERF` tanılama logları ve `timed()` yardımcıları temizlenmeli.
10. P07 yedekleri: kullanıcı kararı; silmeden önce arşiv.
11. Commit/push: kullanıcı onay verirse, `perf/startup-dashboard` dalına yeni commit (force yok).

## 6. Kullanıcının çalışma tercihleri
- Her yanıt `=====RAPOR=====` ile başlar.
- Hızlı ve sorumlu hareket: açık onay gerektirmeyen işleri kendi karar ver ve uygula; **geri dönüşsüz** işler (dosya yazımı, force push, silme, commit/push) için onay al.
- Tek tek düzeltme yerine toplu/otomatik yaklaşım tercih ediyor. "ss" = ekran görüntüsü al, incele, yorum yap; kod yazma yasak "bitti" gelene kadar (kullanıcı bu kuralı koydu).
- Ses her zaman düşük (%0–5).
- Kullanıcı Türkçe konuşuyor; cinsiyet/hitap belirtmedi, nötr ol.
