# SMOOTH MEDIA | GAP MATRIX 03

Tarih: 2026-10-16

Bu matris kaynak kodu, test çıktıları ve mevcut mimari kararlar üzerinden hazırlanmıştır. Dokümantasyon tek başına uygulama kanıtı sayılmamıştır.

| # | Ekran / kapsam | Durum | Kanıt / sınır |
|---:|---|---|---|
| 01 | Splash / startup | PARTIAL | Mevcut Activity başlangıcı var; ayrı splash sözleşmesi yok. |
| 02 | SAF source selection | IMPLEMENTED+TESTED | Mevcut source registry ve manuel SAF akışı; izin kaynağı korunuyor. |
| 03 | Library Dashboard | IMPLEMENTED+TESTED | Mevcut library/collection akışı ve sayfalama. |
| 04 | All Songs | IMPLEMENTED+TESTED | Room sayfalaması, erişilemeyen satır sunumu ve artwork akışı. |
| 05 | Albums grid | IMPLEMENTED+TESTED | İki sütunlu mevcut Compose grid ve embedded artwork. |
| 06 | Album Detail | IMPLEMENTED+TESTED | Gerçek metadata filtresi ve track listesi. |
| 07 | Artists list | IMPLEMENTED+TESTED | Tek sütun, görselsiz, gerçek sayaçlar. |
| 08 | Artist Detail | IMPLEMENTED+TESTED | Room filtresi ve detail navigation. |
| 09 | Genres list | IMPLEMENTED+TESTED | Many-to-many metadata görünümü mevcut. |
| 10 | Genre Detail | IMPLEMENTED+TESTED | Room filtresi ve detail navigation. |
| 11 | Folders list | PARTIAL | Yalnızca gerçek, kayıtlı path; güvenli olmayan hiyerarşi uydurulmuyor. |
| 12 | Folder Detail | PARTIAL | Exact path filtresi var; parent/child tree yok. |
| 13 | Playlists overview | DOCUMENTED ONLY | Playlist Room modeli ve UI henüz yok. |
| 14 | Playlist Detail | DOCUMENTED ONLY | Gerçek entry backend’i yok. |
| 15 | Create/Edit Playlist | DOCUMENTED ONLY | Fiziksel dosyaya dokunmadan Room modeli planlı. |
| 16 | Favorites | DOCUMENTED ONLY | Gerçek favorite modeli yok; sahte veri gösterilmiyor. |
| 17 | Recently Added | DOCUMENTED ONLY | Gerçek tarih/history UI yok. |
| 18 | Most Played | DOCUMENTED ONLY | Playback event/history modeli yok. |
| 19 | Search | IMPLEMENTED+TESTED | Room effective metadata, 250 ms debounce, obsolete generation guard, parameterized raw query. |
| 20 | Search Results / filters | PARTIAL | Song click/detail ve kategori sonuçları var; kategori drill-down/pagination/filter UI eksik. |
| 21 | Mini Player | PARTIAL | Gerçek session state, pause/resume ve open var; artwork/next yok. |
| 22 | Now Playing | PARTIAL | Gerçek state, seek, duration, loading/error ve EQ/queue girişleri var; artwork ve tüm transport kısayolları eksik. |
| 23 | Queue / Up Next | PARTIAL | Gerçek domain, duplicate TrackID, reorder/remove, repeat/shuffle politikası ve restore var; service queue ile tam UI senkronu eksik. |
| 24 | Lyrics | DOCUMENTED ONLY | Plain/LRC kaynağı yok; söz uydurulmuyor. |
| 25 | Track Information | DOCUMENTED ONLY | Ayrı bilgi ekranı yok. |
| 26 | Tag Editor | PARTIAL | Room override mimarisi mevcut; bağımsız editor UI eksik, dosya tag’i yazılmıyor. |
| 27 | Equalizer | PARTIAL | Gerçek device band modeli, attach/detach, clamp, bypass, flat ve profil kalıcılığı eklendi; cihaz DSP doğrulaması yok. |
| 28 | Sleep Timer | DOCUMENTED ONLY | Gerçek timer backend’i yok. |
| 29 | Settings Dashboard | PARTIAL | Source ekranı mevcut; genel ayar dashboard’u yok. |
| 30 | Appearance | PARTIAL | Dark/light/system tema altyapısı var; ayrı settings ekranı yok. |
| 31 | Media scanning/source settings | IMPLEMENTED+TESTED | Manuel scan ve source registry mevcut; otomatik rescan yok. |
| 32 | Headphone/Bluetooth behavior | PARTIAL | Media3 audio focus/noisy handling motor seviyesinde var; ayrı policy UI yok. |
| 33 | Notification/lock-screen | PARTIAL | MediaLibraryService/MediaSession manifest ve session seam var; cihaz transport doğrulaması yapılmadı. |
| 34 | Home widget 4x1 | DOCUMENTED ONLY | Widget modülü/provider yok. |
| 35 | Home widget 4x2 | DOCUMENTED ONLY | Widget modülü/provider yok. |
| 36 | Android Auto | DOCUMENTED ONLY | Telefon MediaLibraryService browse sözleşmesi var; Auto manifest/cihaz doğrulaması yok. |
| 37 | Wear OS | DOCUMENTED ONLY | Companion modül yok. |
| 38 | Playback foundation / startup | IMPLEMENTED+TESTED | Tek HandlerThread ExoPlayer, SAF resolver, fallback, MediaLibraryService; cihaz son kurulum sonrası tekrar doğrulanmalı. |

## Paket durumu

- P02 production playback: **PARTIAL**. Gerçek engine, SAF location seçimi, MediaLibraryService, browse root/categories, session client, queue domain ve persistence mevcut. Queue’nun tüm UI/service operasyonları ve cihaz transport doğrulaması tamamlanmadı.
- Manual EQ: **PARTIAL**. Manual backend artık gerçek session bandlarını ve yerel profili kullanıyor. Adaptive analiz/öğrenme girdisi yok; bu nedenle Adaptive ACTIVE iddiası yapılmıyor.
- P01-12 Search: **PARTIAL**. Backend ve debounce/cancellation gerçek ve testli; kategori drill-down, kategori bazlı ayrı sayfalama ve Türkçe collation garantisi eksik.
- İlk vardiyanın genel kapsamı: **COMPLETE değil**; bağımsız ileri fazların çoğu documented only.

## Anayasa uyumu

Room canonical source, Track/TrackLocation ayrımı, SAF `content://` zinciri, override/EXCLUDED semantiği ve Room-only metadata edit sınırı korunmuştur. Adaptive ölçüm yokken sahte slider/öğrenme gösterilmemiştir. Android Auto, Wear OS ve widget için sahte ekran PASS verilmemiştir.
