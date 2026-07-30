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
    session-dbus.identity
    xfce-session.identity
    apps/
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

The production desktop command executes `dbus-run-session` directly. Its
foreground child performs deterministic setup and ends by executing Debian's
XFCE `xinitrc`; the verified Trixie `xinitrc` then executes `xfce4-session`.
The real XFCE session therefore determines the private bus lifetime:

```text
PRoot -> dbus-run-session -> hedgeyos-start-desktop
  -> /etc/xdg/xfce4/xinitrc -> xfce4-session
```

There is no background principal session and no `dbus-launch` fallback.
Startup records the exact D-Bus and XFCE PID/start-time identities and active
bus address. `hedgeyos-session-guard` detects unexpected D-Bus death, records a
fatal lifecycle event, and stops the now-invalid XFCE session. HedgeyOS does
not emulate a system D-Bus daemon. Software that strictly requires
`/run/dbus/system_bus_socket` can remain unsupported under unprivileged PRoot.

Normal sessions explicitly remove `TERMUX_X11_DEBUG`; setting it to `0` is
incorrect because upstream Termux:X11 tests only whether the variable exists.
Normal X11 stdout/stderr still flows to its own bounded `x11.log`; disabling
debug mode only removes the extra upstream live-logcat path.

`Start X11 Diagnostic Session` warns about the performance cost and requests
one diagnostic restart in process memory. The request is consumed before X11
starts and is not stored in preferences, so the following normal restart, an
Android force-stop, or a process crash cannot leave it enabled. Explicit
diagnostic builds may instead set `HEDGEYOS_X11_DEBUG=1`; release, CI, and
normal local builds default to `0`.

Diagnostic cleanup never scans for arbitrary logcat processes. The native X11
launcher and Android renderer each record their actual child PID, and native
waiter threads reap exited children. Android cleanup requires the same app UID,
the recorded X11 or app parent PID, and the exact
`logcat --pid <x11-pid>` or `logcat --pid=<app-pid>` arguments.

## Bounded Runtime Logging

No arbitrary desktop application receives a direct append descriptor to a
persistent file. The desktop writes to a pipe drained by
`hedgeyos-bounded-log`, which keeps reading even when output is suppressed.
Rotation therefore works without relying on a producer reopening a renamed
file.

Current contracts:

- Session log: 1 MiB plus three rotations.
- Android runtime logs: 1 MiB plus three rotations.
- Supervised application logs: 512 KiB plus two rotations.
- Session output threshold: 128 KiB/s.
- Supervised application threshold: 64 KiB/s.
- Managed log-directory budget: 16 MiB.

The aggregate ceiling is recursive across the top-level Logs directory and its
per-application subdirectory. Guest loggers coordinate through a shared
advisory lock and byte ledger, periodically reconcile against real file sizes,
remove old rotations first, and suppress new persistent bytes when the
remaining budget is exhausted. They continue draining producer output while
suppressed. A metadata reserve keeps status and suppression summaries writable.
- Legacy repair threshold: 8 MiB.

Repeated lines are sampled and collapsed. Sustained excess output is drained
but not persisted, and the status JSON records suppression counts. Android's
viewer uses `HedgeyosBoundedLog.readTail()` and shows file size plus truncation
state; managed runtime paths must never use `Files.readAllBytes()` or a full
line scan.

Before desktop startup, Android repairs only known oversized managed logs. It
seeks directly to a bounded tail, atomically replaces the dangerous file,
records original size/mtime and repair time, and leaves user files untouched.
Repair is idempotent. After repair, startup stops with a clear recovery status
if less than 128 MiB remains.

## Application Supervision

`hedgeyos-app-supervisor` is a generic launcher for multi-process GUI
applications. It:

- starts the leader in a dedicated process session;
- becomes a child subreaper;
- records application instance, executable, leader PID/start time, process
  group, process session, HedgeyOS session, and observed descendants;
