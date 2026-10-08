SMOOTH MEDIA



Kurulum Öncesi Nihai Mimari Kararlar



1\. Ürün konumu



Smooth Media yalnızca “telefondaki MP3'leri çalan Android uygulaması” değildir.



Temel kavram:



Smooth Media kendi müzik kataloğuna sahip bir media system'dır. Android uygulaması bu sistemin ilk client'ıdır.



Bugün:



Galaxy S24 Ultra

&#x20;     ↓

Smooth Media



İleride:



&#x20;                  Smooth Media Catalog

&#x20;                         │

&#x20;            ┌────────────┼────────────┐

&#x20;            ▼            ▼            ▼

&#x20;          Phone      Android Auto    Watch

&#x20;            │

&#x20;            │ USB/Ethernet

&#x20;            ▼

&#x20;       Vehicle Media



Bu nedenle mimari hiçbir zaman yalnızca Android MediaStore kimliklerine veya telefonun mevcut path'lerine bağlanmayacak.



CATALOG \& IDENTITY



2\. Room canonical source of truth



BoomingMusic'in temel yaklaşımını burada almıyoruz.



Booming:



MediaStore → Library

Room       → auxiliary state



Smooth:



Discovery

&#x20;   ↓

Scanner

&#x20;   ↓

Metadata

&#x20;   ↓

Identity

&#x20;   ↓

ROOM CATALOG

&#x20;   ↓

Library



Room, Smooth Media'nın gerçek library database'i olacak.



MediaStore yalnızca Android üzerindeki veri kaynaklarından biri.



3\. Track canonical entity



Track, fiziksel dosya değildir.



Track

├── trackId

├── title

├── duration

├── metadata...

└── relations...



Canonical trackId Smooth'a aittir.



MediaStore \_ID değildir.



4\. TrackLocation



Dosyanın bulunduğu yer Track'ten ayrılır.



Track

&#x20; │

&#x20; ├── TrackLocation: Phone

&#x20; │      /storage/emulated/0/Music/...

&#x20; │

&#x20; └── TrackLocation: Car

&#x20;        /media/music/...



Kabaca:



TrackLocation

├── id

├── trackId

├── source/device

├── uri

├── path

├── mediaStoreId?

├── fileSize

├── modifiedAt

└── availability



Böylece aynı müzik farklı cihazlarda farklı dosya konumlarında bulunabilir.



5\. MediaStore ID canonical kimlik değildir



Kesin yasak:



Track.id = MediaStore.\_ID



MediaStore ID yalnız:



TrackLocation.mediaStoreId



gibi device-local locator olabilir.



Bu karar gelecekteki phone ↔ car sync için kritik.



6\. TrackIdentityResolver



Scanner'ın ortasında bağımsız identity katmanı olacak.



DiscoveredFile

&#x20;     ↓

Metadata

&#x20;     ↓

TrackIdentityResolver

&#x20;     ↓

Existing Track / New Track



İlk sürümde aşırı mühendislik yapılmayacak.



Başlangıçta:



file properties



duration



normalized metadata



gerektiğinde hash



gibi ucuz sinyaller kullanılabilir.



Ama API daha güçlü matching'e izin verecek.



7\. Audio fingerprint ilk fazda yok



İlk günden:



AcoustID



full acoustic fingerprint



MusicBrainz zorunluluğu



bütün arşivin ağır cryptographic hashing'i



yapılmayacak.



IdentityResolver bunu ileride eklemeyi mümkün kılacak.



SCANNER



8\. Smooth kendi scanner'ına sahip olacak



BoomingMusic'in MediaStore merkezli scanner yaklaşımını almıyoruz.



Pipeline:



DiscoverySource

&#x20;     ↓

MediaScanner

&#x20;     ↓

MetadataReader

&#x20;     ↓

MetadataNormalizer

&#x20;     ↓

TrackIdentityResolver

&#x20;     ↓

CatalogWriter



9\. MediaStore discovery source olabilir



Android'de MediaStore'u çöpe atmıyoruz.



DiscoverySource

├── MediaStoreDiscovery

├── File/SAF Discovery

└── future USB/external sources



MediaStore:



hızlı discovery mekanizması.



Ama:



library database değil.



10\. Phase 01 format sınırı



İlk scanner:



MP3 + FLAC



ile başlar.



AAC, ALAC, Opus, OGG, WAV, MIDI vb. sonra eklenebilir.



Format desteği mümkünse ayrı policy/adapter sınırından genişletilebilir.



11\. Incremental scanning tasarıma dahil



İlk uygulama basit olabilir fakat scanner'ın tek çalışma modu:



DELETE DATABASE

SCAN EVERYTHING



olmayacak.



Uzun vadeli hedef:



Initial Scan

