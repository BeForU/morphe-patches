# Unified Morphe bundle with Duolingo updates

`dist/patches-1.47.0-mod.2.mpp` is **Hoodles Morphe Patches (Modded) 1.47.0-mod.2**. It includes the
upstream **Hoodles Morphe Patches 1.47.0** catalog, rebuilt Duolingo sources
targeting **6.102.4** (`com.duolingo`), the login fix from
[PR #1117](https://github.com/hoo-dles/morphe-patches/pull/1117), and the optional
**Restore learning reminders** patch. The Duolingo and universal patches,
including **MicroG integration**, are available from one source.

Add `https://github.com/BeForU/morphe-patches` under Morphe Manager's
**Sources → + → Remote**, or [use the add-source link](https://morphe.software/add-source?github=BeForU/morphe-patches&name=Hoodles%20Morphe%20Patches%20%28Modded%29).
An existing remote source for this fork can use **Update** to fetch version
1.47.0-mod.2, including a source previously named **Duolingo Learning Reminders**
or **BeForU Morphe Patches**. Manager preserves a saved custom source name. If
the source card still shows the old label, use its pencil **Rename** action
under **Sources** and enter **Hoodles Morphe Patches (Modded)**. Removing and
adding the source again is unnecessary.
Choose an original Duolingo 6.102.4 APK and select **Enable Premium**, **MicroG
integration**, and **Restore learning reminders** from this updated source.
The reminder patch is disabled by default; use Expert mode if needed to show it.
Clear Duolingo selections from the old Hoodles 1.47.0 source and old local
reminder sources so each patch is applied once.

The `BasicLoginFingerprint` error reported with **Hoodles Morphe Patches
1.47.0** originates in that old source's Premium dependency. The earlier 1.48.0
bundle contained only the reminder patch and could not update that dependency.
The unified bundle includes the corrected login fingerprint alongside Premium.

An existing local source must be replaced by adding the repository once;
disable or delete the old local reminder source afterwards. Local `.mpp` import
also works, but it does not receive remote updates. Archived reminder-only
artifacts remain available: 1.48.0 targets 6.102.4 and 1.0.0 targets 6.98.4.
The previously published unified 1.49.0 and 1.47.0-mod.1 bundles are also retained as archives.

Fork versions follow **`<upstream version>-mod.<revision>`**. The current
`1.47.0-mod.2` identifies upstream 1.47.0 with the second custom revision;
the first was `1.47.0-mod.1`, and subsequent custom fixes use `1.47.0-mod.3`.
Incorporating upstream 1.48.0 starts at `1.48.0-mod.1`.

The pinned baseline version, download URL, and SHA-256 are stored in
[`upstream.json`](upstream.json). The build and distribution verifier require
the bundle version to match that baseline followed by `-mod.` and a positive
revision number.

When migrating from the former 1.49.0 numbering, use the source card's
**Update** button. Manager 2.0.0 accepts the changed source version even though
the numerical base is lower; the patched app's update badge may not appear.
Its changelog history classifies hyphenated versions as prereleases, but
loading a mod bundle from the stable source does not require the prerelease
toggle. See the [source update implementation](https://github.com/MorpheApp/morphe-manager/blob/v2.0.0/app/src/main/java/app/morphe/manager/domain/bundles/RemotePatchBundle.kt).

The bundle contains JVM classes for desktop use, root `classes.dex` for Manager,
embedded runtime extensions, and a standard Morphe manifest declaring Patcher
1.14.1. See [VALIDATION.md](VALIDATION.md) for the exact engines and combinations
checked. It contains no APK. Device notification delivery still needs testing.

## Build on Windows

Install JDK 21, Android SDK platform 36, and unpack Gradle 9.7.1. Run from the
repository root, substituting your installation paths:

```powershell
.\tools\duolingo-reminders\Build.ps1 `
    -JdkHome 'C:\path\to\jdk-21' `
    -AndroidSdk 'C:\path\to\Android\Sdk' `
    -GradleHome 'C:\path\to\gradle-9.7.1'
```

The standalone build preserves the public upstream bundle's other patches,
resources, and extensions, rebuilds the repository's updated Duolingo sources
and reminder extension, and regenerates the combined Android DEX. It avoids
the private Maven registry required by the full project. The usual full-source
build is still `gradlew.bat :patches:buildAndroid`.

Pinned public dependencies are downloaded into a fresh temporary directory:

- [Hoodles Morphe Patches 1.47.0](https://github.com/hoo-dles/morphe-patches/releases/tag/v1.47.0), providing the baseline patches, resources, and extensions.
- [Morphe Desktop 1.17.0](https://github.com/MorpheApp/morphe-desktop/releases/tag/v1.17.0), supplying the Morphe Patcher 1.14.1 API.
- [Morphe patches library 1.8.0-dev.1](https://github.com/MorpheApp/morphe-patches-library/releases/tag/v1.8.0-dev.1), used at compile time.
- [R8/D8 9.4.34](https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.4.34/r8-9.4.34.jar), supporting the Kotlin 2.4.0 compiler included in Gradle 9.7.1.

Optional `-DesktopJar`, `-PatchesLibraryJar`, `-R8Jar`, and `-UpstreamBundle`
parameters reuse downloaded files; SHA-256 checks enforce the pinned versions.
The bundle version defaults to
`patches-bundle.json`'s `version`; `-BundleVersion` overrides it for a test build.
The app version comes from `Compat.DUOLINGO`. `-OutputPath` changes the destination.
The script prints the bundle SHA-256 and retained temporary build directory.
ZIP timestamps and manifest metadata are fixed for repeatable builds. The
bundle carries the patch dependencies it needs; users do not need to combine it
with an older patch source.

## Validate patch application

[Validate.kt](Validate.kt) applies all five Duolingo patches and MicroG together
and writes the loaded patch metadata, modified DEX, and compiled resources.
Extract and decompress `assets/jvm/patcher-host.jar.xz` from the official Manager
2.0.0 APK, then compile the validator against that JAR with the same Kotlin
compiler as the build. Include both the host JAR and Manager APK on the runtime
classpath: the APK supplies Android framework resources needed by the engine.

```powershell
# Replace the variables with paths to your downloaded files and compiled validator.
& "$JdkHome/bin/java.exe" -Xmx8g -cp "$hostJar;$managerApk;$validatorJar" ValidateKt `
    $bundle $originalBaseApk $temporaryWork $validationOutput SUPER 1.47.0-mod.2 6.102.4
python tools/duolingo-reminders/verify_distribution.py `
    --loaded-catalog "$validationOutput/bundle-metadata.json"
```

Use a fresh disposable work directory. Repeat with `MAX` and separate work and
output directories to check the other Premium option. This checks patch
application and output encoding; test app startup and reminder delivery on an
Android device separately.

## Publishing future updates

1. Update and validate the Duolingo sources against the target APK, then update
   `Compat.DUOLINGO`. When incorporating a newer upstream release, update the
   version, URL, and SHA-256 in `upstream.json` and verify the combined bundle
   again.
2. Set `patches-bundle.json`'s `version` to `<upstream version>-mod.<revision>`.
   Increase the custom revision for fixes on the same upstream baseline, for
   example `1.47.0-mod.1` → `1.47.0-mod.2`. When updating the upstream baseline,
   reset the custom revision, for example `1.48.0-mod.1`. Update
   `created_at` (ISO-8601 UTC), `description`, and `download_url` to the matching
   versioned `dist/patches-VERSION.mpp` on this fork's `main` branch. Keep
   `gradle.properties`'s version and `patches-list.json` in sync with the bundle.
   Cross-check the catalog against the loaded metadata from `Validate.kt`.
3. Build with `Build.ps1`. Keep the metadata version, JAR manifest version, and
   output filename identical. Preserve old versioned artifacts.
4. Run `python tools/duolingo-reminders/verify_distribution.py` from the repository
   root, plus the patch/extension checks described in [VALIDATION.md](VALIDATION.md).
5. Commit and push the source, versioned `.mpp`, and JSON metadata together.
   Manager's remote-source update fetches the new artifact through the metadata.
   Update this README and the root README's download link for that release.

Run the distribution verifier before committing. This fork sets `dryRun: true`
in `.releaserc` so the inherited semantic-release process cannot overwrite the
committed combined bundle metadata. A release here is published by committing
the tested artifact and metadata; the inherited workflow does not create these
versions on its own. Its full-project compilation still requires GitHub
Packages access, independently of the published download and update endpoint.

Manager reads a repository source's stable `main/patches-bundle.json`, then
downloads its `download_url`; no GitHub Release asset is required. Updates from
upstream become available in this source when a new combined bundle is
published here. Detached signatures are optional and omitted because none is
published. See the official [source guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md),
[remote source implementation](https://github.com/MorpheApp/morphe-manager/blob/main/app/src/main/java/app/morphe/manager/domain/bundles/RemotePatchBundle.kt),
and [metadata model](https://github.com/MorpheApp/morphe-manager/blob/main/app/src/main/java/app/morphe/manager/network/dto/MorpheAsset.kt).
