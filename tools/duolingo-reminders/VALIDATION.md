# Bundle validation — 2026-10-22

## Display name update 1.47.0-mod.2

Artifact: `dist/patches-1.47.0-mod.2.mpp` (5,929,597 bytes).

SHA-256:
`96ff3de21f17c1cb2b39fc0f1bee60675a72aa51b712009c0d1786b5b317bfc0`

- The manifest Name is **Hoodles Morphe Patches (Modded)** and Version is
  **1.47.0-mod.2**. Both the standalone and normal Gradle build configurations
  use the requested bundle name, as do the add-source links.
- All 925 ZIP entry names match 1.47.0-mod.1. Only the manifest payload changed,
  with Name and Version the only differing attributes. All patch JVM classes,
  Manager DEX, extensions, and resources are byte-for-byte identical.
- The distribution verifier passed manifest/endpoint/catalog/version and ZIP
  integrity checks, including comparison with the previously loaded 79-patch
  catalog. Application tests were not repeated for this metadata-only change.
- Manager 2.0.0 adopts the manifest Name when updating a source, but preserves
  a saved custom display name. Existing aliases can be changed through
  **Sources → Rename** without removing the source.

## Upstream-based version 1.47.0-mod.1

Artifact: `dist/patches-1.47.0-mod.1.mpp` (5,929,591 bytes).

SHA-256:
`39f6bc4331044239e7cce24b37824e838c6aec4eb7d50f54583a8d0383693513`

- Rebuilt with the same pinned dependencies and sources as 1.49.0. All 925 ZIP
  entries have the same names, and all payloads except `META-INF/MANIFEST.MF`
  are byte-for-byte identical. The manifest's only changed attribute is Version.
- The upstream pin is now in `upstream.json`. Build and distribution checks
  require `<upstream-version>-mod.<positive-revision>` and matching Gradle,
  remote metadata, catalog, artifact filename, and manifest versions.
- The new bundle loaded all 79 public patches in the actual Manager/Patcher
  2.0.0 runtime. All five Duolingo patches and MicroG applied together with SUPER
  on the original 6.102.4 APK; all 35 DEX files and resources were serialized.
  Login, energy, premium, RampUp, debug, extension, and startup audits passed.
  The new loaded catalog also passed `verify_distribution.py`.
- Manager 2.0.0's remote source updater compares version strings and timestamps
  for equality, so updating the source from 1.49.0 to 1.47.0-mod.1 is supported.
  Use the source card's Update button during this numbering transition; the
  patched app's update badge may not appear for the lower numerical base.

This release changes the bundle version convention. The SUPER/MAX application
and bytecode audit results for the identical patch payloads remain recorded
below. Actual Android app startup and notification delivery remain unverified.

## Historical unified bundle 1.49.0

Artifact: `dist/patches-1.49.0.mpp` (5,929,587 bytes).

SHA-256:
`eaf872abafa595bc939193c414f0e11019afe6fdeb4af2535333a733ab630168`

This release combines the upstream Hoodles Morphe Patches 1.47.0 catalog with
the current repository's Duolingo 6.102.4 sources and reminder extension. It
includes the corrected `BasicLoginFingerprint` used by `Enable Premium`, the
Duolingo reminder fallback, and universal `MicroG integration` in one bundle.
The previous reminder-only distribution could not replace the Premium patch
selected from the old Hoodles 1.47.0 source.

- Two independent builds produced identical bytes and SHA-256 values. All four
  downloaded dependencies are pinned by version and SHA-256 in `Build.ps1`.
- Every upstream entry outside the replaced Duolingo/compatibility classes,
  root DEX, and manifest was preserved byte-for-byte, including all MicroG and
  shared extensions/resources. No duplicate ZIP entries or DEX class definitions
  remain. All JVM classes are represented in the regenerated Manager DEX.
- The actual JVM Patcher **2.0.0** shipped in the official Morphe Manager
  **2.0.0** APK loaded **79 public patches** from this bundle. Engine version and
  class origin were checked. Its embedded Android framework resources were
  supplied from the Manager APK on the classpath.
- On the supplied original Duolingo **6.102.4** base APK, all five Duolingo
  patches were selected together with **MicroG integration**: **Enable Premium**,
  **Enable debug mode**, **Disable dynamic app icon**, **Unlimited RampUp time**,
  and **Restore learning reminders**. Both **SUPER** and **MAX** runs completed
  without patch exceptions, serialized all **35 DEX files**, and compiled the
  modified resources. No older patch source was loaded.