Incremental Scan

Targeted Rescan



Tag değişikliği sonrası yalnız etkilenen dosya yeniden işlenebilir.



METADATA



12\. Embedded metadata first-class input



BoomingMusic'in güçlü taraflarından biri.



Smooth scanner doğrudan audio file metadata okuyacak.



Hedef alanlar:



Title

Artist

Album

Album Artist

Genre(s)

Track Number

Total Tracks

Disc Number

Total Discs

Date/Year

Lyrics

Composer

Lyricist

Arranger

Producer

Comment

Encoder

Copyright

Artwork



Teknik metadata:



Duration

Bitrate

Sample Rate

Channels

Codec/format

File size



13\. Raw metadata ile normalized metadata ayrılacak



Örneğin:



Raw:

IRON MAIDEN



Normalized:

Iron Maiden



Normalization yüzünden orijinal bilgi kaybolmayacak.



Bu özellikle matching/search için önemli.



14\. Metadata editing desteklenecek



İleride Tag Editor:



single edit



batch edit



destekleyecek.



Örneğin 14 track seçip:



Album = Powerslave

Year = 1984

Genre = Heavy Metal



uygulanabilir.



15\. Tag write sonrası targeted rescan



Akış:



TagEdit

&#x20;  ↓

Permission

&#x20;  ↓

MetadataWriter

&#x20;  ↓

File

&#x20;  ↓

Targeted Rescan

&#x20;  ↓

Catalog

&#x20;  ↓

Relations/Search



Bir dosyanın genre'sı değişti diye 12.000 track yeniden taranmayacak.



ARTIST / ALBUM



16\. Artist canonical entity



Artist yalnız string olmayacak.



Artist

TrackArtist



ilişkisi kurulabilecek.



Bu ileride multi-artist desteğini mümkün kılar.



17\. Multi-artist önü kapanmayacak



BoomingMusic'in kendi roadmap'inde bile multi-artist geliştirmesi bulunuyor.



Smooth database'i:



Track.artist = "A \& B"



şeklinde tek string'e mahkûm edilmeyecek.



18\. Album canonical entity



Album da MediaStore grouping sonucu değildir.



Album

TrackAlbum

AlbumArtist



ilişkileri catalog tarafından yönetilecek.



Compilation ve album artist senaryoları buna göre çözülebilecek.



GENRE



19\. Multi-genre temel invariant



Bu kesin karar.



Track ↔ Genre = MANY-TO-MANY



Bir track:



Track X

├── Heavy Metal

├── NWOBHM

└── Progressive Metal



olabilir.



Track.genreId şeklinde tek genre alanına kilitlenmeyecek.



20\. Genre hierarchy



Multi-genre'dan ayrı kavram.



Metal

├── Heavy Metal

│   └── NWOBHM

├── Thrash Metal

├── Doom Metal

└── Progressive Metal



Genre'lar birbirleriyle parent/child ilişkisi kurabilir.



21\. Multi-genre ile hierarchy bağımsız



Bir Track farklı dallardaki genre'lara aynı anda bağlı olabilir.



Örneğin:



Track

├── NWOBHM

└── Progressive Metal



Genre ağacı Track'i tek bir dala hapsetmez.



22\. Primary Genre



Track 0..N genre taşıyabilir fakat gerektiğinde:



primaryGenre



seçilebilir.



Örneğin:



Genres:

Heavy Metal

NWOBHM

Progressive Metal



Primary:

Heavy Metal



Primary genre diğer membership'leri silmez.



23\. Primary Genre ileride davranış sağlayabilir



Özellikle EQ için:



Track EQ override

&#x20;       ↓

Primary Genre EQ

&#x20;       ↓

Default EQ



gibi inheritance mümkün.



Phase 01'de EQ yapılmayacak, ama genre modeli bunu engellemeyecek.



FOLDERS



24\. Folder görünümü gerçek filesystem mantığını koruyacak



Kullanıcının ilk fikrindeki gibi:



düz file system görünümü.



Folder görünümü metadata taxonomy değildir.



Music/

├── Metal/

│   ├── Iron Maiden/

│   └── Metallica/

└── Turkish/



25\. Folder ikinci sınıf library özelliği değildir



Özellikle bozuk/eksik tag durumunda Folder güvenilir erişim yolu.



Artist/Album/Genre:



metadata dünyası.



Folder:



storage dünyası.



İkisi de gerekli.



26\. Genre ile Folder birbirine karışmayacak



Genre ağacı:



Metal

└── Heavy Metal



logical taxonomy.



Folder:



/storage/emulated/0/Music/Metal/



physical storage.



Genre değiştirildi diye dosya taşınmayacak.



SEARCH



27\. Search ayrı subsystem



