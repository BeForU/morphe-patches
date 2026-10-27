[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$JdkHome,
    [Parameter(Mandatory)][string]$GradleHome,
    [Parameter(Mandatory)][string]$DesktopJar,
    [Parameter(Mandatory)][string]$R8Jar,
    [Parameter(Mandatory)][string]$AndroidJar,
    [string]$OutputPath
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$build = Join-Path $repo 'work/instagram/bundle-build'
New-Item -ItemType Directory -Force $build, "$build/dex" | Out-Null
if (-not $OutputPath) { $OutputPath = Join-Path $repo 'dist/instagram-guide-1.0.0.mpp' }
$java = Join-Path $JdkHome 'bin/java.exe'
$jar = Join-Path $JdkHome 'bin/jar.exe'
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable exited with $LASTEXITCODE" }
}
$compiler = (Get-ChildItem -LiteralPath "$GradleHome/lib" -Filter '*.jar').FullName -join [IO.Path]::PathSeparator
$source = Join-Path $repo 'patches/src/main/kotlin/hoodles/morphe/patches/instagram/scrollnux/HideSwipeUpGuidePatch.kt'
$patchJar = Join-Path $build 'instagram-guide.jar'
Invoke-Checked $java @('-cp', $compiler, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-module-name', 'instagram_guide', '-classpath', $DesktopJar, '-d', $patchJar, $source)
Invoke-Checked $java @('-cp', $R8Jar, 'com.android.tools.r8.D8', '--release', '--min-api', '26', '--lib', $AndroidJar, '--classpath', $DesktopJar, '--output', "$build/dex", $patchJar)
@"
Name: Instagram swipe guide patch
Description: Disable swipe onboarding guides and automatic peek bounce
Version: 1.0.0
Patcher-Version: 1.14.1
License: GPLv3

"@ | Set-Content -LiteralPath "$build/MANIFEST.MF" -Encoding ascii
Invoke-Checked $jar @('--update', '--file', $patchJar, '--manifest', "$build/MANIFEST.MF", '-C', "$build/dex", 'classes.dex')
New-Item -ItemType Directory -Force ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($OutputPath))) | Out-Null
Copy-Item -LiteralPath $patchJar -Destination $OutputPath
Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256
Write-Output "Bundle: $OutputPath"
