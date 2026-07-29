# Test Report

Date: 2026-07-30

Current status: `v0.1.0-alpha.5` is a published, test-signed GitHub
prerelease. It contains the confirmed X11 diagnostic-overhead and GTK SVG
runtime fixes below and is not the final production-signed `v0.1.0` release.

Release:

- URL: <https://github.com/hedgeyos/hedgeyos/releases/tag/v0.1.0-alpha.5>
- APK: `hedgeyos-arm64-v8a-alpha.5-test-signed.apk`
- Version: `0.1.0-alpha.5`
- Version code: `5`
- APK size: 267,281,395 bytes
- APK SHA-256:
  `76eb866c89e5efcbf7d65e2f312fa237b5db2d50c08c8b307cf872bcd627a298`
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
sha256=76eb866c89e5efcbf7d65e2f312fa237b5db2d50c08c8b307cf872bcd627a298
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
linux_runtime_preflight=assets/hedgeyos-linux/hedgeyos-runtime-preflight
linux_gtk_asset_smoke=assets/hedgeyos-linux/hedgeyos-gtk-asset-smoke
linux_migration_manifest=assets/hedgeyos-linux/migration-packages.tsv
linux_desktop_startup=assets/hedgeyos-linux/hedgeyos-start-desktop
vnc_files=absent_in_apk_listing
```

The rebuilt Debian rootfs also passed
`scripts/inspect-hedgeyos-rootfs.sh`.

- Compressed size: 223,940,166 bytes
- SHA-256:
  `c9858719da4ddc64e3aa74a55b21acff9eb1cf80ce6a8a9562570a3d4162dbbc`
- Required portrait-window package: `devilspie2`
- Required GTK SVG loader package: `librsvg2-common`
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

Visual GTK and process-liveness Qt evidence passed. The restart and Android
force-stop/relaunch checks now pass below. Chromium's extended responsiveness
check remains pending human testing.

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
cannot persist it. Diagnostic logcat cleanup validates both upstream children
using their recorded child PIDs, X11 or app parent PID, app UID, and exact
`logcat --pid <x11-pid>` or `logcat --pid=<app-pid>` arguments before stopping
them; no process-name-wide cleanup is used. Native waiters reap the diagnostic
children.

Regression tests cover normal-variable removal, explicit diagnostic builds,
one-shot consumption, a later normal session, recorded child ownership, and
the existing X11 ownership/runtime contracts. The full X11-enabled release
assembly passed with `HEDGEYOS_X11_DEBUG=0`.

The final APK update-installed successfully. A normal session contained
`org.hedgeyos`, `hedgeyos-x11`, PRoot, `xfce4-session`, `xfwm4`,
`xfce4-panel`, and `xfdesktop`, with no `logcat --pid` child or defunct logcat
entry. Restart Desktop produced new X11/XFCE PIDs, and Android force-stop
removed the app and all guest processes before relaunch returned to
`summary=PASS fatal=0`.

The real menu action also passed on device. Its warning explicitly states the
performance cost and one-session lifetime. The diagnostic session reported
`ON`, produced both upstream scoped logcat children, and recorded their exact
PIDs. A subsequent normal Restart Desktop logged safe cleanup of both recorded
children, started a new X11 process, reported diagnostic mode `OFF`, and had no
remaining diagnostic or defunct logcat process.

Extended Geany and Chromium responsiveness remains `PENDING_HUMAN_TEST`.
Removing the accidental logging is a confirmed fix, not proof that every
GUI-performance issue is resolved.

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

The first update migration correctly exposed a PRoot-only issue in the smoke
helper: Python `ctypes.util.find_library()` returned no GDK-Pixbuf result even
though the package and library were installed. Migration stopped before its
marker or XFCE startup. The helper now loads the concrete Debian multiarch
libraries under `/usr/lib/<multiarch>` and has a regression contract forbidding
the nonportable lookup. On relaunch, `window-policy-v1` was skipped as already
complete, `gtk-svg-loader-v1` retried, all decode checks passed, and only then
was its marker written. This supplies direct interrupted-migration retry
evidence.

ADB guest evidence confirms:

- `librsvg2-common:arm64 2.60.0+dfsg-1` is installed.
- `librsvg2-2:arm64 2.60.0+dfsg-1` is installed.
- `libpixbufloader_svg.so` exists under the ARM64 multiarch path.
- The active loader cache contains SVG.
- Deterministic SVG, Adwaita symbolic SVG, check-indicator SVG, and PNG decode.
- Both generation-specific migration markers exist.
- Existing home entries, Geany, Chromium, Devilspie2, and the previously
  installed `hello` package remain present.
- `Reset Debian` was not used.

The runtime report shows GTK SVG loader, cache, and decode `PASS`. Extended GTK
visual inspection and subjective responsiveness remain `PENDING_HUMAN_TEST`.

## Alpha.5 Device Verification

Objective Codex checks ran on serial `ab6b77a8`, model `CPH2499`, fingerprint
`OPPO/CPH2499/OP56BBL1:16/BP2A.250605.015/T.R4T3.3f6fde2-1ac0734-1ac49a3:user/release-keys`.

The final test-signed APK was update-installed with
`adb install --no-incremental -r`. The existing rootfs migration completed
without clearing app data, and the exact final APK was installed once more
after the clean rootfs rebuild. HedgeyOS reached the themed XFCE desktop, its
panel and dock were present, one-tap Terminal launch opened a maximized
terminal, and the short guest command set recorded package, marker, loader,
decode, preserved-home, preserved-application, and runtime results.

Evidence is under
`build/device-evidence/gtk-svg-x11-debug-20260730-044827/`. Primary captures:

- `booted-desktop-final.png`
- `open-terminal-final.png`
- `terminal-smoke.png`
- `device-smoke.txt`
- `linux-migration.log`
- `linux-runtime-report-final-installed.txt`
- `processes-final-installed-normal.txt`
- `processes-final-diagnostic.txt`
- `processes-final-normal-after-diagnostic.txt`

APK SHA-256:
`76eb866c89e5efcbf7d65e2f312fa237b5db2d50c08c8b307cf872bcd627a298`.
Clean rootfs SHA-256:
`c9858719da4ddc64e3aa74a55b21acff9eb1cf80ce6a8a9562570a3d4162dbbc`.

No subjective responsiveness result is inferred from successful boot,
screenshots, process liveness, or ADB access.

## Automated Verification

Passed:

- `:app:testReleaseUnitTest`
- Full X11-enabled release assembly
- `apksigner` v2/v3 verification
- `scripts/inspect-hedgeyos-apk.sh`
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

`v0.1.0-alpha.5` is suitable as a test-signed public prerelease APK. Final
`v0.1.0` still requires production signing, extended Geany and Chromium
responsiveness testing, physical-keyboard testing, and broader device coverage.
