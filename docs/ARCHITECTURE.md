# hedgeyos Architecture

hedgeyos is a Termux-derived Android application that is being turned into a
single-APK Debian graphical workstation and Home launcher.

Current repository state:

- Termux app history is preserved as the base repository.
- Termux:X11 source is vendored under `third_party/termux-x11` using a Git subtree.
- The Android package id is `org.hedgeyos`.
- Java/Kotlin package namespaces remain `com.termux` for now to reduce refactor risk.
- A hedgeyos Home activity is present and offers recovery actions.
- `HedgeyosRuntimeService` is registered as hedgeyos's foreground runtime boundary.
- The runtime service persists whether the desktop is intentionally running and
  owns the corresponding partial wake lock. `SystemEventReceiver` restores that
  desired state after Android boot.
- `HedgeyosPowerPolicy` owns first-boot background-survival state, Android power
  checks, safe settings intents, and manufacturer-specific guidance.
- `HedgeyosRuntimeManager` persists first-boot/session state and can copy, verify,
  extract, configure, reset, and log the bundled Debian rootfs transaction.
- The build produces a pinned Termux PRoot payload from verified `.deb` files,
  and first boot installs it under hedgeyos's private `files/usr` prefix.
- `HEDGEYOS_INCLUDE_X11_MODULE=1` includes the vendored Termux:X11 `lorie` Android
  library and its shell-loader stub in the hedgeyos APK.
- X11-enabled builds enable `com.termux.x11.HedgeyosHomeActivity` as the launcher
  and HOME activity, disable the fallback native dashboard HOME activity, and
  suppress Termux:X11's standalone launcher entry.
- `com.termux.x11.HedgeyosHomeActivity` subclasses the vendored Termux:X11
  `MainActivity`, preserving its `LorieView` surface, input, resize, clipboard,
  and binder connection path while adding runtime status and the permanent
  activity-local `HedgeyosOverlayController`.
- The overlay uses the transparent Hitomi hedgehog artwork at `96dp`, persists
  normalized drag coordinates, clamps them after layout or rotation changes,
  and opens compact scrollable control and power-setup mini-windows.
- `HedgeyosGuestRuntime` owns one PRoot mount and environment contract for
  desktop, terminal, package, and migration commands. Its host-backed
  `/tmp`, `/run`, and `/dev/shm` tree is reset between desktop sessions.
- `HedgeyosProcessOwner` records and validates exact same-UID desktop and X11
  process identities. Stop and restart do not use broad process-name matching.
- Normal builds and sessions remove the presence-sensitive
  `TERMUX_X11_DEBUG` variable. `HEDGEYOS_X11_DEBUG=1` is reserved for explicit
  diagnostic builds; the launcher also offers a warned, in-memory one-shot
  diagnostic restart that cannot persist across an app-process crash.
- Diagnostic X11 logcat records the actual child PID. Cleanup requires the
  recorded X11 parent PID, matching Android UID, exact parent relationship, and
  exact `logcat --pid <x11-pid>` argv. Vendored native wait threads reap both
  X11 diagnostic logcat children.
- Linux runtime preflight results are atomically exported to
  `linux-runtime-report.txt` and exposed through the hedgehog controls.
- `HedgeyosX11Bridge` starts `com.termux.x11.CmdEntryPoint` through Android
  `app_process` with `CLASSPATH` pointed at the hedgeyos APK and `TMPDIR` pointed at
  hedgeyos's private shared tmp directory.
- `HedgeyosX11Bridge` sets `XKB_CONFIG_ROOT` to the bundled Debian rootfs XKB
  directory so the embedded X11 server does not depend on Termux paths.
- Release packaging stores `lib/arm64-v8a/libXlorie.so` uncompressed because
  the embedded X11 command entry point loads that library directly from the APK
  path.
- `com.termux.x11.HedgeyosHomeActivity` applies phone-friendly X11 defaults:
  scaled resolution, `displayScale=240`, fullscreen, and visible extra-key bar.
- Linux customizations are sourced from
  `rootfs/runtime-assets/hedgeyos-linux`, declared by
  `rootfs/customizations.tsv`, seeded into fresh rootfs builds, and packaged in
  the APK for versioned migration.
- Offline Debian repair is driven by
  `rootfs/runtime-assets/hedgeyos-linux/migration-packages.tsv`. Each
  checksum-pinned package belongs to a durable generation marker under
  `/var/lib/hedgeyos/migrations`; interrupted generations retry before XFCE,
  while completed generations are skipped.
- Fresh rootfs builds include `librsvg2-common` and its exact Trixie
  dependencies. The build runs real GDK-Pixbuf decoding of a deterministic SVG,
  an Adwaita symbolic icon and check indicator, and an ordinary PNG.
- Versioned XFCE defaults identify the Applications plugin by its canonical
  `applicationsmenu` type, give it a compact hedgehog-only button, and enable
  native single-click desktop launchers.
- XFCE starts `devilspie2` for event-driven portrait window policy. Primary
  terminal and file-manager windows maximize; oversized secondary windows
  receive a narrower initial geometry.

Target runtime path:

```text
Debian GUI application
  -> X11 protocol
  -> local Unix socket shared through hedgeyos tmp
  -> embedded Termux:X11 server
  -> Android native Surface in HedgeyosHomeActivity
```

Device evidence:

- On 2026-07-29, the `v0.1.0-alpha.2` test APK booted the bundled
  Debian/XFCE desktop as Android Home on a CPH2499 ARM64 phone. After
  force-stop/relaunch, the runtime returned to `RUNNING` with `org.hedgeyos`,
  `hedgeyos-x11`, `xfce4-session`, `xfwm4`, `xfdesktop`, and `xfce4-panel`
  processes. See `docs/TEST-REPORT.md`.

Major remaining implementation boundaries:

- Complete the remaining final-release evidence for in-app Reset Debian, true
  clean first boot of the exact final artifact, and physical keyboard behavior.
- Replace `com.termux.x11` package assumptions in loader, broadcasts, and native
  code where they conflict with hedgeyos package identity.
- Replace `/data/data/com.termux` native path assumptions with hedgeyos paths where
  runtime environment overrides are not enough.
