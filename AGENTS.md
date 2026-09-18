# hedgeyos Agent Handoff

This repository is mid-flight. Do not treat a green build as a finished
product. The remaining goal is to turn the current source into an
acceptance-tested, publicly downloadable `v0.1.0` release APK.

## Product Target

hedgeyos must be a single installable ARM64 Android APK that turns an ordinary,
unrooted Android phone into a Debian 13 Trixie graphical workstation and Android
Home launcher.

The final user flow must be:

1. Download one `hedgeyos-arm64-v8a.apk`.
2. Install it normally.
3. Open hedgeyos.
4. Select hedgeyos as the Android Home app when prompted.
5. hedgeyos verifies and extracts its bundled Debian rootfs without downloading a
   distribution at runtime.
6. hedgeyos starts its embedded Termux:X11 server.
7. XFCE appears as the Android home screen.
8. Repeated Home presses return to the same Debian desktop session.
9. A Debian terminal can run ordinary commands such as `apt update`,
   `apt install git`, `python3`, and `gcc`.

No separate Termux, Termux:X11, UserLAnd, VNC viewer, VNC server, companion APK,
root access, device-owner mode, or accessibility-service requirement is allowed.
Do not substitute VNC. The display path must remain Debian GUI application to
X11 protocol to local Unix socket to embedded Termux:X11 to Android native
Surface.

## Current State

- Repository: `https://github.com/hedgeyos/hedgeyos`
- Branch: `master`
- Current Android version: `versionCode 4`, `versionName 0.1.0-alpha.4`
- Published prerelease:
  `https://github.com/hedgeyos/hedgeyos/releases/tag/v0.1.0-alpha.4`
- Published APK:
  `hedgeyos-arm64-v8a-alpha.4-test-signed.apk`
- Published APK SHA-256:
  `44865100049105dfd29dadce413ed45715685aa4c80c0dc68444793f09458321`
- Release source: tag `v0.1.0-alpha.4`

The latest local APK inspection records:

- Package id `org.hedgeyos`
- Label `hedgeyos`
- Min SDK 26, target SDK 28
- Native code `arm64-v8a`
- Launcher/Home activity `com.termux.x11.HedgeyosHomeActivity`
- Fallback `com.termux.app.HedgeyosHomeActivity` disabled in X11 builds
- `com.termux.x11.MainActivity` not exposed as launcher/Home
- Bundled Debian rootfs asset and checksum
- Bundled PRoot payload and checksum
- Embedded `libXlorie.so`
- No VNC/RDP files in APK listing

The published GitHub prerelease is signed with the repository test key and is
downloadable. It is not the final release. Device smoke testing proved the
embedded X11/XFCE desktop, force-stop/relaunch recovery, `xfwm4`, `xfdesktop`,
`xfce4-panel`, and wallpaper application on the attached ARM64 phone. Final
`v0.1.0` still needs production signing and the broader acceptance pass recorded
in `docs/TEST-REPORT.md`.

## Device Session Notes — 2026-09-18

Live Duo / HedgeyOS Debian guest work (user Decentricity). These changes were
proven on an installed alpha-class rootfs and are **not yet** in the publish
build. The main agent should read this section and port the same behavior into
`rootfs/runtime-assets/hedgeyos-linux`, defaults, migrations, and (where gated)
Android overlay / hedgehog menu triggers before the next public APK.

### Diagnosis that drove the work

- Desktop supervisor exit **137** = SIGKILL (`128 + 9`), not a Debian boot
  script failure.
- On this Android PRoot host (~5.4 GiB RAM), repeated 137s correlated with
  **Chromium** memory pressure and Android LMK / process-group kill.
- Evidence: many Chromium renderers, OOM-score adjust denied under SELinux,
  logging degraded (`EAGAIN`), then abrupt supervisor death; restart contained
  leftover Chromium PIDs via `hedgeyos-app-supervisor`.
- Product implication: **Chromium must not remain the everyday default
  browser** on low-RAM ARM64 phones.

### Modifications applied on-device (port these)

