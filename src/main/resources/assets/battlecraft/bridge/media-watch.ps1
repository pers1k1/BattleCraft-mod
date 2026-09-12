param(
    [string]$Root = '',
    [int]$ParentPid = 0
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

if ([string]::IsNullOrEmpty($Root)) { $Root = Split-Path -Parent $MyInvocation.MyCommand.Path }
$source = Join-Path $Root 'media-native.cs'
$library = Join-Path $Root 'media-native.dll'
$artPath = Join-Path $Root 'media-art.png'

function Build-Library {
    $framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
    if (-not (Test-Path (Join-Path $framework 'csc.exe'))) {
        $framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319'
    }
    $compiler = Join-Path $framework 'csc.exe'
    $metadata = Join-Path $env:WINDIR 'System32\WinMetadata'

    $arguments = @(
        '/nologo', '/target:library', "/out:$library",
        ('/reference:' + (Join-Path $framework 'System.Runtime.dll')),
        ('/reference:' + (Join-Path $metadata 'Windows.Foundation.winmd')),
        ('/reference:' + (Join-Path $metadata 'Windows.Media.winmd')),
        ('/reference:' + (Join-Path $metadata 'Windows.Storage.winmd')),
        '/reference:System.Core.dll', '/reference:System.dll', $source
    )
    & $compiler $arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "csc exit $LASTEXITCODE" }
}

if (-not (Test-Path $library) -or (Get-Item $source).LastWriteTimeUtc -gt (Get-Item $library).LastWriteTimeUtc) {
    Build-Library
}

Add-Type -Path $library
[BattleCraftMedia]::Init()

$failures = 0
$tick = 0
$app = ''

while ($true) {
    if ($ParentPid -ne 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { break }

    try {
        if ($tick -le 0) {
            $tick = 32
            $state = [BattleCraftMedia]::Poll($artPath)
            Write-Output $state
            $match = [regex]::Match($state, '"app":"([^"]*)"')
            $app = if ($match.Success) { $match.Groups[1].Value } else { '' }
        } else {
            $tick--
            $bands = [BattleCraftSpectrum]::Poll($ParentPid, $app)
            if ($bands) { Write-Output $bands } else { Write-Output ([BattleCraftLevel]::Poll($app)) }
        }
        [Console]::Out.Flush()
        $failures = 0
    } catch {
        Write-Output '{"ok":false}'
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
