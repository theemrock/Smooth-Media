# Smooth Media — 1011 Büyük Arşiv Performans Checkpoint

## Kapsam

- Sürüm: signed 1011
- Kod commit'i: `998df15`
- Gerçek cihaz testi: 7052 dosyalık tüm arşiv
- Sonuç: `Completed`
- İşlenen: 7052/7052
- Hata: 0
- Katalog sonucu: 7036 şarkı
- 7052 işlenen dosya ile 7036 katalog kaydı arasındaki 16 farkın nedeni doğrulanmadı.

## Ölçülen süreler

| Aşama | Süre |
|---|---:|
| Toplam duvar saati | 311.069 sn |
| Discovery | 56.460 sn |
| Discovery query | 600 çağrı / 39.944 sn |
| URI oluşturma | 14837 çağrı / 0.134 sn |
| Diğer discovery | 16.380 sn |
| `openFileDescriptor` | 0 çağrı / 0 sn |
| Full-file hash inclusive | 266.698 sn |
| Full-file hash wall-clock | 254.406 sn |
| Metadata | 192.900 sn |
| Room | 13.215 sn |
| Checkpoint | 4.157 sn |

Inclusive worker süreleri ile hash wall-clock süresi ayrı tutulmuştur; worker toplamı gerçek geçen süre olarak yorumlanmamıştır.

## Referans karşılaştırması

- Eski toplam: 1588.109 sn
- Yeni toplam: 311.069 sn
- Toplam fark: 1277.040 sn
- Toplam iyileşme: yaklaşık `%80.41`
- Eski discovery: 309.137 sn
- Yeni discovery: 56.460 sn

Eski toplam sürenin kapsamı ve eski ölçümün ayrıntılı alt aşamaları bu checkpoint için yeniden doğrulanmadı. 16 katalog farkı için neden uydurulmadı.

## Kanıt

Ham cihaz logu:

`build/release-artifacts/archive-1011-7000plus.log`

Bu rapor yalnızca ölçüm kaydıdır; yeni optimizasyon veya katalog verisi değişikliği içermez.
