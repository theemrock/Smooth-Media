# Smooth Media — P0 Crash Investigation

Tarih: 2026-10-17

## Sonuç

**Düzeltildi ve cihazda yeniden doğrulandı.** Crash kökü Room v5→v6 migration şema doğrulamasıydı.

## Kanıtlanan exception

Temiz logcat oturumu ve kontrollü yeniden başlatma sonrasında gerçek stack trace:

```text
java.lang.IllegalStateException: Migration didn't properly handle:
saf_documents(com.theemrock.smoothmedia.catalog.database.entity.SafDocumentEntity)
```

İlk şema farkı `uri` kolonunun migration tarafında `DEFAULT ''` ile eklenip entity sözleşmesinde belirtilmemesiydi. Entity default tanımından sonra ikinci fark görünür oldu: migration iki index oluşturuyor, entity ise bunları bildirmiyordu.

## Kök neden ve düzeltme

`SafDocumentEntity` üzerinde:

- `uri` için `@ColumnInfo(defaultValue = "''")`
- `sourceId + parentDocumentId` index'i
- `sourceId + uri` index'i

tanımlandı. Migration değiştirilmedi, destructive fallback eklenmedi ve mevcut database verisi silinmedi.

## Doğrulama

- `testDebugUnitTest`: **185 PASS / 0 FAIL / 0 SKIP**
- `compileDebugAndroidTestKotlin`: **PASS**
- `assembleDebug`: **PASS**
- `git diff --check`: **PASS**
- `adb install -r`: **Success**
- `am start -W -n com.theemrock.smoothmedia/.MainActivity`: **Status: ok**
- Uygulama process'i 8 saniye sonra canlı ve `MainActivity` foreground olarak gözlendi.
- Son logcat taramasında `FATAL EXCEPTION`, Room migration validation veya `AndroidRuntime` uygulama crash kaydı yoktu.

## Ekran smoke kontrolü

- Dashboard: AÇILDI; gerçek katalog sayıları ve MiniPlayer görüldü.
- Songs: AÇILDI; `682 şarkı` ve gerçek şarkı satırları görüldü.
- Albums: AÇILDI; `46 koleksiyon` ve albüm satırları görüldü.
- Folders: AÇILDI; `Folders`, `SAF kaynağı` ve gerçek içerik sayısı görüldü.

Bu kontroller UI dump ve foreground activity kanıtıyla yapıldı; connected test çalıştırılmadı.

## Veri güvenliği

- `firstInstallTime` korunmuştur; yalnızca `lastUpdateTime` değişmiştir.
- uninstall, `pm clear`, rescan, SAF izin değişikliği veya katalog silme yapılmadı.
- Kullanıcı veritabanı salt-okunur gözlem dışında değiştirilmedi.

## Değişen dosyalar

- `app/src/main/java/com/theemrock/smoothmedia/catalog/database/entity/SafDocumentEntity.kt`
- Önceki Sprint 04 migration/entity/DAO/repository değişiklikleri korunmuştur.
- Bu rapor dosyası eklendi.

Git işlemleri yapılmadı: commit, tag ve push yok. Çalışma ağacı önceki kullanıcı değişiklikleriyle birlikte korunmaktadır.
