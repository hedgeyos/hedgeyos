# Test Report

Date: 2026-07-28

Current status: `0.1.0-alpha.3` boots the bundled Debian 13/XFCE desktop as
the Android Home screen on the attached ARM64 phone, opens an XFCE terminal
inside Debian, runs `apt update`, and installs/runs the Debian `hello` package.
This is real device evidence, not just APK inspection. The final `v0.1.0`
tag/release is still not published because the full reset/clean acceptance pass
has not been completed.

## Current Device Evidence

Device:

- `ab6b77a8`, model `CPH2499`, ARM64 Android target attached over adb.

Current test build:

- APK:
  `build/device-test/acceptance-2026-07-28/alpha3-final/hedgeyos-arm64-v8a-alpha.3-test-signed.apk`
- Version: `0.1.0-alpha.3`
- Version code: `3`
- SHA-256:
  `b1e00213259c4861026b0969edd91c1d6807bd06895804ee2bdf64c2fab3559c`
- Test signing certificate SHA-256:
  `ddea70a805747e040fe393a8c8024b04fb9a0ed2be532c5b37bcdcbbcb0c9872`
- `apksigner verify --verbose --print-certs`: v2 and v3 signatures verified.

APK inspection:

- Package: `org.hedgeyos`
- Launcher/Home activity: `com.termux.x11.HedgeyosHomeActivity`
- Android Home category: present
- Fallback native Home activity: disabled in APK inspection
- Standalone Termux:X11 launcher: absent
- Native code: `arm64-v8a`
- Bundled rootfs: `assets/debian-trixie-arm64-rootfs.tar.zst`
- Bundled PRoot: `assets/termux-proot-aarch64.tar.zst`
- Embedded X11 library: `lib/arm64-v8a/libXlorie.so`
- `libXlorie.so` zip method: `Stored`, not deflated
- VNC/RDP files: absent from APK listing

Fresh first boot on device:

- Install command: `adb install --no-incremental`
- Home command:
  `cmd package set-home-activity org.hedgeyos/com.termux.x11.HedgeyosHomeActivity`
- Runtime state: `RUNNING`
- Screenshot:
  `docs/images/hedgeyos-scaled-firstboot-xfce.png`
- Evidence folder:
  `build/device-test/acceptance-2026-07-28/`

Runtime log sequence:

```text
INSTALLING_PROOT: Installing bundled PRoot runtime.
VERIFYING_ASSET: Preparing bundled Debian rootfs asset.
EXTRACTING: Extracting Debian rootfs.
CONFIGURING: Configuring Debian rootfs.
READY: Debian rootfs is installed.
STARTING_X11: Starting embedded Termux:X11 server.
STARTING_DESKTOP: Starting Debian XFCE through bundled PRoot.
RUNNING: hedgeyos desktop supervisor is running.
```

X11 log:

```text
Command: [/system/bin/app_process, -Xnoimage-dex2oat, /, --nice-name=hedgeyos-x11, com.termux.x11.CmdEntryPoint, :1]
TMPDIR=/data/user/0/org.hedgeyos/files/debian/tmp
XKB_CONFIG_ROOT=/data/user/0/org.hedgeyos/files/debian/usr/share/X11/xkb
Embedded Termux:X11 server is running.
```

Observed hedgeyos process path:

```text
org.hedgeyos
hedgeyos-x11
proot
xfce4-session
dbus-launch
dbus-daemon
xfce4-panel
```

Debian terminal and APT evidence:

- XFCE terminal screenshot:
  `build/device-test/acceptance-2026-07-28/hedgeyos-terminal-final-scaled-os-release-20260728.png`
- Alpha.3 APT log:
  `build/device-test/acceptance-2026-07-28/debian-acceptance-alpha3-apt-hello-20260728.log`
- The log shows Debian 13/Trixie, `apt update`, `apt install hello`, `Hello, world!`, and `hello 2.10-5 install ok installed`.
- `Open Debian Terminal` now launches `xfce4-terminal` inside bundled Debian
  through hedgeyos's bundled PRoot path; it no longer opens the Android/Termux
  terminal activity.

Termux and VNC dependency evidence:

- Existing `com.termux`, `com.termux.api`, `com.termux.window`, and
  `com.iiordanov.freebVNC` packages on the phone were preserved.
- No `com.termux.x11` package was installed.
- hedgeyos started `hedgeyos-x11` from its own APK through
  `CLASSPATH=context.getPackageCodePath()`.
- hedgeyos started Debian through its bundled `proot` and bundled rootfs under
  `org.hedgeyos` private app storage.
- The observed hedgeyos process tree contains no VNC server/viewer process, and
  the APK listing contains no VNC/RDP payload files.

## Fixed Device Failures

