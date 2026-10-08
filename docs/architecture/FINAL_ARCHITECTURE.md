# SMOOTH MEDIA

## Nihai Mimari Kararlar | Konsolide Anayasa

**Revizyon:** 2026-10-08 / Grill Me sonrasındaki konsolidasyon
**Ürün:** Smooth Media (`com.theemrock.smoothmedia`)
**Repository:** `theemrock/Smooth-Media`
**Statü:** Tasarım kararları konsolide edildi; bu belge tek başına kodun uygulandığını veya testlerden geçtiğini iddia etmez.

> **Temel ilke:** Smooth Media, ses dosyalarını oynatan basit bir ekran değil; kendi canonical müzik kataloğu olan, Android telefonun ilk istemci olduğu bir media system'dır.

### Okuma ve değişiklik kuralları

- **NORMATİF:** “olacak”, “olmayacak”, “yasak”, “yalnızca” gibi ifadeler uygulanacak karardır.
- **İLERİ FAZ:** Özelliğin mimari kapısı açık; Phase 01 ya da Phase 02 kapsamı olduğuna dair söz verilmez.
- **SUPERSEDED:** Önceki ifadeyi sonradan verilen açık kararla geçersiz kılar. Tarihsel iz, sonda yer alan değişiklik tablosundadır.
- **İPTAL:** Grill Me 95, 123 ve 128 bir mimari karar üretmedi; hiçbir uygulama kuralına dönüştürülmedi.
- **ANAYASA SAPMASI:** Kodda veya sonraki belgelerde bu kurallarla çelişen değişiklik fark edilirse önce işaretlenir; anayasa kullanıcı onayı olmadan değiştirilmez.
- **Öncelik:** Sonradan açıkça onaylanmış kullanıcı kararları, önceki “olabilir / değerlendirilebilir” tasarım önerilerini geçersiz kılar. Teknik fizibilite çözülmemişse varsayım sessizce karar haline getirilmez.

### Faz çerçevesi

| Faz | Asıl sorumluluk | Hariç |
|---|---|---|
| **P01 Catalog Foundation** | Kullanıcı kontrollü MP3/FLAC tarama, metadata, kimlik, Room katalog, ilişkiler, Library/Search | Production playback, EQ, AutoEQ, Android Auto, lyrics download, karaoke |
| **P01X Developer Smoke** | Geliştiriciye özel kısa Media3 URI doğrulaması | Kullanıcıya açık müzik oynatıcı |
| **P02 Playback Foundation** | Media3, tek queue, MediaLibraryService, temel kontrol, Mini Player, Now Playing | İleri DSP/Adaptive EQ, gelişmiş öneriler ve bütün ileri faz entegrasyonlarını otomatik olarak içermez |
| **İleri fazlar** | Lyrics, geniş DSP/EQ, autoplay öğrenmesi, istatistik hikâyeleri, Android Auto/Wear, yedekleme senaryolarının ürünleştirilmesi | Her özellik ayrı uygulama/test kapsamı kazanır |

---

# I. CATALOG & IDENTITY

## 1. Ürün konumu

Smooth Media'nın kendi kataloğu vardır. İlk istemci Android telefondur; Android Auto aynı telefonun oynatma oturumuna bağlanır. Wear OS bir telefon companion'ıdır. İleride USB/Ethernet üzerinden araç sistemiyle veri alışverişi mümkün olabilir; bugün bağımsız araç kataloğu veya sync geliştirildiği varsayılmaz. Mimari MediaStore ID'sine ya da tek cihaz path'ine kilitlenmez.

## 2. Room canonical source of truth

**Kütüphane için tek canonical otorite Room'dur.** `Discovery → Scanner → Metadata → Identity → Room Catalog → Library/Search`. MediaStore, SAF ve gelecekteki USB kaynakları discovery girdileridir; kendi başlarına library DB değildir. Kullanıcı tarafından düzenlenmiş metadata, playlist, history, tercih, eksik dosya ve exclusion kayıtları Room'da yaşar.

## 3. Track canonical entity

`Track` mantıksal kayıttır; fiziksel dosya değildir. Stable `trackId`, title, duration/identity metadata ve ilişkileri vardır. `trackId` MediaStore `_ID`, dosya adı, path veya otomatik artan device-local row ID ile özdeşleştirilmez. Aynı müzik kaydının erişilebilir MP3 ve FLAC kopyaları, **ayrı mantıksal birleştirme doğrulamasından sonra** aynı Track'e bağlanabilir. Farklı performans/remaster/mastering otomatik olarak aynı Track sayılmaz.

## 4. TrackLocation

Her fiziksel örnek ayrı `TrackLocation` olarak tutulur. En az: `id`, `trackId`, `sourceId/device`, `uri`, opsiyonel `path`, opsiyonel `mediaStoreId`, `fileSize`, `modifiedAt`, `codec/format`, `availability`, `originalFileHash`, `currentFileHash` ve gerekli doğrulama bilgileri.

- `originalFileHash`: Bu konumun ilk güvenilir importunda hesaplanan tam fiziksel dosya hash'i; sonradan üzerine yazılmaz.
- `currentFileHash`: Bu konum için son doğrulanan gerçek fiziksel dosyanın tam hash'i; fiziksel içeriğin değiştiği güvenilir biçimde doğrulanırsa güncellenir.
- Hash **ses fingerprint'i değildir**; MP3 ve FLAC aynı sesi içerse bile bit düzeyinde aynı hash'i taşımaz.
- Dosya hash algoritması ve alan adlarının migration politikası sürümlenir. Geçici metadata işaretleri hash kanıtı yerine geçmez.

## 5. MediaStore ID canonical kimlik değildir

`Track.id = MediaStore._ID` **yasaktır**. MediaStore kimliği yalnızca ilgili `TrackLocation` için device-local locator olarak kullanılabilir. URI/path değişimlerinde canonical TrackID güvenilir hash eşleşmesiyle korunur.

## 6. TrackIdentityResolver

Resolver iki işi **ayrı** yapar:

1. **Fiziksel kimliği doğrulama:** Gelen dosyanın tam hash'i kayıtlı `originalFileHash` veya `currentFileHash` ile eşleşirse mevcut fiziksel kimliğe güvenle yeniden bağlanabilir. Sırf ad, path, duration veya metadata benziyor diye eski TrackID aktarılmaz.
2. **Mantıksal kayıt ilişkisi:** Aynı kaydın farklı kodlamaları için ayrı, kanıt/inceleme tabanlı `merge` işlemi vardır. Confidence skoru yalnızca aday gösterebilir; **hash eşleşmeyen dosyayı otomatik olarak eski TrackID'ye dönüştürmez**. Kullanıcının açık `merge/split` kararı kalıcıdır. Böylece katı hash kararı korunurken MP3+FLAC aynı Track altında temsil edilebilir.

Bir dosyanın iki kayıtlı hash'i de değişmişse yeni dosya/yeni TrackID oluşturulur. Eski Track/TrackLocation kaydı kaybolduğu için otomatik silinmez; durum uygun şekilde güncellenir. `merge/split` sonrasında playlist, favori, geçmiş ve istatistik bağlantıları veri kaybetmeden göç ettirilir; kullanıcı tercihleri taramayla geri çevrilmez.

**Açık teknik gerilim:** İlk Grill Me kararı güven skorlu aynı-kayıt birleştirmesini mümkün görürken, sonraki karar hash dışı otomatik kimlik atamasını yasakladı. Bu metin güven skorunu **öneri/inceleme** düzeyinde tutar. Otomatik cross-format merge'e geçiş ayrı bir anayasa değişikliği ister.

## 7. Audio fingerprint ilk fazda yok

AcoustID, MusicBrainz zorunluluğu ve akustik fingerprint ilk faz kapsamında değildir. **Bu yasak tam fiziksel dosya hash'ini kapsamaz:** Grill Me kararıyla full-file hash kimlik için gerekli hale gelmiştir. Büyük FLAC arşivinde I/O, pil ve tarama süresi ölçülerek throttling/progress uygulanır. Hash işleminin kısa yoluyla yanlış TrackID bağlamak yasaktır.

---

# II. SCANNER

## 8. Smooth kendi scanner'ına sahip olacak

`DiscoverySource → MediaScanner → MetadataReader → MetadataNormalizer → TrackIdentityResolver → CatalogWriter`. Tarama ve katalog yazımı UI'den ayrılır; idempotent, kesintiden devam edebilir ve oyun/oynatma gibi daha öncelikli işleri engellemez.

## 9. MediaStore discovery source olabilir

`MediaStoreDiscovery`, `SAF/FileDiscovery` ve gelecekte external/removable adapter'lar kullanılabilir. Modern Android'in izin modeline uyulur. Kullanıcının seçmediği klasörlerin tüm cihaz çapında otomatik importu yapılmaz. MediaStore kaynak listelemesi katalog sahibi değildir.

## 10. Phase 01 format sınırı

İlk kapsam **MP3 ve FLAC**. Diğer codec/container türleri sonradan bağımsız policy/adapter ile eklenebilir. Desteklenmeyen veya okunamayan dosya kataloğa bozuk hayalet kayıt olarak yazılmaz.

## 11. Incremental scanning tasarıma dahil

İlk tarama ve devam taramaları kullanıcı komutuyla başlar; uygulama açılışında veya arka planda periyodik otomatik tam tarama yapılmaz. Tek seferde yalnız **bir** tarama çalışır. Değişmeyen dosyalar gereksiz yeniden işlenmez; hash doğrulamasını gerektiren kimlik olaylarında hash atlanmaz. Tarama duraklatılabilir/iptal edilebilir. Kesilirse başarıyla yazılmış kayıtlar ve ilerleme korunur; sonraki **manuel** tarama kaldığı yerden güvenle devam eder. Playback ile eşzamanlı çalışırsa playback önceliklidir. Okunamayan dosya sessizce atlanır, sonsuz retry yapılmaz; eski katalog kayıtları bu yüzden silinmez.

---

# III. METADATA

## 12. Embedded metadata first-class input

