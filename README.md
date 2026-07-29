# hedgeyos

Turn an Android phone into a Debian graphical workstation and Home launcher.

[Download hedgeyos v0.1.0-alpha.4 for ARM64 Android](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.4/hedgeyos-arm64-v8a-alpha.4-test-signed.apk)

Current status: `v0.1.0-alpha.4` is published as a GitHub prerelease and has
device smoke-test evidence on an attached ARM64 phone. The alpha boots into the
bundled Debian 13/XFCE desktop as the Android Home screen, starts embedded
Termux:X11 from inside the hedgeyos APK, opens Debian desktop apps, exposes the
hedgeyos recovery menu, and applies the hedgeyos wallpaper/icon branding on the
Android and Linux sides. On-device acceptance covers a true fresh install,
background-survival onboarding, runtime wake-lock release/reacquisition, window
placement, overlay drag/rotation, `apt update`, and installation and execution
of a Debian package.

This is still an alpha. Final `v0.1.0` has not been tagged: before that, the
remaining release gate is a broader acceptance pass with production signing,
direct Reset Debian evidence, and final release checks. See
[`docs/TEST-REPORT.md`](docs/TEST-REPORT.md) for the current evidence.

Alpha.2 includes the first performance and stability phase: foreground runtime
protection, a first-boot phone power-management guide, a permanent draggable
hedgehog control, terminal-focused XFCE defaults, and portrait-safe window
placement. The remaining optimization phases are recorded in
[`docs/PERFORMANCE-STABILITY-ROADMAP.md`](docs/PERFORMANCE-STABILITY-ROADMAP.md).

Alpha.3 makes Direct touch the default pointer mode, places the soft-keyboard
button in the always-visible extra-key row, and closes the hedgehog control
window before opening Android's soft keyboard.

Alpha.4 doubles the draggable hedgehog control to `96dp`, replaces the
space-heavy XFCE Applications label with a hedgehog-only button, and enables
single-click desktop launchers for touch use.

## What hedgeyos Is

hedgeyos is an experimental, independent Termux-derived Android app. The goal is
a single APK that bundles a Debian 13 Trixie ARM64 rootfs, embeds Termux:X11, and
can be selected as the Android Home app so XFCE appears as the phone home screen.

hedgeyos is not affiliated with, endorsed by, or released by the Termux project.

## Requirements

- Android 8.0 or newer.
- ARM64 device.
- Sideloading enabled by the user.
- Enough free storage for the APK, compressed rootfs, and extracted Debian tree.

Published alpha.4 artifact details:

- Test-signed APK size: 262,136,122 bytes.
- Bundled Debian rootfs size: 221,639,012 bytes compressed.
- Bundled PRoot payload size: 114,325 bytes compressed.
- APK SHA-256:
  `44865100049105dfd29dadce413ed45715685aa4c80c0dc68444793f09458321`.
- Test signing certificate SHA-256:
  `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1`.

The prerelease APK is test-signed for alpha testing. It is not a Play/App Store
production-signed artifact.

## Install

1. Download `hedgeyos-arm64-v8a-alpha.4-test-signed.apk` from the release above.
2. Install it through Android sideloading.
3. Open hedgeyos from the app icon.
4. Use Android's Home app chooser when prompted, or tap `Choose Home App`.
5. During first boot, hedgeyos verifies and extracts the bundled Debian rootfs,
   starts embedded X11, then starts XFCE.
6. In the scrollable Background setup mini-window, allow unrestricted battery
   use and review the instructions for the phone's background and autostart
   controls. The guide permits continuing with a warning when a vendor setting
   cannot be changed programmatically.

hedgeyos is a single-APK desktop path. It bundles Debian, PRoot, and embedded
X11; users do not need a separate Termux app, Termux:X11 APK, VNC app, or
companion APK for the XFCE desktop.

## Screenshots And Demo

The first image was captured from an attached ARM64 Android phone running
`v0.1.0-alpha.2`. The animated demos below it were recorded with alpha.1.

