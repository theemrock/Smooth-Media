# Smooth Media ekran spesifikasyonu

Revizyon: 2026-10-16 gece vardiyası. Hedef cihaz: Galaxy S24 Ultra, portrait, yaklaşık 412 dp. Temel renkler `#090B0D`, `#111418`, `#F4F6F8`, vurgu `#19C3E6`; her etkileşimli hedef en az 48 dp olmalıdır. Ekranlar Room canonical catalog ve mevcut SMOOTH dark design language üzerine kuruludur.

## Durum ve ortak kurallar

`existing` mevcut kodda vardır; `implemented tonight` bu vardiyada gerçekten kodlanan davranıştır; `partial` yalnızca bir bölümü hazırdır; `planned` dokümante edilmiş ancak backend/platform kapısı açılmamıştır. Loading, empty, unavailable ve error durumları her listede ayrı UI state olarak gösterilir; dosya yokluğu katalogdan silinme değildir. Android back, modalı kapatır; modal yoksa ekran hiyerarşisinde bir üst seviyeye döner; kökte uygulamadan çıkar.

Tüm ekranlar edge-to-edge system insets, IME, font scale ve küçük genişliklere göre responsive olmalıdır. Metin ellipsis ile kesilir, touch hedefi 48 dp'den küçük olmaz. Koyu tema varsayılan; açık/System seçimi yalnızca gerçek preference backend'i hazır olduğunda etkinleştirilir.

## Ekran sözleşmeleri

Her maddede sıra: amaç; hiyerarşi/veri; eylem ve gezinme; durumlar; erişilebilirlik; backend; durum.