BoomingMusic'in basit SQL LIKE yaklaşımını almıyoruz.



Hedef:



SearchRepository

&#x20;      ↓

Indexed Search / Room FTS



28\. Nihai global search alanları



Arama:



song



artist



album



genre



lyrics



folder



üzerinden çalışabilecek.



29\. P01 Search scope



Lyrics henüz library bootstrap'ın parçası olmadığı için P01:



Song

Artist

Album

Genre

Folder



ile başlayabilir.



Lyrics geldiğinde search index genişletilir.



Search mimarisi bunu engellemeyecek.



30\. Search filters + recent searches



00\. Tasarım gereği Search ekranı yalnız text box olmayacak.



İleride:



type filters



genre



artist



album vb.



recent searches



destekleyebilecek.



ARTWORK



31\. ArtworkResolver



Artwork doğrudan UI sorumluluğu olmayacak.



UI

&#x20;↓

ArtworkResolver

&#x20;↓

available artwork source



Potansiyel kaynaklar:



user override



embedded artwork



folder artwork



cached remote artwork



generated fallback



32\. Artwork priority henüz dondurulmuyor



Özellikle:



Album custom artwork, Track embedded artwork'ü ezmeli mi?



gibi davranışlar daha sonra belirlenebilir.



Architecture yalnız resolver modelini garanti edecek.



PLAYLIST



33\. Playlist normalized olacak



BoomingMusic'teki metadata-copy modeli reddediliyor.



Smooth:



Playlist

&#x20;  │

&#x20;  └── PlaylistEntry

&#x20;         ├── id

&#x20;         ├── playlistId

&#x20;         ├── trackId

&#x20;         ├── position

&#x20;         └── addedAt



Track metadata PlaylistEntry içine kopyalanmayacak.



34\. PlaylistEntry kendi stable ID'sine sahip olacak



Böylece aynı Track'in playlist'te iki kere bulunmasını teknik olarak yasaklamamış oluruz.



Örneğin:



1\. Track A

2\. Track B

3\. Track A



mümkün olabilir.



Bunu gereksiz unique constraint ile öldürmeyelim.



35\. STATIC / SMART ayrımı



Playlist modeli baştan genişleyebilir:



Playlist

├── STATIC

└── SMART



Static:



PlaylistEntry



Smart:



rules/query



36\. Smart Collections



Sistem query'leri:



Favorites

Recently Played

Most Played

Never Played

Recently Added

Forgotten Tracks



fiziksel playlist olmak zorunda değil.



Query-generated collection olabilir.



37\. Favorites display name ile tanımlanmayacak



BoomingMusic'teki:



playlistName == "Favorites"



mantığı kesinlikle yok.



Stable system identity:



SystemCollection.FAVORITES



veya dedicated Favorite relation.



UI:



Favorites

Favoriler

Favoritos

...



olabilir.



Identity aynı kalır.



QUEUE



38\. Queue playlist değildir



Queue runtime playback state.



QueueController

├── entries

├── currentTrack

├── position

├── repeat

└── shuffle



Playlist'ten bağımsız.



39\. Queue kullanıcı tarafından düzenlenebilir



00\. Tasarım:



Play Next



Add to Queue



Remove



Reorder



Clear



Save as Playlist



destekleyecek.



Queue yalnız ExoPlayer internal playlist wrapper'ı olmayacak.



40\. Queue persistence



BoomingMusic'teki iyi fikir alınacak.



App/process öldüğünde:



Queue

Current Track

Playback Position

Repeat

Shuffle



geri getirilebilir.



Persistence canonical TrackId üzerinden yapılacak.



PLAYBACK



41\. Media3 / ExoPlayer



BoomingMusic'ten alınacak en güçlü mimari referanslardan.



Smooth playback engine:



PlaybackEngine

&#x20;     ↓

Media3 ExoPlayer



üzerinden kurulacak.



42\. MediaLibraryService



Playback Android UI'ye gömülmeyecek.



Catalog

&#x20;  ↓

MediaLibraryService

&#x20;  ↓

PlaybackEngine



Bu Android Auto'nun da aynı library/playback sistemini kullanmasını sağlar.



43\. Android Auto ayrı player değildir



Kesin:



&#x20;              MediaLibraryService

&#x20;                      │

&#x20;              ┌───────┴───────┐

&#x20;              ▼               ▼

&#x20;            Phone        Android Auto



İki playback engine veya iki library yapılmayacak.



44\. Custom audio pipeline seam



P02'de DSP yapılmasa bile architecture:



ExoPlayer

&#x20;  ↓

RenderersFactory

&#x20;  ↓

AudioSink

&#x20;  ↓

AudioProcessors



genişlemesine izin verecek.



45\. ReplayGain



