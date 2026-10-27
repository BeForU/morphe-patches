package hoodles.morphe.patches.instagram.scrollnux

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

private object OnboardingNuxDisabledFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("android_purge_26_q3_ClipsViewerOnboardingNuxController_isNuxDisabledForOnBoarding")
)

private object AnimateBounceNuxFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("android_purge_26_q3_ClipsViewerOnboardingNuxController_animateBounceNux")
)

private object ScrollForPeekBounceFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("F"),
    strings = listOf("android_purge_26_q3_ClipsViewPagerImpl_scrollForPeekBounce")
)

@Suppress("unused")
val hideSwipeUpGuidePatch = bytecodePatch(
    name = "Hide swipe up guide",
    description = "Hides the viewer's swipe-up onboarding guide and automatic peek/bounce movement, " +
        "while preserving manual swiping between posts and Reels.",
    default = false
) {
    compatibleWith(
        Compatibility(
            name = "Instagram",
            packageName = "com.instagram.android",
            apkFileType = ApkFileType.APKM,
            targets = listOf(AppTarget("452.1.0.53.64", versionCode = 385811006))
        )
    )

    execute {
        // Resolve all targets before modifying anything. The diagnostic strings, rather
        // than obfuscated class names, identify the exact routines in the supported APK.
        val disabled = OnboardingNuxDisabledFingerprint.method
        val animate = AnimateBounceNuxFingerprint.method
        val peek = ScrollForPeekBounceFingerprint.method
        check(disabled.definingClass == animate.definingClass) {
            "The onboarding gate and bounce animation no longer belong to the same controller."
        }

        // Both the normal/digest guide and the delayed direct-share guide consult this
        // gate before enabling their overlays or recording an onboarding impression.
        disabled.addInstructions(0, "const/4 v0, 0x1\nreturn v0")

        // Also stop the dedicated animation entry points. Do not change touch handling,
        // setUserInputEnabled, ordinary scrolling, or the end/cancel cleanup routines.
        animate.addInstructions(0, "return-void")
        peek.addInstructions(0, "return-void")
    }
}
