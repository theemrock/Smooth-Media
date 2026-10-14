# P01-07 Missing / Availability Reconciliation Kapsam Taslağı

Durum: P01-07A için kullanıcı tarafından onaylanmış kapsam ve kabul kriterleri. Bu belge anayasa maddesi değildir; burada anayasa ile açıkça belirlenmemiş konular varsayımla kapatılmaz.

## 0. Onaylanmış P01-07 kararları

- `MISSING` yalnız ilgili seçilmiş root'un eksiksiz, hatasız ve tamamlanmış manuel taramasında üretilebilir.
- Eksik alt klasör, discovery/read error, partial, pause, cancel veya failed durumunda MISSING üretilemez.
- İzin/erişim kaybı fiziksel dosya kaybı değildir; `PERMISSION_LOST`/`UNAVAILABLE` ayrımı korunur.
- TrackID, TrackLocation ilişkileri ve kullanıcı Room verileri korunur; otomatik silme yoktur.
- Aynı hash + aynı format tek TrackID altında çoklu Location olabilir; cross-format otomatik merge yapılamaz.
- EXCLUDED kayıtları otomatik geri gelmez; kaynak kaldırılınca katalog korunur.
- Kaybolan dosya doğrulanarak tekrar bulunursa `AVAILABLE` olur.
- Kesinti sonrası devam yalnız açık kullanıcı komutuyla yapılır.
- MISSING'in UI'da soluk ve simgeli gösterimi ileride yapılacaktır; P01-07A UI içermez.

## 1. Dayanak ve mevcut sınır

P01-03/04/05/06 ile seçilmiş SAF kaynak kaydı, MP3/FLAC discovery, tam dosya hash'i, identity/ingestion ve kesintili tarama yaşam döngüsü mevcut kabul edilir. P01-07 bunların üzerine yalnız katalogdaki mevcut `TrackLocation` erişilebilirlik durumunu güvenli biçimde yeniden değerlendirme paketidir.

P01-07'nin ilk bağımsız paketi olarak önerilen dilim:

**P01-07A — tamamlanmış manuel tarama kanıtından reconciliation karar üretimi.**

İlk uygulama saf bir karar/policy bileşeni ve izole testleri olmalı; Room şeması, UI, zamanlayıcı ve fiziksel dosya işlemi bu dilimin parçası olmamalıdır.

## 2. Önerilen kapsam

### 2.1 Erişilebilirlik durumlarının ayrılması

`TrackLocation` için aşağıdaki anlamlar korunur:

- `AVAILABLE`: Dosya mevcut ve okunabilir.
- `UNAVAILABLE`: Kaynağın kendisi geçici olarak erişilemiyor (ör. SD/USB kaynağı yok).
- `PERMISSION_LOST`: Android/SAF erişim hakkı yok veya grant doğrulanamıyor.
- `MISSING`: Kaynak okunabiliyor, fakat beklenen fiziksel dosya tamamlanmış taramada bulunamadı.

Kaynak erişilemiyorsa location `MISSING` yapılmaz. Bu ayrım FINAL_ARCHITECTURE §95 ve §96'nın doğrudan gereğidir.

### 2.2 MISSING kararı

Bir location ancak şu kanıtlar birlikte mevcutsa `MISSING` adayı olabilir:

1. İlgili source `ACTIVE` ve tarama için kullanılabilir durumdadır.
2. Manuel scan session başarıyla tamamlanmıştır; checkpoint `COMPLETE` olmalıdır.
3. Tarama kapsamı seçilmiş root'un tamamını temsil eder.
4. Beklenen locator/candidate bu tamamlanmış discovery sonucunda bulunmamıştır.

`PARTIAL`, `PAUSED`, `CANCELLED`, `FAILED`, hash/izin hatası veya discovery erişim hatası MISSING üretmek için yeterli değildir. Bu, §8, §11, §77, §99 ve §125.5 ile uyumludur.

### 2.3 Taşınan dosyanın yeniden tanınması

Locator/URI değişmiş olsa bile tam SHA-256 hash mevcut `originalFileHash` veya `currentFileHash` ile doğrulanırsa mevcut fiziksel kimlik yeniden bağlanabilir. TrackID, kullanıcı Room metadata override'ları, playlist, favorite ve history korunur.

Yalnız dosya adı, path, duration, MIME veya metadata benzerliğiyle rebind yapılmaz. Hash eşleşmiyorsa eski TrackID devredilmez; §3, §4, §6, §99, §102, §103 ve §105 uygulanır.

### 2.4 Otomatik silme yasağı

Reconciliation yalnız durum/kanıt güncellemesi üretir. Track, TrackLocation veya kullanıcı ilişkileri otomatik silinmez; Catalog Trash, EXCLUDED ve fiziksel silme ayrı açık kullanıcı işlemleridir. Bu §77, §97, §99, §103 ve §104'e dayanır.

## 3. Kabul kriterleri