Booming'in yaklaşımı güçlü referans.



İleride:



track gain



album gain



peak



clipping prevention



preamp



R128



desteklenebilir.



Ama audio thread üzerinde filesystem/DB okumak yasak.



46\. EQ architecture



Booming'in dev EqualizerManager modelini almıyoruz.



İleride:



AudioPipeline

├── ReplayGainProcessor

├── EqualizerProcessor

├── BalanceProcessor

└── ...



ve:



EqualizerController

AudioProfileRepository

OutputDeviceMonitor



ayrı olacak.



47\. Device-specific audio profiles



İleride:



Phone speaker

Headphones

USB DAC

Car



farklı profile sahip olabilir.



Bu özellikle araç projesi açısından değerli.



48\. Bit-perfect / DSP capability



Bit-perfect, DSP, offload gibi özelliklerin birbirleriyle çatışabileceği baştan kabul edilir.



İleride capability sistemi kullanılabilir.



Phase 01 konusu değil.



49\. Gapless



Playback engine gapless kullanımını destekleyecek.



Özellikle live/concept albums için önemli.



50\. Crossfade ayrı özellik



Gapless ≠ Crossfade



Playlist için konuştuğumuz 3–5 saniyelik preview/transition ileriki özellik.



P01/P02'ye sokulmayacak.



51\. Codec fallback



Media3 decoder fallback ve extension renderer architecture'a uygun tutulabilir.



Ama ilk aşamada codec sirki kurulmayacak.



Gerçek library testlerinden ihtiyaç çıkar.



PLAYER UI



52\. Persistent Mini Player



P02 itibarıyla ana navigasyon altında persistent Mini Player.



Library

Search

Playlists

Settings

&#x20;     │

&#x20;     ▼

&#x20;Mini Player

&#x20;     │

&#x20;     ▼

&#x20;Now Playing



53\. Full-screen Now Playing



00\. Tasarım hedefleri:



artwork



title



artist



album



seek



elapsed/remaining



previous



play/pause



next



shuffle



repeat



favorite



lyrics



queue



İleride:



EQ



sleep timer



waveform vb.



LYRICS



54\. Rich Lyrics domain



BoomingMusic'in en iyi referanslarından.



Smooth Lyrics yalnız:



timestamp + text



olmayacak.



Model:



Lyrics

└── Lines

&#x20;   ├── start

&#x20;   ├── end

&#x20;   ├── text

&#x20;   ├── translation

&#x20;   ├── transliteration

&#x20;   ├── actor

&#x20;   └── Words

&#x20;       ├── start

&#x20;       ├── end

&#x20;       └── text



55\. Word-level timing korunacak



Bugün yalnız line-sync göstersek bile word timing varsa atılmayacak.



Sebep:



P14H Karaoke.



Karaoke geldiğinde database'i yeniden parçalamayacağız.



56\. LRC parser capability



İleride parser:



standard LRC



enhanced LRC



multiple timestamps



word timing



offset



translation



background vocals



actor



destekleyebilecek.



Booming'in implementation'ı kopyalanmayacak.



Feature set referans alınacak.



57\. LyricsProvider abstraction



LyricsProvider

├── Local

├── LRCLIB

└── FutureProvider



Provider UI'den bağımsız.



58\. Lyrics sources



Kaynak bilgisi korunacak:



LOCAL\_TAG

LOCAL\_LRC

LOCAL\_DATABASE

LRCLIB

REMOTE\_PROVIDER



Bu bizim mevcut lyrics database çalışmalarımızı da sisteme bağlar.



59\. Lyrics resolution



Kabaca:



Local/Existing

&#x20;     ↓

Smooth lyrics DB/cache

&#x20;     ↓

LRCLIB

&#x20;     ↓

other providers



Ve:



Synced

&#x20;  ↓ unavailable

Plain

&#x20;  ↓ unavailable

None



60\. Lyrics matching kendi sistemimiz



Booming matcher'ı doğrudan alınmayacak.



Bizim yaklaşım:



Candidate

&#x20;  ↓

Normalize

&#x20;  ↓

Score

&#x20;  ↓

Threshold

&#x20;  ↓

MATCH / REVIEW / REJECT



Mevcut lyrics pipeline tecrübemiz burada daha sağlam temel.



61\. Title normalization yalnız search/matching için



Örneğin:



Official Video

Official Audio

Lyrics

Visualizer

Remaster

Live

Acoustic

feat.



normalization sırasında değerlendirilebilir.



Ama canonical title değiştirilmez.



OriginalTitle

NormalizedSearchTitle



ayrı kavramlar.



62\. Synced lyrics seek



00\. Tasarım davranışı:



Synced lyrics satırına dokun:



LyricsLine.start

&#x20;      ↓

