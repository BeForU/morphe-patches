# Automatic upstream synchronization

The workflow at [`.github/workflows/sync-upstream.yml`](../../.github/workflows/sync-upstream.yml)
checks `hoo-dles/morphe-patches` **main** hourly. Its maintained template is
[workflow.yml](workflow.yml); both copies must be kept identical. Inspect
[Actions run history](https://github.com/BeForU/morphe-patches/actions/workflows/sync-upstream.yml)
for the result of an actual scheduled or manual run.

Successful updates retain:

- The **Restore learning reminders** patch and its Duolingo runtime extension.
- The opt-in Instagram **Hide swipe up guide** patch and its validation tools.
- The exact bundle name **Hoodles Morphe Patches (Modded)**.
- The version format **`<upstream version>-mod.<revision>`**. A changed commit or
  manual rebuild on the same baseline increments the custom revision; a new
  upstream base version starts at `-mod.1`.
- A single Manager source containing the upstream catalog, Duolingo patches,
  and universal patches such as **MicroG integration**.

The currently distributed version and download URL are in
[patches-bundle.json](../../patches-bundle.json), its complete patch catalog is
[patches-list.json](../../patches-list.json), and the last incorporated
upstream commit is recorded in [state.json](state.json). The initial state
records upstream release commit `e9c942640130c809fcb908332bf7dc56d59cf86f`
(1.47.0), matching the previously published `1.47.0-mod.2` bundle. State advances
only after the prepared source and built artifact pass verification.

## What each run does

1. Test the synchronization rules and fetch upstream main. If its commit is
   unchanged, stop without a version bump, build, or publication.
2. Merge the new upstream source from the recorded baseline, preserving this
   fork's reminder implementation, release settings, workflow, and tools.
   Resolve the version and bundle branding to this fork's policy. Real merge
   conflicts and changes to the validated Duolingo targets stop the run for
   manual review rather than publishing a partially merged update.
3. Attempt a complete source build with
   `./gradlew codegen :patches:buildAndroid --no-daemon`. GitHub Actions supplies
   its job token for GitHub Packages read access; credentials are not committed.
   Bootstrap Gradle 9.7.1's public compiler, use JDK 21 with JDK 17 available
   for toolchains, and install Android SDK platform 36.
4. If the full source build or catalog export fails, allow the public release
   overlay **only when the official release tag commit equals the exact main
   commit being incorporated**. Its version, URL, and SHA-256 must match the
   prepared pin. If main contains later commits, the full source build must
   succeed; an older release binary cannot be published for those commits.
5. Load the completed bundle and export every public patch's actual metadata.
   Run all eight reminder coordinator regression scenarios in either build
   mode. Verify the mod name/version, source URL, Patcher API, required patches,
   catalog, extensions, and DEX contents before finalizing the versioned `.mpp`,
   Manager metadata, and state.
6. Commit and push the merged source, verified artifact, catalog, and metadata
   together. A build, verification, merge, or push failure does not advance the
   public endpoint to an unverified artifact. Preserve previous versioned files.

An upstream main change can therefore require manual work if its merge is
incompatible, its Duolingo targets differ, or it cannot be built with available
dependencies. Review the failing Actions step, resolve and validate the change,
then request a rebuild. The pipeline checks the bundle and reminder logic; it
does not contain APKs or verify notification delivery on a device.

## Manual run and rebuild

Open **Actions → Sync upstream and publish modded patches → Run workflow** on
`main`. Leave **Rebuild** off to perform a normal upstream check; enable it to
build a new `-mod` revision even if upstream main is unchanged. Changes to the
sync workflow or its tools also trigger a rebuild through the workflow's path
filter. Scheduled runs perform a normal check.

The workflow requires `contents: write` to push the verified update and
`packages: read` for the full source build. Its jobs use a shared concurrency
group so scheduled and manual publications cannot race. The inherited
semantic-release configuration stays in dry-run mode; this workflow owns
publication of this fork's bundles and Manager metadata.

## Scripts and local checks

| File | Purpose |
| --- | --- |
| [sync.py](sync.py) | Prepare a merge and version plan, report its status, and verify/finalize outputs. |
| [Build-CI.ps1](Build-CI.ps1) | Attempt full source compilation, enforce the release overlay gate, export the catalog, and run reminder tests. |
| [test_sync.py](test_sync.py) | Exercise merge preservation, versioning, no-op behavior, parity restrictions, and publication checks using isolated fixtures. |
| [state.json](state.json) | Record the upstream commit, base version, build mode, mod version, and distributed bundle hash. |
| [../duolingo-reminders/upstream.json](../duolingo-reminders/upstream.json) | Pin the public release used when the exact-commit overlay path is permitted. |

Run synchronization unit tests from the repository root:

```text
python -m unittest discover -s tools/upstream-sync -p "test_*.py"
```

`prepare` performs a real merge, so run the complete sequence in a disposable
checkout when testing locally. Keep the plan, compiled outputs, and temporary
files outside the Git checkout:

```powershell
python tools/upstream-sync/sync.py prepare --plan $planPath
./tools/upstream-sync/Build-CI.ps1 -PlanPath $planPath `
    -JdkHome $JdkHome -AndroidSdk $AndroidSdk -GradleHome $GradleHome
```

`Build-CI.ps1` defaults to `JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT`, and the
wrapper distribution when installation paths are omitted. It writes a
`build-result.json` outside the checkout and emits `bundle_path`, `catalog_path`,
`build_mode`, and `result_path` through `GITHUB_OUTPUT` when running in Actions.
Pass the first three values to `sync.py finalize` with the same plan. Finalize
checks that checkout HEAD, protected reminder files, and validated targets
still match the preparation before updating release metadata.

For local testing without private registry access, `-ReleaseOverlay` explicitly
uses the public path but retains the exact main/release commit gate. Optional
`-PublicAssetCache` reuses already downloaded, SHA256-verified dependencies.
Neither switch bypasses verification or publishes files on its own.