1. **Default browser = NetSurf (supervised), not Chromium**
   - Install Debian packages: `netsurf-gtk` (and optionally `dillo` as
     ultra-light fallback).
   - Add `/usr/local/libexec/hedgeyos-launch-netsurf` that runs NetSurf under
     `hedgeyos-app-supervisor` as user `hedgeyos` (same pattern as Chromium).
   - Add `/usr/local/share/applications/netsurf.desktop` with
     `Exec=/usr/local/libexec/hedgeyos-launch-netsurf %u`.
   - Add XFCE helper `hedgeyos-netsurf` under the user helpers dir and set
     `~/.config/xfce4/helpers.rc` to `WebBrowser=hedgeyos-netsurf`.
   - Point `mimeapps.list` http/https/html defaults at `netsurf.desktop`.
   - Optional: supervised `hedgeyos-launch-dillo` + desktop entry for the
     minimal-JS fallback; keep it secondary, not the panel default.

2. **Chromium stays available but constrained**
   - Keep the Cancel-default sandbox warning dialog.
   - Extend the warning to say Chromium is heavy and contributed to exit-137
     kills; prefer NetSurf/Dillo for everyday browsing.
   - When the user Continues, launch with memory-oriented flags in addition to
     `--no-sandbox`, for example:
     `--disable-dev-shm-usage`, `--process-per-site`,
     `--renderer-process-limit=2`, `--disable-gpu`,
     `--disable-features=IsolateOrigins,site-per-process`,
     modest `--disk-cache-size`, and
     `--js-flags=--max-old-space-size=384`.
   - Note: `docs/LINUX-RUNTIME.md` currently says not to paper over `/dev/shm`
     with `--disable-dev-shm-usage`. Prefer fixing the runtime shm contract
     first; keep the flag only as a low-RAM defense-in-depth if preflight
     still cannot guarantee shm under pressure.

3. **Do not rely on Chromium as the XFCE / xdg default**
   - Publish-build acceptance should open panel “Web Browser” / http links into
     **NetSurf**, not Chromium.
   - Chromium remains in the menu for sites that need a full Blink engine.

### Gating / delivery preference (undecided — design for both)

Some of the above (and future Duo/low-RAM tweaks) may be **gated** rather than
always baked into every fresh rootfs:

- **One-time post-install** when the APK/rootfs is fresh (first successful
  desktop or a versioned migration marker under `/var/lib/hedgeyos/`).
- **Thereafter re-triggerable** from the hedgehog floating menu (same family as
  today’s “Restart Desktop” / recovery actions), so users can re-apply or
  opt into light-browser defaults without resetting Debian.

Until product decides, implement the rootfs/runtime assets so they can be:
- applied unconditionally in the bundled rootfs, **or**
- applied by a small idempotent post-install / migration script invoked from
  Android (`HedgeyosOverlayController` / hedgehog menu) and from first-boot
  session init.

Do not require a Debian reset to switch the default browser.

### Out of scope for this note (already on device, not the browser change)

- Surface Duo curl post-install local fixes (`pidfd` fallback, etc.) remain the
  separate `hedgeyos-post-install.sh` path documented in `README.md`.
- Live-session NetSurf/Dillo packages and launchers were installed under the
  guest; the publish build must ship equivalent assets/migrations so new APKs
  do not depend on manual `apt install`.

## Important History

Several recent build issues were real, but they do not by themselves invalidate
the architecture:

- Phone-local X11 builds are blocked because the Android SDK/NDK tools used by
  the Termux:X11 module are Linux x86_64 host binaries. Build X11-enabled
  release artifacts on a normal Linux workstation or GitHub Actions.
- The rolling Termux repository repeatedly removed pinned `proot` package URLs,
  causing CI 404s. The 114 KiB verified PRoot payload and its checksum are now
  committed, so normal builds no longer depend on those moving package URLs.
  `scripts/build-proot-payload.sh` remains the explicit maintainer refresh path.
- `aapt dump badging` lists disabled launcher candidates. The CI inspection
  script now validates the relevant manifest activity blocks directly.
- `v0.1.0-alpha.4` currently uses `versionCode 4`. Future prereleases must bump
  `versionCode` before they can update this APK in place.
- Command-line `pm install` from Termux on the phone hit Android app-UID/FUSE
  restrictions. That is not evidence that normal Android UI install fails.

## Recommended Main-Machine Tactic

Use the workstation as the build/sign/release driver and an ARM64 Android device
as the acceptance target.

1. Clone a fresh copy and fetch submodules:

   ```sh
   git clone --recursive https://github.com/hedgeyos/hedgeyos.git
   cd hedgeyos
   ```