Playback seek



Dolayısıyla timing gerçek domain bilgisi.



HISTORY / STATS



63\. TrackStats



Booming'in duplicated Song metadata içeren play count modelini almıyoruz.



TrackStats

├── trackId

├── playCount

├── skipCount

├── totalPlayedMs

└── lastPlayedAt



64\. PlaybackCountingPolicy



Booming'deki %50 çalındı → play mantığı makul referans.



Ama hardcoded olmayacak.



PlaybackCountingPolicy



üzerinden ileride değiştirilebilir.



65\. PlaybackEvent



Aggregate stats dışında event history de düşünülecek:



PlaybackEvent

├── trackId

├── timestamp

├── playedMs

├── completed/skipped

└── source/device?



Böylece ileride:



Geçen yaz arabada en çok ne dinledim?



gibi sorular mümkün.



Retention/pruning sonra belirlenir.



ANDROID STORAGE



66\. Android modern permissions



Android 13+:



READ\_MEDIA\_AUDIO



eski sürümlerde gerekli legacy permission.



MANAGE\_EXTERNAL\_STORAGE istemeyeceğiz.



67\. File mutation consent



Modern Android'de:



MediaStore.createWriteRequest()

MediaStore.createDeleteRequest()

MediaStore.createTrashRequest()



yaklaşımı.



Booming burada iyi referans.



68\. MediaFileOperations abstraction



Bu permission logic farklı ekranlara saçılmayacak.



MediaFileOperations

├── requestWrite

├── requestDelete

├── requestTrash

├── writeTags

└── rescan



veya eşdeğer temiz abstraction.



DELETE SEMANTICS



69\. Delete File ≠ Delete Track



Çok önemli.



Delete TrackLocation



ile:



Delete canonical Track



aynı işlem değildir.



70\. Location silinince history yok olmaz



Dosya telefondan silinse bile:



favorite



playlist membership



history



stats



lyrics



user metadata



otomatik yok edilmeyecek.



Track başka device/location üzerinde hâlâ bulunabilir.



71\. Missing Track state



Hiçbir location kalmadığında Track'i hemen öldürmek zorunda değiliz.



Track

availableLocations = 0



durumu tutulabilir.



Bu yeniden import ve sync için çok değerlidir.



M3U / INTEROPERABILITY



72\. M3U desteklenecek



M3U:



dış dünya interoperability formatı.



Import/export için.



73\. M3U internal playlist database değildir



Smooth internal:



PlaylistEntry.trackId



M3U export:



TrackId

&#x20;  ↓

TrackLocation

&#x20;  ↓

filesystem path



74\. M3U import resolver kullanacak



Import sırasında:



Path

&#x20;↓

TrackLocation?

&#x20;↓ no

IdentityResolver

&#x20;↓

Track



Mantıklı.



Bulunamayan entry'lerin ileride unresolved olarak korunması değerlendirilebilir.



BACKUP



75\. Versioned portable backup



Booming'in versioned backup fikri alınacak ve genişletilecek.



Örneğin:



SmoothMediaBackup

├── manifest.json

├── catalog/

├── playlists/

├── lyrics/

├── history/

├── artwork/

└── settings/



76\. Raw Room DB ana backup formatı olmayacak



Backup domain-level olacak.



Amaç:



Old Smooth schema

&#x20;      ↓

Portable Backup

&#x20;      ↓

New Smooth schema



DB schema sürümüne mahkûm olmamak.



LIBRARY MAINTENANCE



77\. Catalog sahibiysen bakımından da sorumlusun



Smooth kendi catalog'unu tuttuğu için Library Tools gerekecek.



İleride:



Library Tools

├── Rescan

├── Incremental Scan

├── Missing Files

├── Duplicate Candidates

├── Unresolved Metadata

├── Broken Locations

├── Rebuild Search Index

└── Identity Conflicts



COMMON ACTIONS



78\. Unified Track Actions



Track action'ları her ekranda yeniden yazılmayacak.



Ortak use cases:



Play

Play Next

Add to Queue

Add to Playlist

Favorite

Go to Artist

Go to Album

Lyrics

Edit Tags

Share

Delete/Trash

Info



Songs, Album, Artist, Search, Genre, Folder aynı domain action'larını kullanır.



NAVIGATION \& UI



79\. Ana navigation



00\. Tasarım çizgisi:



Library

Search

Playlists

Settings



Library içinde:



Songs

Albums

Artists

Genres

Folders



80\. Smooth Ürün Ailesi tasarım dili



Smooth Media ayrı kafadan tasarlanmayacak.



Aile sistemi korunacak.



Dark:



Background  #090B0D

Surface     #111418

Text        #F4F6F8

Accent      #19C3E6



Light:



Background  #F5F7F8