| Failure | Cause | Fix |
| --- | --- | --- |
| Rootfs extraction failed with tar exit code 2 | Android could not extract special `/dev` nodes and some preserved ownership/mode metadata | Rebuilt the rootfs as Android-extractable and added runtime tar flags `--no-same-owner --no-same-permissions --delay-directory-restore`. |
| Reset/extraction cleanup failed under `/dev/fd` | Recursive delete followed symlinks | `deleteRecursively()` now uses `NOFOLLOW_LINKS`. |
| Sudoers rewrite failed with permission denied | Existing app-owned 0440 file could not be overwritten | Runtime file writer chmods existing app-owned read-only files before overwrite. |
| Embedded X11 exited with code 137 | `libXlorie.so` was deflated in the APK, but `CmdEntryPoint` loads it directly from the APK path | Release packaging keeps native libraries uncompressed and the APK inspector enforces `Stored`. |
| Embedded X11 exited during startup | XKB config root was not set for the embedded server | `hedgeyosX11Bridge` sets `XKB_CONFIG_ROOT` to the bundled Debian rootfs XKB path. |
| XFCE desktop was tiny on the phone | X11 display defaults used an unscaled desktop or an older one-time scale profile | `HedgeyosHomeActivity` now applies versioned scaled-resolution defaults, `displayScale=240`, fullscreen, and keeps the extra-key bar visible. |
| Open Debian Terminal launched the Android terminal activity | The hedgeyos menu still used the inherited Termux activity path | The menu now launches `xfce4-terminal` inside bundled Debian through hedgeyos PRoot and prints `/etc/os-release`. |
| APT proof could not be typed reliably through Android IME | XFCE terminal focus and soft keyboard input were unreliable through adb | Added `Run Debian APT Check`, which runs `apt update`, `apt install hello`, `hello`, and `dpkg-query` inside bundled Debian and writes `debian-acceptance.log`. |
| Build script could print success after Gradle failed | Gradle output was piped through `tee`, so POSIX `sh` saw `tee` status | `scripts/build-hedgeyos.sh` now preserves Gradle failure status before printing `Built`. |

## Acceptance Matrix

| Requirement | Status | Evidence |
| --- | --- | --- |
| Build/sign current `0.1.0-alpha.3` line | Pass for local test-signed APK | Current APK above, signed and verified with v2/v3 signatures. |
| Install on attached ARM64 Android target | Pass | Installed on CPH2499 with `adb install --no-incremental`. |
| hedgeyos opens from app icon | Not yet directly captured | Home launch is proven; launcher icon tap/monkey evidence still needed. |
| Android offers/selects hedgeyos as Home app | Pass | `set-home-activity` succeeded and activity resumed from `android.intent.category.HOME`. |
| First boot requires no rootfs download | Pass | Runtime extracted `assets/debian-trixie-arm64-rootfs.tar.zst` from the APK. |
| Bundled rootfs verified/extracted | Pass | `VERIFYING_ASSET`, `EXTRACTING`, `CONFIGURING`, `READY`, then `RUNNING`. |
| Embedded X11 surface appears | Pass | Screenshot and `hedgeyos-x11` process/log evidence. |
| XFCE usable desktop appears | Pass | Screenshot shows scaled XFCE desktop icons, hedgeyos overlay, and extra-key bar. |
| Touch input works | Pass for hedgeyos controls | Taps opened the hedgeyos menu, Debian terminal, APT check, Android Apps, Android Settings, and Restart Desktop. |
| Soft keyboard / extra-key controls | Pass for extra-key bar, partial for full IME | Extra-key bar is visible by default; earlier soft keyboard screenshot captured, but physical typing remains flaky through adb. |
| Physical keyboard | Not applicable yet | No physical keyboard evidence captured. |
| XFCE Terminal opens | Pass | Terminal screenshot above shows Debian os-release and `hedgeyos@localhost`. |
| `/etc/os-release` identifies Debian 13 Trixie | Pass | Terminal screenshot and APT log show Debian 13/Trixie. |
| `apt update` works | Pass | Alpha.3 APT log shows Debian repos hit and package lists current. |
| Installing a small Debian package works | Pass | Alpha.3 APT log shows `hello 2.10-5 install ok installed` and `Hello, world!`. |
| Repeated Home returns to same session without duplicate desktop | Pass | Repeated Home left one hedgeyos app, one `hedgeyos-x11`, one XFCE session; extra terminal proots were from the terminal proof. |
| Android app round trip returns to hedgeyos | Pass | hedgeyos opened Android Settings; pressing Home returned to `HedgeyosHomeActivity`. |
| Killing/relaunching hedgeyos recovers cleanly | Pass for app force-stop/relaunch | Alpha.3 was force-stopped, relaunched as Home, and returned to `RUNNING`. |
| Restart Desktop works | Pass | In-app Restart Desktop returned to `RUNNING` with fresh `hedgeyos-x11`/XFCE PIDs and desktop screenshot. |
| Reset Debian works | Partial | `pm clear` plus fresh first boot works; in-app Reset Debian still needs direct evidence. |
| No VNC server/viewer/TCP VNC dependency | Pass for APK/code/process evidence | APK has no VNC/RDP files and runtime process tree contains no VNC process. |
| No separate Termux:X11 APK required | Pass | No `com.termux.x11` package installed; `hedgeyos-x11` starts from hedgeyos APK `CLASSPATH`. |
| Logs reveal actionable errors | Pass | Earlier device failures exposed tar, symlink, sudoers, X11 library, and XKB causes in logs. |
| Required screenshots/logs captured | Mostly complete | XFCE/Home, scaled desktop, menu, terminal/os-release, APT, Android Apps, Android Settings, repeated Home, and restart screenshots/logs captured. |

## Release Decision

Publish `0.1.0-alpha.3` as a prerelease/test APK. Do not tag or publish final
`v0.1.0` yet; in-app Reset Debian and a true clean first boot of this exact
alpha.3 artifact still need a deliberate full pass.
