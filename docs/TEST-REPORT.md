# Test Report

Date: 2026-07-28

Current status: `v0.1.0-alpha.1` is published as a GitHub prerelease and has
real device smoke-test evidence. It is not the final `v0.1.0` release.

Release:

- URL: <https://github.com/hedgeyos/hedgeyos/releases/tag/v0.1.0-alpha.1>
- APK:
  `hedgeyos-arm64-v8a-alpha.1-test-signed.apk`
- Version: `0.1.0-alpha.1`
- Version code: `1`
- APK size: 261,786,612 bytes
- APK SHA-256:
  `54ee061dd9c1f9105e4aeb9f3ad84422ff8451ac9fb42887688d6904ad12a194`
- Test signing certificate SHA-256:
  `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1`
- `apksigner verify --verbose`: v2 and v3 signatures verified.

Device:

- `ab6b77a8`, model `CPH2499`, ARM64 Android target attached over adb.

## APK Inspection

`scripts/inspect-hedgeyos-apk.sh` passed against the unsigned release APK before
test signing.

- Package: `org.hedgeyos`
- Launcher/Home activity: `com.termux.x11.HedgeyosHomeActivity`
- Android Home category: present
- Fallback native Home activity: disabled in X11 builds
- Standalone Termux:X11 launcher: absent
- Native code: `arm64-v8a`
- Bundled rootfs: `assets/debian-trixie-arm64-rootfs.tar.zst`
- Bundled PRoot: `assets/termux-proot-aarch64.tar.zst`
- Embedded X11 library: `lib/arm64-v8a/libXlorie.so`
- VNC/RDP files: absent from APK listing

Inspection output recorded:

```text
package=org.hedgeyos
launcher=com.termux.x11.HedgeyosHomeActivity
fallback_home_activity_enabled=false
home_category=present
x11_main_launcher=absent
native_code=arm64-v8a
bundled_rootfs=assets/debian-trixie-arm64-rootfs.tar.zst
bundled_proot=assets/termux-proot-aarch64.tar.zst
embedded_x11=lib/arm64-v8a/libXlorie.so
vnc_files=absent_in_apk_listing
```

## Device Evidence

The alpha APK was pushed to the attached phone and installed with:

```text
adb push build/device-test/hedgeyos-v0.1.0-alpha.1/hedgeyos-arm64-v8a-alpha.1-test-signed.apk /data/local/tmp/hedgeyos-arm64-v8a-alpha.1-test-signed.apk
adb shell pm install -r /data/local/tmp/hedgeyos-arm64-v8a-alpha.1-test-signed.apk
adb shell am start -W -n org.hedgeyos/com.termux.x11.HedgeyosHomeActivity
```

After force-stop and relaunch, the device showed the required desktop processes:

```text
org.hedgeyos
hedgeyos-x11
xfce4-session
xfwm4
xfdesktop
xfce4-panel
```

The final screenshot captured at
`build/device-test/hedgeyos-v0.1.0-alpha.1/hedgeyos-final-live.png` shows:

- XFCE panel/dock visible.
- `xfwm4` running instead of the fallback X cursor state.
- hedgeyos icon on the desktop and Android overlay.
- hedgeyos wallpaper applied to the live XFCE monitor path.
- Termux:X11 extra-key bar visible.

hedgeyos independence was verified from the package identity, APK contents,
embedded X11 startup path, bundled PRoot and rootfs paths, and absence of a
separate `com.termux.x11` package or VNC/RDP APK payload.

## Fixed Device Failures

| Failure | Cause | Fix |
| --- | --- | --- |
| Restart after Android killed the app showed an X cursor and missing dock/panel | Java static process handles were lost while same-UID X11/XFCE/PRoot/dbus processes survived and poisoned the next session | Startup now cleans stale same-UID desktop processes, clears saved XFCE sessions, disables SaveOnExit, and self-heals `xfwm4`, `xfdesktop`, and `xfce4-panel`. |
| Wallpaper stayed black after restart | XFCE created a device-specific monitor path after `xfdesktop` started | Startup now discovers X11 monitor names with `xrandr` and applies the bundled wallpaper through `xfconf-query` to the live monitor path. |
| XFCE desktop was tiny on the phone | X11 display defaults used an unscaled desktop profile | `HedgeyosHomeActivity` applies scaled display defaults and keeps the extra-key bar visible. |
| Embedded X11 exited with code 137 | `libXlorie.so` was deflated in the APK, but `CmdEntryPoint` loads it directly from the APK path | Release packaging keeps native libraries uncompressed and the APK inspector checks for embedded X11. |
| Embedded X11 exited during startup | XKB config root was not set for the embedded server | `HedgeyosX11Bridge` sets `XKB_CONFIG_ROOT` to the bundled Debian rootfs XKB path. |
| Rootfs extraction failed with tar exit code 2 | Android could not extract special `/dev` nodes and some preserved ownership/mode metadata | The rootfs is Android-extractable and runtime extraction uses `--no-same-owner --no-same-permissions --delay-directory-restore`. |
| `sudo` rejected `/etc/sudo.conf` as owned by uid 1000, and interactive `apt install` lacked privilege | The rootfs archive preserved a rootless builder's uid 1000 metadata, while the XFCE PRoot session also ran as uid 1000 | Rootfs builds now require root, normalize and validate archive ownership/mode, and run Debian desktop/terminal processes with PRoot's fake-root identity. |
| Reset/extraction cleanup failed under `/dev/fd` | Recursive delete followed symlinks | `deleteRecursively()` uses `NOFOLLOW_LINKS`. |

