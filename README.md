# hedgeyos

Turn an Android phone into a Debian graphical workstation and home launcher.

[Download hedgeyos alpha for ARM64 Android](https://github.com/hedgeyos/hedgeyos/releases/download/hedgeyos-v0.1.0-alpha.2/hedgeyos-arm64-v8a.apk)

Current status: the local `0.1.0-alpha.2` development line now boots the
bundled Debian 13/XFCE desktop as the Android Home screen on an attached ARM64
phone. Final `v0.1.0` has not been tagged or published yet because terminal,
APT, input, recovery, and session-resume acceptance evidence is still pending.

For the remaining execution checklist and workstation handoff, see
[`AGENTS.md`](AGENTS.md).

## What hedgeyos Is

hedgeyos is an experimental, independent Termux-derived Android app. The goal is a
single APK that bundles a Debian 13 Trixie ARM64 rootfs, embeds Termux:X11, and
can be selected as the Android Home app so XFCE appears as the phone home screen.

hedgeyos is not affiliated with, endorsed by, or released by the Termux project.

## Requirements

- Android 8.0 or newer.
- ARM64 device.
- Sideloading enabled by the user.
- Enough free storage for the APK, compressed rootfs, and extracted Debian tree.

Published alpha.2 artifact details:

- Test-signed APK size: 259,738,361 bytes.
- Bundled Debian rootfs size: 221,480,599 bytes compressed.
- Bundled PRoot payload size: 114,325 bytes compressed.
- APK SHA-256:
  `dd77f38a73f513d33a14b707282da4763444ad35969804cd822c7196a8df8020`.
- Test signing certificate SHA-256:
  `DDEA70A805747E040FE393A8C8024B04FB9A0ED2BE532C5B37BCDCBBCB0C9872`.

## Install

1. Install `hedgeyos-arm64-v8a.apk`.
2. Open hedgeyos from the app icon.
3. Use Android's Home app chooser when prompted, or tap `Choose Home App`.
4. During the finished first-boot flow, hedgeyos will verify and extract the
   bundled Debian rootfs, start embedded X11, then start XFCE.

The current local test build has completed first boot on an ARM64 Android phone:
it installs bundled PRoot, verifies and extracts the bundled Debian rootfs,
starts embedded X11 from the hedgeyos APK, and starts XFCE as the Home screen.
hedgeyos does not require the phone's installed Termux app, a separate Termux:X11
APK, a VNC app, or a companion APK for this path. See `docs/TEST-REPORT.md` for
the exact APK, screenshot, logs, package list, and remaining release blockers.

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

The release target is Debian 13 Trixie with ordinary Debian APT sources for
Trixie, Trixie updates, and Debian security. Normal commands such as `apt
update`, `apt install git`, `python3`, and `gcc` must work inside the Debian
environment after v0.1.0 acceptance tests pass.

The rootfs will run under unprivileged PRoot, not root, a VM, or a container
with its own kernel. PRoot has compatibility limits around privileged operations,
kernel features, daemons, and filesystem semantics.

## Build

```sh
./scripts/build-hedgeyos.sh
```

See `docs/BUILDING.md` for current toolchain requirements and blockers.

## Documentation

- `AGENTS.md`
- `UPSTREAMS.md`
- `THIRD_PARTY_NOTICES.md`
- `docs/ARCHITECTURE.md`
- `docs/BUILDING.md`
- `docs/FIRSTBOOT.md`
- `docs/KNOWN-ISSUES.md`
- `docs/TEST-REPORT.md`

## License

hedgeyos is GPL-compatible and retains upstream Termux and Termux:X11 notices.
Complete corresponding source must be available for every release APK.
