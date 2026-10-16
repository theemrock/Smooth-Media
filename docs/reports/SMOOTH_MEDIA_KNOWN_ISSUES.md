# Smooth Media — Known Issues

## Album grouping — RESOLVED / DEVICE PASS

- Albüm kimliği, Room sorgularındaki `album + albumArtist + sourceId + folderPrefix` anahtarıyla tutarlı encode/decode edilir.
- Aynı kaynak ve klasör kapsamındaki derleme albümleri, farklı `Track Artist` değerlerine rağmen tek albüm altında gruplanır; parça sanatçıları korunur.
- Farklı kaynak veya klasör kapsamlarındaki aynı adlı albümler ayrı tutulur.
- Album Detail, Albums listesiyle aynı kanonik kimliği kullanır; Metallica / Load cihazda 14 şarkı ve 78:57, Blues Brothers 2000 cihazda 18 şarkı ve 61:16 olarak doğrulandı.
- İlgili JVM regresyon testleri `168/168 PASS` olarak raporlandı; cihaz doğrulamasında farklı parça sanatçıları korundu.
