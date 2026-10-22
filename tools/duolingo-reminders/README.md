# Unified Morphe bundle with Duolingo updates

The published bundle is **Hoodles Morphe Patches (Modded)**. Its current
version and download URL are in [patches-bundle.json](../../patches-bundle.json);
[patches-list.json](../../patches-list.json) describes the actual bundled patches.
These links remain valid as automatic updates publish new versioned `.mpp` files.

The source combines the upstream Hoodles catalog, Duolingo **6.102.4** support,
the login fix from [PR #1117](https://github.com/hoo-dles/morphe-patches/pull/1117),
and the optional **Restore learning reminders** patch. Duolingo and universal
patches, including **MicroG integration**, are available from this one source.

Add `https://github.com/BeForU/morphe-patches` under Morphe Manager's
**Sources → + → Remote**, or [use the add-source link](https://morphe.software/add-source?github=BeForU/morphe-patches&name=Hoodles%20Morphe%20Patches%20%28Modded%29).
An existing remote source for this fork can use **Update**, including one
previously named **Duolingo Learning Reminders** or **BeForU Morphe Patches**.
Manager preserves a saved custom source name. If the source card still shows
the old label, use its pencil **Rename** action under **Sources** and enter
**Hoodles Morphe Patches (Modded)**.

Choose an original Duolingo 6.102.4 APK and select **Enable Premium**, **MicroG
integration**, and **Restore learning reminders** from this source. The reminder
patch is disabled by default; use Expert mode if needed to show it. Clear
Duolingo selections from other sources so each patch is applied once.

The `BasicLoginFingerprint` error reported with **Hoodles Morphe Patches
1.47.0** comes from that old source's Premium dependency. The archived 1.48.0
reminder-only bundle could not update that dependency. The unified bundle
includes the corrected login fingerprint alongside Premium.

An existing local source must be replaced by adding the repository once;
disable the old local reminder source afterwards. Local `.mpp` import works,
but it does not receive remote updates. Previous versioned artifacts remain
available in [dist](../../dist) as archives.

## Automatic upstream updates

The [sync workflow](https://github.com/BeForU/morphe-patches/actions/workflows/sync-upstream.yml)
checks upstream `main` hourly. A changed commit is merged, built, checked, and
published with a matching catalog and Manager metadata. An unchanged commit
does not create a new release. Run **Actions → Sync upstream and publish modded
patches → Run workflow** with **Rebuild** selected to publish another custom
revision without waiting for an upstream change.

Fork versions follow **`<upstream version>-mod.<revision>`**. For example,
`1.47.0-mod.1` and `1.47.0-mod.2` share upstream 1.47.0; moving to upstream
1.48.0 starts at `1.48.0-mod.1`. Automatic synchronization preserves this rule,
the exact **Hoodles Morphe Patches (Modded)** display name, and the reminder
implementation. Conflicting merges or changed Duolingo targets require manual
review and APK validation before a new bundle can be published.

CI first builds the complete merged source with `codegen :patches:buildAndroid`.
If that build or its catalog export fails, a public upstream bundle may be
used only when the official release tag resolves to the exact upstream main
commit being synchronized. The public overlay then replaces all Duolingo
classes and compatibility data with this repository's sources, adds the
reminder extension, and regenerates Android DEX. It cannot publish an older
release binary while claiming to include newer main commits.

The full process, state tracking, and failure handling are documented in
[tools/upstream-sync](../upstream-sync/README.md). `.releaserc` keeps
`dryRun: true` for the inherited release configuration; the new sync workflow
owns this fork's verified artifact and Manager metadata publication.

## Public build on Windows or Linux

Install JDK 21, Android SDK platform 36, and unpack Gradle 9.7.1. This public
build uses [upstream.json](upstream.json)'s SHA256-pinned release baseline;
it does not compile unrelated source changes made after that release. For
automatic upstream synchronization, use the gated CI process described above.

From PowerShell, substitute your installation paths:

```powershell
./tools/duolingo-reminders/Build.ps1 `
    -JdkHome 'C:/path/to/jdk-21' `
    -AndroidSdk 'C:/path/to/Android/Sdk' `
    -GradleHome 'C:/path/to/gradle-9.7.1' `
    -CatalogPath "$env:TEMP/patches-list.json"
```

On Linux, use `pwsh` and Linux paths; choose an output path such as
`/tmp/patches-list.json`. The full source build is
`./gradlew codegen :patches:buildAndroid --no-daemon` and requires GitHub
Packages access for its build plugin.

Public dependencies are downloaded into a fresh temporary directory and
verified by SHA-256: the baseline in `upstream.json`, Morphe Desktop 1.17.0
(Patcher 1.14.1 API), patches library 1.8.0-dev.1, and R8/D8 9.4.34. Gradle
9.7.1 supplies the Kotlin 2.4.0 compiler. Optional `-DesktopJar`,
`-PatchesLibraryJar`, `-R8Jar`, and `-UpstreamBundle` reuse downloaded files
with the same integrity checks.

The version defaults to `patches-bundle.json`'s `version`; `-BundleVersion`
overrides it for a test build. It must match the pinned baseline followed by
`-mod.` and a positive revision number. Target versions come from
`Compat.DUOLINGO`. `-OutputPath` changes the destination; `-CatalogPath`
exports every public patch loaded from the completed bundle. Compiler and ZIP
timestamps are normalized for repeatable public builds.

For a bundle already built from full source, generate the same catalog without
rebuilding the overlay:

```powershell
./tools/duolingo-reminders/Export-Catalog.ps1 `
    -JdkHome $JdkHome -GradleHome $GradleHome `
    -BundlePath $bundle -OutputPath $catalog -ExpectedVersion $bundleVersion
```

The bundle contains JVM classes for desktop use, root `classes.dex` for
Manager, embedded runtime extensions, and a standard Morphe manifest. It
contains no APK. The catalog exporter verifies its version, display name,
and reminder patch; distribution checks also compare catalog metadata with
the actual loaded patches and verify the manifest API and DEX contents.

## Validate patch application

[Validate.kt](Validate.kt) applies all five Duolingo patches and MicroG together
and writes loaded patch metadata, modified DEX, and compiled resources.
Extract and decompress `assets/jvm/patcher-host.jar.xz` from the official
Manager 2.0.0 APK, then compile the validator against that JAR with the same
Kotlin compiler as the build. Include both the host JAR and Manager APK on the
runtime classpath; the APK supplies Android framework resources for the engine.

```powershell
$bundleVersion = (Get-Content patches-bundle.json -Raw | ConvertFrom-Json).version
# Set the paths to your original APK, bundle, runtime, and compiled validator.
& "$JdkHome/bin/java.exe" -Xmx8g -cp "$hostJar;$managerApk;$validatorJar" ValidateKt `
    $bundle $originalBaseApk $temporaryWork $validationOutput SUPER $bundleVersion 6.102.4
python tools/duolingo-reminders/verify_distribution.py `
    --loaded-catalog "$validationOutput/bundle-metadata.json"
```

Use a fresh disposable work directory. Repeat with `MAX` and separate work
and output directories to check the other Premium option. This checks patch
application and output encoding. Device startup and notification delivery
remain separate checks; see [VALIDATION.md](VALIDATION.md) for completed tests.

The automatic workflow runs all eight dependency-free reminder coordinator
regression scenarios for either build mode. It does not include a user's APK
or perform device notification tests. New Duolingo targets must be validated
manually before being incorporated into an automatic update.

## Publishing

Use the upstream sync workflow's **Rebuild** input after reviewed custom fixes,
or let its hourly check incorporate a changed upstream commit. It updates the
versioned artifact, source state, catalog, and `patches-bundle.json` together,
then commits and pushes only after verification. Old versioned artifacts are
preserved. The README's stable metadata links do not need per-release edits.

Manager reads stable `main/patches-bundle.json` and downloads its `download_url`;
a GitHub Release asset is not required. Detached signatures are omitted because
none is published. For Manager's source protocol, see its official
[source guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md),
[remote source implementation](https://github.com/MorpheApp/morphe-manager/blob/main/app/src/main/java/app/morphe/manager/domain/bundles/RemotePatchBundle.kt),
and [metadata model](https://github.com/MorpheApp/morphe-manager/blob/main/app/src/main/java/app/morphe/manager/network/dto/MorpheAsset.kt).