MP3/FLAC içinden title, artist, album, album artist, genre(ler), disk/track numarası, tarih, lyrics, composer, lyricist, arranger, producer, comment, encoder, copyright, artwork; teknik olarak duration, bitrate, sample rate, channels, codec/format ve size okunabilir. Embedded veri **girdidir**, kullanıcı Room override'ının üzerinde otorite değildir.

## 13. Raw metadata ile normalized metadata ayrılacak

Dosyadan okunan değer ve arama/eşleştirmede kullanılan normalize değer ayrı tutulur. Kullanıcı tarafından düzenlenen Room alanları ayrıca işaretlenir. Scan, kullanıcı override'ını sessizce ezmez. Düşük riskli düzeltmeler otomatik uygulanabilir; kimlik/ana alan uyuşmazlığı kontrollü incelemeye gider.

## 14. Metadata editing desteklenecek

Single/batch Tag Editor desteklenir ancak **yalnızca Room kataloğunu günceller**. Ses dosyasının ID3/Vorbis yorumlarını, embedded artwork'ünü veya fiziksel içeriğini değiştirmez. Genre/album gibi düzenlemeler ilgili Room relations ve search index'i günceller; ayrı dosya izni istemez. Bu hüküm önceki file-tag-write fikrini geçersiz kılar.

## 15. Metadata düzenleme sonrası katalog güncellemesi

**SUPERSEDED: eski `Permission → MetadataWriter → File → Targeted Rescan` akışı kaldırıldı.** Yeni akış `User Edit → Validate → Room Transaction → Relations / FTS / UI Invalidation` olur. Room'da kullanıcı override'ı ile hâlâ aynı hash kimliğine bağlı, farklı kaynaktan okunan alanlar arasında tutarsızlık varsa alan bazında Room-koru / dosyayı-kabul-et seçimi sunulabilir; varsayılan **Room'u koru**. **Önemli sınır:** MP3/FLAC içindeki embedded tag'in dış araçla değiştirilmesi normalde tam dosya hash'ini de değiştirir. İki kayıtlı hash de artık eşleşmiyorsa §6 gereği yeni fiziksel kimlik oluşur; bu durumda eski Track'in override'ları otomatik taşınmaz. Bu nedenle eski “tag conflict” akışı dış dosya düzenlemeleri için genel bir eşleştirme kaçamağı olarak kullanılmaz.

---

# IV. ARTIST / ALBUM

## 16. Artist canonical entity

Artist salt serbest string değildir; `Artist`, `TrackArtist` ve role/ilişki modeli kurulur. Primary, secondary/featured, Album Artist, Original Artist, Composer ve `Tribute To` rolleri ayrılabilir. Kullanıcı düzeltmeleri kalıcıdır.

## 17. Multi-artist önü kapanmayacak

`Track.artist = "A & B"` tek alanı canonical ilişki modeli olamaz. Artist ekranında kendi albümleri, konuk olduğu parçalar, cover'lar ve tribute kayıtları ayrı gösterilebilir. `Various Artists` çıkarımı dikkatli yapılır ve manuel override'a açıktır.

## 18. Album canonical entity

Album, MediaStore grouping sonucu değildir. Album/Track/AlbumArtist ilişkileri ve sürüm bilgisi tutulur. Original, remaster, deluxe sürümleri gerektiğinde aynı Album ailesinde açılır/kapanır; **farklı performans/mastering dosyaları aynı TrackID'ye zorlanmaz**. Ana albüm görseli original sürüm artwork'ü olarak seçilir; bu, diskte en eski dosyayı esas almak anlamına gelmez.

---

# V. GENRE & TAG

## 19. Multi-genre temel invariant

`Track ↔ Genre` **many-to-many**. Bir Track birden fazla genre ve bir primary genre barındırabilir; primary seçimi diğer üyelikleri silmez.

## 20. Genre hierarchy

Genre'lar kendi parent/child hiyerarşisini korur. Mantıksal genre ağacı fiziksel klasör ağacı değildir. Bir Track farklı genre dallarında yer alabilir.

## 21. Multi-genre ile hierarchy bağımsız

Tek Track birden fazla hiyerarşi dalındaki Genre ile ilişkilendirilebilir. Kullanıcının genre düzenlemesi fiziksel dosyaya veya path'e dokunmaz.

## 22. Primary Genre

0..N Genre ilişkisi yanında isteğe bağlı `primaryGenre` vardır. Genre/tag üzerinden filtreleme, öneri ve ileriki ses davranışları bu ayrımı okuyabilir; multi-genre yapısı bozulmaz.

## 23. Primary Genre ileride davranış sağlayabilir

Önceki `Track EQ → Genre EQ → Default EQ` şeması **uygulanmış bir ürün davranışı değildir**; Grill Me'deki ana karar Adaptive EQ + isteğe bağlı Manual EQ ve tek ortak EQ profilidir. Genre bilgisinin gelecekte önerilere katkı sunması mümkündür; otomatik genre/output EQ inheritance bu belgede taahhüt edilmez. EQ ayrıntılarında önceki onaylı tasarım esas alınır; yeni parametre uydurulmaz.

---

# VI. FOLDERS

## 24. Folder görünümü gerçek filesystem mantığını koruyacak

Klasör sekmesi fiziksel dizin hiyerarşisini gösterir; genre ağacı olarak yeniden yorumlanmaz. Bu görünüm özellikle eksik/bozuk tag'li arşiv için korunur.

## 25. Folder ikinci sınıf library özelliği değildir

Songs, Albums, Artists, Genres ve Folders aynı katalogdaki farklı erişim görünümleridir. Seçilen kaynaklar ve alt klasörleri görünür olabilir; silinen/erişilemeyen kaynaklar tutarlı durumla işaretlenir.

## 26. Genre ile Folder birbirine karışmayacak

Genre düzenlemesi dosya taşımaz. Uygulama dosya taşıma/kopyalama/yeniden adlandırma/klasör yönetimi yapmaz; istisna, açık kullanıcı onayıyla fiziksel silmedir.

---

# VII. SEARCH

## 27. Search ayrı subsystem

Room + Paging 3 + FTS/uygun indeksler, parametreli SQL. Büyük kütüphaneyi bütünüyle RAM'e yükleyip client-side filtreleme yapılmaz. İndeks yeniden kurma kullanıcı verisini silmez.

## 28. Nihai global search alanları

Şarkı, sanatçı, albüm, genre, lyrics ve folder içinde arama. İleri kurallar: multi-parent Tag DAG, Track–Tag ve Genre–Tag ilişkileri; AND/OR/NOT, parantezler ve descendant genişletme; gelişmiş sıralama/limit/shuffle. Kullanıcı metaverisi indekslenir.

## 29. P01 Search scope

P01 şarkı, sanatçı, albüm, genre ve folder sorgularına odaklanır. Lyrics verisi daha sonra geldiğinde indeks genişler; P01'i lyrics download'a bağımlı yapmak yasaktır.

## 30. Search filters + recent searches

Tür, sanatçı, albüm, genre, folder vb. filtreler ve son aramalar öngörülür. Geniş filtreler yalnızca ilgili fazda uygulanır; query birleşimleri SQL ile güvenli ve ölçekli yürütülür.

---

# VIII. ARTWORK

## 31. ArtworkResolver

UI, görsel önceliklendirme/okuma işini doğrudan üstlenmez. Kaynaklar: kullanıcı override'ı, embedded, folder artwork, mümkünse cache, generated fallback. Kullanıcı özel playlist kapağı optimize edilerek app-private storage'a kopyalanır; geçici kaynak iznine bağımlı kalmaz.

## 32. Artwork priority

Kullanıcının bilinçli custom override'ı otomatik keşiften üstündür. Albüm için original sürüm kapak tercih edilir (§18). Diğer embedded/folder/cache fallback sırası uygulama testinde belirlenir; bu belgede belirlenmemiş ayrıntı “kesin” diye kodlanmaz. Otomatik playlist kolajının kararlılık kuralları §109'dadır.

---

# IX. PLAYLIST

## 33. Playlist normalized olacak

`Playlist → PlaylistEntry(id, playlistId, trackId, position, addedAt)`; şarkı metadata'sı kopyalanmaz. Playlist Entry canonical TrackID'yi referans alır, TrackLocation'ı değil. Normal/Smart playlist ayrımı korunur.

## 34. PlaylistEntry kendi stable ID'sine sahip olacak

Entry'nin stable ID'si korunur **fakat normal playlist'te `(playlistId, trackId)` benzersizdir**. Aynı TrackID ikinci kez eklenmez; bulk işlem duplikeleri atlar. Eski örnekteki A-B-A tekrarı **SUPERSEDED**. Stable entry ID gelecekte edit/reorder ve migration için kalır.

## 35. STATIC / SMART ayrımı

STATIC playlist TrackID üyeliklerini saklar. SMART playlist sorgu/rule'larını saklar, üyelik dinamiktir. Aktif playback queue ise Smart query'nin o andaki sabit snapshot'ıdır; otomatik canlı yeniden sıralanmaz.

## 36. Smart Collections

Favorites, Recently Played, Most Played, Never Played, Recently Added ve Forgotten Tracks mantıksal sistem koleksiyonları olabilir. İsimlerine bakarak playlist identity çıkarılmaz.

## 37. Favorites display name ile tanımlanmayacak

Stable enum/dedicated relation kullanılır. Görünen ad Türkçe veya İngilizce yerelleştirilebilir; identity değişmez. Playlist isimlerinde trim + case-insensitive unique kuralı vardır, çakışmada V1/V2 önerisi veya restore için `(Dirildi)` suffix'i kullanılır.

---

# X. QUEUE

## 38. Queue playlist değildir

Queue tek aktif playback session state'idir. Playlist'ten bağımsızdır; entries, current Track, position ms, repeat, shuffle sırası ve düzenlemeler saklanır. İkinci paralel queue/queue history yoktur.

## 39. Queue kullanıcı tarafından düzenlenebilir