Surface     #FFFFFF

Border      #D8DEE3



Media kendi module icon'unu kullanacak.



Ana Smooth kimliği korunacak.



81\. Multilingual UI



UI string'leri baştan Android resource sistemi üzerinden.



Hardcoded user-facing strings yok.



İlk gün bütün dünya dillerini çevirmek gerekmiyor.



Ama architecture multilingual.



SYNC READINESS



82\. Device-local IDs sync identity olmayacak



Track, Playlist vb. önemli domain entity'leri gelecekte cihazlar arasında tanınabilir kimliklere sahip olmalı.



Room auto increment:



internal row key olabilir.



Ama:



distributed identity değildir.



83\. Phone ↔ Car sync ilk fazda yapılmayacak



Ama architecture bunu engellemeyecek.



İleride:



Phone

&#x20; │

USB/Ethernet

&#x20; │

Car



84\. Tombstone / revision ihtiyacı



Sadece:



maxId



ile sync yapılamaz.



Yeni Track'i yakalar, silinen Track'i yakalayamaz.



İleride:



revision

change log

tombstone



gibi mekanizma gerekecek.



P01'de implement etmiyoruz.



BOOMINGMUSIC'TEN NE ALIYORUZ?



85\. Güçlü referans olarak kullanılacaklar



Booming'den mimari/teknik referans:



Media3 / ExoPlayer

MediaLibraryService

Android Auto integration

Queue state

Queue restoration

Metadata reading

Tag writing

ReplayGain

Custom audio processor seam

Rich lyrics model

Enhanced LRC capabilities

Android scoped-storage handling

Versioned backup concept

M3U interoperability

Codec fallback strategies

Gapless handling



86\. Yeniden tasarlanacaklar



Booming'in fikri iyi ama implementation yapısı Smooth'a uygun değil:



EQ/DSP management

Artwork resolution

Lyrics provider matching

PlaybackService responsibilities

Play statistics

History

Backup representation

Playlist architecture



87\. Kesinlikle alınmayacaklar



MediaStore as canonical library

MediaStore ID as Track identity

Playlist song metadata duplication

Favorites identified by playlist name

Flat MediaStore genre architecture

Path-only folder architecture

Basic LIKE search

God-service architecture

God-manager EQ architecture



SERVICE BOUNDARIES



88\. PlaybackService God Object olmayacak



Booming'de PlaybackService fazla şey biliyor.



Smooth'ta ayrılacak:



MediaLibraryService

&#x20;      │

&#x20;      ├── PlaybackEngine

&#x20;      ├── QueueController

&#x20;      ├── PlaybackStateRepository

&#x20;      ├── LibraryRepository

&#x20;      └── Integration coordinators



Service orchestration yapar.



Her şeyi kendi yapmaz.



PHASE 01 SINIRI



89\. P01 = Catalog Foundation



Bu noktayı özellikle düzeltiyoruz.



P01 normal music player değildir.



P01:



Empty App

&#x20;  ↓

Permission

&#x20;  ↓

MP3 / FLAC Discovery

&#x20;  ↓

Metadata Read

&#x20;  ↓

Identity Resolution

&#x20;  ↓

Room Catalog

&#x20;  ↓

Relations

&#x20;  ↓

Library UI

&#x20;  ↓

Search



Başarı kriteri:



Smooth Media telefondaki MP3/FLAC library'yi kendi canonical catalog'una doğru biçimde alabiliyor ve kullanıcı bunu Library/Search üzerinden gezebiliyor.



90\. P01'de olmayanlar



Özellikle:



❌ Production playback

❌ Full player

❌ Mini Player

❌ EQ

❌ AutoEQ

❌ ReplayGain DSP

❌ Android Auto

❌ Karaoke

❌ Lyrics download pipeline

❌ Vehicle sync

❌ Watch

❌ Crossfade

❌ MusicBrainz

❌ Acoustic fingerprinting



91\. P01X debug audio smoke test olabilir



Scanner'ın verdiği URI gerçekten Media3 tarafından açılabiliyor mu diye:



Track

&#x20;↓

URI

&#x20;↓

Media3

&#x20;↓

5–10 sec playback

&#x20;↓

PASS



gibi developer-only smoke test yapılabilir.



Bu P02 playback implementation değildir.



PHASE 02 YÖNÜ



92\. P02 = Playback Foundation



P01 catalog sağlam olduktan sonra:



Track

&#x20;  ↓

PlaybackEngine

&#x20;  ↓

QueueController

&#x20;  ↓

MediaLibraryService

&#x20;  ↓

PlaybackState

&#x20;  ↓

Mini Player

&#x20;  ↓

Now Playing



kurulur.



Böylece playback, yanlış library modelinin üzerine inşa edilmemiş olur.



