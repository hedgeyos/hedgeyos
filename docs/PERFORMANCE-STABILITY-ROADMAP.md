# hedgeyos Performance And Stability Roadmap

This document preserves the implementation plan beyond the first hardening
phase. Phase 1 shipped in `v0.1.0-alpha.2`; later phases must retain the same
stability-first ordering and the complete Debian workstation package set.

## Phase 1: Background Survival And Portrait UX

Status: completed and device-tested in `v0.1.0-alpha.2`.

- Guide first-boot users through Android and vendor background-power settings.
- Hold a hedgeyos runtime wake lock while the desktop is intentionally running.
- Add the permanent, draggable, activity-local Hitomi-style hedgehog control.
- Make XFCE terminal-focused and portrait-safe.
- Make Linux customizations declarative, versioned, and documented.

The authoritative Linux change list is
[`ROOTFS-CUSTOMIZATIONS.md`](ROOTFS-CUSTOMIZATIONS.md) and
`rootfs/customizations.tsv`.

## Phase 2: Android Runtime Supervisor

- Replace static worker flags, disk polling, and broad process-name cleanup with
  one serialized runtime controller owned by the foreground service.
- Persist desired state, actual state, runtime generation, exact process
  identities, failure history, and timing data.
- Reconcile surviving X11/XFCE processes after Android kills and recreates the
  Java process. Adopt one healthy generation or terminate only the failed
  generation.
- Coalesce duplicate commands and let explicit Stop or Reset supersede pending
  recovery.
- Replace fixed startup delays with readiness signals.
- Retry crashes after 1, 3, and 10 seconds, limited to three attempts in ten
  minutes. Inform the user before each retry, then expose Retry, Reset Debian,
  and Factory Reset after exhaustion.
- Implement Factory Reset through Android's own
  `ActivityManager.clearApplicationUserData()` flow.

## Phase 3: First-Boot And APK I/O

- Store already-compressed rootfs and bootstrap payloads uncompressed in the APK.
- Stream rootfs bytes through SHA-256 verification into `zstd | tar`, removing
  the temporary rootfs copy and separate checksum pass.
- Journal extraction into staging, validate it, and atomically rename it.
- Add a versioned rootfs manifest with hashes, compressed/uncompressed sizes,
  configuration version, and required package capabilities.
- Calculate free-space requirements from uncompressed size plus 512 MiB.
- Replace the 30 MiB JNI bootstrap byte-array allocation with streamed
  extraction while retaining Termux terminal compatibility.
- Record bootstrap, extraction, migration, X11-ready, XFCE-ready, and recovery
  timings.

## Phase 4: Debian Session Stability

- Move the Java-generated desktop command into a versioned Linux session
  supervisor.
- Use `dbus-run-session` and generation-specific PID/readiness files.
- Replace fixed sleeps with bounded readiness checks for X11, `xfce4-session`,
  `xfwm4`, `xfdesktop`, and `xfce4-panel`.
- Start fallback components only after confirming they are absent and report
  `RUNNING` only when the usable desktop is ready.
- Disable release X11 debug logging, retain an explicit diagnostics mode, and
  cap routine logs at 8 MiB.

## Phase 5: Verification And Performance Gates

- Add unit coverage for state reconciliation, PID validation, command
  serialization, crash backoff, migrations, interrupted extraction, and log
  rotation.
- Extend CI inspection to cover rootfs customization metadata, package
  capabilities, session scripts, APK compression, and release debug settings.
- On the reference ARM64 phone, measure the current alpha before comparing the
  optimized build.
- Require 20 clean force-stop/relaunch cycles, ten-minute background and
  screen-off recovery, APT/GCC/Python/Git checks, and complete Reset/Factory
  Reset evidence.
- Target at least 25 percent faster fresh installation, 200 MiB less temporary
  storage, and a usable warm desktop within eight seconds or 30 percent faster
  than the alpha baseline.

## Fixed Constraints

- Stability takes priority over benchmark wins.
- ARM64 remains the release ABI.
- The full Debian compiler, Git, Python, headers, XFCE, and APT toolset remains.
- The extra-key bar and storybook branding remain.
- Target SDK and major upstream modernization wait until these phases are
  stable.
- Installed Termux is outside hedgeyos lifecycle management and must remain
  untouched.
