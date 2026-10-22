<#
Build and inspect a prepared upstream sync without publishing its outputs.
Prefer the complete source build. The public release overlay is permitted only
when prepare proved that upstream main is exactly the official release commit.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PlanPath,
    [string]$GradleHome,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }),
    [string]$PublicAssetCache,
    # Explicitly exercise the public build on machines without registry access.
    # This never bypasses the exact release/main commit parity check.
    [switch]$ReleaseOverlay
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$plan = Get-Content -LiteralPath $PlanPath -Raw | ConvertFrom-Json
if ($plan.changed -isnot [bool] -or -not $plan.changed) { throw 'The sync plan has no prepared update to build.' }
if ($plan.version -notmatch '^\d+\.\d+\.\d+-mod\.[1-9]\d*$') { throw 'Invalid planned mod version.' }
if ($plan.upstream_sha -notmatch '^[0-9a-f]{40}$') { throw 'Invalid planned upstream commit.' }
if (-not $JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome.' }
if (-not $AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk.' }
$JdkHome = (Resolve-Path -LiteralPath $JdkHome).Path
$AndroidSdk = (Resolve-Path -LiteralPath $AndroidSdk).Path
$isWindowsHost = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$executableSuffix = if ($isWindowsHost) { '.exe' } else { '' }
$java = Join-Path $JdkHome "bin/java$executableSuffix"
$javac = Join-Path $JdkHome "bin/javac$executableSuffix"
foreach ($required in @($java, $javac, (Join-Path $AndroidSdk 'platforms/android-36/android.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing prerequisite: $required" }
}
$temporaryRoot = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { [IO.Path]::GetTempPath() }
$outputRoot = [IO.Path]::GetFullPath((Join-Path $temporaryRoot ('morphe-upstream-build-' + [guid]::NewGuid())))
$repoPrefix = $repo.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if ($outputRoot.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase) -or $outputRoot -eq $repo) {
    throw 'Build outputs must be outside the Git checkout. Set RUNNER_TEMP to a temporary directory.'
}
New-Item -ItemType Directory -Path $outputRoot | Out-Null
$bundlePath = Join-Path $outputRoot "patches-$($plan.version).mpp"
$catalogPath = Join-Path $outputRoot 'patches-list.json'
$resultPath = Join-Path $outputRoot 'build-result.json'
$wrapper = Join-Path $repo $(if ($isWindowsHost) { 'gradlew.bat' } else { 'gradlew' })

function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable exited with $LASTEXITCODE" }
}
function Assert-ReleaseParity {
    if ($plan.release_parity -isnot [bool] -or -not $plan.release_parity -or
        -not $plan.release -or $plan.release.commit -ne $plan.upstream_sha -or
        $plan.release.version -ne $plan.upstream_version) {
        throw 'The public overlay cannot represent this upstream main commit. A successful full source build is required; no bundle will be published.'
    }
    $pin = Get-Content -LiteralPath (Join-Path $repo 'tools/duolingo-reminders/upstream.json') -Raw | ConvertFrom-Json
    foreach ($field in @('version', 'download_url', 'sha256')) {
        if ($pin.$field -ne $plan.release.$field) { throw "The pinned public bundle differs from the prepared release: $field" }
    }
}
if ($ReleaseOverlay) { Assert-ReleaseParity }

$previousJavaHome = $env:JAVA_HOME
$previousAndroidHome = $env:ANDROID_HOME
$previousAndroidSdkRoot = $env:ANDROID_SDK_ROOT
try {
    $env:JAVA_HOME = $JdkHome
    $env:ANDROID_HOME = $AndroidSdk
    $env:ANDROID_SDK_ROOT = $AndroidSdk
    Push-Location $repo
    try {
        if (-not $GradleHome) {
            # --version downloads only the wrapper distribution, without resolving
            # the private build plugin. Both build paths need its public compiler.
            Invoke-Checked $wrapper @('--version', '--no-daemon')
            $gradleUserHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle' }
            $distributionRoot = Join-Path $gradleUserHome 'wrapper/dists/gradle-9.7.1-bin'
            $distributions = @(Get-ChildItem -LiteralPath $distributionRoot -Directory | ForEach-Object {
                $candidate = Join-Path $_.FullName 'gradle-9.7.1'
                if (Test-Path -LiteralPath (Join-Path $candidate 'lib/kotlin-compiler-embeddable-2.4.0.jar')) { $candidate }
            })
            if ($distributions.Count -eq 0) { throw 'Gradle wrapper did not provide the expected Gradle 9.7.1 compiler.' }
            $GradleHome = $distributions[0]
        }
        $GradleHome = (Resolve-Path -LiteralPath $GradleHome).Path
        $fullSourceSucceeded = $false
        if (-not $ReleaseOverlay) {
            try {
                # GITHUB_TOKEN/GITHUB_ACTOR are inherited from the Actions job;
                # never persist registry credentials into the repository.
                Invoke-Checked $wrapper @('codegen', ':patches:buildAndroid', '--no-daemon')
                $sourceBundle = Join-Path $repo "patches/build/libs/patches-$($plan.version).mpp"
                if (-not (Test-Path -LiteralPath $sourceBundle)) { throw "Full source build did not produce $sourceBundle" }
                Copy-Item -LiteralPath $sourceBundle -Destination $bundlePath
                $exportArguments = @{
                    JdkHome = $JdkHome; GradleHome = $GradleHome; BundlePath = $bundlePath
                    OutputPath = $catalogPath; ExpectedVersion = $plan.version
                }
                if ($PublicAssetCache) {
                    $cachedDesktop = Join-Path $PublicAssetCache 'morphe-desktop-1.17.0-all.jar'
                    if (Test-Path -LiteralPath $cachedDesktop) { $exportArguments.DesktopJar = $cachedDesktop }
                }
                & (Join-Path $repo 'tools/duolingo-reminders/Export-Catalog.ps1') @exportArguments
                $fullSourceSucceeded = $true
            }
            catch {
                Write-Warning "Full source build/catalog export failed: $($_.Exception.Message)"
            }
        }
        if ($fullSourceSucceeded) {
            $buildMode = 'full-source'
        } else {
            Assert-ReleaseParity
            Write-Output 'Building the verified official-release overlay for this exact upstream commit.'
            $buildArguments = @{
                JdkHome = $JdkHome; AndroidSdk = $AndroidSdk; GradleHome = $GradleHome
                BundleVersion = $plan.version; OutputPath = $bundlePath; CatalogPath = $catalogPath
            }
            if ($PublicAssetCache) {
                $cachedAssets = @{
                    DesktopJar = 'morphe-desktop-1.17.0-all.jar'
                    PatchesLibraryJar = 'morphe-patches-library-1.8.0-dev.1.jar'
                    R8Jar = 'r8-9.4.34.jar'
                    UpstreamBundle = "patches-$($plan.upstream_version).mpp"
                }
                foreach ($key in $cachedAssets.Keys) {
                    $candidate = Join-Path $PublicAssetCache $cachedAssets[$key]
                    if (Test-Path -LiteralPath $candidate) { $buildArguments[$key] = $candidate }
                }
            }
            & (Join-Path $repo 'tools/duolingo-reminders/Build.ps1') @buildArguments
            $buildMode = 'release-overlay'
        }
        # Run the dependency-free behavioral checks in both build modes.
        $testClasses = Join-Path $outputRoot 'coordinator-tests'
        New-Item -ItemType Directory -Path $testClasses | Out-Null
        Invoke-Checked $javac @('--release', '17', '-d', $testClasses,
            (Join-Path $repo 'extensions/duolingo/src/main/java/hoodles/morphe/extension/duolingo/FallbackCoordinator.java'),
            (Join-Path $repo 'extensions/duolingo/src/test/java/hoodles/morphe/extension/duolingo/FallbackCoordinatorTest.java'))
        Invoke-Checked $java @('-cp', $testClasses, 'hoodles.morphe.extension.duolingo.FallbackCoordinatorTest')
        if (-not (Test-Path -LiteralPath $bundlePath) -or -not (Test-Path -LiteralPath $catalogPath)) {
            throw 'Bundle or catalog output is missing.'
        }
        $result = [ordered]@{ bundle_path = $bundlePath; catalog_path = $catalogPath; build_mode = $buildMode }
        $result | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding utf8
        if ($env:GITHUB_OUTPUT) {
            foreach ($entry in $result.GetEnumerator()) {
                "$($entry.Key)=$($entry.Value)" | Add-Content -LiteralPath $env:GITHUB_OUTPUT -Encoding utf8
            }
            "result_path=$resultPath" | Add-Content -LiteralPath $env:GITHUB_OUTPUT -Encoding utf8
        }
        Write-Output "Build result: $resultPath"
    }
    finally { Pop-Location }
}
finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:ANDROID_HOME = $previousAndroidHome
    $env:ANDROID_SDK_ROOT = $previousAndroidSdkRoot
}
