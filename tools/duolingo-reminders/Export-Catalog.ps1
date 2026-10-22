<#
Export the public patch catalog from an already built Manager bundle.
Works on Windows and Linux with JDK 21 and Gradle 9.7.1's Kotlin 2.4.0 compiler.
Only the public, SHA256-pinned desktop runtime is needed; no Maven credentials.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$JdkHome,
    [Parameter(Mandatory)][string]$GradleHome,
    [Parameter(Mandatory)][string]$BundlePath,
    [Parameter(Mandatory)][string]$OutputPath,
    [Parameter(Mandatory)][string]$ExpectedVersion,
    [string]$DesktopJar
)
$ErrorActionPreference = 'Stop'
$executableSuffix = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '.exe' } else { '' }
$java = Join-Path $JdkHome "bin/java$executableSuffix"
if (-not (Test-Path -LiteralPath $java)) { throw "Missing prerequisite: $java" }
$bundle = (Resolve-Path -LiteralPath $BundlePath).Path
$compilerJars = @(Get-ChildItem -LiteralPath (Join-Path $GradleHome 'lib') -Filter '*.jar' | Select-Object -ExpandProperty FullName)
if (-not ($compilerJars | Where-Object { [IO.Path]::GetFileName($_) -eq 'kotlin-compiler-embeddable-2.4.0.jar' })) {
    throw 'GradleHome must contain the Kotlin 2.4.0 compiler (Gradle 9.7.1).'
}
$compilerClasspath = $compilerJars -join [IO.Path]::PathSeparator
$work = Join-Path ([IO.Path]::GetTempPath()) ('duolingo-catalog-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $work | Out-Null
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable exited with $LASTEXITCODE" }
}
try {
    if ($DesktopJar) {
        $DesktopJar = (Resolve-Path -LiteralPath $DesktopJar).Path
    } else {
        $DesktopJar = Join-Path $work 'morphe-desktop-1.17.0-all.jar'
        Invoke-WebRequest -Uri 'https://github.com/MorpheApp/morphe-desktop/releases/download/v1.17.0/morphe-desktop-1.17.0-all.jar' -OutFile $DesktopJar
    }
    if ((Get-FileHash -LiteralPath $DesktopJar -Algorithm SHA256).Hash -ne '8cf6a9eab4ee9dab146bddc24681897851564f53116baec11f36ba2fa2f589be') {
        throw 'Unexpected SHA256 for the desktop runtime. Refusing an unverified catalog dependency.'
    }
    $catalogJar = Join-Path $work 'catalog.jar'
    Invoke-Checked $java @('-cp', $compilerClasspath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', $DesktopJar, '-d', $catalogJar, (Join-Path $PSScriptRoot 'Catalog.kt'))
    $catalogClasspath = @($DesktopJar, $catalogJar) -join [IO.Path]::PathSeparator
    Invoke-Checked $java @('-cp', $catalogClasspath, 'CatalogKt', $bundle, [IO.Path]::GetFullPath($OutputPath), $ExpectedVersion)
}
finally {
    # The folder is a dedicated newly created temporary directory owned by this run.
    $resolvedWork = (Resolve-Path -LiteralPath $work).Path
    $resolvedTemp = (Resolve-Path -LiteralPath ([IO.Path]::GetTempPath())).Path.TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ([IO.Path]::GetDirectoryName($resolvedWork) -ne $resolvedTemp -or [IO.Path]::GetFileName($resolvedWork) -notmatch '^duolingo-catalog-[0-9a-f-]{36}$') {
        throw "Refusing to clean up an unexpected path: $resolvedWork"
    }
    Remove-Item -LiteralPath $resolvedWork -Recurse -Force
}
