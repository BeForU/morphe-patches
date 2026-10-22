# Supplemental Duolingo reminder bundle

`dist/duolingo-reminders-1.48.0.mpp` contains only **Restore learning reminders**
for Duolingo **6.102.4** (`com.duolingo`). It is disabled by default. Add
`https://github.com/BeForU/morphe-patches` under Morphe Manager's
**Sources → + → Remote**, or [use the add-source link](https://morphe.software/add-source?github=BeForU/morphe-patches&name=Duolingo%20Learning%20Reminders).
Then choose the patch in the **Duolingo Learning Reminders** tab. This is a
supplemental bundle, so keep your existing source for other Duolingo patches.
Selecting patches from multiple sources requires Manager Expert mode.

An existing remote source for this fork can use **Update** to fetch version 1.48.0.
An existing local source must be replaced by adding the repository once; disable
or delete the old local reminder source afterwards to avoid duplicate patches.
Local `.mpp` import is also supported, but it does not receive remote updates.
The archived 1.0.0 artifact continues to target 6.98.4.

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
downloaded files; use those exact versions. The bundle version defaults to
`patches-bundle.json`'s `version`; `-BundleVersion` overrides it for a test build.
The app version comes from `Compat.DUOLINGO`. `-OutputPath` changes the destination.
The script prints the bundle SHA-256 and the retained temporary build directory.
ZIP timestamps and manifest metadata are fixed for repeatable builds. Only
Manager-provided Patcher, SMALI and Kotlin APIs are needed at runtime; no class
loader dependency on the other patch source is required.

## Publishing future updates

1. Update and validate the patch against the target APK, then update `Compat.DUOLINGO`.
2. Bump `patches-bundle.json`'s `version` above the last published version. Update
   `created_at` (ISO-8601 UTC), `description`, and `download_url` to the matching
   versioned `dist/duolingo-reminders-VERSION.mpp` on this fork's `main` branch.
   Update `patches-list.json` to describe the same one-patch bundle and target.
3. Build with `Build.ps1`. Keep the metadata version, JAR manifest version, and
   output filename identical. Preserve old versioned artifacts.
4. Run `python tools/duolingo-reminders/verify_distribution.py` from the repository
   root, plus the patch/extension validation described in [VALIDATION.md](VALIDATION.md).
5. Commit and push the source, versioned `.mpp`, and JSON metadata together.
   Manager's remote-source update fetches this new artifact through the metadata.
   Update this README and the root README's download link for that release.

Run the distribution verifier before committing. This fork sets `dryRun: true`
in `.releaserc` so the inherited full-bundle semantic release cannot overwrite
the committed reminder endpoint or publish unrelated full-source metadata.
A reminder release is published by committing the tested artifact and metadata;
the inherited workflow does not create reminder versions on its own. That
workflow's full-project compilation still requires GitHub Packages access,
independently of the already-published reminder download and update endpoint.

Manager reads a repository source's stable `main/patches-bundle.json`, then
downloads its `download_url`; no GitHub Release asset is required. Detached
signatures are optional and are omitted here because none is published.
See the official [source guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md),
[remote source implementation](https://github.com/MorpheApp/morphe-manager/blob/main/app/src/main/java/app/morphe/manager/domain/bundles/RemotePatchBundle.kt),
and [metadata model](https://github.com/MorpheApp/morphe-manager/blob/main/app/src/main/java/app/morphe/manager/network/dto/MorpheAsset.kt).
