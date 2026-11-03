# Hazırlık notu: telefon bağlanınca devam

## 1. Dosya etiketlerine yazma (Tag Editor)
- Kod durumu: `MetadataEditorWriteGate.production()` yazmayı etkinleştiriyor, ama ekranda "Fiziksel yazma kilitli" mesajı görüldü. Hangi koşulun kilitlediği doğrulanmadan kapı değiştirilmeyecek.
- Plan: (a) kilidin kaynağını bul, (b) yazmadan önce her dosya için eski/yeni değeri listele, (c) onaydan sonra yaz, (d) yazılan dosyanın SHA-256'sını ve etiketi yeniden oku.
- Kapı "zorla" açılmayacak; güvenlik kapısı kasıtlı bir engel.

## 2. Çoklu tür (multi-genre) için internet kaynağı
- Aday: MusicBrainz web servisi (`inc=genres`). Kurallar: en fazla 1 istek/sn, anlamlı User-Agent zorunlu, aksi halde 503/IP engeli. Veri lisansı ve ticari kullanım koşulları ayrıca doğrulanacak.
- Kaynak: https://wiki.musicbrainz.org/ws/2 (özet kaynak arama sonucu)
- Plan: eşleşme için sanatçı + albüm + başlık, yalnızca öneri üretir (otomatik yazmaz); kabul edilenler `track_genre_assignments` tablosuna secondary olarak yazılır.

## 3. Smart playlist: öneri zamanlaması (Soru 94, B seçeneği)
- Karar: Otomatik çalma 5 şarkılık paketlerle çalışır; kuyrukta çalınmamış son 2 şarkı kalınca yeni 5 öneri hazırlanıp eklenir.
- Karar (Soru 93): Manuel seçim, otomatik atlamadan (skip) daha güçlü sinyal; erken skip hafif olumsuz; tek skip kara liste değil.
- Bu sonuçlar kural motorunun (`library/SmartRule.kt`) üzerine kurulacak; önce kullanıcı tanımlı kural kaydı (şema v15) ve öneri tetikleyicisi.

## 4. Adaptif EQ / AutoEQ
- Karar (Soru EQ-02..06): İlk analiz parça başlamadan hızlı yapılır; tam analiz temel profil olur; canlı düzeltme ±2 dB ile sınırlı; tek EQ denetleyicisi çakışmayı yönetir.
- Cihazda test: ses %0–5, kısa parçalar.
- Önce yalnızca manuel 10 bant + preamp (V1), sonra canlı düzeltme.

## Açık sorular (telefon bağlanınca)
- Kapı kilidinin kaynağı için yapılacak kontrol sonucu.
- MusicBrainz kullanımı onaylanacak mı (lisans kontrolünden sonra).

## Kararlar (otonom, uygulandı)
- Yazma kapısı KAPALI kalır. Dosya etiketi yazımı kod olarak hazır değil; kapı açılması ayrı bir onay ve geri alma planı gerektirir.
- Tür verisi: MusicBrainz veri dökümü (çekirdek veri CC0) bilgisayarda işlenir, sanatçı bazlı tür haritası üretilir ve telefona küçük bir dosya olarak aktarılır. Telefon 7000 istek atmaz. Haritada bulunmayan kayıtlar için yalnızca yavaş API (1 istek/sn) yedeği kullanılır. Dökümün boyutu ve indirme planı ayrıca kontrol edilecek.
- Öneri zamanlaması: SmartSuggestionPolicy (5'li paket, son 2 şarkıda tetik). Dinleme sinyalleri: ListenScoring (manuel > tamamlanma > erken atlama; 3 sn altı atlama sayılmaz).
- EQ: ParametricEq (RBJ peaking biquad, AutoEQ metin ayrıştırma, manuel ±12 dB sınır, canlı düzeltme ±2 dB). Cihaz entegrasyonu telefon bağlanınca.
- Testler: SmartSuggestionPolicyTest, ParametricEqTest, TurkishMojibakeTableTest, MetadataNormalizerTest geçiyor.
