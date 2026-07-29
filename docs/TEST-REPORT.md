# Test Report

Date: 2026-07-29

Current status: `v0.1.0-alpha.2` is a published, test-signed GitHub
prerelease. It contains Phase 1 of the Android and Debian performance/stability
work and is not the final production-signed `v0.1.0` release.

Release:

- URL: <https://github.com/hedgeyos/hedgeyos/releases/tag/v0.1.0-alpha.2>
- APK: `hedgeyos-arm64-v8a-alpha.2-test-signed.apk`
- Version: `0.1.0-alpha.2`
- Version code: `2`
- APK size: 262,111,461 bytes
- APK SHA-256:
  `50384775976d9f1475634b01d440732d677157b6471e3e1231f2ee27ae856ccd`
- Test signing certificate SHA-256:
  `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1`
- `apksigner verify --verbose`: v2 and v3 signatures verified.

Device:

- Serial `ab6b77a8`
- Model `CPH2499`
- ARM64 Android phone attached over adb

## Artifact Inspection

`scripts/inspect-hedgeyos-apk.sh` passed against the signed release candidate:

```text
sha256=50384775976d9f1475634b01d440732d677157b6471e3e1231f2ee27ae856ccd
package=org.hedgeyos
launcher=com.termux.x11.HedgeyosHomeActivity
fallback_home_activity_enabled=false
home_category=present
x11_main_launcher=absent
native_code=arm64-v8a
bundled_rootfs=assets/debian-trixie-arm64-rootfs.tar.zst
bundled_proot=assets/termux-proot-aarch64.tar.zst
embedded_x11=lib/arm64-v8a/libXlorie.so
power_protection=wake_lock_and_battery_setup
overlay_asset=drawable/hedgeyos_companion
linux_defaults=assets/hedgeyos-linux/hedgeyos-apply-defaults
vnc_files=absent_in_apk_listing
```

The rebuilt Debian rootfs also passed
`scripts/inspect-hedgeyos-rootfs.sh`.

- Compressed size: 221,639,012 bytes
- SHA-256:
  `238b0e9cbb682a588e6428a0a51bbe687c26ee6e53bbd6ef06d93c9fde5e540c`
- Required portrait-window package: `devilspie2`
- Declarative customization manifest:
  `rootfs/customizations.tsv`

## Device Evidence

### Existing-Rootfs Upgrade

Alpha.2 first installed over the existing alpha rootfs. Startup retained the
rootfs and user home, applied the versioned Linux defaults, installed the two
bundled migration packages offline, and reached:

```text
org.hedgeyos
hedgeyos-x11
xfce4-session
xfwm4
xfdesktop
xfce4-panel
devilspie2
```

The desktop showed the wallpaper, panel/dock, extra-key bar, one Terminal
desktop launcher, and normal `xfwm4` cursor/window management. There was no
X-cursor or missing-panel restart regression.

### True Fresh Install

The final signed artifact was uninstalled and reinstalled. This removed only
`org.hedgeyos` app data. It did not uninstall or modify the separate
`com.termux` package.

On first launch:

- The scrollable Background setup mini-window opened automatically.
- The warning-only continuation path worked without claiming vendor settings
  were complete.
- The bundled rootfs and PRoot assets extracted successfully.
- XFCE reached the full themed desktop.
- `devilspie2`, `xfwm4`, `xfdesktop`, and `xfce4-panel` were all running.
- Only the supplied Terminal desktop icon was present.

Captured evidence is under
`build/device-test/hedgeyos-v0.1.0-alpha.2/`, including:

- `first-boot-background-setup.png`
- `first-boot-background-setup-scrolled.png`
- `fresh-rootfs-desktop.png`
- `terminal-maximized-titlebar.png`
- `thunar-maximized-titlebar.png`
- `overlay-dragged.png`
- `overlay-landscape.png`
- `debian-acceptance.log`

### Background Protection

With the desktop running, Android reported the expected active lock:

```text
PARTIAL_WAKE_LOCK 'org.hedgeyos:desktop-runtime'
```

