[CmdletBinding()]
param(
    [string]$SourceDir = (Join-Path $PSScriptRoot '..\source'),
    
    [ValidatePattern('^\d+\.\d+(\.\d+)?$')]
    [string]$Version = '9.0.1',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$releaseFile = Join-Path $SourceDir 'RELEASE'
if (-not $Force -and (Test-Path $releaseFile)) {
    $existingVersion = (Get-Content $releaseFile -Raw).Trim()
    if ($existingVersion -eq $Version) {
        Write-Host "FFmpeg $Version already present at $SourceDir; skipping download."
        return
    }
}

$url = "https://ffmpeg.org/releases/ffmpeg-$Version.tar.xz"
$archivePath = Join-Path ([System.IO.Path]::GetTempPath()) `
    "ffmpeg-$Version-$([guid]::NewGuid().ToString('N').Substring(0, 8)).tar.xz"

try {
    Write-Host "Downloading $url ..."
    Invoke-WebRequest -Uri $url -OutFile $archivePath

    Write-Host "Extracting to $SourceDir ..."
    if (Test-Path $SourceDir) {
        Remove-Item $SourceDir -Recurse -Force
    }
    $null = New-Item -ItemType Directory -Path $SourceDir
    tar --strip-components=1 -xf $archivePath -C $SourceDir
    if ($LASTEXITCODE -ne 0) {
        throw "tar extraction failed with exit code $LASTEXITCODE."
    }

    Write-Host "FFmpeg $Version ready at $SourceDir."
}
finally {
    Remove-Item $archivePath -Force -ErrorAction SilentlyContinue
}
