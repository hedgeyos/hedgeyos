# HedgeyOS

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/hedgeyos_icon.png" width="160" alt="HedgeyOS hedgehog">
</p>

**A pocket Debian workstation that turns an ARM64 Android device into a
storybook-inspired XFCE desktop.**

HedgeyOS packages Debian 13, XFCE, an embedded X11 server, and its Android
launcher into one app. Open it like an ordinary Android app or choose it as
your Home screen, then use familiar Linux terminals, files, editors, browsers,
APT packages, and development tools directly on your phone or tablet.

[Download HedgeyOS v0.1.0-alpha.5 for ARM64 Android](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.5/hedgeyos-arm64-v8a-alpha.5-test-signed.apk)

HedgeyOS is currently an alpha prerelease. The downloadable APK is test-signed
for evaluation and is not yet a Play Store or production-signed release.

## Your Linux Desktop, Anywhere

- **One app, complete desktop.** Debian, PRoot, Termux:X11, XFCE, and the
  HedgeyOS interface ship together. A separate Termux, Termux:X11, VNC, or
  companion app is not required.
- **Real Debian tools.** Use ordinary Trixie APT sources, install packages, run
  Git, Python, compilers, editors, and other ARM64 Linux software.
- **Built for touch.** Direct touch is the default, desktop launchers open with
  one tap, primary apps open maximized, and portrait-safe window controls stay
  reachable on narrow screens.
- **Ready to stay running.** A foreground runtime, partial wake lock, and
  guided vendor power-management setup help Android keep the desktop alive.
- **A friendly desktop.** HedgeyOS combines an easy-to-read XFCE theme with its
  hedgehog controls, wallpaper, compact app menu, and terminal-focused layout.
- **Recovery within reach.** The draggable hedgehog opens controls for the
  terminal, runtime report, logs, desktop restart, background setup, and Debian
  recovery.

## What's New In Alpha.5

Alpha.5 improves Linux application compatibility and removes accidental X11
diagnostic overhead from everyday sessions.

- GTK symbolic icons, checkmarks, menu indicators, and SVG controls now use
  Debian's standard `librsvg2-common` GDK-Pixbuf loader.
- Existing HedgeyOS installations receive the GTK repair through a verified,
  offline, retryable migration without replacing the Debian rootfs.
- Normal X11 sessions no longer start permanent live Android logcat children.
- A clearly warned **Start X11 Diagnostic Session** action provides detailed
  logs for one desktop session, then returns to normal automatically.
- The Linux Runtime Report now verifies the SVG loader, loader cache, real SVG
  decoding, and current X11 session mode before XFCE starts.

These fixes address two confirmed defects. They do not claim to eliminate
every source of GUI overhead under PRoot, Android scheduling, software
rendering, or memory pressure.

## Requirements

- Android 8.0 or newer.
- An ARM64 phone or tablet.
- Permission to install an APK from outside the Play Store.
- At least 1.5 GB of free space for extraction, plus room for your Debian
  packages and files.

## Install

1. Download `hedgeyos-arm64-v8a-alpha.5-test-signed.apk` from the link above.
2. Install the APK through Android's sideloading flow.
3. Open HedgeyOS.
4. Follow the scrollable Background setup guide and allow unrestricted battery
   use where your device offers it.
5. Wait while HedgeyOS verifies and extracts Debian on first launch.
6. Choose HedgeyOS as the Android Home app when prompted if you want the Linux
   desktop to become your launcher.

First boot takes longer because the bundled Debian filesystem is being
verified and extracted. Later launches reuse the installed rootfs.

## Desktop Controls

The always-visible, draggable hedgehog opens a compact control window with:

- Background survival setup.
- Open Debian Terminal.
- Run Debian APT Check.
- Linux Runtime Report.
- Open HedgeyOS logs.
- Restart Desktop.
- Start X11 Diagnostic Session.
- Stop Desktop.
- Reset Debian.
- Android keyboard, apps, settings, display, and Home-app controls.

The hedgehog remains on-screen through rotation and remembers its position.
The extra-key bar keeps common terminal keys and the Android keyboard button
within thumb reach.

## Screenshots