## APT And Sudo Postmortem

The broken alpha rootfs archived Debian system files, including
`/etc/sudo.conf`, `/etc/sudoers`, `/usr/bin/sudo`, and dpkg state, as
`1000/1000`.

The failure chain was:

1. `rootfs/build-rootfs.sh` allowed a non-root build.
2. `tar --numeric-owner` preserved the builder's existing numeric ownership; it
   did not convert those files to root ownership.
3. Android correctly extracted with `--no-same-owner` for app-sandbox
   compatibility.
4. XFCE and its terminals were launched with PRoot
   `--change-id=1000:1000`.
5. `sudo` saw `/etc/sudo.conf` as uid 1000 and refused to run, while direct APT
   commands lacked the PRoot root identity.

The issue escaped because the alpha gate proved boot, X11, XFCE, branding, and
process recovery, but did not inspect rootfs ownership/modes or complete a real
APT install.

The corrected build now:

- Requires root for rootfs construction.
- Normalizes Debian system files to `0/0` and `/home/hedgeyos` to `1000/1000`.
- Restores and validates `sudo` mode `4755` after ownership changes.
- Rejects an archive unless critical sudo/dpkg files are `0/0`.
- Uses a pinned, checksum-verified Debian 2025.1 archive keyring.
- Runs the Debian desktop and terminals with PRoot's fake-root identity.
- Makes the built-in acceptance check verify uid 0, `sudo -n`, `apt update`,
  package installation, execution, and dpkg state.

Fresh-device evidence from the attached `CPH2499`:

```text
uid=0(root) gid=0(root) groups=0(root),3003,9997,20142,50142
hello 2.10-5 install ok installed
Hello, world!
Debian acceptance command exited with code 0.
```

The visible XFCE terminal also completed:

```text
sudo -n id
uid=0(root) gid=0(root) groups=0(root)
sudo -n apt install -y hello
hello is already the newest version (2.10-5).
```

## Acceptance Matrix

| Requirement | Status | Evidence |
| --- | --- | --- |
| Published alpha APK is downloadable | Pass | GitHub prerelease `v0.1.0-alpha.1` has uploaded APK and SHA-256 assets. |
| APK is signed for testing | Pass | `apksigner verify` verified v2/v3 signatures. |
| Package and launcher identity are hedgeyos | Pass | APK inspection reports package `org.hedgeyos` and launcher `com.termux.x11.HedgeyosHomeActivity`. |
| First boot uses bundled rootfs/proot assets | Pass | App data was cleared, the corrected APK freshly extracted its bundled rootfs/PRoot, and runtime reached `RUNNING`. |
| Embedded X11 surface appears | Pass | `hedgeyos-x11` process and visible XFCE screenshot. |
| XFCE usable desktop appears | Pass | Screenshot shows wallpaper, icons, panel/dock, and extra-key bar. |
| Killing/relaunching hedgeyos recovers cleanly | Pass | Force-stop/relaunch returned to `RUNNING` desktop processes including `xfwm4`, `xfdesktop`, and `xfce4-panel`. |
| No separate Termux:X11 APK required | Pass | No `com.termux.x11` package installed; embedded `hedgeyos-x11` started from the hedgeyos APK. |
| No VNC server/viewer/TCP VNC dependency | Pass for APK/process evidence | APK inspection found no VNC/RDP payload and process checks showed no VNC process. |
| Direct Reset Debian evidence | Not complete for final release | `pm clear`/fresh app data paths have been exercised, but in-app Reset Debian needs a deliberate final pass. |
| APT package install proof on exact published alpha | Pass | Built-in check ran as PRoot root, passed `sudo -n`, completed `apt update`, installed and ran `hello`, and verified `hello 2.10-5 install ok installed`. |

## Release Decision

`v0.1.0-alpha.1` is suitable as a published prerelease/test APK. Do not tag final
`v0.1.0` yet; final release still needs production signing and direct in-app
Reset Debian evidence against the exact final artifact.