1. **Splash / startup** — Room/source bootstrap ve son güvenli ekranı açar; sade logo ailesi, progress ve kısa hata metni; back yok; loading/error/empty için kaynak yönetimine geçiş; TalkBack startup açıklaması; `CatalogDatabaseFactory`, source registry; `existing`, startup yükleme genişletmesi planned.
2. **SAF source selection** — Kullanıcının seçtiği root ve kalıcı okuma iznini yönetir; açıklama, kayıtlı kaynaklar, Add/Refresh/Remove; seçici açılır, geri kaynak ekranına döner; izin kaybı ve provider hatası açıkça yazılır; butonlar 48 dp; `SourceRegistry`, SAF gateway; `existing`.
3. **Library Dashboard** — Kataloğun girişidir; Songs/Albums/Artists/Genres/Folders sekmeleri ve mini player alanı; sekme, search ve kaynaklar; geri source root'a; empty/no-source/error; semantik sekme adları; Room counts; `partial` (mevcut library akışı var, dashboard/player entegrasyonu eksik).
4. **All Songs** — Sayfalı tek kolon track listesi; embedded artwork, title, artist, album, duration ve subdued unavailable; scroll near-end ile next page, track seçimi gelecekte player'a bağlanır; geri dashboard; loading/empty/error; stable TrackID key; `LibraryDao.loadTracks`, `LibraryViewModel`; `existing`.
5. **Albums grid** — İki kolon, gerçek embedded artwork/fallback; albüm seçimi detail; path yoksa cover uydurulmaz; grid item label; collections DAO; `existing`.
6. **Album Detail** — Başlık, artist, artwork, sayım ve tracks; play/add queue gelecekteki player contract'ına bağlanır; geri Albums; artwork yok/track unavailable/error; artwork contentDescription; filtered Room query; `partial`.
7. **Artists list** — Tek kolon full-width cards; fotoğraf, artwork, monogram ve placeholder yok; artist, distinct album/song, non-zero MP3/FLAC; detail navigation; geri collections; empty/error; horizontal stats scroll; artist DAO; `existing`.
8. **Artist Detail** — Artist başlığı, albüm ve şarkı ilişkileri; selection/filter; geri artist list; unavailable/empty; semantics; Room effective artist; `partial`.
9. **Genres list** — Metadata ilişkileri olarak tek kolon; genre seçimi; fiziksel folder ile karıştırılmaz; empty/error; accessible labels; Room normalized/override genre; `existing`.
10. **Genre Detail** — Genre track listesi ve sayım; play/filter contract; geri genres; missing metadata; stable keys; filtered DAO; `partial`.
11. **Folders list** — Yalnız gerçek `TrackLocation.path`; hierarchy yoksa fictional path üretmez; folder selection; geri collections; unsupported/empty/error; path okunabilirliği; Room location path; `existing`, hierarchy reconstruction planned.
12. **Folder Detail** — Gerçek path altındaki trackler; scan/player actions backend'e bağlı; geri folder list; unavailable; path semantics; filtered DAO; `partial`.
13. **Playlists overview** — Room playlist summaries; create/open; geri dashboard; no playlists/error; list semantics; playlist schema absent; `planned`.
14. **Playlist Detail** — Canonical unique TrackID ordered list; add/remove/reorder; geri overview; empty/unavailable; reorder announcements; playlist repository absent; `planned`.
15. **Create/Edit Playlist modal** — Name, validation, save/cancel; modal back dismisses; error preserves input; labels; persistence absent; `planned`.
16. **Favorites** — Room favorite relation and stable ordering; toggle/open; empty; no fake toggles; backend absent; `planned`.
17. **Recently Added** — scan timestamps from Room; page and empty state; no automatic scan; backend query absent; `planned`.
18. **Most Played** — gerçek PlaybackEvent threshold; reset policy ayrı; empty; no fabricated counts; event schema not implemented; `planned`.
19. **Search** — Text field, clear, back and result categories; 250 ms debounce, cancellation, effective metadata, song/artist/album/genre/folder results; back clears screen; loading/no results/error; IME action and content description; `LibrarySearchQuery`, `RoomLibraryRepository.search`, `LibrarySearchViewModel`; `implemented tonight`.
20. **Search Results and filters** — Search result categories are independently countable and paged; category navigation must reuse existing detail screens; unavailable songs remain visible but subdued; no lyrics-content/online search claim; same backend; `implemented tonight` for result presentation, detail routing `partial`.
21. **Mini Player** — Small artwork/title/artist, play-pause/next, tap Now Playing; never obscures nav/gestures; loading/error/unavailable disabled states; semantics; Media3 state; no production player yet; `planned`.
22. **Now Playing** — Large real artwork, title/artist/album, progress, elapsed/remaining, prev/next, shuffle/repeat/favorite/lyrics/EQ/sleep shortcuts; controls disabled truthfully; Media3/MediaSession; `planned`.
23. **Queue / Up Next** — One ordered queue, current highlight, add/remove/reorder, shuffle/repeat; persistence must not autoplay on restore; queue contract absent; `planned`.
24. **Lyrics** — Local plain/LRC only; synced scrolling only with real timing; unavailable truthful; no download/invention; lyrics model absent; `planned`.
25. **Track Information** — Effective metadata, location/format/status, TrackID; no raw override loss; back from modal; Room track/location; `planned`.
26. **Tag Editor / overrides** — Room-only overrides; never writes original audio tags; save/cancel/error; Room override DAO exists; editor UI absent; `planned`.
27. **Equalizer** — Adaptive primary, manual secondary; sliders reflect real DSP state; if DSP absent show planned contract, never fake analysis; EQ backend absent; `planned`.
28. **Sleep Timer** — Duration/end-of-track policy with real player; no fake countdown; backend absent; `planned`.
29. **Settings Dashboard** — Appearance, source scanning, output behavior and integration links; only real toggles; back hierarchy; settings persistence absent; `planned`.
30. **Appearance settings** — Dark/Light/System preference and preview; apply after persistence; theme currently follows system; `partial`.
31. **Media scanning / source settings** — registered SAF sources, manual scan, progress/pause/cancel, no auto full scan; existing source/scan coordinators; `existing`.
32. **Headphone/Bluetooth behavior** — display constitution policy: unexpected disconnect pauses, no auto-resume; calls may resume according to focus; preference only when backend exists; MediaSession absent; `planned`.
33. **Media notification / lock screen** — MediaSession transport controls and real notification; no decorative fake notification; Media3 service absent; `planned`.
34. **Home widget 4x1** — title/artwork/play-pause/next and current session; AppWidget/Glance backend absent; `planned`.
35. **Home widget 4x2** — expanded controls/queue preview; same session, no second player; backend absent; `planned`.
36. **Android Auto** — Media3 MediaLibraryService browsing using phone session; no static phone screen claim; manifest/service/templates/tests absent; `planned`.
37. **Wear OS companion** — phone remote for Now Playing/volume/queue, no watch DB/audio; companion module and transport absent; `planned`.
38. **Additional existing screens** — Source management and metadata progress are part of current app; preserve their behavior and back navigation; `existing`.

## Implementation contracts and acceptance

Search uses one Room-backed effective-metadata query, stable `track_id` ordering, bounded pages and real folder paths. SQLite `lower()/NOCASE` is used for offline matching; full Turkish collation is not claimed because the current SQLite/Android layer has no custom Turkish collation. Playback must later resolve TrackID to an accessible TrackLocation, prefer valid available alternatives and never mutate accessibility on decoder failure. A queue restore must not autoplay. Android Auto/Wear remain separate platform work.
