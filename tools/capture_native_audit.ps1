param(
    [string]$Name = 'welcome-light',
    [string[]]$Extras = @('--ez', 'empty', 'true'),
    [string]$Serial = 'emulator-5580',
    [switch]$Current
)

$ErrorActionPreference = 'Stop'
$auditAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (!(Test-Path $auditAdb)) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if (!$adbCommand) { throw 'adb not found; set ANDROID_SDK_ROOT or add platform-tools to PATH.' }
    $auditAdb = $adbCommand.Source
}
$auditRoot = Join-Path $PSScriptRoot '..\app\build\visual-checks\native-review'
$auditRoot = [IO.Path]::GetFullPath($auditRoot)
New-Item -ItemType Directory -Path $auditRoot -Force | Out-Null
if ($Name -notmatch '^[a-z0-9-]+$') { throw 'Use a simple audit scenario name.' }

$captureId = [Guid]::NewGuid().ToString('N')
$remoteScreen = "/data/local/tmp/xuanji-audit-$captureId.png"
$remoteElements = "/data/local/tmp/xuanji-audit-$captureId.xml"
if (!$Current) {
    & $auditAdb -s $Serial shell am start -S -n 'com.xuanji.android/.ui.chat.UiAuditActivity' @Extras
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the offline audit activity.' }
}
# uiautomator may exit 0 after failing; a unique path prevents stale evidence.
$snapshotReady = $false
for ($attempt = 0; $attempt -lt 3; $attempt++) {
    $snapshotOutput = & $auditAdb -s $Serial shell uiautomator dump --compressed $remoteElements 2>&1
    if ($LASTEXITCODE -eq 0 -and ($snapshotOutput -join "`n") -match [regex]::Escape("dumped to: $remoteElements")) {
        $snapshotText = & $auditAdb -s $Serial shell cat $remoteElements
        if ($LASTEXITCODE -eq 0) {
            [xml]$elements = $snapshotText -join "`n"
            $windowState = & $auditAdb -s $Serial shell dumpsys window
            $focusedApp = $windowState -match 'mFocusedApp=.*com\.xuanji\.android'
            $systemError = $windowState -match 'mCurrentFocus=.*Application Not Responding'
            if ($LASTEXITCODE -eq 0 -and $focusedApp -and !$systemError -and
                $elements.SelectSingleNode('//node[@package="com.xuanji.android"]') -and
                !$elements.SelectSingleNode('//node[@package!="com.xuanji.android"]')) {
                $snapshotReady = $true
                break
            }
        }
    }
    Start-Sleep -Milliseconds 1000
}
if (!$snapshotReady) { throw "Accessibility snapshot failed: $snapshotOutput" }
$localElements = Join-Path $auditRoot "$Name-$captureId.xml"
& $auditAdb -s $Serial pull $remoteElements $localElements
if ($LASTEXITCODE -ne 0) { throw 'Accessibility snapshot pull failed.' }
& $auditAdb -s $Serial shell screencap -p $remoteScreen
if ($LASTEXITCODE -ne 0) { throw 'Screenshot capture failed.' }
& $auditAdb -s $Serial pull $remoteScreen (Join-Path $auditRoot "$Name-$captureId.png")
if ($LASTEXITCODE -ne 0) { throw 'Screenshot pull failed.' }
Write-Output (Join-Path $auditRoot "$Name-$captureId.png")
