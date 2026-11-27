# Unified Morphe bundle with Duolingo updates

This repository maintains a complete **Hoodles Morphe Patches (Modded)** bundle containing Duolingo **6.106.7**, the optional **Restore learning reminders** patch and universal **MicroG integration**. Select them from this single source in Morphe Manager; the reminder is off by default. The bundle does not include an APK or the Manager application.

Add `https://github.com/BeForU/morphe-patches` as a remote source or import the bundle locally. The repository's [metadata](../../patches-bundle.json) describes the remote bundle only after the changes are pushed to `main`. Use the original APK and clear duplicate patch selections from other sources. Updating the bundle does not update an already installed app.

Upstream Hoodles synchronization is paused. Build the whole current checkout without that upstream or its release artifact using [the standalone instructions](../upstream-sync/STANDALONE.md). The previous `Build.ps1` is a historical release-overlay build and is not the standalone build path.

For results and limits of APK, bytecode and device checks, see [VALIDATION.md](VALIDATION.md). The reminder fallback does not promise delivery of all server notifications; a prior local alarm was blocked from starting its background service on the tested device.

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
    $bundle $originalBaseApk $temporaryWork $validationOutput SUPER $bundleVersion 6.106.7
python tools/duolingo-reminders/verify_distribution.py `
    --loaded-catalog "$validationOutput/bundle-metadata.json"
```

Use a fresh disposable work directory. Repeat with `MAX` and separate work
and output directories to check the other Premium option. This checks patch
application and output encoding. Device startup and notification delivery
remain separate checks; see [VALIDATION.md](VALIDATION.md) for completed tests.

The standalone build runs all eight dependency-free reminder coordinator
regression scenarios after the full-source build. It does not include a user's APK
or perform device notification tests. New Duolingo targets must be validated
manually before being incorporated into a distribution update.