2. Install standard Linux Android build prerequisites: JDK 21, Android SDK
   platform 36, build-tools 35.0.0, NDK 29, CMake 3.22.1, `zstd`,
   `mmdebstrap`, `qemu-user-static`, `binfmt-support`, `debian-archive-keyring`,
   and `bison`.

3. Build the X11-enabled unsigned APK from source:

   ```sh
   git submodule update --init --recursive
   HEDGEYOS_INCLUDE_X11_MODULE=1 \
   HEDGEYOS_USE_EXTERNAL_NATIVE_BUILD=1 \
   HEDGEYOS_SIGN_RELEASE=0 \
   ./scripts/build-hedgeyos.sh
   ```

4. Run structural inspection:

   ```sh
   AAPT="$ANDROID_HOME/build-tools/35.0.0/aapt" \
     ./scripts/inspect-hedgeyos-apk.sh \
     app/build/outputs/apk/release/hedgeyos-arm64-v8a.apk \
     build/hedgeyos-inspection \
     rootfs/manifests/debian-trixie-arm64-rootfs.provenance
   ```

5. Sign with the existing hedgeyos release key. Keep the keystore and passwords
   outside the repo. Do not print or commit secrets.

6. Install through Android's normal package installer or through `adb install`
   from the workstation. Prefer a clean ARM64 device or clean Android user
   profile for final acceptance.

## Required Acceptance Tests Before `v0.1.0`

Do not tag or publish final `v0.1.0` until these are actually exercised on
device and recorded in `docs/TEST-REPORT.md`:

1. hedgeyos installs without Termux, Termux:X11, UserLAnd, or any VNC app
   installed.
2. hedgeyos opens from its application icon.
3. Android offers hedgeyos as a Home application.
4. First boot requires no rootfs download.
5. The bundled rootfs is verified and extracted successfully.
6. The embedded X11 surface appears.
7. XFCE reaches a usable desktop.
8. Touch input works.
9. The soft keyboard can be opened.
10. A physical keyboard works if available.
11. XFCE Terminal opens.
11a. Panel / default Web Browser opens **NetSurf** (supervised), not Chromium.
    Chromium remains launchable from the menu with the Cancel-default warning.
12. `/etc/os-release` identifies Debian 13 Trixie.
13. `apt update` works.
14. Installing a small package through Debian APT works.
15. Pressing Home repeatedly returns to the same session without duplicate
    processes.
16. Opening an Android app and pressing Home returns to hedgeyos.
17. Killing the hedgeyos activity does not corrupt Debian.
18. Relaunching hedgeyos recovers or cleanly restarts the desktop.
19. Restart Desktop works.
20. Reset Debian works.
21. No VNC server, viewer, or TCP VNC port exists.
22. No separate Termux:X11 APK is required.
23. Logs reveal actionable errors instead of silently showing a black screen.

Also capture screenshots under `docs/images/` for:

- First-boot setup
- XFCE as Android home screen
- XFCE Terminal showing Debian release information
- Android app drawer or Settings launched from hedgeyos

## Final Release Gate

After acceptance passes:

1. Update `README.md`, `docs/TEST-REPORT.md`, `docs/KNOWN-ISSUES.md`, and any
   affected architecture/build docs with the exact final tested status.
2. Ensure `README.md` links the stable latest-release URL:
   `https://github.com/hedgeyos/hedgeyos/releases/latest/download/hedgeyos-arm64-v8a.apk`
3. Tag `v0.1.0`.
4. Create GitHub Release `hedgeyos v0.1.0 - Debian as your Android home screen`.
5. Upload:
   - `hedgeyos-arm64-v8a.apk`
   - `hedgeyos-arm64-v8a.apk.sha256`
   - signing verification report
   - rootfs and PRoot provenance/checksum files
6. Verify the latest-release download URL works without GitHub authentication.

## Ground Rules For Future Agents

- Keep claims honest. A green Gradle build is not acceptance.
- Keep hedgeyos an independent Termux derivative; do not imply official Termux
  affiliation.
- Preserve GPL-compatible source availability and third-party notices.
- Do not commit APKs, rootfs blobs, keystores, signing passwords, or generated
  private artifacts to normal Git history.
- Put user-facing exported APKs, reports, and screenshots in Android-accessible
  shared storage when working from Termux, preferably under
  `/storage/emulated/0/Download/`.
- Continue with small, meaningful commits and push progress regularly.
