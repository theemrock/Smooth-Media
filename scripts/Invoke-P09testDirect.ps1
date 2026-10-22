[CmdletBinding()]
param(
    [string]$DeviceSerial = "",
    [int]$UserId = 0,
    [int]$ExpectedVersionCode = 1012,
    [string]$ExpectedVersionName = "v0.0.01.012",
    [int]$ExpectedCatalogCount = 7036,
    [string]$TestClass = "com.theemrock.smoothmedia.p09test.DeterministicPlaybackVerificationTest",
    [string]$PreflightClass = "com.theemrock.smoothmedia.p09test.P09testCatalogPreflightTest"
)

Set-StrictMode -Version 5.1
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    Stop-FailClosed "DeviceSerial zorunludur; cihaz kimliği betiğe gömülmez."
}

$ProductionPackage = "com.theemrock.smoothmedia"
$TestPackage = "com.theemrock.smoothmedia.p09test"
$InstrumentationPackage = "$TestPackage.test"
$RunnerClass = "androidx.test.runner.AndroidJUnitRunner"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AdbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$AaptPath = $null
$AppApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\p09test\app-p09test.apk"
$TestApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\androidTest\p09test\app-p09test-androidTest.apk"

function Stop-FailClosed([string]$Message) {
    throw "P09TEST FAIL-CLOSED STOP: $Message"
}

function Invoke-AdbRead([string[]]$AdbArguments) {
    $output = & $AdbPath @AdbArguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Stop-FailClosed ("adb {0} başarısız oldu (exit {1}): {2}" -f ($AdbArguments -join " "), $exitCode, ($output -join " "))
    }
    return ($output -join [Environment]::NewLine)
}

function Assert-ExactPackage([string]$PackageName) {
    $packages = Invoke-AdbRead @("-s", $DeviceSerial, "shell", "pm", "list", "packages", "--user", $UserId)
    if ($packages -notmatch "(?m)^package:$([regex]::Escape($PackageName))$") {
        Stop-FailClosed "User $UserId içinde paket yok: $PackageName. Kurulum veya katalog oluşturma bu betiğin kapsamı dışındadır."
    }
}

function Read-InstalledPackageDump([string]$PackageName) {
    return Invoke-AdbRead @("-s", $DeviceSerial, "shell", "dumpsys", "package", $PackageName)
}

function Assert-Version([string]$PackageName) {
    $dump = Read-InstalledPackageDump $PackageName
    $versionMatch = [regex]::Match($dump, "versionCode=(\d+).*?versionName=([^\s\r\n]+)")
    if (-not $versionMatch.Success) {
        Stop-FailClosed "Paket sürümü okunamadı: $PackageName"
    }
    if ([int]$versionMatch.Groups[1].Value -ne $ExpectedVersionCode -or $versionMatch.Groups[2].Value -ne $ExpectedVersionName) {
        Stop-FailClosed ("Beklenmeyen sürüm: {0} versionCode={1}, versionName={2}; beklenen {3}/{4}" -f $PackageName, $versionMatch.Groups[1].Value, $versionMatch.Groups[2].Value, $ExpectedVersionCode, $ExpectedVersionName)
    }
}

function Assert-InstrumentationComponent {
    $instrumentations = Invoke-AdbRead @("-s", $DeviceSerial, "shell", "pm", "list", "instrumentation")
    $expected = "instrumentation:{0}/{1} (target={2})" -f $InstrumentationPackage, $RunnerClass, $TestPackage
    if ($instrumentations -notmatch [regex]::Escape($expected)) {
        Stop-FailClosed "Beklenen instrumentation bileşeni yok: $expected"
    }
}

function Find-Aapt {
    $buildTools = Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools"
    if (-not (Test-Path -LiteralPath $buildTools)) {
        Stop-FailClosed "Android SDK build-tools bulunamadı; APK metadata doğrulanamıyor."
    }
    $candidate = Get-ChildItem -LiteralPath $buildTools -Filter "aapt.exe" -File -Recurse | Sort-Object FullName -Descending | Select-Object -First 1
    if ($null -eq $candidate) {
        Stop-FailClosed "aapt.exe bulunamadı; APK metadata doğrulanamıyor."
    }
    return $candidate.FullName
}

function Assert-ApkMetadata([string]$ApkPath, [string]$ExpectedPackage, [switch]$CheckVersion) {
    if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
        Stop-FailClosed "APK bulunamadı: $ApkPath. Betik APK üretmez."
    }
    $badging = & $AaptPath dump badging $ApkPath 2>&1
    if ($LASTEXITCODE -ne 0) {
        Stop-FailClosed "APK metadata okunamadı: $ApkPath"
    }
    $packageMatch = [regex]::Match(($badging -join "`n"), "package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'")
    if (-not $packageMatch.Success -or $packageMatch.Groups[1].Value -ne $ExpectedPackage) {
        Stop-FailClosed "APK package identity beklenmiyor: $ApkPath"
    }
    if ($CheckVersion -and ([int]$packageMatch.Groups[2].Value -ne $ExpectedVersionCode -or $packageMatch.Groups[3].Value -ne $ExpectedVersionName)) {
        Stop-FailClosed "APK sürümü beklenmiyor: $ApkPath"
    }
}

function Invoke-Instrumentation([string]$ClassName) {
    $component = "$InstrumentationPackage/$RunnerClass"
    $args = @("-s", $DeviceSerial, "shell", "am", "instrument", "-w", "--user", $UserId, "-e", "class", $ClassName, $component)
    $output = & $AdbPath @args 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Stop-FailClosed ("Instrumentation başarısız oldu: $ClassName; exit $exitCode; $($output -join ' ')")
    }
    return ($output -join [Environment]::NewLine)
}

if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    Stop-FailClosed "adb.exe bulunamadı: $AdbPath"
}

$deviceState = Invoke-AdbRead @("-s", $DeviceSerial, "get-state")
if ($deviceState.Trim() -ne "device") {
    Stop-FailClosed "Cihaz yetkili/bağlı değil: $DeviceSerial ($($deviceState.Trim()))"
}

$currentUser = Invoke-AdbRead @("-s", $DeviceSerial, "shell", "cmd", "activity", "get-current-user")
if ([int]$currentUser.Trim() -ne $UserId) {
    Stop-FailClosed "Aktif Android kullanıcısı $($currentUser.Trim()); yalnızca User $UserId hedeflenebilir."
}

$AaptPath = Find-Aapt
Assert-ApkMetadata $AppApkPath $TestPackage -CheckVersion
Assert-ApkMetadata $TestApkPath $InstrumentationPackage

Assert-ExactPackage $ProductionPackage
Assert-ExactPackage $TestPackage
Assert-ExactPackage $InstrumentationPackage
Assert-Version $ProductionPackage
Assert-Version $TestPackage
Assert-InstrumentationComponent

$preflightOutput = Invoke-Instrumentation $PreflightClass
$marker = [regex]::Match($preflightOutput, "P09TEST_PREFLIGHT\|catalogActive=(\d+)\|expected=(\d+)\|PASS")
if (-not $marker.Success) {
    Stop-FailClosed "Room katalog preflight marker'ı alınamadı; regresyon testi çalıştırılmadı."
}
if ([int]$marker.Groups[1].Value -ne $ExpectedCatalogCount -or [int]$marker.Groups[2].Value -ne $ExpectedCatalogCount) {
    Stop-FailClosed "Room katalog sayısı beklenenden farklı: $($marker.Groups[1].Value); beklenen $ExpectedCatalogCount."
}

$testOutput = Invoke-Instrumentation $TestClass
Write-Output $testOutput
