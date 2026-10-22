# Bundle validation — 2026-10-22

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
