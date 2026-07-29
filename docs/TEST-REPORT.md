# Test Report

Date: 2026-07-30

Current status: `v0.1.0-alpha.4` is a published, test-signed GitHub
prerelease. It contains Phase 1 of the Android and Debian performance/stability
work and is not the final production-signed `v0.1.0` release. The X11 debug
and GTK SVG fixes below are an unpublished alpha.5 development candidate; this
work does not tag, publish, or merge a release.

Release:

- URL: <https://github.com/hedgeyos/hedgeyos/releases/tag/v0.1.0-alpha.4>
- APK: `hedgeyos-arm64-v8a-alpha.4-test-signed.apk`
- Version: `0.1.0-alpha.4`
- Version code: `4`
- APK size: 262,136,122 bytes
- APK SHA-256:
  `44865100049105dfd29dadce413ed45715685aa4c80c0dc68444793f09458321`
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
sha256=44865100049105dfd29dadce413ed45715685aa4c80c0dc68444793f09458321
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
linux_menu_icon=assets/hedgeyos-linux/hedgeyos-menu.png
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

### Alpha.4 Touch Ergonomics

Alpha.4 installed over alpha.3 with `adb install -r`, preserving the existing
Debian data and applying Linux defaults migration version 2.

- The XFCE panel replaced its icon-plus-`Applications` label with the
  transparent hedgehog-only button. Tapping it opened the normal Applications
  menu.
- The Android hedgehog overlay grew from 48 dp to 96 dp. Its UI bounds doubled
  from 133 by 133 physical pixels to 265 by 265 pixels at the tested density
  and remained fully on-screen.
- XFCE's native `/desktop-icons/single-click` setting was enabled. One physical
  tap on the Terminal desktop icon opened a maximized terminal.
- The signed APK reported source commit `79f29dd9` on the attached phone.

### Alpha.3 Upgrade And Interaction Checks

Alpha.3 installed over alpha.2 with `adb install -r`. Android retained the
original hedgeyos install time and Debian data while updating to version code 3.

- Tapping `Toggle Soft Keyboard` dismissed the hedgehog mini-window first and
  then opened the IME. The post-action hierarchy contained neither the control
  button nor its close button, while Android reported `mInputShown=true`.
- The visible extra-key row was `ESC`, `/`, `-`, `HOME`, `UP`, `END`,
  `KEYBOARD`, `PREFERENCES`; `PGUP` moved to the second row.
- Pointer settings showed `Direct touch` after the versioned default migration.
  Switching to Trackpad survived a force-stop and relaunch, proving later user
  choices are not overwritten; Direct touch was restored after the test.
- `proot`, `xfce4-session`, and `xfce4-panel` remained live after relaunch, and
  the `org.hedgeyos:desktop-runtime` partial wake lock was held.
- The separately installed `com.termux` package remained at version 0.118.3
  with unchanged install and update timestamps.

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

## Linux Runtime Compatibility Postmortem

The alpha.4 guest mounted Android `/dev` wholesale and backed guest `/tmp`
inside the persistent rootfs. Android did not provide `/dev/shm` on the test
phone, so ordinary Linux applications could not create POSIX shared-memory
objects. Chromium reported the missing path and deliberately aborted, but the
fault was a generic Linux runtime omission rather than a browser defect.
Persistent X11, D-Bus, and lock state also made restart behavior less isolated
than a desktop session expects.

The alpha.5 candidate routes every production PRoot command through one runtime
builder. It creates host-backed ephemeral `/tmp`, `/run`, and `/dev/shm`, keeps
the specific shared-memory bind after the parent `/dev` bind, gives X11 the
same `/tmp`, launches XFCE under a private session D-Bus, and publishes parsed
capabilities plus detailed preflight results. Stop and restart now validate
same-UID PIDs against the exact PRoot/rootfs or X11 role instead of killing
broad process-name matches.

The initial runtime commit `150c4001` and completed capability commit
`ee1a5664` passed the full local test suite and clean GitHub rootfs/APK build.
The clean rootfs inspector confirmed
`hedgeyos-runtime-preflight` and `hedgeyos-start-desktop` as root-owned mode
`0755`.

On 2026-07-29, a fresh alpha.5 install on the attached CPH2499 reached the
complete desktop. The user confirmed desktop startup, terminal launch,
`sudo -n true`, `apt update`, package installation, and `hello`. Startup
preflight reported writable `/dev/shm`, successful Python POSIX shared memory,
memfd, System V shared memory, private session D-Bus, and
`summary=PASS fatal=0`.

An interactive rerun initially produced a false X11 fatal because XFCE Terminal
exports `DISPLAY=:1.0` while startup uses `DISPLAY=:1`. The checker incorrectly
looked for `/tmp/.X11-unix/X1.0`; both forms now normalize to the actual
`/tmp/.X11-unix/X1` socket, with contract coverage for `:1`, `:1.0`, and
`localhost:10.0`.

The same test session exposed a restart cleanup defect. X11 left a broken
`ICEauthority` symlink under `/run/user/0`; `File.exists()` followed the missing
target and skipped the link, leaving the parent nonempty. Runtime cleanup now
uses `Files.exists(..., NOFOLLOW_LINKS)`, and a regression test creates and
removes the same broken-link shape.

Visual GTK and process-liveness Qt evidence passed. Chromium's flag-free
normal-user launch and the final restart/force-stop acceptance steps remain
pending before alpha.5 publication.

