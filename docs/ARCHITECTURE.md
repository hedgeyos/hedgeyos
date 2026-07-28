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
  and binder connection path while adding a hedgeyos emergency menu overlay and
  runtime status on the startup screen.
- `hedgeyosX11Bridge` starts `com.termux.x11.CmdEntryPoint` through Android
  `app_process` with `CLASSPATH` pointed at the hedgeyos APK and `TMPDIR` pointed at
  hedgeyos's private shared tmp directory.
- `hedgeyosX11Bridge` sets `XKB_CONFIG_ROOT` to the bundled Debian rootfs XKB
  directory so the embedded X11 server does not depend on Termux paths.
- Release packaging stores `lib/arm64-v8a/libXlorie.so` uncompressed because
  the embedded X11 command entry point loads that library directly from the APK
  path.
- `com.termux.x11.HedgeyosHomeActivity` applies phone-friendly X11 defaults:
  scaled resolution, `displayScale=200`, fullscreen, and visible extra-key bar.

Target runtime path:

```text
Debian GUI application
  -> X11 protocol
  -> local Unix socket shared through hedgeyos tmp
  -> embedded Termux:X11 server
  -> Android native Surface in HedgeyosHomeActivity
```

Device evidence:

- On 2026-07-28, the published `v0.1.0-alpha.1` test APK booted the bundled
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