- allows a short graceful shutdown after leader exit;
- signals only exact PID/start-time survivors;
- reaps children and clears the instance record; and
- recovers exact stale records from an earlier desktop session.

The Chromium desktop entry uses `hedgeyos-launch-chromium`, which explains that
Chromium's setuid and user-namespace sandboxes cannot initialize inside this
Android PRoot and defaults to Cancel. Only explicit per-launch consent starts
Chromium as Debian user `hedgeyos` with `--no-sandbox`, inside Android's app
sandbox and the generic HedgeyOS process/output supervisor. The flag is not
silent and is not treated as the runaway fix.

The supervisor mechanism is reusable for Electron apps, IDEs, media tools, and
other browsers without changing their binaries. It never uses process names or
the shared Android app UID as sole ownership proof. Desktop stop asks the
supervisor to close recorded applications before XFCE and D-Bus stop.

## Runtime Preflight

Every desktop start runs
`/usr/local/libexec/hedgeyos-runtime-preflight` from XFCE autostart after the
active session bus and process identities exist. Its report is atomically
published as
`/home/hedgeyos/Logs/linux-runtime-report.txt` and can be opened from the
hedgehog control window.

Checks cover:

- Directory existence, mode, and write access for `/tmp`, `/dev/shm`, `/run`,
  `/run/lock`, and `XDG_RUNTIME_DIR`.
- `/proc`, `/sys`, and Android inotify visibility.
- The active X11 socket.
- POSIX shared-memory create, read, close, and unlink.
- `memfd_create` when the Android kernel permits it.
- A reply from the active private session D-Bus.
- Exact live session-D-Bus and XFCE process identities.
- Presence or absence of a system D-Bus socket.
- Presence of `libpixbufloader_svg.so` and its active loader-cache entry.
- Real GDK-Pixbuf decoding of a deterministic SVG, Adwaita symbolic icon,
  checked menu indicator, and ordinary PNG.
- Current X11 session mode, `NORMAL` or `DIAGNOSTIC`.
- Measured supervised-application records and orphan count.
- Session and aggregate persistent log budgets, suppression state, and legacy
  repair state.
- Normal, browser-abort, and browser-kill ARM64 PRoot
  `SOCK_SEQPACKET`/`recvmsg` lifecycle checks.

Result classes are:

- `PASS`: the facility works.
- `WARNING`: an optional or Android-controlled facility is unavailable.
- `UNSUPPORTED`: the test dependency itself is absent.
- `FATAL`: a facility required to start a reliable desktop is broken.

Any `FATAL` result exits with code 2 and marks the packaged runtime contract
failed. Warnings about system D-Bus or Android-owned `/proc/sys` entries do not
mark the desktop failed.
The X11 check accepts both display-only values such as `:1` and the
display-and-screen form used by interactive terminals, such as `:1.0`; both
resolve to `/tmp/.X11-unix/X1`.

Android code parses the same report into `LinuxRuntimeCapabilities`. Call
`HedgeyosRuntimeManager.getLinuxRuntimeCapabilities(context)` to inspect
writable temporary storage, POSIX and System V shared memory, memfd, D-Bus,
recorded session identities, procfs visibility, X11, GTK SVG
loader/cache/decode status, logging, application supervision, PRoot recvmsg
lifecycle, diagnostic mode, and structured compatibility warnings without
parsing display text.

## Process Ownership

HedgeyOS records the exact desktop PRoot, embedded X11, D-Bus, XFCE, diagnostic
logcat, and supervised-application identities under
`linux-runtime/processes`. State files are written atomically. New records
include process-start ticks; legacy integer PID records remain readable during
the transition.

Ephemeral cleanup checks paths without following symlinks. This matters for
X11 authority links whose temporary target has already disappeared: a broken
link still occupies its parent directory and must be deleted during restart.

On stop or restart, a recorded PID is acted on only when:

