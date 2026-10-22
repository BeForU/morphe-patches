# Bundle validation — 2026-10-22

Artifact: `dist/duolingo-reminders-1.0.0.mpp` (65,303 bytes).

SHA-256:
`6318e9a92ee4064ebbb98c8ab4df4ffc93831621aeabe5358c95933199fd9721`

- Built twice from the repository sources with `Build.ps1`; identical SHA-256.
- D8 9.4.34 release/minimum API 26 compiled the patch and extension without warnings.
- Bundle contains root `classes.dex`, desktop JVM classes, embedded
  `extensions/duolingo.mpe`, and standard manifest attributes including
  `Patcher-Version: 1.14.1`.
- Actual Morphe Desktop 1.17.0 engine `PatchBundleLoader.loadFlat` loaded exactly
  one patch: **Restore learning reminders**, disabled by default, version 1.0.0.
- Actual Patcher 1.14.1 applied that loaded patch and serialized modified DEX
  successfully against the supplied original Duolingo 6.98.4 base APK and the
  existing `v6.98.4-patches-v1.46.0.apk`.
- Runtime classpath contained only the official desktop fat JAR and validation
  harness. The patches library was absent, confirming the bundle needs no
  runtime classes from that library or another patch source.
- Bytecode audit of the original-APK result passed all nine checks: original
  manager methods preserved; correct failure hook and logout hook; preference
  guard before writes; existing alarm scheduling and native executor retained;
  original branch destinations retained; generated branches valid.

No Android device or emulator was connected. Android's DEX class loader and the
Manager import UI were not executed; actual notification delivery remains
unverified. Desktop loader/application checks and Android-format packaging are
verified separately and should not be described as a completed device test.
