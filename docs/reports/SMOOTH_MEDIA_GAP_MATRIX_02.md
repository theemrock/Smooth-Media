# Smooth Media | İlk vardiya GAP Matrix — devam 02

Bu matris kod, test ve cihaz kanıtını dokümantasyondan ayrı değerlendirir.

| İş paketi / ekran | Durum | Kanıt / açık iş |
|---|---|---|
| P01 catalog identity / Room / SAF | TESTED | Önceki 7049/7068/7041/8 baseline korunuyor; yeni migration/scan yok. |
| P01-09 metadata enrichment | TESTED | Mevcut testler korunuyor; yeniden tarama yapılmadı. |
| P01-10 Songs | TESTED | Existing paging/artwork/unavailable list korunuyor. |
| P01-10 Albums | TESTED | Existing two-column grid ve artwork korunuyor. |
| P01-10 Artists | TESTED | Single-column, no image, counts ve zero-hide korunuyor. |
| P01-10 Genres | TESTED | Existing grouping korunuyor. |
| P01-10 Folders | PARTIAL | Gerçek path kullanılıyor; güvenli hierarchy reconstruction yok. |
| P01-12 Search backend | TESTED | Effective metadata, folder path, bounded raw SQL, stable TrackID ordering. |
| P01-12 Search UI | PARTIAL | Field/clear/back/loading/empty/error/category list var; detail drilldown ve full category pagination eksik. |
| Türkçe search collation | PARTIAL | SQLite lower/NOCASE gerçekçi fallback; custom Turkish collation yok. |
| Lyrics-content search | DOCUMENTED ONLY | Index/provider yok; sahte destek yok. |
| P02 Media3 dependency | IMPLEMENTED | `media3-exoplayer` + `media3-session` implementation. |
| P02 single player | IMPLEMENTED | HandlerThread-owned ExoPlayer, one service owner, release latch. |
| TrackID → Location → SAF URI | IMPLEMENTED | Catalog resolver, availability/exclusion checks, content URI only. |
| MP3 / FLAC | IMPLEMENTED | Media3 MediaItem path; FLAC preferred when accessible. Device audio proof intentionally skipped. |
| fallback location | IMPLEMENTED | Decoder error sonrası same TrackID alternative location, near position attempt. |
| play/pause/resume/seek/progress/error | PARTIAL | Engine/client/Now Playing wired; device playback not audibly exercised. |
| end-of-track next/previous | PARTIAL | MediaController transport API and queue domain exist; full queue UI wiring pending. |
| MediaLibraryService | IMPLEMENTED | Service manifest/session/notification integration compiled. Catalog browsing callbacks pending. |
| audio focus / noisy | IMPLEMENTED | ExoPlayer audio attributes + handleAudioBecomingNoisy. Call-specific resume policy pending verification. |
| queue duplicate/repeat/shuffle policy | TESTED | Domain controller and focused tests. |
| queue persistence / restore no autoplay | PARTIAL | State contract says no autoplay; Room/preferences persistence not integrated. |
| Mini Player | PARTIAL | Real controller state, title and pause/resume; next button and artwork absent. |
| Now Playing | PARTIAL | Real pause/resume/seek/progress/error/EQ entry; artwork, next/previous/shuffle/repeat/favorite/lyrics absent. |
| Queue screen | DOCUMENTED ONLY | Domain/controller exists; Compose queue screen not yet integrated. |
| Splash/startup | EXISTING | Existing startup path; dedicated splash contract not expanded. |
| SAF source selection | EXISTING | Existing source registry and permission explanation. |
| Library dashboard | PARTIAL | Existing library dashboard; playback/search navigation incomplete. |
| Album detail | DOCUMENTED ONLY | Existing filtered flow, dedicated detail not implemented. |
| Artist detail | DOCUMENTED ONLY | No dedicated detail screen. |
| Genre detail | DOCUMENTED ONLY | Filtered flow only. |
| Folder detail | DOCUMENTED ONLY | Filtered flow only; no fabricated hierarchy. |
| Playlists / edit | DOCUMENTED ONLY | No playlist schema/UI; no fake data. |
| Favorites / recently added / most played | DOCUMENTED ONLY | No real relation/history backend added. |
| Lyrics view | DOCUMENTED ONLY | No local plain/LRC model/UI added. |
| Track information | DOCUMENTED ONLY | No dedicated screen. |
| Tag editor | DOCUMENTED ONLY | Existing override DAO architecture preserved; no file-tag writer. |
| Settings / appearance / source settings | PARTIAL | Source UI exists; appearance currently system theme; dedicated settings hierarchy absent. |
| EQ Manual backend | PARTIAL | Android `Equalizer` attaches to Media3 audio session and clamps levels; persisted profile/UI application not wired. |
| EQ Adaptive | PARTIAL | Explicit unavailable state without measured input; no invented learning algorithm. |
| Sleep timer | DOCUMENTED ONLY | No backend. |
| Notification/lock-screen | PARTIAL | MediaLibraryService integration compiled; device notification transport not inspected. |
| Widgets | DOCUMENTED ONLY | No AppWidget/Glance module. |
| Android Auto | DOCUMENTED ONLY | Service seam exists; browsing/templates/automotive tests absent. |
| Wear OS | DOCUMENTED ONLY | No companion module. |

## Önceliklendirilmiş kalan işler

1. Queue persistence + real Queue Compose screen + add/remove/reorder actions.
2. MediaLibraryService catalog browsing callbacks and device-safe controller lifecycle test.
3. Now Playing artwork/next/previous/shuffle/repeat and track transition state.
4. EQ profile persistence and manual band binding to exposed audio session; Adaptive only after a real measured-input design exists.
5. Playlists/favorites/history data model, then detail screens and settings hierarchy.

**P01 COMPLETE değildir:** Search backend gerçek ve testlidir; UI drilldown/category pagination ve Türkçe custom collation eksikleri nedeniyle P01 kapanış etiketi verilmedi.
