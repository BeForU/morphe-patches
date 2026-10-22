# Supplemental Duolingo reminder bundle

`dist/duolingo-reminders-1.0.0.mpp` contains only **Restore learning reminders**
for Duolingo **6.98.4** (`com.duolingo`). It is disabled by default. Import the
file as a local patch source in Morphe Manager, then select that patch. This is
a supplemental bundle, so keep your existing source for other Duolingo patches.
Selecting patches from multiple sources requires Manager Expert mode.

The bundle includes JVM classes for desktop use, root `classes.dex` for Manager,
an embedded runtime extension (`extensions/duolingo.mpe`), and the standard
Morphe manifest. It requires Morphe Patcher 1.14.1 or a compatible newer version.
It does not contain an APK. Device notification delivery still needs testing.

## Build on Windows

Install JDK 21, Android SDK platform 36, and unpack Gradle 9.7.1. Run from the
repository root, substituting your installation paths:

```powershell
.\tools\duolingo-reminders\Build.ps1 `
    -JdkHome 'C:\path\to\jdk-21' `
    -AndroidSdk 'C:\path\to\Android\Sdk' `
    -GradleHome 'C:\path\to\gradle-9.7.1'
```

The script compiles the actual repository patch and extension sources. Its
standalone build avoids the private Maven registry needed by the full project.
The usual full bundle build is still `gradlew.bat :patches:buildAndroid`.

Pinned public dependencies downloaded into a fresh temporary directory:

- [Morphe Desktop 1.17.0](https://github.com/MorpheApp/morphe-desktop/releases/tag/v1.17.0), supplying the exact Morphe Patcher 1.14.1 API.
- [Morphe patches library 1.8.0-dev.1](https://github.com/MorpheApp/morphe-patches-library/releases/tag/v1.8.0-dev.1), used only at compile time (its `getReference` helper is inline).
- [R8/D8 9.4.34](https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.4.34/r8-9.4.34.jar), supporting the Kotlin 2.4.0 compiler included in Gradle 9.7.1.

Optional `-DesktopJar`, `-PatchesLibraryJar` and `-R8Jar` parameters reuse already
downloaded files; use those exact versions. `-OutputPath` changes the destination.
The script prints the bundle SHA-256 and the retained temporary build directory.
ZIP timestamps and manifest metadata are fixed for repeatable builds. Only
Manager-provided Patcher, SMALI and Kotlin APIs are needed at runtime; no class
loader dependency on the other patch source is required.
