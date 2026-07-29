# HedgeyOS Linux Runtime

HedgeyOS runs Debian processes through bundled PRoot against the Android
kernel. It is not a VM, does not boot a Debian kernel, and does not receive
Android root privileges. This document defines the runtime facilities that all
guest applications receive and the procedure required to preserve them in
future builds.

## Runtime Contract

`HedgeyosGuestRuntime` is the only production component that constructs PRoot
guest arguments. Desktop startup, terminal launch, package acceptance, and
offline rootfs migration all use it.

Android app-private storage owns this ephemeral runtime tree:

```text
<files>/linux-runtime/
  tmp/                 01777
  shm/                 01777
  run/                 0755
    dbus/               0755
    lock/               01777
    shm/                01777 mount-point placeholder
    user/0/             0700
    user/1000/          0700
  processes/            0700
    desktop.pid
    x11.pid
    x11-diagnostic-logcat.pid
```

`tmp`, `shm`, and `run` are deleted and recreated before a new desktop session.
They are deliberately outside the persistent Debian rootfs so stale X11,
D-Bus, lock, and shared-memory files cannot poison a restart. Process identity
records survive that cleanup.

The guest bind order is part of the compatibility contract:

1. Android `/dev` to guest `/dev`.
2. Runtime `shm` to guest `/dev/shm`.
3. Android `/proc` to guest `/proc`.
4. Android `/sys` to guest `/sys`.
5. Runtime `tmp` to guest `/tmp`.
6. Runtime `run` to guest `/run`.
7. Runtime `shm` to guest `/run/shm`.
8. HedgeyOS export and public-log directories into the user home.

The specific `/dev/shm` bind must follow the parent `/dev` bind. Reversing
those entries hides shared memory again. Guest processes receive
`TMPDIR=/tmp` and `XDG_RUNTIME_DIR=/run/user/0`.

`<files>/tmp` remains separate and is used only as PRoot's host-side
`PROOT_TMP_DIR`. It is not exposed as guest `/tmp`.

## Shared Memory

The runtime supplies a real writable directory at `/dev/shm` rather than an
application-specific workaround. POSIX shared memory, Python
`multiprocessing.shared_memory`, Chromium, GTK applications, Qt applications,
and other Linux software all use the same facility.

Do not add Chromium wrappers or flags such as `--disable-dev-shm-usage` or
`--no-sandbox`. A compatibility issue in a generic Linux facility belongs in
the runtime contract and its preflight checks.

PRoot still enables `--sysvipc` and `--ashmem-memfd`. Availability of an
individual kernel operation ultimately depends on the Android kernel and phone
vendor.

## D-Bus And X11

The embedded X11 server and Debian see the same host-backed `/tmp`, including
`/tmp/.X11-unix/X1`. XKB data remains read from the installed rootfs at
`debian/usr/share/X11/xkb`; moving guest `/tmp` outside the rootfs must not
change that path.

`hedgeyos-start-desktop` prefers `dbus-run-session` and falls back to
`dbus-launch`. This creates a private session bus whose lifetime follows XFCE.
HedgeyOS does not emulate a system D-Bus daemon. Software that strictly
requires `/run/dbus/system_bus_socket` can remain unsupported under
unprivileged PRoot.

Normal sessions explicitly remove `TERMUX_X11_DEBUG`; setting it to `0` is
incorrect because upstream Termux:X11 tests only whether the variable exists.
The existing process stdout/stderr redirect to `desktop.log` remains active in
normal mode.

`Start X11 Diagnostic Session` warns about the performance cost and requests
one diagnostic restart in process memory. The request is consumed before X11
starts and is not stored in preferences, so the following normal restart, an
Android force-stop, or a process crash cannot leave it enabled. Explicit
diagnostic builds may instead set `HEDGEYOS_X11_DEBUG=1`; release, CI, and
normal local builds default to `0`.

Diagnostic cleanup never scans for arbitrary logcat processes. The native X11
launcher records its actual child PID, native waiter threads reap exited
children, and Android cleanup requires the same app UID, the recorded X11
parent PID, and exact `logcat --pid <x11-pid>` arguments.

## Runtime Preflight

Every desktop start runs
`/usr/local/libexec/hedgeyos-runtime-preflight` after X11 starts and before
XFCE starts. Its report is atomically published as
`/home/hedgeyos/Logs/linux-runtime-report.txt` and can be opened from the
hedgehog control window.

Checks cover:

- Directory existence, mode, and write access for `/tmp`, `/dev/shm`, `/run`,
  `/run/lock`, and `XDG_RUNTIME_DIR`.
