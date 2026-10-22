<#
Build the supplemental Manager bundle without GitHub Packages credentials.
Requires JDK 21, Android SDK platform 36, and a Gradle 9.7.1
distribution (for its Kotlin 2.4.0 compiler). Downloads two public, pinned
official Morphe release assets and R8 9.4.34 to the temporary directory.
The normal full-project build remains .\gradlew.bat :patches:buildAndroid.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$JdkHome,
    [Parameter(Mandatory)][string]$AndroidSdk,
    [Parameter(Mandatory)][string]$GradleHome,
    [string]$OutputPath,
    [string]$DesktopJar,
    [string]$PatchesLibraryJar,
    [string]$R8Jar
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $OutputPath) { $OutputPath = Join-Path $repo 'dist/duolingo-reminders-1.0.0.mpp' }
$work = Join-Path ([IO.Path]::GetTempPath()) ('duolingo-reminders-build-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $work | Out-Null
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable exited with $LASTEXITCODE" }
}
function Get-Asset([string]$Existing, [string]$Name, [string]$Url) {
    if ($Existing) { return (Resolve-Path -LiteralPath $Existing).Path }
    $target = Join-Path $work $Name
    Invoke-WebRequest -Uri $Url -OutFile $target
    return $target
}
$java = Join-Path $JdkHome 'bin/java.exe'
$javac = Join-Path $JdkHome 'bin/javac.exe'
$jar = Join-Path $JdkHome 'bin/jar.exe'
$androidJar = Join-Path $AndroidSdk 'platforms/android-36/android.jar'
foreach ($required in @($java, $javac, $jar, $androidJar)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing prerequisite: $required" }
}
$DesktopJar = Get-Asset $DesktopJar 'morphe-desktop-1.17.0-all.jar' 'https://github.com/MorpheApp/morphe-desktop/releases/download/v1.17.0/morphe-desktop-1.17.0-all.jar'
$PatchesLibraryJar = Get-Asset $PatchesLibraryJar 'morphe-patches-library-1.8.0-dev.1.jar' 'https://github.com/MorpheApp/morphe-patches-library/releases/download/v1.8.0-dev.1/morphe-patches-library-1.8.0-dev.1.jar'
$d8 = Get-Asset $R8Jar 'r8-9.4.34.jar' 'https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.4.34/r8-9.4.34.jar'
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
$kotlinSources = @(Get-ChildItem (Join-Path $repo 'patches/src/main/kotlin/hoodles/morphe/patches/duolingo/misc/notifications') -Filter '*.kt' | Select-Object -ExpandProperty FullName)
$kotlinSources += Join-Path $repo 'patches/src/main/kotlin/hoodles/morphe/compatibility/Constants.kt'
$patchJar = Join-Path $work 'patch.jar'
Invoke-Checked $java (@('-cp', $compilerClasspath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', ($DesktopJar + [IO.Path]::PathSeparator + $PatchesLibraryJar), '-d', $patchJar) + $kotlinSources)
Invoke-Checked $jar @('--update', '--file', $patchJar, '--date=2026-10-22T00:00:00Z', '-C', $resources, '.')
# getReference is inline: no patches-library runtime classes are referenced.
# Patcher, SMALI and Kotlin are supplied by Manager; no other bundle is needed.
Invoke-Checked $java @('-cp', $d8, 'com.android.tools.r8.D8', '--release', '--min-api', '26', '--lib', $androidJar, '--classpath', $DesktopJar, '--output', $patchDex, $patchJar)
$manifest = Join-Path $work 'MANIFEST.MF'
@"
Name: Duolingo Learning Reminders
Description: Supplemental local reminder fallback for Duolingo 6.98.4
Version: 1.0.0
Timestamp: 1792627200000
Source: https://github.com/BeForU/morphe-patches
Author: BeForU
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
