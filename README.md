# HedgeyOS

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/hedgeyos_icon.png" width="176" alt="HedgeyOS hedgehog">
</p>

<p align="center"><strong>Your pocket Debian desktop, ready wherever Android goes.</strong></p>

HedgeyOS turns an ARM64 Android phone or tablet into a storybook-inspired Linux
workstation. Debian 13, XFCE, an embedded X11 server, touch controls, and
recovery tools arrive together in one app. There is no separate Termux,
Termux:X11, VNC, or companion-app requirement.

<p align="center">
  <a href="https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.6/hedgeyos-arm64-v8a-alpha.6-test-signed.apk"><strong>Download HedgeyOS alpha.6 for ARM64 Android</strong></a>
</p>

HedgeyOS is currently an alpha prerelease. The APK is test-signed for evaluation
and is not yet a Play Store or production-signed release.

## A Real Desktop In Your Pocket

- **Complete in one app.** Open HedgeyOS like any Android app or choose it as
  your Home screen.
- **Real Debian software.** Use APT, Git, Python, compilers, editors, browsers,
  terminals, and other ARM64 Linux packages.
- **Made for touch.** Direct touch is the default, desktop launchers open with
  one tap, important apps open maximized, and window controls remain reachable
  on portrait screens.
- **Designed to keep running.** A foreground service, wake lock, and guided
  battery-management setup help protect the desktop from aggressive Android
  background policies.
- **Friendly by design.** The HedgeyOS wallpaper, readable storybook theme,
  compact application menu, terminal-focused desktop, and draggable hedgehog
  make XFCE feel at home on a phone.
- **Recovery is always close.** The hedgehog opens terminal, runtime, log,
  restart, diagnostic, background-permission, and Debian recovery controls.
- **Safer when software misbehaves.** Runtime logs are bounded and rate-limited,
  oversized logs from older builds are repaired automatically, and supervised
  applications cannot leave an uncontrolled process tree after their leader
  exits.

## New In Alpha.6

Alpha.6 is a resilience and runtime-safety release built around a confirmed
Chromium failure that produced gigabytes of repeated output.

- XFCE now runs as the foreground session beneath its private D-Bus, so the bus
  lifetime follows the real desktop session.
- Session, X11, migration, first-boot, and application logs have strict size
  limits, rotation, output-rate control, and repetition summaries.
- Existing multi-gigabyte HedgeyOS logs are repaired on upgrade while retaining
  a small diagnostic tail and metadata. Debian files and installed packages are
  preserved.
- Chromium launches through a reusable application supervisor that records its
  exact process instance and contains surviving descendants without broad
  process-name killing.
- Because Chromium's internal Linux sandbox cannot initialize inside Android
  PRoot, its launcher shows a clear warning on every launch and defaults to
  Cancel. Continuing runs Chromium inside Android's app sandbox with HedgeyOS
  supervision, but without Chromium's additional internal process sandbox.
- Desktop restart, force-stop recovery, and runtime reporting use exact process
  identities with PID-reuse protection.
- The bundled PRoot payload is now reproducibly pinned to `5.1.107.89`, and a
  bounded ARM64 socket-lifecycle test checks normal, abort, and killed-parent
  behavior at runtime.
- Alpha.5's GTK SVG loader repair and normal-session X11 debug fix remain
  included.

The original ENOSYS loop is safely contained, but its narrow low-level PRoot
trigger was not reproduced by the bounded socket test. Alpha.6 does not claim
that every source of Linux GUI overhead on Android is solved.

## Requirements

- Android 8.0 or newer.
- An ARM64 phone or tablet.
- Permission to install an APK from outside the Play Store.
- At least 1.5 GB of free space for extraction, plus room for Debian packages
  and personal files.

## Install

1. Download `hedgeyos-arm64-v8a-alpha.6-test-signed.apk`.
2. Install it through Android's APK sideloading flow.
3. Open HedgeyOS.
4. Follow the Background setup guide and allow unrestricted battery use where
   your device offers it.
5. Wait while HedgeyOS verifies and extracts Debian on first launch.
6. Choose HedgeyOS as the Android Home app when prompted if you want the Linux
   desktop to become your launcher.

First boot takes longer because the bundled Debian filesystem is verified and
extracted. Later launches reuse the installed system. Updating the APK keeps the
existing Debian rootfs, home directory, installed packages, and files.

## Everyday Controls

The always-visible, draggable hedgehog opens a compact control window with:

- Background survival setup.
- Debian Terminal.
- APT check.
- Linux Runtime Report.
- Bounded runtime logs.
- Restart Desktop.
- One-session X11 diagnostics.
- Stop Desktop.
- Reset Debian.
- Android keyboard, apps, settings, display, and Home controls.

The hedgehog remains visible through rotation and remembers its position. The
extra-key bar keeps common terminal keys and the Android keyboard button within
thumb reach.

## Screenshots

![HedgeyOS alpha.6 XFCE desktop](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.6/hedgeyos-alpha.6-desktop.png)

![HedgeyOS alpha.6 terminal](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.6/hedgeyos-alpha.6-terminal.png)

![HedgeyOS first launch](https://github.com/hedgeyos/hedgeyos/releases/download/v0.1.0-alpha.1/hedgeyos-first-load.gif)

Older alpha.1 captures show the pre-rename `panix@localhost` prompt. Current
builds use HedgeyOS branding and open Debian desktop terminals through PRoot
fake-root as `root@localhost`.

## Debian And Your Files

HedgeyOS runs Debian 13 Trixie under unprivileged PRoot. Debian's fake-root
identity lets APT manage packages inside the guest without granting root access
to Android. HedgeyOS exports its Downloads and Logs directories into the Debian
home directory.

This is a compatibility layer, not a virtual machine with its own kernel.
Software that requires privileged kernel features, a full system D-Bus, or
hardware-specific acceleration may need adaptation or may not work.

HedgeyOS is an independent Termux-derived project. It is not affiliated with,
endorsed by, or released by the Termux project.

## Technical Details

### Release Status

`v0.1.0-alpha.6` is an ARM64, test-signed prerelease:

- Package: `org.hedgeyos`
- Version code: `6`
- Minimum Android API: `26`
- Debian: `13 (Trixie), arm64`
- PRoot: `5.1.107.89`
- APK size: `266,430,161` bytes
- APK SHA-256:
  `2d6e514b54aca757f12573b2eda79f73ff69e77f27c3159dfce1eceff61152bf`
- Bundled Debian rootfs size: `223,067,304` bytes
- Bundled Debian rootfs SHA-256:
  `604b955f697358374e25f88adfa50390137731b6adfcdc4e8f750e09a6d91ba7`
- Test signing certificate SHA-256:
  `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1`

The checksum file is published beside the APK and the complete objective test
record is in [`docs/TEST-REPORT.md`](docs/TEST-REPORT.md). The final
production-signed `v0.1.0` has not been declared. Prolonged Chromium and Geany
responsiveness, physical-keyboard behavior, broader device coverage, and
production signing remain release work.

### Runtime Architecture

The APK embeds Termux:X11, a checksum-pinned PRoot payload, and an ARM64 Debian
rootfs. Android starts X11 and then a direct PRoot -> `dbus-run-session` ->
XFCE foreground chain. Host-backed `/tmp`, `/run`, and `/dev/shm` provide the
expected Linux session facilities.

Runtime preflight verifies X11, D-Bus, process identity, shared memory, GTK SVG
support, bounded logging, supervised-application state, and three bounded
PRoot socket-lifecycle cases. Existing-rootfs package repairs are offline,
manifest-driven, checksum-verified, versioned, and retryable.

The new log architecture uses bounded pipes rather than giving arbitrary
desktop applications a direct append descriptor to a persistent file. Process
cleanup uses recorded PID, start time, executable identity, process group,
session, and ownership data. It never uses `pkill` or `killall`.

Chromium's launcher does not silently add `--no-sandbox`. A separate warned
helper requires explicit per-launch consent, defaults to Cancel, drops to the
Debian `hedgeyos` user, and then starts Chromium through the generic supervisor.
This is a disclosed PRoot compatibility boundary, not the fix for the
historical runaway failure.

### Build And Verification

```sh
./scripts/build-hedgeyos.sh
```

See [`docs/BUILDING.md`](docs/BUILDING.md) for the Android toolchain,
[`docs/DEBIAN-REBUILD.md`](docs/DEBIAN-REBUILD.md) for clean rootfs
construction, and [`docs/LINUX-RUNTIME.md`](docs/LINUX-RUNTIME.md) for session,
logging, supervision, and runtime contracts.

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

HedgeyOS is GPL-compatible and retains the required upstream Termux,
Termux:X11, and PRoot notices. Complete corresponding source must remain
available for every distributed APK.