Play Next, Add to Queue, Remove, Reorder, Clear, Save as Playlist. Aynı TrackID queue'da iki kez tutulmaz. Play Next mevcut girdiyi sıradaki pozisyona **taşır**; Add Queue mevcut girdiyi **atlar**. Played girdiler queue'da kalır, previous gezinmesi yapılabilir; Repeat All kurala göre döngü sağlar.

## 40. Queue persistence

Queue sırası, shuffle permutation, Repeat, current Track, ms position ve düzenlemeler process death/reboot sonrası geri gelir. Uygulama tekrar açılınca **otomatik playback başlamaz**. Ulaşılamayan Track girdileri sessizce silinmez; skip/fallback anlık yürütülür.

---

# XI. PLAYBACK & AUDIO ARCHITECTURE

## 41. Media3 / ExoPlayer

PlaybackEngine Media3/ExoPlayer tabanlıdır. Canonical TrackID'den o anda erişilebilen en kaliteli TrackLocation seçilir; MP3+FLAC varsa uygun durumda FLAC tercih edilir. EQ decode edilmiş PCM'e uygulanır; dosya etiketine yazılmaz.

## 42. MediaLibraryService

Playback UI içinde yaşayamaz. MediaLibraryService tek ses oturumunu ve MediaSession'ı yayınlar; notification/headset/Bluetooth/car kontrolleri aynı session'a bağlanır. Android Auto aynı library/playback sistemini kullanır.

## 43. Android Auto ayrı player değildir

Telefon ve Android Auto aynı queue/MediaLibraryService üzerinde çalışır. Araç ekranında Android'in sürüş güvenliği kurallarına uygun media browsing ve hazır platform şablonları kullanılır; özel dikkat dağıtıcı UI tasarlanmaz.

## 44. Custom audio pipeline seam

RenderersFactory, AudioSink ve AudioProcessors genişleyebilir. DSP çekirdeği UI/Service God Object içinde toplanmaz. Audio thread üzerinde dosya, Room sorgusu veya bloklayan ağ işlemi yapılmaz.

## 45. ReplayGain

Varsayılan **ON**, kullanıcı kapatabilir. Tekli/shuffle oynatmada Track Gain; ardışık albüm oynatmada Album Gain tercih edilir. Mevcutsa R128 değerlendirilebilir; veri yoksa kayda agresif analiz uygulanmaz. Clipping guard ve gerekli peak koruması vardır. Dosya içeriği değişmez.

## 46. EQ architecture

İki mod: **Adaptive EQ (ana mod)** ve kullanıcı seçimiyle **Manual EQ**. Adaptive parametreler çalışırken kaydırıcıların canlı hareketi UI'de görünür. Ayrıntılı frekans/band/öğrenme tercihleri önceki EQ tasarım kararlarına bağlıdır, bu belgenin eksik varsayımlarıyla yeniden icat edilmez. Ortak işlem hattı modülerdir, servis katmanından ayrılır.

## 47. Device-specific audio profiles

**SUPERSEDED:** Telefon/hoparlör/kulaklık/araç için otomatik ayrı EQ profilleri **yok**. Tek ortak EQ profili geçerlidir. Kullanıcı downstream araç/ses sistemi ayarlarını ayrıca yapabilir. OutputDeviceMonitor yalnız ses odağı/çıkış kesintisi gibi gerekler için bulunabilir.

## 48. Bit-perfect / DSP capability

İsteğe bağlı ve varsayılan OFF olan **gelecek faz** bit-perfect, yalnız desteklenen ve doğrulanabilen çıktılarda etkinleşir. Bit-perfect açıkken EQ, Adaptive EQ, ReplayGain, crossfade ve benzer işleme bypass edilir. Bu sırada DSP açılmak istenirse uyarı ve onay istenir: onay bit-perfect'i kapatır, iptal mevcut durumu korur. Kapatılınca önceki DSP tercihleri geri gelir.

## 49. Gapless

Gapless/live/concept-album ardışıklığı korunur. Şarkılar gerçekten kesintisiz albüm parçalarıysa crossfade bunu bozmamalıdır. Yanlış pozitif durumlar için güvenilir metadata/bağlam gerekir.

## 50. Crossfade ayrı özellik

**Smart Crossfade varsayılan OFF**, kullanıcı 0–12 saniye seçebilir. Gapless ≠ Crossfade. Shuffle/karışık listede mümkün olabilir; güvenilir live/continuous album geçişi korunur. Playlist audio preview için geçmişte konuşulan 3–5 saniyelik kısa örnek/geçiş ayrı gelecekteki özelliktir; production crossfade ile karıştırılmaz. P01/P02'nin zorunlu çıktısı değildir.

## 51. Codec fallback

Media3 decoder fallback ve extension renderer'a uygun seam korunur. Playback sırasında decoder hatasında aynı Track'in başka erişilebilir Location'ı mümkünse yaklaşık aynı pozisyondan denenir; yoksa sıradaki oynatılabilir queue girdisine geçilir. Sorunlu Location işaretlenir; Track ve history silinmez.

---
# XII. PLAYER UI

## 52. Persistent Mini Player

P02'den itibaren Library/Search/Playlists/Settings üzerinde kalıcı Mini Player bulunabilir. Mini Player aktif tek MediaSession'ı kontrol eder; ayrı ikinci player örneği yaratmaz.

## 53. Full-screen Now Playing

Artwork, title, artist, album, seek, elapsed/remaining, previous/play-pause/next, shuffle, repeat, favorite, lyrics ve queue tek oturumu gösterir. Gelecekte EQ/sleep timer/waveform gibi alanlar eklenebilir. Çalma kaynağı sessizce alternatif FLAC/MP3'e geçerse doğru teknik format bilgisi UI'de güncellenir; zorunlu toast/modal gösterilmez.

---

# XIII. LYRICS

## 54. Rich Lyrics domain

Lyrics domain yalnız timestamp + text değildir. Lines için start/end, text, translation, transliteration, actor; Words için start/end/text alanları tasarlanır. Kaynak ve edit geçmişi veri modelinde karışmaz.

## 55. Word-level timing korunacak

Kaynakta mevcut word timing import sırasında atılmaz; ilk sürüm yalnız satır-senkron gösterse bile ileride P14H Karaoke için geriye dönük veri kaybı yaratılmaz.

## 56. LRC parser capability

İleride standard/enhanced LRC, birden fazla timestamp, offset, word timing, translation, background vocals ve actor desteğine açık. Üçüncü taraf uygulamanın GPL parser kodu kopyalanmaz.

## 57. LyricsProvider abstraction

`LyricsProvider = Local | LRCLIB | FutureProvider`. Provider ve matcher, UI/MediaLibraryService içinde gömülü değildir. Çevrimdışı müzik dinleme bir lyrics sağlayıcısının çevrimiçi olmasına bağımlı olamaz.

## 58. Lyrics sources

`LOCAL_TAG`, `LOCAL_LRC`, `LOCAL_DATABASE`, `LRCLIB`, `REMOTE_PROVIDER` gibi provenance saklanır. Kullanıcının mevcut lyrics arşiv/veritabanı daha sonraki fazda ayrı adapter ile kullanılabilir.

## 59. Lyrics resolution

Önce yerel/mevcut ve Smooth DB/cache, sonra isteğe bağlı çevrimiçi provider. Synced bulunmazsa plain, o da yoksa none gösterilir. Otomatik yanlış lyrics bağlamaktansa REVIEW tercih edilir.

## 60. Lyrics matching kendi sistemimiz

`Candidate → Normalize → Score → Threshold → MATCH / REVIEW / REJECT`. Lyrics provider matching'i fiziksel Track kimlik eşleştirmesiyle (§6) aynı mekanizma değildir.

## 61. Title normalization yalnız search/matching için

`OriginalTitle` ile `NormalizedSearchTitle` ayrı kalır. “Official Video”, “Live”, “Remaster”, “feat.” vb. işaretlerin normalization kuralları canonical title'ı sessizce değiştirmez.

## 62. Synced lyrics seek

Kullanıcı bir senkron lyrics satırına dokununca ilgili `LyricsLine.start` konumuna seek yapılır. Lyrics UX medya oturumuna komut verir, yeni playback engine oluşturmaz.

---

# XIV. HISTORY / STATS

## 63. TrackStats

`TrackStats` canonical TrackID üzerinden aggregate tutar: `playCount`, `lastPlayedAt` ve gerekirse kullanıcı onaylı minimal olaylardan türetilebilen sayılar. **Eski `skipCount` ve `totalPlayedMs` alanları zorunlu tarihçe metrikleri değildir.** Early skip sinyali gerekliyse History'ye ek alan açmadan ayrı `PreferenceModel` işleminde kullanılır. Sahip olunmayan süre bilgisine dayanarak “dinlenen toplam dakika” uydurulmaz.

## 64. PlaybackCountingPolicy

**Sabit ürün kuralı:** Track süresinin en az %50'si gerçekten dinlenmişse anlamlı dinleme sayılır. Seek ile atlanan bölüm ve pause süresi sayılmaz; bir ilgili session içinde gereksiz mükerrer Count yazılmaz. Playlist Play Count da içindeki en az bir Track bu koşulu karşıladığında o playback session için +1 artar. Completed Count ayrı tutulmaz. Teknik policy katmanı test edilebilir, kullanıcı kararı değiştirilmeksizin keyfi eşik uygulanamaz.

## 65. PlaybackEvent

Minimal ve süresiz yerel history kaydı: `trackId`, gerçek zaman `timestamp` ve **olayın yaşandığı tarihsel yerel saat dilimi/ofseti**. Ayrı `playedMs`, `completed/skipped`, output, source/device veya pause dizisi tutulmaz. Android sistem saatine güvenilir; anomali düzeltme motoru yoktur. Olay yıl/ay hesapları sonraki cihaz saat dilimi değişiminde yeniden kaymaz. History kullanıcı silince silinir, otomatik pruning yoktur. Güncel sonuçları bu alanlardan türetilemeyen istatistikler sunulmaz.

---

# XV. ANDROID STORAGE

## 66. Android modern permissions