- `/proc`, `/sys`, and Android inotify visibility.
- The active X11 socket.
- POSIX shared-memory create, read, close, and unlink.
- `memfd_create` when the Android kernel permits it.
- Creation of a private session D-Bus.
- Presence or absence of a system D-Bus socket.
- Presence of `libpixbufloader_svg.so` and its active loader-cache entry.
- Real GDK-Pixbuf decoding of a deterministic SVG, Adwaita symbolic icon,
  checked menu indicator, and ordinary PNG.
- Current X11 session mode, `NORMAL` or `DIAGNOSTIC`.

Result classes are:

- `PASS`: the facility works.
- `WARNING`: an optional or Android-controlled facility is unavailable.
- `UNSUPPORTED`: the test dependency itself is absent.
- `FATAL`: a facility required to start a reliable desktop is broken.

Any `FATAL` result exits with code 2 and blocks XFCE startup. Warnings about
system D-Bus or Android-owned `/proc/sys` entries do not block the desktop.
The X11 check accepts both display-only values such as `:1` and the
display-and-screen form used by interactive terminals, such as `:1.0`; both
resolve to `/tmp/.X11-unix/X1`.

Android code parses the same report into `LinuxRuntimeCapabilities`. Call
`HedgeyosRuntimeManager.getLinuxRuntimeCapabilities(context)` to inspect
writable temporary storage, POSIX and System V shared memory, memfd, D-Bus,
procfs visibility, X11, GTK SVG loader/cache/decode status, diagnostic mode,
and structured compatibility warnings without parsing display text.

## Process Ownership

HedgeyOS records the exact desktop PRoot and embedded-X11 process identities
under `linux-runtime/processes`. State files are written atomically.

Ephemeral cleanup checks paths without following symlinks. This matters for
X11 authority links whose temporary target has already disappeared: a broken
link still occupies its parent directory and must be deleted during restart.

On stop or restart, a recorded PID is acted on only when:

- `/proc/<pid>/status` reports the HedgeyOS Android UID.
- NUL-delimited `/proc/<pid>/cmdline` argv entries match the expected role.
- Desktop PRoot includes the exact bundled PRoot path, exact rootfs path, and
  `--kill-on-exit`.
- X11 identifies itself as `hedgeyos-x11`.
- A diagnostic logcat child has the recorded X11 PID as `PPid` and exact
  `logcat --pid <x11-pid>` argv.

The same exact signatures are scanned to recover an orphan left by an older
app process. HedgeyOS no longer kills processes by broad names such as
`proot`, `dbus-daemon`, `Thunar`, or `xfce4-panel`.

## Linux Assets

These files are the rebuildable Linux runtime surface:

- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-runtime-preflight`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-gtk-asset-smoke`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-start-desktop`
- `rootfs/runtime-assets/hedgeyos-linux/migration-packages.tsv`
- `rootfs/runtime-assets/hedgeyos-linux/packages/`
- `rootfs/customizations.tsv`

The manifest installs both helpers as root-owned mode `0755`. Android also
packages the same asset directory. On every app start,
`ensureLinuxBranding()` copies the current helpers into an existing rootfs, so
upgrading does not require deleting Debian or installed applications.

## Future Build Procedure

1. Make generic runtime argument changes in `HedgeyosGuestRuntime`; do not
   assemble another PRoot argument list in an activity, service, or app
   launcher.
2. Make guest preflight or XFCE startup changes in the versioned Linux
   assets, not in ad hoc Java shell strings.
3. Keep their owner, mode, and destination rows current in
   `rootfs/customizations.tsv`.
4. Update this document whenever paths, modes, bind order, severity, ownership,
   or kernel limitations change.
5. Run `./scripts/test-linux-runtime.sh`,
   `./scripts/test-linux-migrations.sh`, ShellCheck, and the Android unit suite.
   The tests cover bind order, guest UID environment selection, directory
   modes, ephemeral cleanup, atomic state replacement, capability parsing, and
   exact same-UID process validation.
6. Build the rootfs with `rootfs/build-rootfs.sh`.
7. Run `scripts/inspect-hedgeyos-rootfs.sh` and confirm both runtime helpers are
   root-owned mode `0755`.
8. Package with `./scripts/build-hedgeyos.sh`.
9. Run `scripts/inspect-hedgeyos-apk.sh` and confirm both helpers are present in
   APK assets.
10. Test an upgrade without resetting Debian, then test a fresh extraction.
11. Verify restart and force-stop recovery, the runtime report, Python shared
    memory, a GTK app, a Qt app when available, and Chromium without
    app-specific flags.
12. Confirm the separately installed Termux package was not changed or
    removed.

The runtime contract is release-critical. A build must not ship merely because
XFCE appears once; it must preserve these facilities across upgrade, stop,
restart, app-process death, and fresh rootfs construction.
