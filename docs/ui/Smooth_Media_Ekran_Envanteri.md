# Smooth Media Ekran Envanteri

**Durum:** UI/UX tasarım envanteri
**Kapsam:** Konuşulan ekranlar ve ek arayüz bileşenleri
**Not:** Envanter, uygulanmış özellik listesi veya geliştirme taahhüdü değildir. Nihai Mimari Kararlar bağlayıcıdır.

## 1. Kütüphane ve keşif

| No. | Ekran / Bileşen | İçerik |
|---|---|---|
| 1.1 | Library Dashboard | Ana sayfa, kütüphane özeti, hızlı erişim |
| 1.2 | All Songs | Şarkı listesi, filtreleme, sıralama |
| 1.3 | Albums | Kapak ızgarası, sıralama |
| 1.4 | Album Detail | Albüm kapağı, bilgileri, şarkıları |
| 1.5 | Artists | Sanatçı listesi |
| 1.6 | Genres | Hiyerarşik tür ağacı, çoklu tür |
| 1.7 | Artist Detail | Albümler, şarkılar, ilişkili içerik |
| 1.8 | Genre Detail | Alt türler, sanatçılar, şarkılar |
| 1.9 | Folders | SAF kaynakları ve klasör ağacı |
| 1.10 | Folder Detail | Klasör içeriği ve gezinme |

## 2. Oynatıcı

| No. | Ekran / Bileşen | İçerik |
|---|---|---|
| 2.1 | MiniPlayer | Kalıcı mini oynatıcı |
| 2.2 | Now Playing | Tam ekran oynatıcı, kapak, kontroller |
| 2.3 | Queue | Ghost Context, User Queue, sıra yönetimi |
| 2.4 | Lyrics View | Plain ve senkronize şarkı sözleri |

## 3. Arama

| No. | Ekran / Bileşen | İçerik |
|---|---|---|
| 3.1 | Search | Şarkı, sanatçı, albüm, tür, söz, klasör |
| 3.2 | Search Results | Gruplandırılmış sonuçlar, filtreler |
| 3.3 | Search History | Son aramalar ve geçmiş yönetimi |

## 4. Çalma listeleri

| No. | Ekran / Bileşen | İçerik |
|---|---|---|
| 4.1 | Playlists | Liste koleksiyonu |
| 4.2 | Playlist Detail | Kapak, içerik, sıra, oynatma |
| 4.3 | Playlist Editor | Oluşturma, düzenleme, çoğaltma, sürüm |
| 4.4 | Smart Playlists | AND/OR/NOT kuralları, dinamik listeler |

## 5. Equalizer

| No. | Ekran / Bileşen | İçerik |
|---|---|---|
| 5.1 | EQ Dashboard | Adaptive / Manual geçişi |
| 5.2 | Adaptive EQ | Canlı hareket eden bantlar |
| 5.3 | Manual EQ | Gerçek 24 bant kontrolü |
| 5.4 | EQ Presets | Flat, Rock, Metal, Bass Boost, Vocal, Jazz, Classical, Custom |

## 6. Müzik bilgileri ve geçmiş

| No. | Ekran / Bileşen | İçerik |
|---|---|---|
| 6.1 | Track Details | Metadata, kaynak, türler, teknik bilgiler |
| 6.2 | Track Edit | Kullanıcı düzeltmeleri ve sınıflandırma |
| 6.3 | Listening History | Dinleme geçmişi |
| 6.4 | Statistics | Dinleme istatistikleri |
| 6.5 | Various Artists / Collaborations | Derlemeler ve feat. ilişkileri |

## 7. Kütüphane yönetimi

| No. | Ekran / Bileşen | İçerik |
|---|---|---|
| 7.1 | Library Sources | SAF klasörleri ekleme/çıkarma |
| 7.2 | Scan Progress | Tarama ilerlemesi |
| 7.3 | Scan Results | Bulunan, atlanan ve sorunlu dosyalar |
| 7.4 | Review Candidates | Kimlik eşleşmesi inceleme |
| 7.5 | Excluded Tracks | Hariç tutulan parçalar |
| 7.6 | Trash | Süresiz, manuel yönetilen çöp kutusu |

## 8. Ayarlar ve sistem

| No. | Ekran / Bileşen | İçerik |
|---|---|---|
| 8.1 | Settings | Genel ayarlar |
| 8.2 | Playback Settings | Repeat, Shuffle, Autoplay |
| 8.3 | Library Settings | Tarama ve katalog tercihleri |
| 8.4 | Appearance | Dark / Light ve vurgu renkleri |
| 8.5 | Backup & Restore | Taşınabilir yedekleme ve geri yükleme |
| 8.6 | About / Diagnostics | Sürüm, hata ve sistem bilgileri |

## Ek arayüzler ve bileşenler

- Sleep Timer
- Medya bildirimi ve kilit ekranı kontrolleri
- Çalma listesi kapak kolajı
- Kısa audio preview
- Smart Continuation'ın Queue üzerindeki gösterimi

## Tasarım çalışma notları

- Önceki görsel tasarım serisindeki numaralandırma farklıdır (örneğin 4.1 Equalizer, 4.2 Sleep Timer, 4.3 Settings). Bu belge işlevsel gruplamayı esas alır.
- Öncelikli tasarım sırası: kütüphane → oynatıcı → arama → çalma listeleri → EQ → yönetim/ayarlar.
- Önceki koyu tema, turkuaz vurgu ve kompakt oynatıcı tasarım dili korunur.
- UI/UX tasarım çalışmaları, devam eden P02A kodlama ve hata düzeltmelerinden ayrı yürütülür.