The runtime notification reported `RUNNING: background protection active`.
Using Stop Desktop removed X11/XFCE processes and released the partial wake
lock. Force-stop/relaunch then restored the desktop processes and reacquired the
same lock.

### Overlay And Rotation

The transparent Hitomi hedgehog control remained exactly 48 dp and could not be
hidden. Dragging changed its Android bounds from:

```text
[961,148][1094,281]
```

to:

```text
[232,635][365,768]
```

After landscape rotation it remained visible at:

```text
[636,232][769,365]
```

Returning to portrait restored the normalized position. The control
mini-window and Background setup window were both scrollable and closable while
the hedgehog itself remained present.

### XFCE Portrait Policy

The fresh desktop retained XFCE and its panel. Terminal and Thunar opened
maximized. Both showed title-bar items on the left in Close, Maximize,
Minimize, app-icon order with the text title centered. The versioned
`devilspie2` policy is active for narrower initial sizing of oversized secondary
windows.

### Debian Package Management

The built-in acceptance command ran inside the fresh bundled rootfs:

```text
uid=0(root) gid=0(root) groups=0(root),3003,9997,20622,50622
Fetched 10.1 MB in 1min 53s
All packages are up to date.
Setting up hello (2.10-5) ...
Hello, world!
hello 2.10-5 install ok installed
Debian acceptance command exited with code 0.
```

This proves ordinary APT package management works with PRoot fake-root without
granting root access over Android.

### Installed Termux Preservation

Before and after testing, the unrelated installed Termux package reported:

```text
package=com.termux
versionName=0.118.3
firstInstallTime=2024-01-18 06:18:03
lastUpdateTime=2025-12-16 12:57:51
```

Its APK path and timestamps were unchanged. hedgeyos independence is proved by
its own package, embedded X11, bundled PRoot/rootfs, and process paths, not by
removing Termux from the phone.

## Rootfs Ownership Postmortem

The broken alpha rootfs archived Debian system files, including
`/etc/sudo.conf`, `/etc/sudoers`, `/usr/bin/sudo`, and dpkg state, as uid/gid
1000. `tar --numeric-owner` preserved those bad numeric owners; it did not
convert them to root ownership. The desktop then also launched under PRoot uid
1000, so `sudo` rejected its own configuration and direct APT lacked fake-root.

The corrected rootfs pipeline:

- Requires a root-owned construction tree.
- Normalizes Debian system files to `0/0`.
- Keeps `/home/hedgeyos` at `1000/1000`.
- Restores and validates `sudo` mode `4755`.
- Rejects archives whose critical sudo/dpkg files are not `0/0`.
- Uses a pinned, checksum-verified Debian archive keyring.
- Launches Debian desktop and terminal processes with PRoot fake-root.
- Runs a real APT install as an acceptance gate.

The host Ubuntu "System Program Problem" popup encountered during this work was
an existing NVIDIA 535 DKMS failure against the host's 7.0.0-28 kernel. It was
not caused by the hedgeyos APK or rootfs, and this work did not alter that
driver.

## Automated Verification

Passed:

- `:app:testReleaseUnitTest`
- Full X11-enabled release assembly
- `apksigner` v2/v3 verification
- `scripts/inspect-hedgeyos-apk.sh`
- `scripts/inspect-hedgeyos-rootfs.sh`
- `scripts/test-linux-defaults.sh`
- `git diff --check`

The build retains pre-existing Kotlin metadata diagnostics from lint tooling,
three pre-existing Termux `PendingIntent` lint warnings, and Java 8 target
deprecation warnings under JDK 21. Lint reported zero errors. `shellcheck` was
not installed on the host.

The first post-push full build exposed a 404 after the rolling Termux repository
removed the pinned `proot` package. Normal builds now verify the exact 114 KiB
PRoot payload and checksum committed with the source, eliminating that moving
network dependency. Package downloads remain only in the explicit maintainer
refresh path.

## Release Decision

`v0.1.0-alpha.2` is suitable as a public prerelease/test APK. Final `v0.1.0`
still requires production signing, direct in-app Reset Debian evidence,
physical-keyboard testing, and broader device coverage.