Android 13+ için uygun `READ_MEDIA_AUDIO`, kullanıcı seçtiği klasörler için SAF grant/persisted permission ve gerekirse erişim isteği kullanılır; eski sürüm politikası supported `minSdk` ile uyumlu tutulur. `MANAGE_EXTERNAL_STORAGE` istenmez. Uygulama dış ses dosyalarını metadata düzenlemek amacıyla yazma izni istemez.

## 67. File mutation consent

Yalnızca kullanıcının seçtiği fiziksel silme için geçerli Android `MediaStore.createDeleteRequest()` veya SAF/DocumentProvider akışı ve gerekli platform onayı kullanılır. Android sürümüne, URI türüne ve sağlayıcı izinlerine göre uygulanabilir seçenek seçilir. Silinemeyen dosya silinmiş gibi gösterilmez. Android'in trash/write API'leri genel mimari referans olabilir ama Smooth'a ikinci fiziksel çöp kutusu veya tag writer eklemez.

## 68. MediaFileOperations abstraction

Merkezî servis `resolveLocation`, `requestDelete`, `verifyDeletion`, `reconcileCatalog` ve toplu işlemin devam durumunu yönetir. **SUPERSEDED:** `requestWrite`, `writeTags` ve sırf tag writer için `rescan` sorumlulukları çıkarıldı. Uygulama dosya move/copy/rename yapmaz. Physical-delete akışı ile catalog-trash akışı ayrı tutulur.

---

# XVI. DELETE SEMANTICS

## 69. Delete File ≠ Delete Track

Fiziksel `TrackLocation` silmek; Track'i catalog trash'e atmak; bir Track'i EXCLUDED olarak katalogdan çıkarmak; playlist üyeliğini kaldırmak **dört ayrı işlemdir**. Kullanıcı “Dosyayı sil” dediğinde yalnız katalogdan kaldırılmış gibi davranılmaz. Silme birden fazla location varsa hedef fiziksel dosyalar listelenerek seçilir; boyut, format ve kaynak gösterilir.

## 70. Location silinince history yok olmaz

Bir Location silinse bile diğer kopya mevcutsa Track, playlist membership, favorite, lyrics, user metadata, history ve stats korunur. Son kopyanın yokluğu otomatik Track silme yetkisi vermez. Gerçek Track'in kullanıcı tarafından **kalıcı** silinmesi durumunda Track geçmişi ve ilişkileri ayrıca temizlenir; bu işlem geçici unavailable veya catalog trash ile karıştırılmaz.

## 71. Missing Track state

Track'in erişilebilir Location sayısı sıfır olabilir; canonical Track yine durur. Erişilebilir kaynakta dosyanın gerçekten bulunmaması `MISSING`, kaynağın geçici erişilmemesi `UNAVAILABLE`, Android izni kaybı `PERMISSION_LOST` olarak ayrılır. SD/USB geri döndüğünde izin uygunsa sessiz rebind denenebilir, fiziksel hash doğrulanmadan eski TrackID bağlanmaz. İzin yoksa kullanıcıya izin isteği sunulur; tam library scan kendiliğinden başlamaz.

---

# XVII. M3U / INTEROPERABILITY

## 72. M3U desteklenecek [SUPERSEDED]

**Yürürlükteki karar: Smooth Media içinde M3U import/export yoktur.** Önceki interoperability önerisi iptal edildi. Bu başlık numarası tarihsel iz ve diğer maddelerin referansları korunabilsin diye bırakıldı.

## 73. M3U internal playlist database değildir [SUPERSEDED]

İlk kısmı hâlâ geçerli: playlist storage Room içinde canonical TrackID kullanır. M3U serialization veya path tabanlı export yapılmaz. Genel portable **uygulama yedeği** (§75) M3U export değildir.

## 74. M3U import resolver kullanacak [SUPERSEDED]

Import resolver, unresolved M3U entry ve M3U path migration **uygulanmaz**. Dosya/Track identity resolver yalnız katalog ve tanımlı restore akışlarında kullanılır.

---

# XVIII. BACKUP / RESTORE

## 75. Versioned portable backup

Ses dosyalarını **içermeyen**, tam taşınabilir ve sürümlü uygulama yedeği: manifest/schema, canonical kimlikler, iki fiziksel hash, metadata ve kullanıcı override'ları, artist/album/genre/tag ilişkileri, playlists/kurallar/kapaklar, favoriler, ayarlar, queue state gerekiyorsa, minimal history, counters, kişisel preference model ve yıllık arşiv raporları. Kullanıcının app-private custom artwork'ü de dahildir. İçe aktarmada version migration desteklenir; başarısız restore eski veriyi bozmaz. Backup'ın checksum/manifest bütünlüğü doğrulanır.

**Restore için katı koşul:** Mevcut cihazdaki müzik dosyasının tam hash'i yedekteki `originalFileHash` **veya** `currentFileHash` ile eşleşmelidir. Eşleşmeyen Track **tamamen restore dışı kalır**; playlist ilişkileri, tag/history/stats ve buna bağlı per-track veriler ghost record olarak tutulmaz. Bu, normal günlük `UNAVAILABLE` durumundan farklıdır ve kasıtlı olarak sınırlı restore politikasıdır. Eşleşen gerçek dosyanın `currentFileHash`'i restore sonrası gerçekten mevcut byte'lara göre güncellenir. Hiçbir ses dosyası kopyalanmaz.

## 76. Raw Room DB ana backup formatı olmayacak

Portable backup domain-level ve versioned olur; doğrudan SQLite dosyasını başka schema sürümüne kör kopyalamak normal restore yöntemi değildir. Ancak acil DB bozulması kurtarması için ayrı yerel, doğrulanmış recovery snapshot mekanizması kullanılabilir. Bu ikisi aynı format zorunluluğu taşımaz. Otomatik kurtarmada son doğrulanmış yedek aranır; bulunamazsa bozuk DB korunur, onaysız sıfırlama yapılmaz.

---

# XIX. LIBRARY MAINTENANCE

## 77. Catalog sahibiysen bakımından da sorumlusun

Library Tools: kullanıcı komutuyla rescan/incremental scan, Missing/Unavailable/Permission Lost raporları, duplicate candidates, unresolved metadata, broken locations, FTS rebuild ve identity conflict review. Tarama dosya bulamadı diye Track'i otomatik trash'e atmaz. Toplu bakım silme işlemi açık kullanıcı onayı gerektirir. Hash doğrulaması/güven ilişkisi korunur.

---

# XX. COMMON ACTIONS

## 78. Unified Track Actions

Songs, Albums, Artists, Search, Genre ve Folder ortak use case kullanır: Play, Play Next, Add Queue/Playlist, Favorite, Go to Artist/Album, Lyrics, **Edit Room Metadata**, Share, Track Details, Catalog Trash ve açık onaylı **Delete Physical File**. “Sil” butonu farklı ekranlarda gizlice farklı işlem yapamaz. Playlist'ten kaldırma yalnız üyeliği kaldırır.

---

# XXI. NAVIGATION & UI

## 79. Ana navigation

Ana yapı: Library / Search / Playlists / Settings. Library: Songs / Albums / Artists / Genres / Folders. History, stats, now playing, queue ve bakım ekranları aynı bilgi mimarisinde erişilir; bunları zorunlu olarak ayrı bottom-navigation tab'lerine çevirmek gerekmez.

## 80. Smooth Ürün Ailesi tasarım dili

SMOOTH ailesi markası, icon ve token'ları korunur. Dark: `#090B0D` background, `#111418` surface, `#F4F6F8` primary text, `#19C3E6` accent. Light: `#F5F7F8` background, `#FFFFFF` surface, `#D8DEE3` border. Media kendi module icon'unu kullanır; ana Smooth markası değişmez. Kod/UI değişiklikleri öncesi güncel **Smooth Ürün Ailesi** kararları kontrol edilir.

## 81. Multilingual UI

**İlk sürüm Türkçe + İngilizce.** Android resource tabanlı, hardcoded kullanıcı metni yok. Varsayılan Android per-app/system language seçimine uyar. Müzik metadata'sı, lyrics veya dosya isimleri otomatik tercüme edilmez; ek diller kapsam dışı.

---

# XXII. SYNC READINESS

## 82. Device-local IDs sync identity olmayacak

Canonical Track, Album, Artist ve Playlist kimlikleri future sync'e uygun stable ID taşır. Room auto-increment yalnız internal row key olabilir. Kaynak cihaz URI'si ve MediaStore ID taşınabilir TrackID yerine geçemez.

## 83. Phone ↔ Car sync ilk fazda yapılmayacak

USB/Ethernet temelli future sync için seam korunur, ancak P01/P02'de ikinci cihaz katalog senkronu, araç player'ı veya Wi-Fi/Bluetooth tabanlı sync zorunlu değildir.

## 84. Tombstone / revision ihtiyacı

Gelecekte revision/change log ve stable tombstone gerekebilir. **Bugünkü EXCLUDED/tombstone** ise katalogdan kaldırılan ama diskte kalan dosyanın sonraki manuel scan ile yeniden import edilmemesi için gerçek ürün gereksinimidir (§104). Bunlar aynı şeyi yapmak zorunda değildir; future distributed sync ile karıştırılmaz.

---

# XXIII. BOOMINGMUSIC REFERANSI

## 85. Güçlü referans olarak kullanılacaklar

Bağımsız teknik referans: Media3/ExoPlayer, MediaLibraryService, Android Auto, queue state/restore, metadata **reading**, ReplayGain, modular audio processors, Rich Lyrics/enhanced LRC, Android scoped-storage **deletion**, versioned backup konsepti, codec fallback ve gapless. **SUPERSEDED:** tag writing ve M3U interoperability artık ürün referans listesinde yoktur.

## 86. Yeniden tasarlanacaklar

EQ/DSP yönetimi, artwork resolution, lyrics provider/matching, PlaybackService sorumlulukları, minimal history/stats, backup representation, normalized playlist ve preference learning Smooth'un bağımsız tasarımıdır.

## 87. Kesinlikle alınmayacaklar