| ID | Kabul kriteri | Anayasa dayanağı |
|---|---|---|
| R1 | Tamamlanmış ve erişilebilir source taramasında bulunmayan location `MISSING` adayı olur. | §§77, 95, 99, 125.4 |
| R2 | Partial/paused/cancelled/failed veya permission/access hatalı tarama hiçbir location'ı MISSING yapmaz. | §§8, 11, 77, 99, 102, 125.5 |
| R3 | Kaynak erişilemiyorsa sonuç `UNAVAILABLE` veya `PERMISSION_LOST` olur; `MISSING` değildir. | §§95–96, 125.4 |
| R4 | Yeni locator ile aynı tam hash doğrulanırsa mevcut Track/Location kimliği güvenli biçimde korunur veya mevcut location'a kontrollü rebind yapılır. | §§3–6, 99, 102 |
| R5 | Hash eşleşmiyorsa filename/path/metadata ile eski TrackID atanmaz. | §§6, 99, 103, 105 |
| R6 | Reconciliation TrackID, Room override, playlist, favorite ve history'yi değiştirmez. | §§3, 6, 99, 103–105 |
| R7 | Aynı reconciliation tekrarlandığında yeni Track/Location üretmez; karar idempotenttir. | §§8, 102, 125.1 |
| R8 | EXCLUDED/tombstone kayıtları normal reconciliation ile otomatik geri getirilmez. | §104 |
| R9 | MISSING/UNAVAILABLE/PERMISSION_LOST durumundaki Track otomatik silinmez ve alternatif AVAILABLE location korunur. | §§95, 97, 99, 104 |
| R10 | İşlem yalnız tamamlanmış scan kanıtı üzerinde çalışır; yarım taramadan yanlış eksik listesi çıkmaz. | §§8, 11, 77, 102, 125.5 |

## 4. İlk paket için önerilen test matrisi

İlk üretim paketi uygulanırken saf policy testleri en az şu durumları kapsamalıdır:

1. Complete + bulunamadı → `MISSING` adayı.
2. Partial/paused/cancelled/failed → no-op / `NOT_ELIGIBLE`.
3. Source `UNAVAILABLE` → `UNAVAILABLE`, `MISSING` değil.
4. Permission grant kaybı → `PERMISSION_LOST`, `MISSING` değil.
5. Locator değişti + aynı full hash → mevcut TrackID/location ilişkisi korunur.
6. Locator değişti + farklı hash → eski TrackID'ye rebind yok.
7. Aynı kararın ikinci uygulanışı → idempotent.
8. MISSING location yanında AVAILABLE alternatif → Track ve ilişkiler korunur.
9. EXCLUDED kayıt → otomatik yeniden import/rebind yok.
10. Kullanıcı override ve playlist/history fixture'ları → hiçbiri değişmez.

Bu testler gerçek kullanıcı SAF ağacına veya fiziksel dosyaya yazmamalıdır. Room entegrasyonu, şema ve transaction sınırı bu saf policy paketi onaylandıktan sonra ayrı alt paket olarak tanımlanmalıdır.

## 5. Açık mimari kararlar

Aşağıdaki noktalar mevcut anayasa tarafından tek bir uygulanabilir API/şema olarak belirlenmemiştir; varsayımla kapatılmamalıdır:

1. `MISSING` kararı locator bazında mı, source+locator bazında mı tutulacak? Mevcut P01-05 locator binding benzersizliği source kapsamındadır; reconciliation tablosu ve API kapsamı ayrıca belirtilmelidir.
2. Tamamlanmış scan'in kapsam kanıtı yalnız checkpoint `COMPLETE` ile mi, yoksa discovery toplamı ve source snapshot'ı ile de mi doğrulanacak?
3. Dosya taşınması sonrası eski location `MISSING` mi kalacak, yoksa hash doğrulamasından sonra `MISSING → AVAILABLE` geçişi aynı transaction'da nasıl temsil edilecek?
4. Aynı hash birden fazla TrackLocation'da bulunduğunda hangi mevcut identity kararları `ReuseLocation`, hangileri `ReviewCandidate` üretir?
5. Hash okuma hatası ile provider'ın geçici erişim hatası nasıl ayrıştırılacak; mevcut `FullFileHashResult` sonuçlarının reconciliation durumlarına kesin eşlemesi nedir?
6. Source refresh ile scan reconciliation sıralaması ve eşzamanlı scan lease davranışı nedir?
7. MISSING durumunda son görülme zamanı, reconciliation kanıtı ve kullanıcıya gösterilecek açıklama için mevcut şema yeterli mi? Şema değişikliği gerekiyorsa ayrı migration paketi gerekir.
8. Kullanıcının manuel rebind/merge kararı ile sonraki scan'in otomatik hash rebind kararı arasındaki öncelik ve conflict API'si nedir?
9. Bir source kaldırıldığında location'lar `UNAVAILABLE`, `PERMISSION_LOST` veya başka bir lifecycle durumuna mı geçer? §95 ayrımı bunu tek başına seçmiyor.

## 6. Kapsam dışı

- UI, otomatik/background scan ve scheduler.
- Metadata parsing/editing, playback, search/FTS ve physical delete.
- Acoustic fingerprint veya hash dışı otomatik cross-format merge.
- Room schema/migration, Catalog Trash/EXCLUDED write API'si ve kullanıcı onay akışları.
- Kullanıcı dosyasına yazma, taşıma, silme veya SAF grant değiştirme.

## 7. Sonraki adım

Önce yukarıdaki dokuz açık karar için mimari onay verilmelidir. Onaydan sonra P01-07A'nın saf reconciliation policy + JVM testleri, ardından ayrı ve açık transaction/migration paketi planlanabilir. Bu taslak tek başına production davranışını değiştirme yetkisi vermez.