![hedgeyos alpha.2 themed XFCE desktop](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.2/hedgeyos-alpha.2-fresh-desktop.png)

![hedgeyos first launch reaching the XFCE desktop](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.1/hedgeyos-first-load.gif)

![hedgeyos XFCE desktop on a phone](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.1/hedgeyos-initial-load-final.png)

![hedgeyos desktop demo with menu, Debian terminal, display controls, and landscape file manager](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.1/hedgeyos-desktop-demo.gif)

![hedgeyos landscape file manager on Android](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.1/hedgeyos-landscape-desktop.png)

This is full Debian 13 Trixie on the phone: XFCE, Thunar, xfce4-terminal, the
Debian filesystem, and ordinary Debian APT sources run inside the bundled PRoot
rootfs.

The older demo capture shows a legacy `panix@localhost` prompt from the
pre-rename test rootfs. The current downloadable APK contains only the
`hedgeyos` Debian account and opens terminals as `root@localhost`.

## Background Protection

While the desktop is intentionally running, hedgeyos owns an Android foreground
service and partial wake lock. The runtime notification remains visible, and a
completed setup guide can always be reopened from the hedgehog control.

The hedgehog is an activity-local control over the Linux desktop, not a
system-wide overlay over other Android apps. It uses the transparent Hitomi
companion artwork at `96dp`, can be dragged, remembers a normalized position,
and stays visible and on-screen after display rotation.

## Desktop And Controls

XFCE remains the desktop environment and retains its panel. The default desktop
shows one hedgeyos Terminal launcher; built-in Home, Filesystem, Trash, and
removable-volume icons are hidden without deleting user-created desktop files.
Desktop launchers open with one click, and the panel's Applications button uses
the transparent hedgehog icon without a text label.

Primary terminal and file-manager windows open maximized. Window controls are
ordered on the left as Close, Maximize, Minimize, and app icon, while the title
remains centered. Other oversized windows receive a narrower portrait-safe
initial size and remain user-resizable.

The draggable hedgehog opens a compact, scrollable control mini-window with:

- Background survival setup.
- Open Debian Terminal.
- Run Debian APT Check.
- Open hedgeyos logs.
- Restart Desktop.
- Stop Desktop.
- Reset Debian.
- Toggle Soft Keyboard.
- Open Android Apps.
- Open Android Settings.
- Display Settings.
- Choose Home App.

These controls are present so a broken launcher build does not trap the user.

## Debian And APT

The bundled rootfs is Debian 13 Trixie with ordinary Debian APT sources for
Trixie, Trixie updates, and Debian security. Normal commands such as `apt
update`, `apt install git`, `python3`, and `gcc` are intended to work inside the
Debian environment. The current alpha can run the Debian acceptance check from
the hedgeyos menu. On the attached ARM64 phone, that check completed `apt update`,
installed `hello`, ran it, and verified the installed dpkg record.

The rootfs runs under unprivileged PRoot, not Android root, a VM, or a container
with its own kernel. Debian processes use PRoot's fake-root identity so package
management works inside the rootfs without granting privileges over Android.
PRoot still has compatibility limits around kernel features, daemons, and
filesystem semantics.

All Debian launch paths share host-backed ephemeral `/tmp`, `/run`, and
`/dev/shm` facilities. Before XFCE starts, hedgeyos verifies directory modes,
POSIX shared memory, the X11 socket, session D-Bus, and visible Android kernel
interfaces. The resulting Linux Runtime Report is available from the hedgehog
controls. This is a generic Linux compatibility layer; installed applications
do not receive Chromium-specific wrappers or sandbox-disabling flags.

## Build

```sh
./scripts/build-hedgeyos.sh
```

See [`docs/BUILDING.md`](docs/BUILDING.md) for current toolchain requirements.

## Documentation

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

## License

hedgeyos is GPL-compatible and retains upstream Termux and Termux:X11 notices.
Complete corresponding source must be available for every release APK.
