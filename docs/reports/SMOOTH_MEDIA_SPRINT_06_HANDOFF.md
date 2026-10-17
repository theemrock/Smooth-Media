# Smooth Media — Sprint 06 Devir Notu

## Kapsam

Sprint 06, yalnız **Source Management 2.0 + Settings Navigation** çalışmasıdır. Katalog, SAF izinleri ve mevcut tarama motoru yeniden yazılmayacaktır.

## Kabul edilmiş ürün kararları

- Kaynaklar, ana hamburger menüsünden kaldırılacak.
- Erişim yolu: **Ayarlar → Kütüphane Yönetimi → Müzik Kaynakları**.
- Müzik Kaynakları ekranında sol üstte hamburger, sağ üstte Home bulunacak.
- Menü öğeleri solda ikon ve sağda metinle tek yatay satırda gösterilecek.
- Aktif kaynaklar üstte listelenecek.
- Kaldırılmış kaynaklar ayrı ve daraltılabilir bölümde yer alacak.
- Kaynak kartları okunabilir adı, erişim durumu ve gerçek tarama durumunu gösterecek.
- Pause, Resume ve Stop kontrolleri mevcut `ManualScanController` akışına bağlı kalacak.
- Teknik kimlikler ve ham URI'ler ana görünümde gösterilmeyecek.
- Onaylı koyu tema ve Dashboard tasarım dili korunacak.

## Korunacak teknik sınırlar

- Room katalog için canonical kaynaktır.
- Tarama yalnız kullanıcı eylemiyle başlar ve aynı anda tek oturum çalışır.
- Track/TrackLocation kimliği, hash ve idempotent ingestion kuralları değiştirilmez.
- SAF izinleri, katalog verisi ve kaynakların erişim durumları kullanıcı eylemi olmadan değiştirilmez.
- Kaynak kaldırma, tarama ve erişim kontrolleri gerçek source ID üzerinden çalışmaya devam eder.

## Açık takip maddesi

Pause/Resume sonrası ilerleme sayaçları, önceden checkpoint'e yazılmış adayları tekrar sayabiliyor. Bu hata Sprint 06 kapsamı değildir; ayrı GitHub issue üzerinden takip edilir. Kaynak yönetimi arayüzü bu sayaçları mevcut gerçek scan state'inden okumalı, yeni tahmini sayaç üretmemelidir.
