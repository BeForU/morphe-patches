<#
Build the full Manager bundle without GitHub Packages credentials.
Requires JDK 21, Android SDK platform 36, and a Gradle 9.7.1
distribution (for its Kotlin 2.4.0 compiler). The pinned upstream 1.47.0
bundle supplies unchanged patches, shared libraries and embedded extensions.
All local Duolingo patches and compatibility data replace their upstream
classes before DEX is regenerated, so Manager loads one consistent source.
The normal full-project build remains .\gradlew.bat :patches:buildAndroid.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$JdkHome,
    [Parameter(Mandatory)][string]$AndroidSdk,
    [Parameter(Mandatory)][string]$GradleHome,
    [string]$BundleVersion,
    [string]$OutputPath,
    [string]$DesktopJar,
    [string]$PatchesLibraryJar,
    [string]$R8Jar,
    [string]$UpstreamBundle
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $BundleVersion) {
    $BundleVersion = (Get-Content -LiteralPath (Join-Path $repo 'patches-bundle.json') -Raw | ConvertFrom-Json).version
}
if ($BundleVersion -notmatch '^\d+\.\d+\.\d+([.-][A-Za-z0-9.-]+)?$') {
    throw "Invalid bundle version: $BundleVersion"
}
$compatibilitySource = Get-Content -LiteralPath (Join-Path $repo 'patches/src/main/kotlin/hoodles/morphe/compatibility/Constants.kt') -Raw
$duolingoTarget = [regex]::Match($compatibilitySource, '(?s)val DUOLINGO = Compatibility\(.*?targets = listOf\(AppTarget\("([^"\r\n]+)"\)\)')
if (-not $duolingoTarget.Success) { throw 'Could not identify the Duolingo target version.' }
$appVersion = $duolingoTarget.Groups[1].Value
if (-not $OutputPath) { $OutputPath = Join-Path $repo "dist/patches-$BundleVersion.mpp" }
$work = Join-Path ([IO.Path]::GetTempPath()) ('duolingo-patches-build-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $work | Out-Null
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable exited with $LASTEXITCODE" }
}
function Get-Asset([string]$Existing, [string]$Name, [string]$Url, [string]$Sha256) {
    $target = if ($Existing) { (Resolve-Path -LiteralPath $Existing).Path } else { Join-Path $work $Name }
    if (-not $Existing) { Invoke-WebRequest -Uri $Url -OutFile $target }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $Sha256) {
        throw "Unexpected SHA256 for $Name. Refusing an unverified build dependency."
    }
    return $target
}
$java = Join-Path $JdkHome 'bin/java.exe'
$javac = Join-Path $JdkHome 'bin/javac.exe'
$jar = Join-Path $JdkHome 'bin/jar.exe'
$androidJar = Join-Path $AndroidSdk 'platforms/android-36/android.jar'
foreach ($required in @($java, $javac, $jar, $androidJar)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing prerequisite: $required" }
}
$DesktopJar = Get-Asset $DesktopJar 'morphe-desktop-1.17.0-all.jar' 'https://github.com/MorpheApp/morphe-desktop/releases/download/v1.17.0/morphe-desktop-1.17.0-all.jar' '8cf6a9eab4ee9dab146bddc24681897851564f53116baec11f36ba2fa2f589be'
$PatchesLibraryJar = Get-Asset $PatchesLibraryJar 'morphe-patches-library-1.8.0-dev.1.jar' 'https://github.com/MorpheApp/morphe-patches-library/releases/download/v1.8.0-dev.1/morphe-patches-library-1.8.0-dev.1.jar' '7f17ab3893d5f0b3da932d9e5ad63ca8114a3bcf59f751195ff4f999e2fefd82'
$d8 = Get-Asset $R8Jar 'r8-9.4.34.jar' 'https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.4.34/r8-9.4.34.jar' '3a358220d5d33623543080e9b34841d1d20f708683c1504926f8cf77e9884695'
$UpstreamBundle = Get-Asset $UpstreamBundle 'patches-1.47.0.mpp' 'https://github.com/hoo-dles/morphe-patches/releases/download/v1.47.0/patches-1.47.0.mpp' 'eac6c34b93d09cefba65c0944b681268c9a40fea1d5c25931272641ec7ff481b'
# The compiler expects a .jar suffix for classpath entries.
$upstreamJar = Join-Path $work 'upstream.jar'
Copy-Item -LiteralPath $UpstreamBundle -Destination $upstreamJar
$compilerJars = @(Get-ChildItem -LiteralPath (Join-Path $GradleHome 'lib') -Filter '*.jar' | Select-Object -ExpandProperty FullName)
if (-not ($compilerJars | Where-Object { [IO.Path]::GetFileName($_) -eq 'kotlin-compiler-embeddable-2.4.0.jar' })) {
    throw 'GradleHome must contain the Kotlin 2.4.0 compiler (Gradle 9.7.1).'
}
$compilerClasspath = $compilerJars -join [IO.Path]::PathSeparator
$extensionClasses = Join-Path $work 'extension-classes'
$extensionDex = Join-Path $work 'extension-dex'
$resources = Join-Path $work 'resources'
$patchDex = Join-Path $work 'patch-dex'
New-Item -ItemType Directory -Path $extensionClasses, $extensionDex, "$resources/extensions", $patchDex | Out-Null
$extensionSources = @(Get-ChildItem (Join-Path $repo 'extensions/duolingo/src/main/java') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
Invoke-Checked $javac (@('--release', '17', '-classpath', $androidJar, '-d', $extensionClasses) + $extensionSources)
$extensionJar = Join-Path $work 'extension.jar'
Invoke-Checked $jar @('--create', '--file', $extensionJar, '--date=2026-10-22T00:00:00Z', '-C', $extensionClasses, '.')
Invoke-Checked $java @('-cp', $d8, 'com.android.tools.r8.D8', '--release', '--min-api', '26', '--lib', $androidJar, '--output', $extensionDex, $extensionJar)
Copy-Item -LiteralPath "$extensionDex/classes.dex" -Destination "$resources/extensions/duolingo.mpe"
$kotlinSources = @(Get-ChildItem (Join-Path $repo 'patches/src/main/kotlin/hoodles/morphe/patches/duolingo') -Recurse -Filter '*.kt' | Sort-Object FullName | Select-Object -ExpandProperty FullName)
$kotlinSources += Join-Path $repo 'patches/src/main/kotlin/hoodles/morphe/compatibility/Constants.kt'
$overlayJar = Join-Path $work 'duolingo-overlay.jar'
$compileClasspath = @($upstreamJar, $DesktopJar, $PatchesLibraryJar) -join [IO.Path]::PathSeparator
Invoke-Checked $java (@('-cp', $compilerClasspath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-module-name', 'duolingo_overlay', "-Xfriend-paths=$upstreamJar", '-classpath', $compileClasspath, '-d', $overlayJar) + $kotlinSources)
# Copy the official JVM classes/resources while dropping every superseded class
# and the old root DEX. Never merge two DEX files containing the same classes.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$patchJar = Join-Path $work 'patch.jar'
$merged = [IO.Compression.ZipFile]::Open($patchJar, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($inputArchive in @($upstreamJar, $overlayJar)) {
        $archive = [IO.Compression.ZipFile]::OpenRead($inputArchive)
        try {
            foreach ($entry in $archive.Entries) {
                $name = $entry.FullName
                if ($name.EndsWith('/') -or $name -eq 'META-INF/MANIFEST.MF') { continue }
                if ($inputArchive -eq $upstreamJar -and (
                    $name -match '^classes[0-9]*\.dex$' -or
                    $name.StartsWith('hoodles/morphe/patches/duolingo/') -or
                    $name.StartsWith('hoodles/morphe/compatibility/') -or
                    $name -match '^META-INF/[^/]+\.(SF|RSA|DSA)$'
                )) { continue }
                $copy = $merged.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal)
                $copy.LastWriteTime = [DateTimeOffset]::Parse('2026-10-22T00:00:00Z')
                $sourceStream = $entry.Open()
                $targetStream = $copy.Open()
                try { $sourceStream.CopyTo($targetStream) }
                finally { $sourceStream.Dispose(); $targetStream.Dispose() }
            }
        }
        finally { $archive.Dispose() }
    }
}
finally { $merged.Dispose() }
Invoke-Checked $jar @('--update', '--file', $patchJar, '--date=2026-10-22T00:00:00Z', '-C', $resources, '.')
# The original libraries and extensions are included; Patcher, SMALI and Kotlin
# are supplied by Manager, as in the upstream bundle.
# Embedded extensions may themselves contain .dex resources. Give D8 only JVM
# classes, retaining those resources unchanged in the final bundle.
$classesJar = Join-Path $work 'classes.jar'
$classOutput = [IO.Compression.ZipFile]::Open($classesJar, [IO.Compression.ZipArchiveMode]::Create)
$classInput = [IO.Compression.ZipFile]::OpenRead($patchJar)
try {
    foreach ($entry in $classInput.Entries) {
        if (-not $entry.FullName.EndsWith('.class')) { continue }
        $copy = $classOutput.CreateEntry($entry.FullName, [IO.Compression.CompressionLevel]::Optimal)
        $copy.LastWriteTime = [DateTimeOffset]::Parse('2026-10-22T00:00:00Z')
        $sourceStream = $entry.Open()
        $targetStream = $copy.Open()
        try { $sourceStream.CopyTo($targetStream) }
        finally { $sourceStream.Dispose(); $targetStream.Dispose() }
    }
}
finally { $classInput.Dispose(); $classOutput.Dispose() }
Invoke-Checked $java @('-cp', $d8, 'com.android.tools.r8.D8', '--release', '--min-api', '26', '--lib', $androidJar, '--classpath', $DesktopJar, '--output', $patchDex, $classesJar)
$manifest = Join-Path $work 'MANIFEST.MF'
@"
Name: BeForU Morphe Patches
Description: Hoodles 1.47.0 patches with updated Duolingo $appVersion and local reminder fallback
Version: $BundleVersion
Timestamp: 1792627200000
Source: https://github.com/BeForU/morphe-patches
Author: Hoo-dles, BeForU
Contact: https://github.com/BeForU/morphe-patches/issues
Website: https://github.com/BeForU/morphe-patches
License: GPLv3
Patcher-Version: 1.14.1

"@ | Set-Content -LiteralPath $manifest -Encoding ascii
Invoke-Checked $jar @('--update', '--file', $patchJar, '--manifest', $manifest, '--date=2026-10-22T00:00:00Z', '-C', $patchDex, '.')
# Normalize compiler-created ZIP timestamps as well as resources for reproducibility.
$normalized = Join-Path $work 'normalized'
New-Item -ItemType Directory -Path $normalized | Out-Null
Push-Location $normalized
try { Invoke-Checked $jar @('--extract', '--file', $patchJar) }
finally { Pop-Location }
$finalJar = Join-Path $work 'bundle.mpp'
Invoke-Checked $jar @('--create', '--file', $finalJar, '--manifest', "$normalized/META-INF/MANIFEST.MF", '--date=2026-10-22T00:00:00Z', '-C', $normalized, '.')
New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($OutputPath))) -Force | Out-Null
Copy-Item -LiteralPath $finalJar -Destination $OutputPath
Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256
Write-Output "Bundle: $OutputPath"
Write-Output "Build intermediates: $work"