LİSANS SINIRI



93\. BoomingMusic kodu kopyalanmayacak



BoomingMusic:



GPL-3.0



Biz repo üzerinden architecture, davranış, edge case ve implementation fikirlerini öğrendik.



Smooth implementation bağımsız yazılacak.



Özellikle:



source copy/paste



sınıfı rename edip taşıma



parser code transplant



GPL implementation'ı ufak değiştirip kullanma



yapılmayacak.



SON MİMARİ RESİM



94\. Smooth Media Core



&#x20;                        SMOOTH MEDIA



&#x20;                      DISCOVERY SOURCES

&#x20;                             │

&#x20;                 ┌───────────┴───────────┐

&#x20;                 │                       │

&#x20;            MediaStore              SAF / File

&#x20;                 │                       │

&#x20;                 └───────────┬───────────┘

&#x20;                             ▼

&#x20;                          SCANNER

&#x20;                             │

&#x20;                             ▼

&#x20;                      METADATA READER

&#x20;                             │

&#x20;                             ▼

&#x20;                   METADATA NORMALIZER

&#x20;                             │

&#x20;                             ▼

&#x20;                   IDENTITY RESOLVER

&#x20;                             │

&#x20;                             ▼

&#x20;                ┌────────────────────────┐

&#x20;                │    CANONICAL CATALOG   │

&#x20;                │         ROOM           │

&#x20;                └────────────┬───────────┘

&#x20;                             │

&#x20;             ┌───────────────┼───────────────┐

&#x20;             ▼               ▼               ▼

&#x20;         Relations         Search          Library

&#x20;             │               │               │

&#x20;    Artist/Album/Genre      FTS      Songs/Albums/etc.

&#x20;             │                               │

&#x20;             └───────────────┬───────────────┘

&#x20;                             ▼

&#x20;                      DOMAIN ACTIONS

&#x20;                             │

&#x20;                    ┌────────┴────────┐

&#x20;                    ▼                 ▼

&#x20;                Playlist            Queue

&#x20;                                      │

&#x20;                                      ▼

&#x20;                               PLAYBACK ENGINE

&#x20;                                    Media3

&#x20;                                      │

&#x20;                                      ▼

&#x20;                             MediaLibraryService

&#x20;                                      │

&#x20;                         ┌────────────┴────────────┐

&#x20;                         ▼                         ▼

&#x20;                       Phone                  Android Auto



&#x20;                                  FUTURE

&#x20;                                     │

&#x20;                    ┌────────────────┼────────────────┐

&#x20;                    ▼                ▼                ▼

&#x20;                  Watch          USB Sync         Vehicle



Kurulumda korunacak 10 değişmez



Bunları özellikle Codex'e çiviyle çakarım:



Room canonical library'dir.



MediaStore canonical library değildir.



MediaStore ID canonical Track ID değildir.



Track ile TrackLocation farklı entity'dir.



Track ↔ Genre many-to-many'dir.



Genre hierarchy ile multi-genre ayrı kavramlardır.



Playlist metadata kopyalamaz, TrackId referanslar.



Folder ile Genre birbirine karıştırılmaz.



Playback P01'in parçası değildir.



BoomingMusic GPL kodu kopyalanmaz, yalnız bağımsız implementation için teknik referanstır.



Bence artık burada mimariyi dondurabiliriz. Bundan sonraki adım tartışma değil: C:\\Projects\\SmoothMedia içinde sıfır repo → Android project → GitHub → P01 iskeleti. Ve ilk Codex prompt'unda bütün 94 maddeyi kusmak yerine, bu 10 invariant + P01'in ihtiyaç duyduğu ilgili maddeleri vererek başlarız. Böylece Codex'e anayasa veririz, telefon rehberi değil. 🛠️



Mevcut 94 maddelik Nihai Mimari Kararlar'a eklenmesi gereken maddeler var. Özellikle Aurora ve Flick incelemelerinden çıkan sonuçlarla, daha önce konuştuğumuz dosya erişilebilirliği ve silme davranışlarını mimariye bağlamalıyız.

Bunlar yeni özellik listesi değil, ileride veri kaybını ve yanlış mimari kararları önleyecek kurallar.



Ek maddeler





95\. Track varlığı ile dosya erişilebilirliği ayrılacak

Bir şarkının katalogda bulunması, fiziksel dosyasının şu anda erişilebilir olduğu anlamına gelmez.

TrackLocation için en az şu durumlar tanımlanmalı:

\- AVAILABLE: Dosyaya erişilebiliyor.

\- UNAVAILABLE: Kaynak geçici olarak erişilemiyor.

\- PERMISSION\_LOST: Android erişim izni kaybedilmiş.

