[CmdletBinding()]
param(
    [string]$SourceDir = (Join-Path $PSScriptRoot '..\source'),
    [string]$BuildDir = (Join-Path $PSScriptRoot '..\build'),

    [string]$AndroidSdkRoot = $env:ANDROID_HOME ?? $env:ANDROID_SDK_ROOT,
    [string]$NdkVersion = '26.1.10909125',
    [string[]]$AbiList = @('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'),
    [int]$AndroidApiLevel = 27,
    [Parameter(Mandatory)]
    [string]$Msys2Root,
    [string]$MsysEnvironment = 'MINGW64',
    [string]$HostCc,
    [int]$Jobs = [Environment]::ProcessorCount
)

$ErrorActionPreference = 'Stop'

$bashPath = Join-Path $Msys2Root 'usr\bin\bash.exe'
if (-not (Test-Path $bashPath)) {
    throw "msys2 bash not found: $bashPath (pass -Msys2Root)."
}

if (-not $AndroidSdkRoot) {
    throw "Android SDK root not set; pass -AndroidSdkRoot or set ANDROID_HOME."
}

$ndkRoot = Join-Path $AndroidSdkRoot "ndk\$NdkVersion"
if (-not (Test-Path $ndkRoot)) {
    throw "NDK not found: $ndkRoot"
}

if (-not (Test-Path (Join-Path $SourceDir 'configure'))) {
    throw "FFmpeg source not found in $SourceDir; run scripts/fetch.ps1 first."
}

if (-not $HostCc) {
    foreach ($name in @('gcc', 'cc', 'clang')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) {
            $HostCc = $command.Source
            break
        }
    }
}
if ($HostCc -and -not (Test-Path $HostCc)) {
    throw "Host C compiler not found: $HostCc"
}

$buildScriptPath = (Join-Path $PSScriptRoot 'build.sh').Replace('\', '/')
$bashCommand =
"set -euo pipefail; " +
"FFMPEG_SOURCE_DIR='$($SourceDir.Replace('\', '/'))' " +
"FFMPEG_BUILD_DIR='$($BuildDir.Replace('\', '/'))' " +
"ANDROID_NDK_ROOT='$($ndkRoot.Replace('\', '/'))' " +
"ANDROID_ABI_LIST='$($AbiList -join ' ')' " +
"ANDROID_API_LEVEL='$AndroidApiLevel' " +
"HOST_CC='$(if ($HostCc) { $HostCc.Replace('\', '/') })' " +
"JOBS='$Jobs' " +
"bash '$buildScriptPath'"

$env:MSYSTEM = $MsysEnvironment
& $bashPath -l -c $bashCommand
if ($LASTEXITCODE -ne 0) {
    throw "build.sh failed with exit code $LASTEXITCODE."
}
