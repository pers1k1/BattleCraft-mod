param(
    [string]$Root = '',
    [int]$ParentPid = 0
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

if ([string]::IsNullOrEmpty($Root)) { $Root = Split-Path -Parent $MyInvocation.MyCommand.Path }
$source = Join-Path $Root 'media-native.cs'
$fingerprint = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.Substring(0, 12).ToLowerInvariant()
$library = Join-Path $Root ('media-native-' + $fingerprint + '.dll')
$artPath = Join-Path $Root 'media-art.png'

function Build-Library {
    $framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
    if (-not (Test-Path -LiteralPath (Join-Path $framework 'csc.exe'))) {
        $framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319'
    }
    $compiler = Join-Path $framework 'csc.exe'
    $metadata = Join-Path $env:WINDIR 'System32\WinMetadata'
    $staging = $library + '.build'

    $arguments = @(
        '/nologo', '/target:library', "/out:$staging",
        ('/reference:' + (Join-Path $framework 'System.Runtime.dll')),
        ('/reference:' + (Join-Path $metadata 'Windows.Foundation.winmd')),
        ('/reference:' + (Join-Path $metadata 'Windows.Media.winmd')),
        ('/reference:' + (Join-Path $metadata 'Windows.Storage.winmd')),
        '/reference:System.Core.dll', '/reference:System.dll', '/reference:System.Drawing.dll', $source
    )
    & $compiler $arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "csc exit $LASTEXITCODE" }
    Move-Item -LiteralPath $staging -Destination $library -Force
}

function Remove-StaleLibraries {
    Get-ChildItem -LiteralPath $Root -Filter 'media-native*.dll' |
        Where-Object { $_.FullName -ne $library } |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -ErrorAction SilentlyContinue }
}

if (-not (Test-Path -LiteralPath $library)) {
    Build-Library
}
try { Remove-StaleLibraries } catch { }

try {
    Add-Type -LiteralPath $library
} catch {
    Remove-Item -LiteralPath $library -ErrorAction SilentlyContinue
    throw
}
[BattleCraftMedia]::Init()
[BattleCraftRemote]::Listen()

$parent = $null
if ($ParentPid -ne 0) {
    try { $parent = [Diagnostics.Process]::GetProcessById($ParentPid) } catch { exit }
}

$failures = 0
$tick = 0
$app = ''

while ($true) {
    if ($null -ne $parent -and $parent.HasExited) { break }

    try {
        if ([BattleCraftRemote]::Obey()) { $tick = [Math]::Min($tick, 6) }
        if ($tick -le 0) {
            $state = [BattleCraftMedia]::Poll($artPath)
            $tick = if ([BattleCraftMedia]::Settling()) { 6 } else { 32 }
            [Console]::Out.WriteLine($state)
            $match = [regex]::Match($state, '"app":"([^"]*)"')
            $app = if ($match.Success) { $match.Groups[1].Value } else { '' }
        } else {
            $tick--
            if (-not [BattleCraftSpectrum]::Follow($ParentPid, $app)) {
                [Console]::Out.WriteLine([BattleCraftLevel]::Poll($app))
            }
        }
        [Console]::Out.Flush()
        $failures = 0
    } catch {
        [Console]::Out.WriteLine('{"ok":false}')
        [Console]::Out.Flush()
        $failures++
        $tick = 0
        if ($failures -ge 4) {
            $failures = 0
            try { [BattleCraftMedia]::Init() } catch { }
        }
    }

    [Threading.Thread]::Sleep(15)
}
