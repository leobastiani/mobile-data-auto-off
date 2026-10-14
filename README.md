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
5. Use **Permitir alarmes durante o repouso** to allow exact alarms. Use **Configurar bateria em segundo plano** to exempt the app from battery optimization, and allow unrestricted battery use and autostart for both this app and Shizuku in manufacturer settings.
6. Tap **Ativar automação**. Allow notifications to see the silent monitoring notification and stop action.

Requires Android 11 or newer, active and authorized Shizuku, and firmware that
allows the shell user to run `cmd phone data enable` and `cmd phone data disable`.
On HyperOS, allow unrestricted battery use and autostart for both apps if available.
After reboot, start Shizuku again. The app attempts to resume previously enabled
automation after boot, but Android or manufacturer restrictions may block startup.

**Early release:** mobile data commands were verified on a Poco X7 Pro
(Xiaomi 2412DPC0AG), Android 16, on October 13, 2026. A device probe disabled and
enabled data twice, verified `isDataEnabled()` after every command, and restored
the initial enabled state. The wake-up alarm, complete Wi-Fi transition, and 120-second timer
still need screen-off and Doze validation on the physical device. Other devices have not been verified.

## Behavior and limitations

- While automation runs, a single silent foreground-service notification remains
  available with a stop action. Wi-Fi transitions and mobile data commands do not
  send or update notifications; detailed status and errors stay in the app and log.
- Wi-Fi connectivity is monitored, even if that Wi-Fi has no internet access.
- Stopping automation attempts to enable data regardless of its initial state.
- Manual mobile data changes are not preserved while automation is running.
- If Shizuku stops while data is off, you may need to enable data manually in quick settings.
- Restoration uses a wake-up alarm that runs during idle, with a persisted deadline
  preserved across process restarts within the same boot. Reconnection or stopping cancels it.
  A reboot starts a new wait after Wi-Fi is checked. Exact alarm access is checked before
  scheduling; without it, an inexact idle alarm is used and restoration can run late.
- Manufacturer restrictions, forced stops, and Android idle alarm quotas can still delay
  or interrupt automation. Opening the app reconciles previously enabled monitoring.
  If Shizuku is unavailable at the deadline, restoration remains pending until it returns.
- Short wake locks (30-second maximum) keep alarm delivery and data commands awake;
  no wake lock is held during the two-minute wait.
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
| Exact alarms (special access) | Schedule the two-minute restoration during idle. |
| Wake lock | Keep alarm delivery and commands awake briefly. |
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
