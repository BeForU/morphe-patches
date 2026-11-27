<#
Build the complete current checkout without fetching upstream source or using an
upstream patches bundle. Output is staged in a temporary directory; this script
does not change Manager's published metadata or push anything.
#>
[CmdletBinding()]
param(
    [string]$GradleHome,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }),
    [string]$PublicAssetCache,
    [string]$Java17Home,
    [ValidateRange(1, 128)][int]$MaxWorkers = 4,
    [switch]$PublicSourceDependencies
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$properties = Get-Content -LiteralPath (Join-Path $repo 'gradle.properties') -Raw
$match = [regex]::Match($properties, '(?m)^\s*version\s*=\s*(\S+)\s*$')
if (-not $match.Success -or $match.Groups[1].Value -notmatch '^\d+\.\d+\.\d+-mod\.[1-9]\d*$') {
    throw 'gradle.properties must contain a valid mod bundle version.'
}
$version = $match.Groups[1].Value
$head = (& git -C $repo rev-parse --verify HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') { throw 'Cannot identify the local checkout commit.' }
$temporaryRoot = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { [IO.Path]::GetTempPath() }
$planPath = Join-Path $temporaryRoot ('morphe-local-plan-' + [guid]::NewGuid() + '.json')
# Build-CI validates this source identity but FullSourceOnly never fetches or
# incorporates it. The working tree, including local changes, is compiled.
$plan = [ordered]@{ changed = $true; version = $version; upstream_sha = $head }
$previousPublicSources = $env:MORPHE_PUBLIC_BUILD_SOURCES
$previousActor = $env:GITHUB_ACTOR
$previousToken = $env:GITHUB_TOKEN
try {
    if ($PublicSourceDependencies) {
        # These are toolchain libraries, not the stopped Hoodles patch source.
        # Verify the exact commits instead of trusting movable release tags.
        $sourceRoot = Join-Path $temporaryRoot ('morphe-public-deps-' + [guid]::NewGuid())
        New-Item -ItemType Directory -Path $sourceRoot | Out-Null
        $dependencies = @(
            @{ name = 'gradle-plugin'; repo = 'morphe-patches-gradle-plugin'; tag = 'v1.3.4'; commit = 'a230c33f7eca1e18b07bf1114ca850421520f98d' },
            @{ name = 'patcher'; repo = 'morphe-patcher'; tag = 'v1.14.1'; commit = '6f189f9ffb448ae32ceaf6c136c9d84d9a7ed274' },
            @{ name = 'patches-library'; repo = 'morphe-patches-library'; tag = 'v1.8.0-dev.1'; commit = '30aa492b282cae7f61e88caa8e3099e9952dabbe' }
        )
        foreach ($dependency in $dependencies) {
            $destination = Join-Path $sourceRoot $dependency.name
            & git clone --quiet --depth 1 --branch $dependency.tag "https://github.com/MorpheApp/$($dependency.repo).git" $destination
            if ($LASTEXITCODE -ne 0) { throw "Could not obtain public source $($dependency.repo)." }
            $actual = (& git -C $destination rev-parse --verify HEAD).Trim()
            if ($LASTEXITCODE -ne 0 -or $actual -ne $dependency.commit) { throw "Unexpected commit for $($dependency.repo)." }
        }
        $env:MORPHE_PUBLIC_BUILD_SOURCES = $sourceRoot
        # The official plugin reads these properties eagerly, even though the
        # source substitutions avoid the authenticated Morphe artifacts.
        if (-not $env:GITHUB_ACTOR) { $env:GITHUB_ACTOR = 'anonymous' }
        if (-not $env:GITHUB_TOKEN) { $env:GITHUB_TOKEN = 'anonymous' }
        Write-Output "Pinned public build sources: $sourceRoot"
    }
    $plan | ConvertTo-Json | Set-Content -LiteralPath $planPath -Encoding utf8
    $arguments = @{ PlanPath = $planPath; FullSourceOnly = $true }
    if ($GradleHome) { $arguments.GradleHome = $GradleHome }
    if ($JdkHome) { $arguments.JdkHome = $JdkHome }
    if ($AndroidSdk) { $arguments.AndroidSdk = $AndroidSdk }
    if ($PublicAssetCache) { $arguments.PublicAssetCache = $PublicAssetCache }
    if ($Java17Home) { $arguments.Java17Home = $Java17Home }
    $arguments.MaxWorkers = $MaxWorkers
    & (Join-Path $PSScriptRoot 'Build-CI.ps1') @arguments
}
finally {
    Remove-Item -LiteralPath $planPath -Force -ErrorAction SilentlyContinue
    $env:MORPHE_PUBLIC_BUILD_SOURCES = $previousPublicSources
    $env:GITHUB_ACTOR = $previousActor
    $env:GITHUB_TOKEN = $previousToken
}
