param(
    [string]$AvdName = 'Pixel_6_API_36',
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk"
)

$ErrorActionPreference = 'Stop'
$emulatorPath = Join-Path $SdkPath 'emulator\emulator.exe'
$adbPath = Join-Path $SdkPath 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $emulatorPath) -or -not (Test-Path -LiteralPath $adbPath)) {
    throw "Android SDK bulunamadı. -SdkPath ile SDK klasörünü belirt."
}

$availableAvds = @(& $emulatorPath -list-avds)
if ($availableAvds -notcontains $AvdName) {
    throw "AVD bulunamadı: $AvdName. Kullanılabilir cihazlar: $($availableAvds -join ', ')"
}

foreach ($deviceLine in (& $adbPath devices)) {
    if ($deviceLine -match '^(emulator-\d+)\s+device$') {
        $deviceId = $Matches[1]
        $runningAvd = @(& $adbPath -s $deviceId emu avd name)
        if ($runningAvd -contains $AvdName) {
            Write-Output "$AvdName zaten çalışıyor ($deviceId). Android Studio > Running Devices bölümünden açabilirsin."
            return
        }
    }
}

# A visible window, software graphics, and no blocking crash/metrics consent prompt.
# Cold boot keeps the existing userdata and gallery; it does not wipe the AVD.
Start-Process -FilePath $emulatorPath -ArgumentList @(
    '-avd', $AvdName, '-gpu', 'swiftshader',
    '-no-snapshot-load', '-no-snapshot-save',
    '-no-metrics', '-crash-report-mode', 'never'
) -WindowStyle Normal
Write-Output "$AvdName görünür pencerede başlatıldı. İlk açılış biraz sürebilir."
