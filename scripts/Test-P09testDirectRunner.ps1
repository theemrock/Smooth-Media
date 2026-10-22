[CmdletBinding()]
param()

Set-StrictMode -Version 5.1
$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "Invoke-P09testDirect.ps1"
$source = Get-Content -Raw -LiteralPath $scriptPath

if ($source -match "connectedP09testAndroidTest") {
    throw "Direct runner betiği connectedP09testAndroidTest içeremez."
}

foreach ($forbidden in @(
    "install",
    "uninstall",
    "pm clear",
    "rescan",
    "scan",
    "volume"
)) {
    if ($source -match "(?im)^\s*(?:&\s*)?(?:\$AdbPath|adb)\b[^\r\n]*\b$([regex]::Escape($forbidden))\b") {
        throw "Yasak cihaz işlemi bulundu: $forbidden"
    }
}

if ($source -notmatch 'am",\s*"instrument"') {
    throw "Doğrudan am instrument çağrısı bulunamadı."
}
if ($source -notmatch "--user") {
    throw "User 0 koruması bulunamadı."
}
if ($source -notmatch "P09TEST_PREFLIGHT") {
    throw "Room preflight marker kontrolü bulunamadı."
}
if ($source -notmatch "Stop-FailClosed") {
    throw "Fail-closed koruması bulunamadı."
}

Write-Output "P09TEST direct runner statik korumaları PASS"
