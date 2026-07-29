# Known Issues

- `v0.1.0-alpha.3` is a published, test-signed prerelease. It is not the final
  `v0.1.0` production release.
- The alpha boot/restart gate passes on the attached ARM64 phone: hedgeyos
  installs, starts embedded X11, reaches XFCE with the panel/dock and `xfwm4`
  running, applies the hedgeyos wallpaper, and recovers after app
  force-stop/relaunch.
- Remaining final-release evidence gaps are direct in-app Reset Debian,
  physical-keyboard behavior, broader device coverage, and production signing.
  Alpha.2 has exact-artifact proof for true clean first boot, APT package
  installation, background protection, and process recovery.
- Termux must not be removed from the test phone. hedgeyos independence should
  be proven from APK contents, code paths, packages, and processes. Current
  evidence shows hedgeyos uses its own package, bundled PRoot/rootfs, and
  embedded X11; it does not require the installed Termux app, a separate
  Termux:X11 APK, or VNC.
- `third_party/termux-x11` is vendored as an optional module and still contains
  upstream `com.termux.x11` namespace assumptions. The current hedgeyos build
  works by embedding those classes inside the hedgeyos APK and starting
  `CmdEntryPoint` with `CLASSPATH` pointed at hedgeyos's own `base.apk`.
- Some native Termux:X11 code still has upstream path assumptions. Current
  runtime overrides provide the working `TMPDIR` and `XKB_CONFIG_ROOT`, but this
  area should remain part of release regression testing.
- On-phone Gradle builds cannot execute official Android SDK/NDK Linux x86_64
  host binaries. Use a Linux host or CI for X11-enabled release builds.
- Host lint currently reports existing PendingIntent mutability warnings in
  `TermuxService`. They are not blocking the current target SDK, but they should
  be addressed before raising target SDK.
- Gradle with JDK 21 emits Java 8 source/target deprecation warnings. The build
  still completes with the current toolchain.
