# Hoodles Morphe Patches (Modded)

## Instagram swipe-up guide (with Piko)

This remote source includes the opt-in **Hide swipe up guide** patch for
Instagram **452.1.0.53.64**. Update this source in Morphe Manager, then select
this patch alongside the Instagram patches you want from Piko. Leave
**Disable Reels scrolling** off to preserve manual swiping. The guide patch
does not change touch handling or normal scrolling.

Use a Piko **pre-release** supporting **452.1.0.53.64**; Piko's stable source
currently targets an older version. Joint application with Piko **3.11.0-dev.3**
(`Add settings`, `Disable ads`, `Disable video autoplay`) passed patch execution,
resource compilation and DEX generation on the supplied APK. Other Piko patch
combinations and device behavior remain untested. The guide patch also passed
standalone application and DEX checks.
See [implementation and validation](tools/instagram-guide/README.md).

## Duolingo 6.106.7 and learning reminders

This repository provides one complete **Hoodles Morphe Patches (Modded)** source: Duolingo patches, the optional learning reminder fallback, and universal patches including **MicroG integration** are in the same bundle. No separate Hoodles patch source or reminder add-on is needed. Morphe Manager and an original compatible APK are still required.

[Add this repository to Morphe Manager](https://morphe.software/add-source?github=BeForU/morphe-patches&name=Hoodles%20Morphe%20Patches%20%28Modded%29) · [Bundle metadata](patches-bundle.json) · [Patch catalog](patches-list.json)

1. Add `https://github.com/BeForU/morphe-patches` as a remote source in Morphe Manager, or update it if already added. The source name can be changed using **Sources → Rename**.
2. Select an original Duolingo **6.106.7** APK and choose **Enable Premium**, **MicroG integration**, and, if wanted, **Restore learning reminders** from this source. The reminder patch is off by default; use Expert mode if needed. Choose SUPER or MAX in the Premium options.
3. Disable duplicate Duolingo patches from other sources, then patch the original APK. Updating the source does not modify an already installed app. Keep learning notifications enabled in both Duolingo and Android.

A local `.mpp` can also be imported, but local sources do not update automatically. The endpoint and download link above describe the version actually published on the repository's `main` branch; changes in an unpushed checkout are not available remotely.

The Hoodles upstream sync is paused. The complete source in this repository can be built independently of that upstream repository and its release bundle; see [standalone build instructions](tools/upstream-sync/STANDALONE.md). The normal build uses Morphe's Gradle plugin and libraries; an alternative builds pinned public sources for those tooling dependencies without GitHub Packages credentials. Neither route requires an older Hoodles patch bundle. Existing historical overlay and sync tools are retained for reference, not required for standalone builds.

See [validation results](tools/duolingo-reminders/VALIDATION.md) for the exact APK, patch combinations and device checks performed, including remaining limitations. Older `.mpp` files in `dist` are archives.

## Upstream hoodles Morphe Patches catalog

The following documentation and table are inherited from [hoo-dles/morphe-patches](https://github.com/hoo-dles/morphe-patches). The generated [current patch catalog](patches-list.json) is authoritative for the published bundle; this table is a documentation snapshot. Other apps retain their upstream compatibility requirements.

![GitHub Workflow Status (with event)](https://img.shields.io/github/actions/workflow/status/hoo-dles/morphe-patches/release.yml)
![GPLv3 License](https://img.shields.io/badge/License-GPL%20v3-yellow.svg)

<br/>

> [!NOTE]
> Patch requests are always welcome, **but please read the [announcement](https://github.com/hoo-dles/morphe-patches/discussions/232) before opening an issue!**

<br/>

| App                                                  | Package                                    | Patches                                                                                                                                                                  |
|------------------------------------------------------|--------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AdGuard                                              | `com.adguard.android`                      | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| AllTrails                                            | `com.alltrails.alltrails`                  | <ul><li>Enable Peak membership</li></ul>                                                                                                                                 |
| Amazon Prime Video                                   | `com.amazon.avod.thirdpartyclient`         | <ul><li>Enable speed control</li><li>Skip ads</li></ul>                                                                                                                  |
| ASL Bloom                                            | `com.toleio.us`                            | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Avocards                                             | `com.avocards`                             | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Bend :dart: :computer:                               | `com.bowerydigital.bend`                   | <ul><li>Enable Premium</li><ul>                                                                                                                                          |
| Bunpo :dart: :computer:                              | `com.bunpoapp`                             | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Busuu                                                | `com.busuu.android.enc`                    | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Cake :dart:                                          | `me.mycake`                                | <ul><li>Enable Plus</li></ul>                                                                                                                                            |
| CamScanner                                           | `com.intsig.camscanner`                    | <ul><li>Disable telemetry</li><li>Enable Premium</li></ul>                                                                                                               |
| DAILY POCKET                                         | `kr.co.yjteam.dailypay`                    | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Duolingo                                             | `com.duolingo`                             | <ul><li>Disable dynamic app icon</li><li>Enable debug mode</li><li>Enable Super/MAX</li><li>Restore learning reminders</li><li>Unlimited RampUp time</li></ul>                                              |
| Eggbun                                               | `kr.eggbun.eggconvo`                       | <ul><li>Enable Premium</li><li>Force native keyboard</li></ul>                                                                                                           |
| FotMob                                               | `com.mobilefootie.wc2010`                  | <ul><li>Enable FotMob+</li></ul>                                                                                                                                         |
| GitHub                                               | `com.github.android`                       | <ul><li>AMOLED dark theme</li></ul>                                                                                                                                      |
| Google News                                          | `com.google.android.apps.magazines`        | <ul><li>Enable custom tabs</li><ul>                                                                                                                                      |
| Goodreads                                            | `com.goodreads`                            | <ul><li>Disable ads</li></ul>                                                                                                                                            |
| HelloChinese :computer:                              | `com.hellochinese`                         | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| IbisPaint :computer:                                 | `jp.ne.ibis.ibispaintx.app`                | <ul><li>Enable Prime membership</li></ul>                                                                                                                                |
| Icon Packer                                          | `cn.ommiao.iconpacker`                     | <ul><li>Unlocked premium features</li></ul>                                                                                                                              |
| Lightroom Mobile :dart: :computer:                   | `com.adobe.lrmobile`                       | <ul><li>Unlock premium features</li></ul>                                                                                                                                |
| Ling :dart:                                          | `com.simyasolutions.ling.universal`        | <ul><li>Enable Pro</li></ul>                                                                                                                                             |
| Lingodeer :lock: :dart: :computer:                   | `com.lingodeer`                            | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Lingory :dart: :computer:                            | `org.languageapp.lingory`                  | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Lyfta                                                | `com.lyfta`                                | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| MacroFactor :lock: <br/> MacroFactor Workouts :lock: | `com.sbs.diet`<br/>`com.sbs.train`         | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Meme Generator                                       | `com.zombodroid.MemeGenerator`             | <ul><li>Enable Pro</li></ul>                                                                                                                                             |
| Merriam-Webster                                      | `com.merriamwebster`                       | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Mimo                                                 | `com.getmimo`                              | <ul><li>Enable Pro</li></ul>                                                                                                                                             |
| Mirinae                                              | `com.mirinae.mirinae`                      | <ul><li>Enable Pro</li></ul>                                                                                                                                             |
| MyExpenses                                           | `org.totschnig.myexpenses`                 | <ul><li>Enable Pro</li></ul>                                                                                                                                             |
| MyFitnessPal                                         | `com.myfitnesspal.android`                 | <ul><li>Enable Premium+</li></ul>                                                                                                                                        |
| Niagara Launcher                                     | `bitpit.launcher`                          | <ul><li>Enable Pro</li></ul>                                                                                                                                             |
| NOMone Desktop                                       | `nom.vrd`                                  | <ul><li>Disable telemetry</li><li>Remove trial limit</li></ul>                                                                                                           |
| Nova Launcher                                        | `com.teslacoilsw.launcher`                 | <ul><li>Enable Prime</li></ul>                                                                                                                                           |
| Pandora                                              | `com.pandora.android`                      | <ul><li>Disable ads</li><li>Unlimited skips</li></ul>                                                                                                                    |
| Podcast Addict                                       | `com.bambuna.podcastaddict`                | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Proton VPN                                           | `ch.protonvpn.android`                     | <ul><li>Remove server change delay</li><li>Unlock custom DNS</li><li>Unlock LAN connections</li><li>Unlock split tunneling</li></ul>                                     |
| PyDroid3                                             | `ru.iiec.pydroid3`                         | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| RP Hypertrophy                                       | `com.rp.hypertrophy`                       | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Showly                                               | `com.michaldrabik.showly2`                 | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Smart Launcher                                       | `ginlemon.flowerfree`                      | <ul><li>Enable Pro</li></ul>                                                                                                                                             |
| SnoreLab                                             | `com.snorelab.app`                         | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Sofascore                                            | `com.sofascore.results`                    | <ul><li>Disable ads</li></ul>                                                                                                                                            |
| SoundCloud                                           | `com.soundcloud.android`                   | <ul><li>AMOLED dark theme</li><li>Enable SoundCloud Go</li><li>Disable telemetry</li></ul>                                                                               |
| Solid Explorer                                       | `pl.solidexplorer2`                        | <ul><li>Enable Pro</li><ul>                                                                                                                                              |
| SuperChinese                                         | `com.superchinese`                         | <ul><li>Block launch upsell</li><li>Disable telemetry</li><li>Unlock all lessons</li></ul>                                                                               |
| TTMIK Stories :dart:                                 | `app.ttmikstories.android`                 | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Ventusky                                             | `cz.ackee.ventusky`                        | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Wallcraft                                            | `com.wallpaperscraft.wallpaper`            | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| Windy                                                | `com.windyty.android`                      | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| World Map Quiz                                       | `com.qbis.guessthecountry`                 | <ul><li>Enable Premium</li></ul>                                                                                                                                         |
| WPS Office                                           | `cn.wps.moffice_eng`                       | <ul><li>Disable anti-tamper checks</li><li>Enable Pro</li></ul>                                                                                                          |
| Xodo                                                 | `com.xodo.pdf.reader`                      | <ul><li>Enable Pro</li></ul>                                                                                                                                             |
| XRecorder                                            | `videoeditor.videorecorder.screenrecorder` | <ul><li>Enable Pro</li></ul>                                                                                                                                             |
|                                                      |                                            |                                                                                                                                                                          |
| _Universal Patches_                                  |                                            | <ul><li>Change package name</li><li>Disable Pairip license check</li><li>Enable debug</li><li>Hide app icon</li><li>MicroG integration</li></ul> |

:lock: _Requires Root Mount installation method_\
:dart: _This app has a strict requirement to use the version defined in the patch._\
:computer: _These patches will only work for devices running `arm64-v8a` CPUs._

<br>

## Frequently Asked Questions :man_shrugging:

#### How do I use this thing?
Add `https://github.com/BeForU/morphe-patches` as a remote source using the instructions above. This bundle includes the upstream catalog and the updated Duolingo patches. Use the patches for Duolingo from this source together to avoid selecting an older dependency from another source.

#### Help, why can't I log in with my Google account? Why doesn't Google Drive work?
MicroG integration is needed for features that require Google Play Services. Please use the universal `MicroG integration` patch provided by this repo.

#### Duolingo learning reminders do not arrive when push registration fails

Select the optional `Restore learning reminders` patch and open Duolingo's home screen after installation. If FCM device registration fails, this patch enables Duolingo's local reminder fallback and replaces the alarm for the current learning language at the configured reminder time. It respects the user's learning notification setting and keeps the app's original activity and expiry checks. Existing language records do not prevent the alarm from being scheduled again.

This patch does not repair FCM registration or detect messages that silently fail to arrive after successful registration. Android notification permissions, notification channels, and background restrictions still apply.

The local fallback stays enabled if FCM later recovers, so both delivery paths can produce reminders. Its asynchronous state tests run with `./gradlew :extensions:duolingo:testFallbackCoordinator` and are included in that module's `check` task.

#### What apk version should I use?
Versions I have tested are defined in each patch, but they may (hopefully?) work for newer app releases. Give it a shot and open a ticket if there's issues. Patches marked with a :dart: will, almost certainly, only work with that specific version.

#### Can you make a patch for \[Super Cool App Goes Here\]?
Maybe? Each patch is different, and many features use server-side functionality that cannot be modified. Before submitting a request, please check the existing open (and closed!) issues to reduce duplication.

#### You haven't completed my patch request.. Did you not see it? / Did you forget? / Why do you hate me?
I did see it. I haven't forgotten. We're good, bro. I'm a one-person "team" who does this stuff in my free time. If it's within my technical capabilities, I'll get around to it at some point.