- The original base APK SHA-256 was
  `e8229cddcdfff44f77f2fcb6cd1b1463368104d4cc0f4fd7f4a495e09ad3281a`.
- Output audits verified the obfuscated `Lgqn.B0` login method and the login
  constructor override, the energy constructor's trailing `Integer` parameter,
  SUPER/MAX user flags and subscriber tier, RampUp timer overrides, and debug
  configuration. Both runtime extensions were embedded. Startup initializes the
  shared context before checking MicroG, then continues the original code.
- The nine reminder bytecode checks passed with the combined selection:
  original manager/display methods unchanged; failure and logout hooks correct;
  notification preference checked before writes; existing alarm API and native
  executor retained; original branch destinations preserved; helper branches
  resolve to valid instruction boundaries.
- Output manifests for SUPER and MAX retained the MicroG package query and
  original-package/signature metadata and removed the dynamic icon aliases.
- `verify_distribution.py --loaded-catalog .../bundle-metadata.json` passed:
  all 79 loaded patches agree with the published catalog's names, descriptions,
  defaults, dependencies, app targets, and options. Manifest, endpoint version,
  extension DEX, and ZIP integrity checks also passed.

The official [Manager 2.0.0 release](https://github.com/MorpheApp/morphe-manager/releases/tag/v2.0.0)
APK SHA-256 was
`22301ded7ad4cea7ad3ac2f1f9f15411cab0336ec0b67943570dec3a6bb73e21`.
Its extracted `assets/jvm/patcher-host.jar.xz` payload, decompressed as a JAR,
had SHA-256
`26e15233ce44350bf632636ec15a8b1c2d99103edec76edf571f93d6ae4a499c`.
The reusable application harness is [Validate.kt](Validate.kt).

No Android device or emulator has been tested for this release. Manager's
Android source-import UI, app startup on the device, and actual notification
delivery remain unverified. Other apps' patches retain the upstream binaries
and have not been reapplied to their APKs in this release.

## Historical reminder-only bundle 1.48.0

Artifact: `dist/duolingo-reminders-1.48.0.mpp` (65,297 bytes).

SHA-256:
`21ef3096dec7f4352208c4150fe76eb117ac906c426198a6a139903ae8e43975`

- Built twice from the repository sources, once with `Build.ps1 -BundleVersion
  1.48.0` and once using the default version read from `patches-bundle.json`;
  the SHA-256 values were identical.
- D8 9.4.34 release/minimum API 26 compiled the patch and extension without warnings.
- Bundle contains root `classes.dex`, desktop JVM classes, embedded
  `extensions/duolingo.mpe`, and standard manifest attributes including
  `Patcher-Version: 1.14.1`.
- Actual Morphe Desktop 1.17.0 engine `PatchBundleLoader.loadFlat` loaded exactly
  one patch: **Restore learning reminders**, disabled by default, version 1.48.0.
- The loaded patch's compatibility metadata was asserted to contain exactly
  `com.duolingo` / `6.102.4`. No compatibility overrides were used.
- Actual Patcher 1.14.1 applied that loaded patch and serialized modified DEX
  successfully against the supplied original Duolingo 6.102.4 base APK.
  Its SHA-256 is
  `e8229cddcdfff44f77f2fcb6cd1b1463368104d4cc0f4fd7f4a495e09ad3281a`.
  Twelve modified/new classes were written, and all 17 output DEX files were
  serialized successfully.
- Runtime classpath contained only the official desktop fat JAR and validation
  harness. The patches library was absent, confirming the bundle needs no
  runtime classes from that library or another patch source.
- Bytecode audit of the original-APK result passed all nine checks: original
  manager methods preserved; correct failure hook and logout hook; preference
  guard before writes; existing alarm scheduling and native executor retained;
  original branch destinations retained; generated branches valid.
- Recompiled and ran the eight dependency-free `FallbackCoordinatorTest`
  scenarios successfully, covering early registration failures, current account
  and language binding, repository identity, retries, reentrant state updates,
  logout/stale work suppression, and native default language handling.
- Ran `verify_distribution.py`: repository metadata, patch catalog, target APK
  version, bundle manifest, embedded DEX files and ZIP integrity all agree.

The reminder fingerprints and dynamically resolved manager references already
match 6.102.4. Updating the declared compatibility and bundle packaging was
sufficient; no version-specific obfuscated class names were hardcoded.

No Android device or emulator was connected. Android's DEX class loader and the
Manager import UI were not executed; actual notification delivery remains
unverified. Desktop loader/application checks and Android-format packaging are
verified separately and should not be described as a completed device test.
