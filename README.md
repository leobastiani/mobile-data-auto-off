# Mobile Data Auto Off

An Android app that disables mobile data while connected to Wi-Fi and enables it
again after two minutes without Wi-Fi. Reconnecting before the timer ends cancels
restoration. Uses [Shizuku](https://shizuku.rikka.app/) to execute privileged Android
commands without requiring root.

The installed app is currently named **Wi-Fi Automático**. Its interface and
logs are in Brazilian Portuguese.

## Download and setup

1. Download the signed APK from [GitHub Releases](https://github.com/leobastiani/mobile-data-auto-off/releases/latest).
2. Open the APK on your phone and allow installation from that source when prompted.
3. Install and start Shizuku using its [official setup guide](https://shizuku.rikka.app/guide/setup/).
4. Open **Wi-Fi Automático**, tap **Autorizar Shizuku**, and grant access.
5. Tap **Ativar automação**. Allow notifications to see the persistent status and stop action.

Requires Android 11 or newer, active and authorized Shizuku, and firmware that
allows the shell user to run `cmd phone data enable` and `cmd phone data disable`.
On HyperOS, allow unrestricted battery use and autostart for both apps if available.
After reboot, start Shizuku again. The app attempts to resume previously enabled
automation after boot, but Android or manufacturer restrictions may block startup.

**Early release:** mobile data commands were verified on a Poco X7 Pro
(Xiaomi 2412DPC0AG), Android 16, on October 13, 2026. A device probe disabled and
enabled data twice, verified `isDataEnabled()` after every command, and restored
the initial enabled state. The complete Wi-Fi transition and 120-second timer
still need everyday-use validation. Other devices have not been verified.

## Behavior and limitations

- Wi-Fi connectivity is monitored, even if that Wi-Fi has no internet access.
- Stopping automation attempts to enable data regardless of its initial state.
- Manual mobile data changes are not preserved while automation is running.
- If Shizuku stops while data is off, you may need to enable data manually in quick settings.
- Manufacturer battery restrictions can interrupt monitoring. The two-minute timer
  may run late while the phone sleeps.
- Commands execute serially, with a 15-second timeout and captured output.
- Uses the Shizuku remote-process interface pinned through API 13.1.5 to avoid
  a UserService startup failure on HyperOS/MediaTek ([upstream issue](https://github.com/RikkaApps/Shizuku/issues/1198)).

## Privacy and permissions

No ads, analytics, account, or external server. The app does not request Android's
`INTERNET` permission. Logs stay locally on the device unless you copy and share
them. The history is limited to 24,000 characters and includes device model,
Android/build version, automation events, command output, Shizuku UID, and observed
data state. It does not intentionally record phone numbers, Wi-Fi names, or device
identifiers. Review logs before sharing: system errors can contain additional information.

| Permission | Purpose |
| --- | --- |
| Network state | Observe Wi-Fi connections. |
| Phone state | Read mobile data status for display, toggle, and diagnostics. |
| Notifications | Display monitoring status and a stop action. |
| Foreground service / special use | Keep the Wi-Fi monitor running. |
| Boot completed | Attempt to resume enabled automation after reboot. |
| Shizuku authorization | Run data-control commands as the shell user. |

## Build from source

Install JDK 17, Android SDK Platform 35, and Build Tools 35.0.0. Set `ANDROID_HOME`
or configure `sdk.dir` in the ignored `local.properties`. The project uses Android
Gradle Plugin 8.7.3 and Gradle 8.9. The first build downloads Gradle and Maven dependencies.

```sh
git clone https://github.com/leobastiani/mobile-data-auto-off.git
cd mobile-data-auto-off
./gradlew assembleDebug
./gradlew lintDebug lintRelease assembleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.
`./build.sh` wraps `./gradlew` and detects common SDK locations.

For signed builds, copy `keystore.properties.example` to `keystore.properties`,
provide your own keystore and credentials, and run `./gradlew assembleRelease`.
Keys and credentials must never be committed. See [RELEASING.md](RELEASING.md).

GitHub releases use the maintainer's release certificate. A debug installation
uses a different key and must be uninstalled first (clearing settings and logs).
Future distribution channels may also use different signing certificates.

## Diagnostics and testing

Stop automation and wait for its restore attempt before manual tests. Buttons:
**Ativar dados móveis** (enable), **Desligar dados móveis** (disable), and
**Toggle dos dados móveis**. Toggle requires phone-state permission; grant it
and tap again if requested. If the state cannot be read reliably, toggle is cancelled.
Command success only indicates no execution error was detected: verify Android's
data switch. Enabled data does not guarantee internet access.

**Copiar log de diagnóstico** copies logs; **Limpar log** clears them.
Report problems in [GitHub Issues](https://github.com/leobastiani/mobile-data-auto-off/issues)
with Android version, device model, Shizuku version, reproduction steps, and a reviewed log.

The debug build includes an ADB probe that changes data four times, reads the actual
state, and attempts to restore the initial state in `finally`. It is excluded from
release builds. Run with automation stopped and Shizuku authorized:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant br.com.pessoal.wifiautomatico android.permission.READ_PHONE_STATE
adb shell am instrument -w br.com.pessoal.wifiautomatico/br.com.pessoal.wifiautomatico.DataControlProbe
```

For an automation check: connect to Wi-Fi and confirm data switches off; disconnect
and reconnect within two minutes and confirm the timer cancels; disconnect for more
than two minutes and confirm data switches on; stop and check restoration. Repeat
with the screen off and with Shizuku stopped.

## Source layout

- `MainActivity.java`: authorization, automation controls, tests, and logs.
- `WifiMonitorService.java`: Wi-Fi events, timer, and foreground notification.
- `MobileDataCommand.java`: Shizuku commands, timeout, and output capture.
- `DiagnosticLog.java`: persistent diagnostic history.
- `BootReceiver.java`: attempts to resume enabled automation after boot.
- `app/src/debug/`: device probe, excluded from releases.

## F-Droid

Submission is planned; the app is not yet available in F-Droid. This repository
provides licensed source, versioned release tags, a Gradle Wrapper, unsigned release
builds without private signing material, and store metadata. Inclusion still requires
review under the [F-Droid inclusion policy](https://f-droid.org/docs/Inclusion_Policy/).

## License

Copyright (C) 2026 Leonardo Guarnieri de Bastiani.
Licensed under **GPL-3.0-only**; see [LICENSE](LICENSE).
Shizuku dependencies retain their [upstream licenses](https://github.com/RikkaApps/Shizuku-API).

Gradle Wrapper files are licensed under Apache-2.0; see [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt).
