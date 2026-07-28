# hedgeyos

Turn an Android phone into a Debian graphical workstation and Home launcher.

[Download hedgeyos v0.1.0-alpha.1 for ARM64 Android](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.1/hedgeyos-arm64-v8a-alpha.1-test-signed.apk)

Current status: `v0.1.0-alpha.1` is published as a GitHub prerelease and has
device smoke-test evidence on an attached ARM64 phone. The alpha boots the
bundled Debian 13/XFCE desktop as the Android Home screen, starts embedded
Termux:X11 from inside the hedgeyos APK, recovers after Android force-stops and
relaunches the app, and applies the hedgeyos wallpaper/icon branding on the
Android and Linux sides.

This is still an alpha. Final `v0.1.0` has not been tagged: before that, the
remaining release gate is a broader acceptance pass with production signing,
direct Reset Debian evidence, and a deliberately clean target profile. See
[`docs/TEST-REPORT.md`](docs/TEST-REPORT.md) for the current evidence.

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

Published alpha.1 artifact details:

- Test-signed APK size: 262,630,388 bytes.
- Bundled Debian rootfs size: 221,480,599 bytes compressed.
- Bundled PRoot payload size: 114,325 bytes compressed.
- APK SHA-256:
  `464bb857c8bcf410953535caddde2d82d985f5281c17d65031eca1881adfc91a`.
- Test signing certificate SHA-256:
  `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1`.

The prerelease APK is test-signed for alpha testing. It is not a Play/App Store
production-signed artifact.

## Install

1. Download `hedgeyos-arm64-v8a-alpha.1-test-signed.apk` from the release above.
2. Install it through Android sideloading.
3. Open hedgeyos from the app icon.
4. Use Android's Home app chooser when prompted, or tap `Choose Home App`.
5. During first boot, hedgeyos verifies and extracts the bundled Debian rootfs,
   starts embedded X11, then starts XFCE.

hedgeyos does not require the phone's installed Termux app, a separate
Termux:X11 APK, a VNC app, or a companion APK for this path. The current alpha
kept an installed `com.termux` package intact during testing; independence was
verified from APK contents, code paths, package list, and the running process
tree.

## Recovery

The hedgeyos Home shell includes:

- Start Runtime.
- Restart Desktop.
- Stop Desktop.
- Reset Debian.
- Open X11 Surface.
- Open hedgeyos Logs.
- Open hedgeyos Terminal.
- Open Android Apps.
- Open Android Settings.
- Choose Home App.

These controls are present so a broken launcher build does not trap the user.

## Debian And APT

The bundled rootfs is Debian 13 Trixie with ordinary Debian APT sources for
Trixie, Trixie updates, and Debian security. Normal commands such as `apt
update`, `apt install git`, `python3`, and `gcc` are intended to work inside the
Debian environment. Final `v0.1.0` should rerun and record a full APT acceptance
pass against the exact release artifact.

The rootfs runs under unprivileged PRoot, not root, a VM, or a container with its
own kernel. PRoot has compatibility limits around privileged operations, kernel
features, daemons, and filesystem semantics.

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
- [`docs/FIRSTBOOT.md`](docs/FIRSTBOOT.md)
- [`docs/KNOWN-ISSUES.md`](docs/KNOWN-ISSUES.md)
- [`docs/TEST-REPORT.md`](docs/TEST-REPORT.md)

## License

hedgeyos is GPL-compatible and retains upstream Termux and Termux:X11 notices.
Complete corresponding source must be available for every release APK.