MediaStore canonical library/TrackID; playlist içine Track metadata kopyalama; Favorites identity'yi display name'den çıkarma; tek genre sütunu; path-only folder; yalnız basit LIKE araması; God Service/God EQ Manager. Üçüncü taraf kodu kopyalama lisans sınırlamasına tabidir (§93).

---

# XXIV. SERVICE BOUNDARIES

## 88. PlaybackService God Object olmayacak

`MediaLibraryService` yalnız orchestration yapar: PlaybackEngine, QueueController, PlaybackStateRepository, LibraryRepository ve integration coordinators ayrı sorumluluklara sahiptir. File scan, backup, Room migration, lyrics, preference model ve artwork servis içinde monolitik olarak birikmez.

---

# XXV. PHASE 01 / PHASE 02

## 89. P01 = Catalog Foundation

`Permission/Selected Sources → MP3/FLAC Discovery → Metadata → File Hash Identity → Room Catalog → Relations → Library → Search`. Kabul kriteri kullanıcı seçtiği MP3/FLAC ağacının canonical katalogda doğru temsil edilmesi ve yeniden taramada veri bütünlüğünün korunmasıdır. Hash maliyeti ve interrupted scan pratik test konusudur. P01'i önce bitmeyen bütün ileri fazlara bağımlı kılmak yasaktır.

## 90. P01'de olmayanlar

**Production playback, full/mini player, EQ/Adaptive EQ, ReplayGain DSP, Android Auto, karaoke, lyrics downloader, vehicle sync, Wear, Crossfade, MusicBrainz/acoustic fingerprint.** Kimlik için cryptographic **full-file hash** bulunması bu listenin acoustic fingerprint yasağını ihlal etmez.

## 91. P01X debug audio smoke test olabilir

Geliştiriciye özel, isteğe bağlı, 5–10 saniyelik Media3 URI doğrulama smoke'ı mümkündür. Production MediaLibraryService veya P02 player değildir; kullanıcı ürününde müzik çalma tamamlandı diye raporlanamaz.

## 92. P02 = Playback Foundation

Canonical Track → PlaybackEngine → QueueController → MediaLibraryService/MediaSession → PlaybackState → Mini Player → Now Playing. Temel Playback/Queue güvenceye alındıktan sonra DSP, smart autoplay, gelişmiş UI ve bağlantılı cihaz işleri ayrı fazlarda genişler.

---

# XXVI. LİSANS & ÇEKİRDEK

## 93. BoomingMusic kodu kopyalanmayacak

BoomingMusic'in GPL-3.0 kaynak kodu isim değiştirerek, kısmen kopyalayarak veya parser/class transplant ile Smooth'a taşınmaz. Davranış, açık dokümantasyon ve mimari fikirlerinden bağımsız implementation üretilebilir; her haricî dependency'nin lisansı ayrıca denetlenir.

## 94. Smooth Media Core

```text
                  DISCOVERY (selected MediaStore / SAF roots)
                                    |
                  SCANNER + METADATA READER
                                    |
                FULL-FILE HASH IDENTITY RESOLVER
                                    |
                 CANONICAL ROOM CATALOG
                  /        |         \
          Relations      Search      Library
          /    |           |            |
      Artist  Genre/Tag   FTS/Paging   UI
          \      /          |            /
                   DOMAIN ACTIONS
                     /       \
               Playlists   Queue (one persistent session)
                              |
                        PlaybackEngine (Media3)
                              |
                     MediaLibraryService / Session
                         /              \
                       Phone        Android Auto
                         |
                      Wear companion (remote)

  Side domains: source permissions, covers, lyrics,
  minimal history, preferences, backups, catalog maintenance.
```

**Korunan temel invariant'lar:** Room canonical; MediaStore discovery only; device-local ID ≠ TrackID; Track ≠ TrackLocation; Genre many-to-many; Genre hierarchy ≠ folder; playlist canonical TrackID; fiziksel silme ≠ katalog silme; P01 production player değil; GPL implementation kopyalanmaz.

---

# XXVII. 95–102 EK KARARLAR

## 95. Track varlığı ile dosya erişilebilirliği ayrılacak

`AVAILABLE`: Dosyaya erişilir. `UNAVAILABLE`: Kaynak geçici yok (SD/USB). `PERMISSION_LOST`: Android erişim hakkı yok. `MISSING`: Kaynak okunuyor, fiziksel dosya yok. Track, hiçbir kullanılabilir kaynak bulunmadığında da katalogda kalır. En az bir alternatif AVAILABLE ise Track çalınabilir; eksik Location'lar Track'i bütünüyle unavailable yapmaz.

## 96. Erişilemeyen şarkılar görsel olarak ayırt edilecek

Hiç AVAILABLE Location'ı olmayan Track'in adı, sanatçısı ve mevcut artwork'ü korunur ancak dosya yöneticisindeki gibi **soluk** görünür. Kullanıcı ayrıntıdan nedenini görür. Erişilebilir alternatif Location varsa Track tümden soluklaştırılmaz; teknik detay ilgili kaynağın durumunu gösterir. Yeniden erişimde görünüm normale döner.

## 97. Kütüphaneden kaldırma ile fiziksel dosya silme ayrılacak

Catalog Trash, EXCLUDED, physical file delete ve permanent Track delete ayrı API/use case'dir. **Catalog-only kaldırma** diskteki dosyayı silmez, ayrıca EXCLUDED kaydı bırakır. “Dosyayı sil” gerçek fiziksel işlemdir ve Android yetkisi gerekir; izin/başarı doğrulanmadan UI başarı ilan edemez. Catalog Trash sınırsız saklanır; manuel Empty Trash gerekir.

## 98. Dosya silme işlemi location-aware olacak

Bir Track'in Phone/SD/USB/Car gibi çok Location'ı olabilir. Kullanıcı fiziksel silme öncesi dosya(ları) **tek tek seçer**; filename, format, size ve kaynak gösterilir. MP3'ü silmek aynı Track'in FLAC Location'ını silmez. Kalan kullanılabilir Location varsa oynatıcı onu kullanır. Fiziksel silme hiçbir zaman sessiz şekilde başka kaynaklara yayılmaz.

## 99. Yeniden tarama veri kaybına yol açmayacak

Scanner, bulunamayan dosyayı otomatik trash/delete yapmaz. Kaynak availability ve permission ayrılır. Hash eşleşen dönen dosya eski Track'e rebind edilir. Eşleşmeyen fiziksel dosya eski Track'e metadata/path benzerliğiyle zorlanmaz (§6). Room override, favoriler, history ve playlist varsayılan olarak korunur.

## 100. Fiziksel silme ile katalog güncellemesi tutarlı olacak

`Resolve TrackLocation → Android consent → Physical Delete → Verify → Room Transaction → Search/UI Refresh`. Toplu delete'de işlem **her dosya için** tamamlanıp doğrulandıktan sonra sonraki dosyaya geçer. 100'de 60 başarıysa 60 doğrulanmış başarı korunur; kalan 40 pending kaydedilir. Sonraki oturumda **kullanıcıya devam sorulur**, sessiz toplu silme yapılmaz; hedef dosyalar yeniden kimlik doğrulamasından geçer. Android fiziksel silme kalıcıdır; Smooth ikinci fiziksel recycle bin tutmaz.

## 101. Kütüphane kapsamı kullanıcı tarafından yönetilebilecek

Kullanıcı **bir veya birden fazla kök klasör** seçer. Her seçilen klasör **tüm alt klasörleriyle** manuel taranır. **SUPERSEDED:** Ayrı alt klasör include/exclude rule sistemi **yoktur**; doğru kök klasörü seçme sorumluluğu kullanıcıdadır. Genel kaynak ayarı ve izin yönetimi vardır. Uygulama kendi kendine geniş kapsamlı background scan başlatmaz.

## 102. Scanner idempotent olacak

Aynı fiziksel dosyanın aynı içerikle yeniden taranması yeni Track/TrackLocation çoğaltmaz; kullanıcı override, favori, playlist ve history'yi ezmez. Full hash eşleşmesi veya güvenilir önceki doğrulama bilgisi mevcut kimliğin korunmasını sağlar. Kesintiye uğramış işleme yeniden giriş/transaction replay test edilir.

---
# XXVIII. GRILL ME SONRASI BAĞLAYICI EK HÜKÜMLER

## 103. Canonical Track merge/split ve kopya temizliği

- Tam byte aynı fiziksel dosyanın birden fazla kopyası tek canonical Track'e, ayrı TrackLocation'lar olarak bağlanabilir.
- MP3/FLAC aynı kayıt adayları güven skoruyla **inceleme için önerilebilir**; hash uyumsuzluğunda otomatik TrackID devri yasaktır (§6). Kullanıcı merge/split yapabilir. Farklı performans/remaster/mastering ayrı Tracks'tir.
- Merge/split kararları sonraki scan ile otomatik geri alınmaz. Playlist, favorite, history ve user metadata bütünlüğü kontrollü transaction/migration ile korunur.
- Exact duplicate cleanup **kullanıcı onayıyla**, kullanıcı hangi fiziksel dosyayı saklayacağını seçerek gerçekleşir. Otomatik fiziksel duplicate silme yoktur.
- Backup/restore ve relink, stable TrackID korunabildiği durumlarda korur; hash eşleşmesi yoksa yalnız filename/path ile TrackID devralınmaz.

## 104. Catalog trash, EXCLUDED ve retention

