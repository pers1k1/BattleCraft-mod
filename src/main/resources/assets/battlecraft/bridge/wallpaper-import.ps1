param(
    [string]$Root = '',
    [int]$ParentPid = 0,
    [string]$Source = '',
    [string]$Folder = '',
    [int]$MaxFps = 30,
    [int]$MaxSeconds = 20
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

if ([string]::IsNullOrEmpty($Root)) { $Root = Split-Path -Parent $MyInvocation.MyCommand.Path }
$native = Join-Path $Root 'wallpaper-native.cs'
$library = Join-Path $Root 'wallpaper-native.dll'

function Build-Library {
    $framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
    if (-not (Test-Path -LiteralPath (Join-Path $framework 'csc.exe'))) {
        $framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319'
    }
    $compiler = Join-Path $framework 'csc.exe'
    $arguments = @('/nologo', '/target:library', '/optimize+', "/out:$library", '/reference:System.dll', $native)
    & $compiler $arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "csc exit $LASTEXITCODE" }
}

try {
    if (-not (Test-Path -LiteralPath $library) -or (Get-Item -LiteralPath $native).LastWriteTimeUtc -gt (Get-Item -LiteralPath $library).LastWriteTimeUtc) {
        Build-Library
    }
    Add-Type -LiteralPath $library
} catch {
    [Console]::Out.WriteLine('{"error":"bridge"}')
    [Console]::Out.Flush()
    exit 2
}

[BattleCraftFrames]::Extract($Source, $Folder, $MaxFps, $MaxSeconds, $ParentPid)