## Accidental Permanent X11 Debug Mode Postmortem

A manual diagnostic was run while Geany was open and the desktop was nearly
unusable. Sequential writes to `/tmp` and `/home/hedgeyos` were fast, as were
500-file create/delete tests in both locations. Geany used about 31 MiB RSS,
used no swap, consumed little CPU, and normally slept in `ppoll`. More activity
was attributable to PRoot, `hedgeyos-x11`, and the desktop/display path.

The Android process list also contained multiple live `logcat --pid ...`
processes and defunct logcat children. `HedgeyosX11Bridge` exported
`TERMUX_X11_DEBUG=1` unconditionally. Upstream Termux:X11 enables this debug
path based on the environment variable's presence, so changing the value to
`0` would still enable it.

Normal sessions now explicitly remove `TERMUX_X11_DEBUG` while preserving the
existing appended desktop/X11 stdout and stderr log. The build-time
`HEDGEYOS_X11_DEBUG` flag defaults to false for release, CI, and local builds.
The launcher action `Start X11 Diagnostic Session` shows a performance warning
and requests diagnostic mode in memory for one desktop session. The request is
consumed before launch and cleared after use, so a crash or Android force-stop
cannot persist it. Diagnostic logcat cleanup validates the recorded child PID,
parent X11 PID, app UID, and exact `logcat --pid <x11-pid>` arguments before
stopping it; no process-name-wide cleanup is used. Native waiters reap the
diagnostic children.

Regression tests cover normal-variable removal, explicit diagnostic builds,
one-shot consumption, a later normal session, recorded child ownership, and
the existing X11 ownership/runtime contracts. The full X11-enabled release
assembly passed with `HEDGEYOS_X11_DEBUG=0`.

ADB update-install, boot, normal-session process evidence, restart, relaunch,
and light terminal smoke results will be recorded below after device
verification. Extended Geany and Chromium responsiveness remains
`PENDING_HUMAN_TEST`. Removing the accidental logging is a confirmed fix, not
proof that every GUI-performance issue is resolved.

## Missing GTK SVG Loader Postmortem

A diagnostic run inside the installed Debian/X11 session found repeated GTK
errors while loading symbolic SVG assets, including `check-symbolic.svg`.
The installed rootfs lacked `librsvg2-common`, `librsvg2-2`, and
`libpixbufloader_svg.so`. GTK therefore could not render some checkmarks,
symbolic icons, menu indicators, buttons, and related controls.

Fresh rootfs builds now request `librsvg2-common`, allowing Debian Trixie to
install its exact dependencies, including `librsvg2-2`. The build locates the
architecture-specific GDK-Pixbuf query-loader path, verifies the Debian loader
cache, and decodes deterministic SVG, Adwaita symbolic-icon, check-indicator,
and PNG assets through GDK-Pixbuf. The package fix does not patch Adwaita or
replace SVG files with PNG files.

Existing alpha.4 rootfs installations are repaired offline before XFCE starts.
The published alpha.4 package database and the new rootfs package database
showed an exact missing dependency closure of `libdav1d7`, `librsvg2-2`, and
`librsvg2-common`. A versioned manifest records package, version, architecture,
migration generation, SHA-256, filename, and reason. `window-policy-v1` remains
supported alongside `gtk-svg-loader-v1`. Each generation uses its own durable
marker under `/var/lib/hedgeyos/migrations`; the marker is written only after
package configuration, triggers, loader/cache checks, and actual SVG decoding
all pass. Interrupted or failed work remains retryable without replacing the
rootfs or deleting user packages.

The clean ARM64 rootfs build, package/dependency-closure verifier, rootfs
inspector, runtime tests, migration tests, and Java migration tests all passed.
The final rootfs contains both packages, the architecture-specific SVG loader,
an SVG loader-cache entry, and a successful GTK asset smoke record in
provenance.

ADB migration/package/runtime evidence and the booted-desktop and terminal
screenshots will be recorded below after device verification. Extended GTK
visual inspection and subjective responsiveness remain `PENDING_HUMAN_TEST`.

## Automated Verification

Passed:

- `:app:testReleaseUnitTest`
- Full X11-enabled release assembly
- `scripts/inspect-hedgeyos-rootfs.sh`
- `scripts/test-linux-defaults.sh`
- `scripts/test-linux-runtime.sh`
- `scripts/test-linux-migrations.sh`
- `rootfs/verify-gtk-svg.sh` during the clean rootfs build
- `rootfs/verify-migration-packages.sh` during the clean rootfs build
- ShellCheck 0.9.0 for the changed rootfs/runtime test scripts
- `git diff --check`

The build retains pre-existing Kotlin metadata diagnostics from lint tooling,
three pre-existing Termux `PendingIntent` lint warnings, and Java 8 target
deprecation warnings under JDK 21. Lint reported zero errors.

The first post-push full build exposed a 404 after the rolling Termux repository
removed the pinned `proot` package. Normal builds now verify the exact 114 KiB
PRoot payload and checksum committed with the source, eliminating that moving
network dependency. Package downloads remain only in the explicit maintainer
refresh path.

## Release Decision

`v0.1.0-alpha.4` is suitable as a public prerelease/test APK. Final `v0.1.0`
still requires production signing, direct in-app Reset Debian evidence,
physical-keyboard testing, and broader device coverage.