- **Catalog Trash:** Kaynak dosya kaybolsa bile Track'in otomatik silinmesini engeller. Kullanıcı tekli/toplu trash'e atabilir; retention **süresiz**. Restore mümkündür.
- **EXCLUDED/tombstone:** Fiziksel dosya dururken katalogdan kaldırılan Track için kalıcı yeniden-import engeli. Sonraki scan aynı dosyayı otomatik geri getirmez; kullanıcı yeniden eklerse yeni üyelikleri açıkça oluşturur.
- EXCLUDED Track normal playlist üyeliğinden çıkarılır; yeni import eski üyelikleri **otomatik geri getirmez**. Buna karşılık sıradan `UNAVAILABLE/MISSING/PERMISSION_LOST` durumda playlist üyeliği korunur.
- **Empty Trash:** Her seferinde sorulur: varsayılan katalog kaydını temizle; alternatif olarak açık kapsam ve platform onayıyla fiziksel dosyaları da sil. Özet olmadan toplu fiziksel silme yoktur.
- Kalıcı Track silme ile yalnız history temizleme farklıdır. Kalıcı silinen Track'in ilişkili history ve istatistiği temizlenir; sırf inaccessible olması nedeniyle temizlenmez.

## 105. Metadata uyuşmazlıkları ve kullanıcı otoritesi

Yalnız **hash ile aynı fiziksel kimlik doğrulanabildiğinde** ve farklı metadata okumaları/normalize değerleri Room override'ıyla gerçekten uyuşmadığında kullanıcıya **alan bazlı** veya **toplu** “Room'u koru / Dosyayı kabul et” seçeneği sunulabilir; varsayılan **Room'u koru**. **Dışarıdan embedded tag düzenlemek dosya byte'larını ve dolayısıyla tam hash'i değiştirir.** İki kayıtlı hash de eşleşmiyorsa eski TrackID'ye bu ekran aracılığıyla yeniden bağlamak yasaktır; yeni dosya sayılır (§6). Bu, önceki Grill Me conflict kararının katı hash kararı altında **uygulanabilirliği sınırlı** olduğunu açıkça belgeler. Room editleri hiçbir ses dosyasına yazılmaz.

## 106. Normal playlist işlemleri

- Track başına tek entry, stable entry ID; yeni Tracks kullanıcı seçim sırasına göre **sona eklenir**.
- Çoklu playlist'e ekleme önizlemesinde **yeni eklenecek / zaten mevcut** sayıları gösterilir. Tek Room transaction, duplicates skip.
- Manuel sıra kalıcıdır. Geçici UI sort kalıcı pozisyonları değiştirmez; “bu sıralamayı kaydet” ayrı eylemdir. Drag/drop, pozisyon numarası ve relative order'ı koruyan multi-select reorder desteklenebilir.
- Üyelik kaldırmak ses dosyasını veya Track'i silmez. Toplu üyelik kaldırma **tek onay + tek transaction** ile yapılır.
- Editler anında kaydolur, kısa süreli Undo olabilir; kalıcı undo geçmişi veya ayrı edit session şart değildir.
- **Boş** normal playlist silinince onay istemez, Undo vardır. **Dolu** playlist silme onay ister. Smart playlist sonuç sayısı sıfır olsa bile aktif rule varsa onay ister.

## 107. Playlist isimlendirme, çoğaltma ve çöp kutusu

- `trim + case-insensitive` unique playlist adı; çakışmalarda V1/V2 gibi öneri. Bu kontrol kullanıcıya görünür isimler için, stable ID yerine değil.
- Duplicate Playlist yeni ID alır; normal playlist membership/sıra, Smart playlist rules, description/tags ve uygun cover seçeneği korunur; yeni versiyon adı önerilir.
- Playlist listesi **düzdür; playlist folder/subfolder sistemi yoktur**. Playlist çöp kutusu Track çöp kutusundan **ayrıdır**, retention **süresiz**. Restore isim çakışmasında otomatik `(Dirildi)`, `(Dirildi 2)` ... suffix'i verir; original playlist ID ve üyelik/sıra/rule/kapak korunur.
- Playlist description multiline olabilir; playlist'e verilen Tag, şarkıların Tag üyeliğini kendiliğinden değiştirmez.

## 108. Smart playlists, Tag grafı ve filtreler

- `Tag` tanımı Track ve Playlist arasında paylaşılır ama `TrackTag` / `PlaylistTag` ayrı ilişkiler kullanır. Genre–Tag bağlantısı ayrıca bulunabilir.
- Tag yapısı çok ebeveynli yönlü döngüsüz grafik (**DAG**) olabilir; eklemede cycle denetimi gerekir. Genre hierarchy ayrı mantıksal yapıdır.
- Track araması/Smart rules AND/OR/NOT/parantezler, children/descendants, güvenli SQL parametreleştirme ve istenen çoklu sıralama/shuffle/limit kabiliyetine açık.
- Smart playlist üyeliği dinamik sorgu sonucudur; **oynatma başladığında queue snapshot** alınır. Aktif queue arkadan rule değişti diye kendiliğinden dağılmaz.
- Playlist listesinde Tag filtreleri **AND/OR**, Track aramasındaki ileri NOT/parantez kapsamını otomatik devralmaz.
- Playlist için önerilen Tag'ler içerideki Track Tag sayımlarından gösterilebilir ama **asla otomatik atanmaz**; kullanıcının verdiği Tag'ler şarkı değişti diye otomatik kaldırılmaz.

## 109. Playlist artwork ve bilgi paneli

- Kapak: kullanıcı custom artwork veya **2×2 otomatik kolaj**; custom her zaman override'dır.
- Otomatik kolaj **dört farklı albümden rastgele** seçilir, seçilen albümler playlist içinde kaldıkça karelerin konumu **sabit** kalır. Kaynak geçici unavailable diye kolaj yeniden dağıtılmaz. Seçilen albüm playlist'ten tamamen ayrılırsa ilgili kare yenilenir.
- Otomatik kolaj yeniden üretilebilir cache olabilir; kullanıcı kapağı app-private alana optimize edilerek kopyalanır.
- Playlist info panelinde Track sayısı, toplam katalog süreleri, farklı sanatçı sayısı, playlist play count ve açıklama/tags gösterilir. Play Count ölçütü §64'tedir.

## 110. History UX ve silme kuralları

- History Room'da **sınırsız** tutulur; otomatik retention sınırı, bulut history servisi veya background prune yoktur.
- Ekran Track'e göre gruplayabilir: toplam count + son dinlenme zamanı, varsayılan en son dinlenen önce. Track'e dokunmak aktif queue'yu o Track ile **değiştirir**.
- History long-press eylemleri yalnız **Play Next / Add Queue / Add Playlist / Track Details**. Silme/temizleme ayrı seçim modunda tekli/çoklu, onaylı yapılır.
- Filtreler: şarkı/sanatçı araması, tarih preset/custom, artist/album/genre; filtreler birleştirilebilir ve gösterilen sayılar seçili kapsamla uyumludur.
- Sıralamalar: son dinleme, en çok/az dinleme, title, artist; eşitliklerde stable tie-break.
- Tüm history temizlemede onayla iki seçenek: **yalnız history** (varsayılan; counters saklanır) veya **history + counters reset**. Preference model reset'i bundan ayrı bir işlemdir.

## 111. İstatistikler ve yıllık Müzik Yılım

- Kaydı bulunan anlamlı dinlemelerden total plays, distinct Track/Artist, top lists, genre dağılımı, aylık/yıllık grafikler, alışkanlıklar ve kişisel rekorlar üretilebilir. Minimal event'te bulunmayan **çalınan gerçek dakika, skip sayısı, cihaz/bölge** gibi nicelikler üretilmez.
- **Müzik Yılım** tam ekran görsel hikâyedir; ayrıca image/PDF export gerektirmez, çevrimdışı çalışır.
- Mevcut yıl canlıdır. Bitmiş yıllar yeni yılda ilk açılışta **idempotent**, retryable şekilde immutable archive snapshot'a dönüştürülür.
- Archived year tek tek/toplu silinebilir; history veya counter temizleme bundan bağımsızdır. Kullanıcının sildiği arşiv **otomatik yeniden yaratılmaz**.
- Restore'da hash eşleşmeyen Track'leri çıkarma kuralı (§75) yıllık rapor snapshot'larının bütünlüğüyle karşı karşıya gelebilir. Eksik Track referanslarıyla **sanki eksiksizmiş gibi rapor restore etmek yasaktır**. Rapor doğrulama/atlanma davranışı restore sırasında açıkça raporlanır; arşivde uydurma sayı oluşturulmaz. Bu edge case testte doğrulanır.

## 112. Kişisel PreferenceModel

- Autoplay öğrenmesi tamamen **yerel, çevrimdışı** ve Room'da minimal `PlaybackEvent`'ten **ayrı** modelde tutulur.
- Erken skip hafif negatif, manuel seçilen şarkı pozitif, isteyerek yeniden dinleme daha güçlü pozitif sinyaldir. Bilinçli manuel dinlemenin pozitif etkisi kısa skip negatifinden büyük olmalıdır.
- Zaman ağırlığı yeni sinyalleri öne çıkarır, eskileri sıfırlamaz. Model Track/Artist/Genre/Tag benzerliklerini kullanabilir; kullanıcıya kapalı bir bulut servisine veri göndermez.
- Kullanıcı onayıyla yalnız **öğrenilen tercih modelini sıfırlamak** mümkün. History, play counts, playlists ve archive reports bundan etkilenmez.
- Model sürümü/ayarları genel taşınabilir backup'a dahildir. Yeni model şemalarında uyumlu migration ya da kontrollü yeniden öğrenme gerekir; eski history'de bulunmayan telemetry geriye dönük uydurulmaz.

## 113. Autoplay Queue Continuation

- **Autoplay varsayılan ON**, Settings'ten OFF yapılabilir. Queue sona geldiğinde Repeat OFF ise, uygun kütüphane Track'leriyle çalma devam edebilir. Hiç aday yoksa durur.
- Mevcut queue korunur; TrackID tekrarı eklenmez. Autoplay ekleri kullanıcı ekleriyle **aynı queue'da**, özel separator olmadan görünür.
- Sona **2 oynatılmamış şarkı** kaldığında **5'lik parti** yeni Track önerilir; seri skip durumunda eşik pratik biçimde kontrol edilir, sonsuz tekrar yapılmaz.
- Benzerlik **ilk kullanıcı queue'sunun genel karakterini** esas alır ve son dinlenenlere ağırlık verir; yalnız son kendi önerilerini takip ederek zevki kendi kendine uçlara sürüklemez. Artist diversity dengelenir.
- Autoplay sırasında kullanıcı manuel Track eklerse önceki öneriler silinmez; sonraki öneri profili manuel eklemelere göre güncellenir.
- PreferenceModel bu seçimi kişiselleştirir (§112). Bu özellik P01/P02'nin otomatik kapsamı sayılmaz.

