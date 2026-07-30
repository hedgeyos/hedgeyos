# Known Issues

- `v0.1.0-alpha.6` is a test-signed prerelease candidate, not the final
  production-signed `v0.1.0`.
- The historical Chromium failure is confirmed: its browser process lost the
  session D-Bus, surviving zygotes looped on recvmsg `ENOSYS`, and the inherited
  desktop log grew at roughly 33 MB/s to about 5.93 GB. Alpha.6 corrects the
  fragile D-Bus/XFCE lifetime and contains descendant/output failure
  generically.
- The bounded ARM64 `SOCK_SEQPACKET` reproducer passes in Ubuntu, ARM64 QEMU,
  native Android userspace, and the published HedgeyOS PRoot guest. The exact
  Chromium-specific low-level condition that produced persistent ENOSYS has
  not been reproduced. Do not describe the PRoot `5.1.107.89` refresh as a
  proven syscall fix.
- Chromium, Electron applications, IDEs, and other multi-process software may
  still expose Android-kernel or PRoot compatibility limits. Applications must
  opt into the generic HedgeyOS supervisor through their launcher to receive
  instance-specific descendant containment.
- Chromium's setuid and user-namespace sandboxes do not initialize under the
  tested Android PRoot. The HedgeyOS launcher therefore warns on every launch,
  defaults to Cancel, and requires explicit consent for a session using
  `--no-sandbox`. Android app isolation and HedgeyOS process/output containment
  remain active, but websites can access files available to the Debian user.
- HedgeyOS log safety covers known runtime logs and supervised application
  output. It intentionally does not truncate arbitrary user files or logs
  created independently inside the Debian home directory.
- A full system D-Bus is not provided under unprivileged PRoot. Session D-Bus
  is supported and required. ConsoleKit, AT-SPI, DPMS, power-service, and
  Android-hidden `/proc/sys` warnings are not automatically fatal.
- Final-release evidence gaps include direct in-app Reset Debian,
  physical-keyboard behavior, prolonged Chromium and Geany responsiveness,
  broader device coverage, and production signing.
- Termux must not be removed from the test phone. HedgeyOS independence is
  established by its own Android package, bundled PRoot/rootfs, embedded X11,
  and process paths. It does not require the separately installed Termux app,
  a separate Termux:X11 APK, or VNC.
- `third_party/termux-x11` retains upstream `com.termux.x11` namespace and
  native path assumptions. The current embedded build works through HedgeyOS
  runtime overrides, but these paths remain release regression surfaces.
- X11-enabled builds require a conventional Linux host. Official Android
  SDK/NDK host tools are Linux x86_64 binaries and cannot be executed by an
  on-phone ARM64 Gradle build.
- Host lint retains existing Termux `PendingIntent` mutability warnings, and
  JDK 21 reports Java 8 source/target deprecation warnings.
- Remaining performance contributors can include PRoot syscall interception,
  Termux:X11 rendering, software OpenGL, Android scheduler or cpuset behavior,
  excessive X11 resolution, application workloads, and system-wide Android
  RAM/swap pressure. Alpha.6 does not claim that all GUI performance problems
  are solved.