- `/proc/<pid>/status` reports the HedgeyOS Android UID.
- `/proc/<pid>/stat` still has the recorded process-start value.
- NUL-delimited `/proc/<pid>/cmdline` argv entries match the expected role.
- Desktop PRoot includes the exact bundled PRoot path, exact rootfs path, and
  `--kill-on-exit`.
- X11 identifies itself as `hedgeyos-x11`.
- A diagnostic logcat child has the recorded X11 PID as `PPid` and exact
  `logcat --pid <x11-pid>` argv.
- A D-Bus or XFCE identity has the expected exact `comm` value.
- A supervised application process still matches the PID/start identity
  captured for that application instance.

Once an exact identity has been recorded, normal cleanup does not scan for
additional same-role processes. Stale supervised records are recovered only
through their recorded exact identities. HedgeyOS does not kill processes by
broad names such as `proot`, `dbus-daemon`, `chromium`, `Thunar`, or
`xfce4-panel`.

Intentional shutdown order is:

1. Recorded supervised GUI applications.
2. Recorded XFCE session.
3. Recorded session D-Bus.
4. Recorded desktop PRoot.
5. Embedded X11.

The Android monitor marks an unrequested foreground desktop exit as failed and
stops X11 to contain the broken session.

## PRoot Socket Reproducer

`hedgeyos-proot-seqpacket-reproducer` is a bounded ARM64 glibc executable built
from `scripts/proot-seqpacket-reproducer.c`. It tests Unix
`SOCK_SEQPACKET`/`recvmsg`, ancillary descriptor passing, close-on-exec, and
normal/aborted/killed parent lifecycles. Each mode has a hard timeout and writes
structured counts for successful receives, EOF, ENOSYS, and other errors.

The simplified test passes natively on Ubuntu, under ARM64 QEMU, in native
Android userspace, and inside the published HedgeyOS PRoot baseline. That does
not prove PRoot was unrelated to the historical Chromium loop. It means the
narrow Chromium-specific low-level trigger remains isolated but unreproduced;
generic descendant and output containment remains mandatory.

## Linux Assets

These files are the rebuildable Linux runtime surface:

- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-runtime-preflight`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-gtk-asset-smoke`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-start-desktop`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-session-init`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-session-guard`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-bounded-log`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-app-supervisor`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-launch-chromium`
- `rootfs/runtime-assets/hedgeyos-linux/chromium.desktop`
- `rootfs/runtime-assets/hedgeyos-linux/hedgeyos-proot-seqpacket-reproducer`
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
   `./scripts/test-linux-migrations.sh`,
   `./scripts/test-runtime-containment.sh`,
   `./scripts/test-proot-seqpacket-reproducer.sh`, ShellCheck, and the Android
   unit suite.
   The tests cover bind order, guest UID environment selection, directory
   modes, ephemeral cleanup, atomic state replacement, capability parsing, and
   exact same-UID process validation.
6. Build the rootfs with `rootfs/build-rootfs.sh`.
7. Run `scripts/inspect-hedgeyos-rootfs.sh` and confirm every declared runtime
   helper is root-owned with its manifest mode.
8. Package with `./scripts/build-hedgeyos.sh`.
9. Run `scripts/inspect-hedgeyos-apk.sh` and confirm the logging, lifecycle,
   supervision, and reproducer assets are present.
10. Test an upgrade without resetting Debian, then test a fresh extraction.
11. Verify restart and force-stop recovery, the runtime report, Python shared
    memory, a GTK app, a Qt app when available, and supervised Chromium without
    app-specific compatibility flags. After Chromium closes, observe a bounded
    interval for orphaned zygotes, ENOSYS growth, and log-budget compliance.
12. Confirm the separately installed Termux package was not changed or
    removed.

The runtime contract is release-critical. A build must not ship merely because
XFCE appears once; it must preserve these facilities across upgrade, stop,
restart, app-process death, and fresh rootfs construction.
