# hedgeyos Rootfs Customizations

The Debian rootfs is built from Debian 13 Trixie with `mmdebstrap`. All
hedgeyos-specific Linux files come from `rootfs/runtime-assets/hedgeyos-linux`
and are declared in `rootfs/customizations.tsv`. The same source files are
packaged into the Android APK so an installed rootfs can receive versioned,
idempotent migrations without being reset.

## Package Additions

The explicit package set in `rootfs/build-rootfs.sh` keeps the complete
workstation base: APT, sudo, networking tools, Git, Python, build-essential,
XFCE, terminal, Thunar, fonts, and icons.

Phase 1 additionally installs `devilspie2`. It reacts only when an X11 window is
created:

- XFCE Terminal, Thunar, and xterm are maximized.
- Oversized secondary normal windows receive one portrait-safe initial size.
- Dialogs, fullscreen windows, and later user resizing are not changed.

## Accounts And Privilege

- Debian system files are archived as `0:0`.
- `/home/hedgeyos` is archived as `1000:1000`.
- `/usr/bin/sudo` is mode `4755`.
- `/etc/sudoers` and `/etc/sudoers.d/hedgeyos` are mode `0440`.
- hedgeyos launches PRoot with fake-root identity; this does not grant Android
  root access.

## APT And Machine Identity

- APT sources point to Trixie, Trixie updates, and Debian security.
- Package archives, APT lists, temporary files, and build-time machine IDs are
  removed before packaging.
- `/etc/resolv.conf` is initialized at Android extraction time.

## XFCE Defaults

- XFCE remains the desktop environment and its panel remains enabled.
- The desktop hides built-in Home, Filesystem, Trash, and removable-volume
  icons. `Terminal.desktop` is the only hedgeyos-provided desktop launcher.
- XFWM button order is `CMHO|`: Close, Maximize, Minimize, app icon, then the
  centered title.
- Compositing and session saving remain disabled for PRoot reliability.
- Terminal colors, wallpaper, DejaVu fonts, scaling, and the hedgeyos storybook
  visual identity remain.
- Terminal and xterm receive narrower direct-launch geometry; primary app
  windows are maximized by the event-driven window rules.

## Runtime Migration

`/usr/local/libexec/hedgeyos-apply-defaults` owns the current Linux defaults
version. It runs after XFCE's configuration service is available.

- Fresh rootfs builds receive complete default files.
- Existing installations receive only the named hedgeyos migration properties.
- Existing alpha rootfs installations receive `devilspie2` and `liblua5.1-0`
  from two bundled offline Debian packages, so the portrait policy does not
  require a reset or network access.
- User-created desktop files and unrelated XFCE settings are preserved.
- The legacy hedgeyos launcher is replaced with the terminal launcher.
- Completion is recorded under
  `/home/hedgeyos/.config/hedgeyos/defaults-version`.

The generic Linux runtime also installs:

- `/usr/local/libexec/hedgeyos-runtime-preflight`, which verifies temporary
  storage, shared memory, runtime directories, X11, session D-Bus, and visible
  Android kernel facilities before XFCE starts.
- `/usr/local/libexec/hedgeyos-start-desktop`, which owns the reproducible XFCE
  and session D-Bus startup sequence.

Both files are copied into existing rootfs installations during normal app
startup. They do not require a Debian reset. The host-backed runtime layout and
mount contract are documented in [`LINUX-RUNTIME.md`](LINUX-RUNTIME.md).

## Rebuild Procedure

1. Change a source file under `rootfs/runtime-assets/hedgeyos-linux`.
2. Add or update its row in `rootfs/customizations.tsv`.
3. Update this document when behavior, packages, ownership, or modes change.
4. Build the rootfs as root with `rootfs/build-rootfs.sh`.
5. Confirm the APK asset source includes the runtime helpers and
   `app/src/main/assets/hedgeyos-linux/packages` contains the two
   version-matched offline migration packages.
6. Inspect archive ownership, modes, package capabilities, and customization
   coverage.
7. Package the generated rootfs and the same runtime assets into the Android
   APK.
8. Run `scripts/test-linux-runtime.sh`.
9. Test both fresh extraction and migration of an existing installation,
   including the runtime report and `/dev/shm`.

Do not add ad hoc Linux configuration strings to Android Java code. A new base
image must be reproducible from the Debian package list, runtime asset tree,
customization manifest, and build scripts alone.
