# Smooth Media — Known Issues

## Album grouping — OPEN / DEVICE FAIL

- Derleme albümleri, farklı `Track Artist` değerleri nedeniyle `Albums` ekranında birden fazla albüm olarak görünebiliyor.
- Genel albüm gruplama düzeltmesi uygulanmış olsa da sorun gerçek cihazda devam ediyor.
- `164/164` JVM testi PASS olmasına rağmen cihaz doğrulaması FAIL.
- Olası nedenler: eksik `AlbumArtist`, `sourceId`/`folderPrefix` ayrımı, mevcut katalog verileri veya aynı track için birden fazla fiziksel konum.
- Bu sorun çözülmüş veya PASS olarak işaretlenmemelidir.
- Sonraki adım: gerçek katalog üzerinde salt-okunur teşhis, ardından kanıtlanmış kök nedene yönelik düzeltme.