## 114. Playback queue ve kaynak fallback davranışı

- Tek aktif queue; başka album/playlist/History Track seçimi queue'yu **değiştirir**. Play Next/Add Queue bunu değiştirmeyen kontrollü düzenleme eylemleridir.
- Oynatma için erişilebilir TrackLocation seçilir; uygun durumda yüksek kaliteli FLAC öncelikli. Bir kaynak şarkı ortasında kaybolursa **aynı TrackID'nin alternatif erişilebilir Location'ına yakın pozisyondan** devam denenir. Mümkün değilse sonraki oynatılabilir queue girdisi, o da yoksa stop.
- Kaynak değiştirme sessizdir (toast/snackbar/modal yok); Now Playing teknik format bilgisi gerçeği gösterir.
- Dinleme sayacı yalnız §64'teki gerçek dinlenme şartını karşılayan Track için artar. Kaynak çökmesi tek başına anlamlı dinleme değildir.

## 115. Audio focus, kulaklık ve çıkış kesintileri

- Kulaklık çıkarılması, Bluetooth veya araç bağlantısının **beklenmedik** kopmasında otomatik **pause**; tekrar bağlanınca **otomatik resume yok**. Kullanıcının bilinçli output değiştirmesi bu kesintiyle aynı sayılmaz.
- Telefon araması Audio Focus'u keserse, yalnız çağrıdan kaynaklı ve kullanıcı manuel durdurmamışsa focus dönüşünde **otomatik resume** olabilir. Beklenmedik output loss kuralı bunun önüne geçer.
- Kısa navigation/notification focus'unda ducking yapılır; ses düzeyi uygun şekilde geri gelir.
- **SMOOTH Priority** kullanıcı tercihiyle açılır, varsayılan OFF. Android'in izin verdiği ölçüde focus'u korumayı/yeniden almayı dener; başka uygulamanın sesini platform dışında doğrudan kontrol edemez. Arama ve güvenlik önceliklidir.

## 116. Ses işleme ve EQ mod sınırları

- Adaptive EQ ana varsayılan kullanıcı deneyimidir; kullanıcı Manual EQ'ya geçebilir. Adaptive parametrelerdeki canlı değişim slider UI'sinde görülebilir. Band sayısı, A/B doğrulama protokolü, ek offline öğrenme ayrıntıları **önceden konuşulmuş EQ tasarımına** bağlıdır; bu metin bunları yeniden açmaz.
- Teknik test imkânı kullanıcı donanımına göre sınırlı olabilir; referans şarkılarda kulakla **kör A/B** tercih edilebilir, 7.000 şarkının tek tek iki kez dinlenmesi kabul kriteri değildir.
- Crossfade, gapless, ReplayGain ve bit-perfect uyumsuzlukları §§45–50 ve §48'e göre açık mod yönetimiyle çözülür; bit-perfect açıkken sessizce DSP açılmaz.

## 117. Android Open With geçici oynatma

- Dış uygulama/Android **Open With** ile açılan ve katalogda bulunmayan MP3/FLAC **geçici** olarak oynatılır; Room'a import edilmez, import sorusu sorulmaz, yalnız manuel scan library'ye ekleyebilir.
- Bu eylem aktif queue'yu **tek Track'lik geçici session** ile değiştirir. Bir kez oynatıp durur ve geçici queue'yu temizler; Autoplay OFF. Önceki queue **otomatik geri gelmez**.
- Geçici harici dosyanın canonical TrackID'si, playlist ilişkisi, history veya Play Count yoktur.

## 118. Bulut ve yerel backup operasyon politikası

- Portable backup kullanıcı tarafından **dosya olarak export/import** edilebilir. Ek olarak isteğe bağlı **Google Drive yedeklemesi 30 günde bir** hedeflenir; offline dinleme ve Library kullanımı Google oturumuna/internete bağlı değildir.
- Drive OAuth/standart hesap yetkisi kullanılır; **ek uygulama şifresi veya uygulama katmanı şifrelemesi istenmez**. Bu, yedeğin gizli olduğu anlamına gelmez; kullanıcının Google hesabı koruması esastır.
- Başarıyla yeni backup oluşturulup integrity/okunabilirlik **doğrulanmadan eski doğrulanmış backup silinmez**. Başarılı rotasyonda son **bir doğrulanmış** Drive yedeği tutulur.
- Planlanan günde offline kalınırsa sonraki internetli **Smooth Media oturumunda kullanıcıya yükleme teklifi** gösterilir. “Şimdi değil” derse ileriki uygun oturumda tekrar sorulur; **sessiz upload yapılmaz**.
- Yedek dosyası **müzik dosyalarını içermez**. Storage/migration/recovery başarısızlığında katalog üzerine kısmi restore yazıp veri bozmak yasaktır.

## 119. Tarama planlaması ve cihaz kaynaklarının korunması

- Auto full scan: **yok**. Kullanıcı başlatır; seçilen köklerin tüm alt klasörleri taranır. Ek exclude rules yoktur.
- Tek scan, görünür progress/pause/cancel; kazanılmış başarı kayıtları korunur. Sonraki manuel scan kaldığı noktadan devam eder, değişen dosyayı doğrular.
- Playback'te scan I/O throttle uygulanır. Okunamayan dosya **sessiz atlanır**; file bazında sonsuz retry ve teknik rapor zorunluluğu yoktur. Reader sorunu eski katalog Track'lerini otomatik silmez.
- SD/USB dönüşünde **önceden kayıtlı Location'ların** izin + hash kontrollü rebind'i tam manual scan sayılmaz; yalnız erişilebilirlik durumunu iyileştirir.
- Hash için optimizasyon ancak canonical kimlik doğruluğunu bozmuyorsa uygulanır. Full-hash maliyeti benchmark konusudur.

## 120. Fiziksel dosya işlemleri, kesinti ve düşük depolama

- Physical Delete user consent ve source provider izinleri ile yapılır. Silme **kalıcıdır**; Smooth'un ek physical trash/recovery copy mekanizması yoktur.
- Toplu silme **file-by-file verify-then-commit**, pending işler kalıcı; yeniden açıldığında kullanıcının izniyle devam eder (§100).
- Alan dolduğunda başarısız yazım güvenli şekilde kesilir; catalog mümkünse ve playback korunur, Smooth kullanıcı onayı olmadan otomatik cache temizlemez. Sistem uyarılarından yararlanılır.
- Dosya taşıma/kopyalama/rename/folder manager **yoktur**; bunlar dış dosya yöneticisinin işidir. Sonrasında kullanıcı manuel tarama yapabilir.

## 121. Room migration, bozulma ve uygulama güvenliği

- Room schema migration **veri koruyucu** olmalıdır; destructive fallback **yasaktır**. Upgrade/migration testleri eski DB fixture'larına karşı çalışır; hata olursa önceki veritabanı korunur.
- Korrupt DB'de önce en güncel **doğrulanmış** recovery yedeğinden güvenli kurtarma denenebilir; yoksa bozuk DB korunur, kullanıcı onayı olmadan sıfırlanmaz.
- Ek uygulama PIN, biometric gate veya “hidden library” **yok**. Cihazın Android kilidi yeterli kabul edilir.
- Büyük kataloglarda Room + Paging 3, indeksli SQL ve küçük cache'ler; uygulamanın bütün arşivi RAM'e çekmesi yasaktır.

## 122. Android Auto ve Wear OS kapsamı

- **Android Auto:** Media3 media app browsing (şarkı, albüm, sanatçı, playlist), güvenli queue/playback, sürüşe uygun platform template ve aynı telefon MediaSession. Ayrı Android Auto player'ı yoktur.
- **Wear OS:** Smooth Media Companion **telefon kumandasıdır**. Now Playing, volume, queue, playlist kontrolü bulunabilir; watch üzerinde bağımsız library DB, ayrı audio player veya watch'a yerel şarkı kopyalama öngörülmez.
- Headset/araç/BT hardware media buttons standart MediaSession üzerinden çalışır. Özel mapping zorunlu değildir.
- Her iki entegrasyon ürün fazına göre açılır; P01 kapsamına dahil değildir.

## 123. Telefon / tablet yönelim ve responsive düzen

- **Telefon:** portrait only.
- **Tablet:** portrait + landscape, aynı uygulama ve aynı tasarım dilinde adaptive responsive düzen. Uygun genişlikte iki panelli Library/Details ve Now Playing/Queue gösterilebilir; ayrı tablet uygulaması yoktur.
- Android Auto ve Wear OS kendi platform layout kurallarına uyar.

## 124. Offline-first sınırı ve hesap bağlantıları

- Media playback, library, history, local autoplay ve mevcut lyrics dosyaları **internetsiz çalışır**. Google Drive isteğe bağlı yedek özelliğidir, library otoritesi değildir.
- Online lyrics provider gerektiğinde bağımsız opsiyonel veri kaynağıdır; başarısız olunca yerel katalog bozulmaz.
- Kullanıcı verisi için varsayılan üçüncü taraf telemetry veya cloud recommendation zorunluluğu yoktur.

## 125. Acceptance test kapsamı

Minimum senaryolar:

