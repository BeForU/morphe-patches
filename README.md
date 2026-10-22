# BeForU Morphe Patches

## Duolingo 6.102.4 and learning reminders

[Add this repository to Morphe Manager](https://morphe.software/add-source?github=BeForU/morphe-patches&name=BeForU%20Morphe%20Patches) · [Download version 1.49.0](https://raw.githubusercontent.com/BeForU/morphe-patches/main/dist/patches-1.49.0.mpp)

This fork's remote source serves **BeForU Morphe Patches 1.49.0**. It combines the upstream **Hoodles Morphe Patches 1.47.0** catalog with the current Duolingo **6.102.4** patches, the login fingerprint fix from [upstream PR #1117](https://github.com/hoo-dles/morphe-patches/pull/1117), and `Restore learning reminders`. Duolingo patches and universal patches including `MicroG integration` can now be selected from this one source.

1. Open Morphe Manager's **Sources → + → Remote** and add `https://github.com/BeForU/morphe-patches`, or use the add-source link above. If this repository is already added as **Duolingo Learning Reminders 1.48.0**, tap its **Update** button. Confirm that the downloaded bundle is version **1.49.0**.
2. Choose an original Duolingo **6.102.4** APK. In this source, select **Enable Premium**, **MicroG integration**, and **Restore learning reminders**, plus any other Duolingo patches you want. The reminder patch is off by default; enable Expert mode if needed to show and select it.
3. Clear Duolingo patch selections from the old **Hoodles Morphe Patches 1.47.0** source and any old local reminder source. Select each patch once from this updated source. To save this choice, long-press Duolingo's home card, open **Patch sources**, and keep only this source enabled for Duolingo. Other sources can remain installed for other apps.
4. Open Duolingo's home screen after installation and keep learning notifications enabled in Duolingo and Android.

Remote sources receive future published bundle updates through this repository's `patches-bundle.json`. An existing local `.mpp` source does not become remote automatically: add the repository once, then disable or delete the old local reminder source to avoid selecting it twice. Local import under **Sources → + → Local** is also supported, but future updates then require another file import. Upstream releases are incorporated when a new bundle is published here; Manager does not merge another source into this bundle automatically.

The bundle declares Patcher `1.14.1`; see [validation results](tools/duolingo-reminders/VALIDATION.md) for the engines and patch combinations actually checked. Build and publishing instructions are in [tools/duolingo-reminders](tools/duolingo-reminders/README.md). The archived [1.48.0 reminder-only bundle](https://raw.githubusercontent.com/BeForU/morphe-patches/main/dist/duolingo-reminders-1.48.0.mpp) targets 6.102.4, and [1.0.0](https://raw.githubusercontent.com/BeForU/morphe-patches/main/dist/duolingo-reminders-1.0.0.mpp) targets 6.98.4.

If `Enable Premium` fails with `BasicLoginFingerprint` while the report lists **Hoodles Morphe Patches 1.47.0**, that patch came from the old source. The 1.48.0 reminder-only bundle did not replace its login dependency. Update this repository and select all Duolingo patches from version **1.49.0** to use the corrected dependency together with the reminder fallback.

## Upstream hoodles Morphe Patches catalog

The following documentation and catalog are inherited from [hoo-dles/morphe-patches](https://github.com/hoo-dles/morphe-patches). This fork includes the upstream 1.47.0 catalog and the Duolingo updates described above. Other apps retain their upstream compatibility requirements.

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
