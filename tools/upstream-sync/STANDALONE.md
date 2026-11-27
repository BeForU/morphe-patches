# Build the current checkout independently

`Build-Standalone.ps1` builds the complete local source tree and exports the
catalog from the resulting bundle. It does not fetch the Hoodles repository or
read an upstream patches release bundle. A failed full-source build or catalog
export stops the build instead of falling back to a release overlay. It also
runs the reminder coordinator tests and reports the temporary output paths.

The build still needs its toolchain and library dependencies: JDK 21, a JDK 17
toolchain, Android SDK platform 36, and Gradle 9.7.1. With
`-PublicSourceDependencies`, the script obtains the official Morphe Gradle
plugin, Patcher and patch libraries at pinned commits, and includes their source
as Gradle composite builds. It uses public Maven/Google/JitPack dependencies and
does not require GitHub Packages credentials. Alternatively, omit the switch to
use the configured Morphe registry; GitHub Packages access then uses
`GITHUB_ACTOR` and `GITHUB_TOKEN` or the `gpr.user` and `gpr.key` Gradle
properties. These build dependencies are distinct from the stopped Hoodles
patch source. Do not commit credentials.

```powershell
./tools/upstream-sync/Build-Standalone.ps1 `
    -JdkHome 'C:/path/to/jdk-21' `
    -Java17Home 'C:/path/to/jdk-17' `
    -AndroidSdk 'C:/path/to/Android/Sdk' `
    -GradleHome 'C:/path/to/gradle-9.7.1' `
    -PublicSourceDependencies
```

The version comes from `gradle.properties`. The current working tree is built,
including uncommitted changes. The script stages a bundle and catalog only; it
does not publish them, update `patches-bundle.json`, or check an APK. Before a
distribution update, validate the actual bundle against an original APK and
verify that the bundle, catalog, source version, and Manager endpoint agree.
Code generation and bundle compilation run as separate Gradle invocations so
generated Pairip extension projects are discovered during configuration. On
Windows, the generated extension directory must support case-sensitive names;
the existing `codegen` task enables that setting if necessary.