\- MISSING: Kaynak erişilebilir fakat dosya bulunamıyor.

SD kart veya USB çıkarıldığında Track katalogdan silinmeyecek.

96\. Erişilemeyen şarkılar görsel olarak ayırt edilecek

Dosya yöneticilerindeki gibi:

\- Şarkı adı, sanatçı ve albüm bilgisi korunacak.

\- Artwork mevcutsa gösterilecek.

\- Şarkı satırı soluklaştırılacak.

\- Erişilemezlik durumu açıklanabilecek.

\- Dosya yeniden erişilebilir olduğunda normal görünümüne dönecek.

Önemli: Soluk görünüm yalnız UI davranışıdır. Track'in silindiği anlamına gelmez.

97\. Kütüphaneden kaldırma ile fiziksel dosya silme ayrılacak

Üç farklı işlem tanımlanmalı:

İşlem	Sonuç

Kütüphaneden kaldır	Catalog ilişkisinin kaldırılması

Dosyayı sil	Fiziksel dosyanın silinmesi

Her yerden kaldır	Uygun kapsamda katalog ve ilişkili kayıtların temizlenmesi

Ancak normal kullanıcı arayüzündeki Sil işlemi, senin kararına göre yalnız katalogdan kaldırma yapmayacak. Gerçek dosyayı silmeye yönelik olacak.

Android'in izin/onay mekanizması kullanılacak. Başarısız fiziksel silme, katalogda başarılı silme gibi gösterilmeyecek.

98\. Dosya silme işlemi location-aware olacak

Bir Track'in birden fazla fiziksel kopyası olabilir.

Örneğin:

Track A

├── Phone: AVAILABLE

├── SD Card: AVAILABLE

└── Vehicle: UNAVAILABLE





Telefondaki dosyanın silinmesi diğer iki location'ı otomatik silmeyecek.

Son fiziksel kopya silindiğinde bile Track'in geçmişi, favori durumu ve playlist ilişkileri için ayrı retention policy uygulanacak.

99\. Yeniden tarama veri kaybına yol açmayacak

Scanner bir dosyayı bulamadığında hemen silmeyecek.

\- Kaynağın erişilebilirliğini kontrol edecek.

\- Geçici erişim kaybını gerçek silmeden ayıracak.

\- Yeniden bulunan dosyayı mümkünse mevcut Track ile eşleştirecek.

\- Kullanıcıya ait metadata, favoriler ve istatistikler korunacak.

100\. Fiziksel silme ile katalog güncellemesi tutarlı olacak

Silme işlemi kontrollü bir akış olacak:

Delete Request

&#x20;   ↓

Resolve TrackLocation

&#x20;   ↓

Android Permission / Consent

&#x20;   ↓

Physical File Operation

&#x20;   ↓

Verify Result

&#x20;   ↓

Update Catalog

&#x20;   ↓

Refresh Search / Library





Dosya silinemediyse katalogdan silinmiş gibi davranılmayacak.

101\. Kütüphane kapsamı kullanıcı tarafından yönetilebilecek

Kullanıcı hangi klasörlerin veya kaynakların taranacağını belirleyebilecek.

\- Dahil edilen klasörler

\- Hariç tutulan klasörler

\- Kaynak bazlı tarama

\- Yeniden tarama politikası

Böylece telefondaki her ses dosyası zorunlu olarak müzik kütüphanesine dolmayacak.

102\. Scanner idempotent olacak

Aynı dosyayı değişiklik olmadan tekrar taramak:

\- Yeni Track oluşturmamalı.

\- Gereksiz yeni TrackLocation oluşturmamalı.

\- Kullanıcı metadata'sını ezmemeli.

\- Playlist ve favorileri değiştirmemeli.

Bu davranış otomatik testlerle doğrulanmalı.



Mevcut mimariyle çakışma kontrolü

Burada bir düzeltme gerekiyor: 95, 97, 98 ve 100 numaralı önerilerin önemli kısımları zaten 4, 67, 68, 69, 70 ve 71. maddelerde bulunuyor. Bunları tamamen yeni kavramlar olarak değil, mevcut kuralların kesinleştirilmesi olarak değerlendirmeliyiz.

Benim önerim:

95–102 eklerini kabul edelim, fakat 97. maddedeki katalogdan kaldırma davranışını ayrıca netleştirelim. Çünkü fiziksel dosya dururken yalnız katalogdan kaldırmak, scanner'ın bir sonraki çalışmasında şarkıyı tekrar içeri almasına neden olabilir. Bunun için kalıcı exclusion mekanizması gerekir.

Böylece 102 maddelik mimariyi dondurabiliriz. P01'e yeni özellik yüklemiyoruz; yalnızca ileride yanlış temeller atılmasını önlüyoruz.