1. Aynı MP3/FLAC dosyasının tekrarlı scan'i Track/Location çoğaltmaz; full hash kimliği korunur.
2. Aynı recording'in farklı formatları hash ile **otomatik aynı dosya sanılmaz**; user-approved logical merge/split kalıcı ve ilişkileri koruyucudur.
3. Dış tag edit ile iki hash değişince yeni Track oluşur; eski UNAVAILABLE/MISSING kalır, metadata benzerliğiyle yanlış bağlanmaz.
4. Source SD/USB kaybı, izin kaybı ve gerçek MISSING ayrı görünür; rebind hash/izin kontrollüdür.
5. Interrupted scan progress ve idempotency, playback priority ve tek aktif scan.
6. EXCLUDED dosya bir sonraki scan'de geri gelmez; normal playlist üyeliği kaldırılır; geçici unavailable üyeliği korunur.
7. Fiziksel MP3 silinince aynı Track'in FLAC Location'ı ve history'si kalır; toplu delete başarısı/pending doğru saklanır.
8. Playlist unique TrackID, bulk atomic add/remove, manual order, restore collision, Smart snapshot.
9. Minimal history + %50 gerçek dinleme sayımı; seek/pause dışlanır; history reset ile preference reset bağımsız.
10. Queue restore **auto-play yapmaz**; duplicate yoktur; alternative source fallback, Repeat ve shuffle sürer.
11. Autoplay 5'lik batch, iki şarkı eşiği, no-duplicate, manual reweight ve offline davranışı.
12. Bit-perfect/DSP çatışmalarında kullanıcı onayı, Google Drive yedek rotasyonu ve restore hash reject senaryosu.
13. Room migration/recovery fail-safe, partial restore rollback, Play Store/Android permissions uygunluğu.
14. Türkçe/İngilizce resources, phone portrait, tablet adaptive portrait/landscape; Android Auto/Wear ayrı faz doğrulaması.

## 126. Teknik riskler ve karar olmayan alanlar

| Risk | Bağlayıcı sınır | Doğrulanacak iş |
|---|---|---|
| **Hash maliyeti** | Katı dosya kimliği değiştirilemez | MP3/FLAC büyük arşivde I/O, pil, resume benchmark |
| **Cross-format mantıksal eşleştirme** | Hash dışı otomatik TrackID aktarımı yok | Confidence candidate UX, manuel merge/split migration testi |
| **Harici editte yeni Track** | İki hash değişirse yeni identity; eski conflict ekranı kimlik kaçamağı olamaz | Eski Track retention, Room uyuşmazlığı UX ve dış tag değişikliği testi |
| **Restore unmatched drops** | Ghost Track yok | Playlist/history/archive filtreleme ve açık restore raporu |
| **Çevrimiçi Drive** | Offline-first, sessiz upload yok | OAuth, WorkManager uygunluğu, izin ve rotasyon testi |
| **EQ/bit-perfect** | Tek profil, çakışma onaylı | Platform output capability ve dinleme testleri |
| **SAF + MediaStore delete** | İzin onayını atlamak yok | Sağlayıcı/Android sürümü uyumluluk matrisi |
| **Büyük playlist/arama** | Canonical Room/SQL, Paging | Query plan, indeks ve performans ölçümü |

Bu tabloda bulunan “doğrulanacak” işler kullanıcı kararını değiştirmez; gerekli implementasyonun henüz kanıtlanmadığını gösterir. Grill Me'de iptal edilen tekrar sorularından yeni şart çıkarılmaz.

## 127. Değişiklik yönetişimi

- Önce bu anayasa, sonra ilgili phase roadmap ve geçerli Smooth Ürün Ailesi tasarım dili kontrol edilir.
- Bir özellik üzerinde kod değiştirilmeden, hangi maddeye dayandığı ve hangi fazın scope'unda olduğu saptanır.
- Yeni teknik keşif bir kararla çelişirse **ANAYASA SAPMASI** işaretlenir; kullanıcı onayı olmadan anayasa değiştirilmez.
- Markdown güncellemesi, kod değişikliği, cihaz testinin PASS olması, Git commit ve GitHub push **ayrı durumlar** olarak raporlanır.
- Bu belge, yüklenen **102 maddelik** MD ile Grill Me'de kaydedilmiş kesin kararların birleştirilmiş sürümüdür; kaynakla uyumsuz eski aday hükümler değişiklik kaydında görünür.

---

# XXIX. KAYNAK UZLAŞTIRMA / DEĞİŞİKLİK KAYDI

## Revizyon ilkeleri

1. Kaynak MD'deki **1–102 madde numaraları korunmuştur**. Yeni hükümler 103–127 olarak eklenmiştir.
2. Kaynak içindeki “potansiyel”, “ileride olabilir” metinleri, açık Grill Me kararlarını geçersiz kılamaz.
3. Önceki bir özelliğin yasaklanması başka bir özelliğin de yasaklandığı anlamına gelmez: örneğin **M3U yok** fakat portable backup var; **tag writer yok** fakat fiziksel delete var; **otomatik scan yok** fakat removable source rebind var.
4. Bu dosyaya karar olarak yazılmamış teknik ayrıntı uygulama aşamasında keşif/test konusudur; geliştirme ekibi kullanıcı adına politika seçmez.

## Eski anayasa → yürürlükteki kararlar

| Kaynak MD | Durum | Yeni yön |
|---|---|---|
| §§6–7: ucuz sinyaller/gerektiğinde hash, toplu full hash yok | **SUPERSEDED** | TrackLocation iki full-file hash; farklı dosyalar için metadata ile otomatik ID aktarımı yok. Mantıksal merge ayrı. |
| §11: incremental/targeted rescan | **GENİŞLETİLDİ** | Yalnız manuel scan; pause/resume, tek scan, error skip, playback priority. |
| §15: MetadataWriter → fiziksel tag → rescan | **SUPERSEDED** | Yalnız Room edit ve katalog indeks güncellemesi. |
| §23: genre'ye göre EQ inheritance adayı | **DARALTILDI** | Tek ortak Adaptive/Manual EQ; genre EQ otomatiği kesin hüküm değil. |
| §32: artwork priority açık | **KISMEN NET** | User custom > auto; album original ana kapak; diğer fallback test konusu. |
| §34: playlist A–B–A tekrarı mümkün | **SUPERSEDED** | Bir playlist'te aynı TrackID yalnız bir kez. |
| §47: output-device ayrı ses profilleri | **SUPERSEDED** | Tek ortak EQ profili. |
| §50: crossfade 3–5 sn preview ile ilişkilendirilmiş | **AYRILDI** | Smart Crossfade 0–12 sn OFF, 3–5 sn audio preview ayrı fikir. |
| §§63–65: skipCount, totalPlayedMs, playedMs, device history | **SUPERSEDED** | Minimal PlaybackEvent; ayrı preference model; %50 gerçek dinleme. |
| §§67–68: writeTags/requestWrite/trash API sorumlulukları | **DARALTILDI** | Fiziksel delete + doğrulama; Room-only metadata; katalog/physical trash ayrımı. |
| §§72–74: M3U import/export | **SUPERSEDED** | M3U import/export yok. Numaralar tarihsel iz olarak kaldı. |
| §§75–76: genel portable backup | **GENİŞLETİLDİ** | Ses dosyasız yedek, iki hash ile strict restore, migration, opsiyonel Drive. |
| §81: gelecekte çok dil | **KESİNLEŞTİ** | İlk sürüm Türkçe + İngilizce. |
| §85: Booming tag write ve M3U referansı | **TEMİZLENDİ** | Ürün referansından çıkarıldı. |
| §97: silme tanımları | **AYRILDI** | Katalog trash, EXCLUDED, fiziksel Location delete, permanent Track delete. |
| §101: dahil/hariç klasör kuralları | **SUPERSEDED** | Yalnız seçilen root(lar) + tüm alt klasörler, ayrı exclude rules yok. |
| §§89–92: P01/P01X/P02 sınırı | **KORUNDU** | Sonradan karar verilen özellikler P01'e otomatik alınmadı. |

## Özellikle korunmuş ilkeler

- Room canonical; MediaStore yalnız discovery.
- Canonical TrackID ve TrackLocation ayrımı; removable media için bilgi kaybı yok.
- Multi-genre ve hierarchy; folder yapısı gerçek storage'dan gelir.
- Lyrics rich model, word-level timing, LRCLIB adapter ve bağımsız provider.
- Tek MediaLibraryService/MediaSession, ileride Android Auto; Wear yalnız companion.
- BoomingMusic GPL kodu **kopyalanmaz**.
- Smooth Ürün Ailesi marka/tasarım dili ve Türkiye/Türkçe kullanım senaryosu korunur.

## Yürürlükte olmayan öneri/kararlar

- Ses dosyasına tag/metadata yazmak.
- M3U import/export.
- Her output device için otomatik bağımsız EQ profili.
- Aynı TrackID'nin bir normal playlist'te birden fazla kez yer alması.
- Kullanıcının seçtiği root altında ayrıca include/exclude alt klasör kuralları.
- İlk açılışta/periyodik sessiz full library scan.
- Hash uyumsuz fiziksel dosyayı yalnız metadata/path ile eski TrackID'ye bağlamak.
- App PIN/biometric lock, gizli library, ikinci physical trash, dosya taşıma/rename.
- Grill Me **95, 123 ve 128** iptal sorularından varsayımsal ek şartlar.

---

# XXX. KURULUMDA KORUNACAK 12 DEĞİŞMEZ

1. **Room, tek canonical kütüphanedir.**
2. **MediaStore ID, TrackID değildir.**
3. **Track ve TrackLocation farklı entity'dir.**
4. **Fiziksel dosya hash kimliği ve mantıksal Track merge ayrı süreçtir.**
5. **Kullanıcı override'ı Room'dadır; ses dosyasına metadata yazılmaz.**
6. **Track–Genre many-to-many; Tag ilişkisi ayrı ve gelişebilirdir.**
7. **Genre ağacı ile fiziksel Folder birbirine karıştırılmaz.**
8. **Playlist ve queue canonical TrackID kullanır; normal playlist/queue içinde duplicate TrackID olmaz.**
9. **Erişilemeyen Track otomatik silinmez; katalog trash ile physical delete farklıdır.**
10. **Tarama manuel, seçilen kökleri kapsayan, idempotent ve kesintiden devam edebilirdir.**
11. **P01 production player değildir; P01X yalnız developer smoke, P02 Playback Foundation'dır.**
12. **Üçüncü taraf GPL kodu bağımsız Smooth implementation'a kopyalanmaz.**

**Belge sonu.**
