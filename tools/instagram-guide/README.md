# Instagram swipe guide patch

Target: `com.instagram.android` **452.1.0.53.64** (version code 385811006).

`Hide swipe up guide` is opt-in. It disables the viewer onboarding gate and
the dedicated bounce/peek animation entry points. It does not disable user input,
ordinary post/Reel scrolling, video playback, or carousel navigation.

## Use

Import `dist/instagram-guide-1.0.0.mpp` as a local patch bundle in a compatible
Morphe Manager, then select **Hide swipe up guide** when patching the supported
original Instagram APK. This file is an additional standalone bundle, not an
Android application and not a replacement for the existing Hoodles bundle.
Leave **Disable Reels scrolling** unselected if manual Reel navigation is wanted.

The supplied `.apkm` is a split archive. The analyzed input was its `base.apk`;
the density splits were not modified. Preserve the density splits when building
an installable split package, or use a supported merged APK workflow. This task
does not produce a signed installable Instagram APK or change an installed app.

## Implementation evidence

The supplied APK resolves these unique diagnostic fingerprints:

| Original method | Change |
| --- | --- |
| `X/02Yd.A09`: `ClipsViewerOnboardingNuxController_isNuxDisabledForOnBoarding` | Return true |
| `X/02Yd.A04`: `ClipsViewerOnboardingNuxController_animateBounceNux` | Return immediately |
| `X/04QD.GwE`: `ClipsViewPagerImpl_scrollForPeekBounce` | Return immediately |

The ordinary guide, digest guide, and delayed direct-share guide consult the
disabled gate. The bounce routine also enables the swipe-guide overlay through
`maybeToggleNuxOverlay`; blocking it prevents that overlay path from starting.
The viewer supports different media item types, so the patch is not limited by
video playback. Other unrelated guide systems, including horizontal grid nudges,
are outside its scope. A report from a different photo viewer may require another
fingerprint after screen verification.

## Validation (2026-10-27)

- Kotlin compilation and Android bundle DEX generation passed.
- Morphe Desktop 1.18.1's bundle loader loaded the one opt-in patch and verified
  the supported package/version metadata.
- The real patch engine applied all three fingerprints to the supplied original
  APK and finished STRIP_SAFE DEX generation successfully.
- `VerifyDex.java` verified the emitted return instructions and byte-for-byte
  normalized equality of all other methods and class metadata in both touched
  classes, including the ordinary navigation implementation.
- No connected ADB device was available. App startup, actual Korean guide
  disappearance, photos/Reels, and manual swiping still need device testing.
- Piko **3.11.0-dev.3** targets the same Instagram version. Joint selection of
  `Add settings`, `Disable ads`, `Disable video autoplay`, and this guide patch
  passed patch execution, resource compilation and DEX generation using Morphe
  Desktop **1.20.0-dev.3**. The three guide return instructions and all other
  methods in its two classes passed the same DEX comparison after joint patching.
  This does not cover all optional Piko selections or device execution.
- The complete Hoodles bundle is published by the repository's verified remote
  source workflow. Its catalog must contain this opt-in patch before publication.

Local analysis and patch-engine logs are under ignored `work/instagram/`.

## Rebuild

Run `Build.ps1` with JDK 21, a Gradle distribution containing Kotlin 2.4,
Morphe Desktop 1.18.1's all-in-one JAR, R8 9.4.34, and Android 36's android.jar:

```powershell
./tools/instagram-guide/Build.ps1 -JdkHome <jdk> -GradleHome <gradle> `
  -DesktopJar <desktop.jar> -R8Jar <r8.jar> -AndroidJar <android.jar>
```

`Validate.kt` loads the built bundle and applies it to an original base APK.
Compile it against the same Desktop JAR, then run with four arguments: bundle,
original APK, a new temporary directory, and the output DEX directory.
Compile `VerifyDex.java` against the Desktop JAR and run it with the original
APK and the emitted DEX containing the two modified classes (`classes.dex` in
the validated STRIP_SAFE run).
