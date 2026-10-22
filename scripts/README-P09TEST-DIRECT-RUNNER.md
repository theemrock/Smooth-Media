# P09TEST doğrudan cihaz akışı

`Invoke-P09testDirect.ps1`, Gradle `connectedP09testAndroidTest` görevini kullanmaz. Mevcut APK’ları ve cihaz paket durumunu yalnızca okur; ardından AndroidJUnitRunner’ı doğrudan `adb shell am instrument -w --user 0` ile çağırır.

## Güvenlik davranışı

- APK kurmaz, yeniden kurmaz veya kaldırmaz.
- `pm clear`, veri temizleme, rescan, SAF izin değişikliği ve ses komutu içermez.
- Production paketi test hedefi olarak kullanılmaz; yalnızca paket/sürüm koruması için okunur.
- P09TEST veya test APK’sı cihazda yoksa fail-closed STOP verir.
- Room katalog sayısı doğrudan ADB ile okunmaz. Önce p09test’e özel `P09testCatalogPreflightTest` salt-okunur instrumentation’ı çalışır.
- Preflight çıktısı tam olarak 7.036 aktif kayıt bildirmiyorsa regresyon instrumentation’ı başlatılmaz.
- Preflight ve regresyon sonrasında cleanup çağrısı yoktur.

## Kullanım

Bu betik APK üretmez. APK’ların daha önce kullanıcı tarafından güvenli biçimde kurulmuş olması gerekir:

```powershell
Set-Location C:\Projects\SmoothMedia-ghost-context
& .\scripts\Invoke-P09testDirect.ps1 -DeviceSerial (Read-Host "Device serial")
```

Mevcut cihazda P09TEST bulunmadığı için beklenen sonuç fail-closed STOP’tur. Kurulum veya katalog yeniden oluşturma bu akışın parçası değildir ve ayrıca açık onay gerektirir.

## Kurulum riski

İleride APK kurulumu ayrıca onaylanırsa package identity, imza, versionCode ve mevcut uygulama verisinin korunacağı doğrulanmadan işlem yapılmamalıdır. `install -r` dahi veri/SAF durumunu garanti eden bir yedek değildir; `uninstall`, `pm clear` ve yeni imza kullanımı özellikle reddedilmelidir.

## Statik kontrol

Cihazda çalıştırmadan betik kaynak korumalarını denetlemek için:

```powershell
& .\scripts\Test-P09testDirectRunner.ps1
```

Bu komut cihazla iletişim kurmaz; bu turda çalıştırılmamıştır.
