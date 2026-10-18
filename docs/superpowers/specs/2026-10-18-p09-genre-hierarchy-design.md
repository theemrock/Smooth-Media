# P09 Genre Hierarchy — Tasarım Spec’i

## Amaç

Mevcut Genre altyapısını yeniden kurmadan tamamlamak: çoklu genre üyeliği, en fazla bir Primary Genre, çok ebeveynli döngüsüz hiyerarşi, güvenli Room güncellemeleri ve Smart Playlist’in ileride kullanabileceği SQL tabanlı AND/OR/NOT sorgu sözleşmesi.

Kapsam dışı: Smart Playlist motoru/UI’si, playback/MiniPlayer, otomatik rescan, gerçek metadata apply, ses dosyasına yazma, Artist/Album merge ve cihaz kataloğunda değişiklik.

## Anayasa uyumu

- §19–22: Track–Genre many-to-many; Primary ayrı bir özellik ve diğer üyelikleri silmez.
- §23: Genre bilgisi EQ davranışına bağlanmaz.
- §27–30: Room/SQL, parametreli sorgu, RAM’e tüm katalog yüklememe, descendant ve AND/OR/NOT desteği.
- §108: DAG/cycle kontrolü ve ileride Smart Playlist sorgusuna açık bağımsız sözleşme.
- §128: kullanıcı override’ları, stable ID’ler ve mevcut katalog verisi korunur.

## Mevcut altyapı

Room v10’daki `genre_nodes`, `genre_edges` ve `track_genre_assignments` tabloları kullanılacak. `GenreHierarchy`, `GenreDao`, `RoomGenreRepository` ve mevcut Genre UI korunarak genişletilecek. Yeni tablo veya kolon eklenmeyecek; yeni indeks ancak sorgu planı gerektirirse additive migration ile değerlendirilecek.

## Genre graph sözleşmesi

`GenreHierarchy.build` tam graph doğrulamasının tek saf kapısıdır. ID’ler trim edilir; boş/duplicate ID, self-parent, eksik parent ve dolaylı cycle reddedilir. Çoklu parent geçerlidir. Descendant traversal tekrarları Set ile engeller ve stable ID sırasıyla döner.

Repository iki güncelleme türü sunar:

- Tam snapshot: çağrı açıkça bütün graph’ı temsil ettiğini belirtir. Snapshot bütünüyle doğrulanmadan hiçbir edge silinmez. Doğrulama başarılıysa transaction içinde eski edge’ler kaldırılır, geçerli node/edge snapshot’ı yazılır; SQL hatası veya başka hata tüm transaction’ı rollback eder.
- Kısmi güncelleme: yalnızca belirtilen node/edge değişikliklerini uygular. Mevcut edge’ler korunur; ekleme/parent değiştirme öncesi yeni graph transaction içinde doğrulanır. Geçersiz veya eksik parent durumunda mevcut graph değişmez.

Stable Genre ID korunur. Metadata kaynaklı upsert, `userOverride=true` node’un display name, provenance ve kullanıcıya ait alanlarını ezmez.

## Track assignment sözleşmesi

Track başına 0..N assignment ve en fazla bir `isPrimary=true` vardır. Genre ve Track foreign key’leri Room tarafından korunur.

- Yeni secondary assignment mevcut üyelikleri korur.
- İlk Primary assignment yalnızca Track’te Primary yoksa eklenir.
- Açık Primary değişiminde hedef assignment Primary olur; eski Primary silinmez, `isPrimary=false` yapılır.
- Açık Primary kaldırma eski assignment’ı secondary yapar; otomatik veya keyfi yeni Primary seçilmez.
- İstem dışı ikinci Primary girişimi reddedilir.
- Metadata assignment’ı `userOverride=true` kayıtları ezmez.
- Tekli ve toplu işlemler tek Room transaction’ında yürür; başarısız işlemde tüm önceki assignments korunur.

## Filter/query sözleşmesi

Saf `GenreQuery` modeli nested `Term`, `And`, `Or`, `Not` ifadelerini temsil eder. Üretim sorgusu bunu parametreli SQLite SQL’e çeviren bağımsız bir query contract ile çalıştırır; `SimpleSQLiteQuery`/Room `@RawQuery` veya eşdeğer mevcut Room deseni kullanılabilir.

Kök aday evreni aktif katalogdaki Track’lerdir. Term, doğrudan Genre ID veya `includeDescendants=true` ise recursive descendant CTE üzerinden `EXISTS` ile eşleşir. AND/OR nested boolean SQL olarak gruplanır; NOT `NOT EXISTS` ile aynı aktif aday evrenine uygulanır. Sonuç `DISTINCT track_id` ve stable `track_id` tie-break ile deterministiktir. Limit/offset query contract’ın parçasıdır.

Tanımlar:

- Boş AND: true.
- Boş OR: false.
- Bilinmeyen Genre Term: false; `NOT(unknown)` aktif katalogda true.
- Boş katalog: her sorgu boş sonuç döndürür.

Sorgu hiçbir aşamada tüm katalog assignment’larını RAM’e yüklemez. Bu contract ileride Smart Playlist rule saklama/çalıştırma katmanına doğrudan aktarılabilir; Smart Playlist motoru bu fazda yapılmaz.

## UI entegrasyonu

Mevcut Genre listesi, hierarchy gezinmesi ve Genre Detail ekranı korunur. Child/parent ilişkileri canonical graph’tan; track listeleri, Primary/Secondary göstergeleri ve sayaçlar canonical assignments’tan okunur. Cycle’siz graph garantisi gezinmeyi güvenli kılar. Yeni navigasyon veya görsel tasarım yapılmaz.

## Hata ve veri güvenliği

Validation hataları açık domain sonucu/exception ile bildirilir; DB’ye yazılmaz. Room transaction dışına taşan kısmi state bırakılmaz. Destructive migration, restore, rescan, uninstall, `pm clear`, fiziksel dosya/tag yazımı ve gerçek cihaz kataloğu değişikliği yapılmaz.

## Test kabul kriterleri

JVM unit testleri graph validation, descendant deduplication, assignment invariant’ları, override koruması ve nested filter semantics’i kapsar. Room instrumentation testleri transaction rollback, tam/kısmi graph update, SQL query contract, foreign key ve mevcut catalog ilişkilerini kapsar. Mevcut v6→v7 migration testi korunur; Room sürümü değişmediği için yeni migration testi eklenmez.

Çalıştırılacak doğrulamalar: JVM unit tests, `assembleDebug`, `compileDebugAndroidTestKotlin`, `git diff --check`. `connectedDebugAndroidTest` çalıştırılmaz.

## Açık durumlar

Issue #8, #9 ve #11 açık kalır. P08 PARTIAL durumu değişmez. P09 PASS yalnızca tüm yerel kabul kriterleri ve istenen doğrulamalar başarılı olursa raporlanır.