![HedgeyOS alpha.5 themed XFCE desktop](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.5/hedgeyos-alpha.5-desktop.png)

![HedgeyOS alpha.5 terminal](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.5/hedgeyos-alpha.5-terminal.png)

![HedgeyOS first launch](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.1/hedgeyos-first-load.gif)

![HedgeyOS desktop demo](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.1/hedgeyos-desktop-demo.gif)

Older alpha.1 captures show the pre-rename `panix@localhost` prompt. Current
builds use HedgeyOS branding and open Debian desktop terminals through PRoot
fake-root as `root@localhost`.

## Debian And Your Files

HedgeyOS runs Debian 13 Trixie under unprivileged PRoot. Debian's fake-root
identity lets APT manage packages inside the guest without granting root access
to Android itself. Files exported by HedgeyOS appear in the Debian user's
Downloads and Logs directories.

This is a compatibility layer, not a virtual machine with its own kernel.
Software that requires privileged kernel features, a full system D-Bus, or
hardware-specific acceleration may need adaptation or may not work.

HedgeyOS is an independent Termux-derived project. It is not affiliated with,
endorsed by, or released by the Termux project.

## Technical Details

### Release Status And Integrity

`v0.1.0-alpha.5` is an ARM64 test build:

- APK size: 267,281,395 bytes.
- APK SHA-256:
  `76eb866c89e5efcbf7d65e2f312fa237b5db2d50c08c8b307cf872bcd627a298`.
- Test signing certificate SHA-256:
  `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1`.
- Bundled rootfs size: 223,940,166 bytes.
- Bundled rootfs SHA-256:
  `c9858719da4ddc64e3aa74a55b21acff9eb1cf80ce6a8a9562570a3d4162dbbc`.
- Package name: `org.hedgeyos`.
- Minimum Android API: 26.

The final production-signed `v0.1.0` has not been declared. Subjective Geany
and Chromium responsiveness testing, broader device coverage, and production
signing remain release work. Objective build and device evidence is recorded
in [`docs/TEST-REPORT.md`](docs/TEST-REPORT.md).

### Runtime Architecture

The APK embeds Termux:X11 and an ARM64 Debian rootfs. Android starts the X11
server, then launches Debian through a common PRoot command builder with
host-backed `/tmp`, `/run`, and `/dev/shm`. XFCE runs under a private session
D-Bus. Runtime preflight verifies shared memory, X11, D-Bus, GTK SVG support,
and other packaged desktop contracts before startup.

Existing-rootfs repairs are versioned in
`rootfs/runtime-assets/hedgeyos-linux/migration-packages.tsv`. Every bundled
package is matched by name, version, architecture, filename, and SHA-256.
Migration markers are written only after package configuration and capability
checks pass.

### Build

```sh
./scripts/build-hedgeyos.sh
```

See [`docs/BUILDING.md`](docs/BUILDING.md) for the Android toolchain and
[`docs/DEBIAN-REBUILD.md`](docs/DEBIAN-REBUILD.md) for reproducible rootfs
construction, package migration closure, ownership checks, and GTK asset
verification.

### Maintainer And Agent Documentation

- [`AGENTS.md`](AGENTS.md)
- [`UPSTREAMS.md`](UPSTREAMS.md)
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/BUILDING.md`](docs/BUILDING.md)
- [`docs/DEBIAN-REBUILD.md`](docs/DEBIAN-REBUILD.md)
- [`docs/FIRSTBOOT.md`](docs/FIRSTBOOT.md)
- [`docs/KNOWN-ISSUES.md`](docs/KNOWN-ISSUES.md)
- [`docs/LINUX-RUNTIME.md`](docs/LINUX-RUNTIME.md)
- [`docs/PERFORMANCE-STABILITY-ROADMAP.md`](docs/PERFORMANCE-STABILITY-ROADMAP.md)
- [`docs/ROOTFS-CUSTOMIZATIONS.md`](docs/ROOTFS-CUSTOMIZATIONS.md)
- [`docs/TEST-REPORT.md`](docs/TEST-REPORT.md)

### License

HedgeyOS is GPL-compatible and retains the required upstream Termux and
Termux:X11 notices. Complete corresponding source must remain available for
every distributed APK.
