# Building hedgeyos

Rebuilding or upgrading the bundled Debian base has additional ownership,
migration, provenance, and device gates documented in
[`DEBIAN-REBUILD.md`](DEBIAN-REBUILD.md).

The intended top-level build command is:

```sh
./scripts/build-hedgeyos.sh
```

For CI builds without the private release key:

```sh
HEDGEYOS_USE_EXTERNAL_NATIVE_BUILD=1 HEDGEYOS_SIGN_RELEASE=0 ./scripts/build-hedgeyos.sh
```

To include the embedded Termux:X11 module:

```sh
git submodule update --init --recursive
HEDGEYOS_INCLUDE_X11_MODULE=1 HEDGEYOS_USE_EXTERNAL_NATIVE_BUILD=1 HEDGEYOS_SIGN_RELEASE=0 ./scripts/build-hedgeyos.sh
```

Normal local, CI, and release builds leave `HEDGEYOS_X11_DEBUG` unset or set it
to `0`; both produce `BuildConfig.HEDGEYOS_X11_DEBUG=false`, and normal runtime
code removes `TERMUX_X11_DEBUG` entirely. Only an intentional diagnostic build
may set `HEDGEYOS_X11_DEBUG=1`.

The script currently verifies:

- Java.
- Android SDK platform `android-36`.
- Termux `aapt2` override.
- Termux-native `zipalign` and `apksigner`.
- Private signing properties at
  `/data/data/com.termux/files/home/.signing/hedgeyos-release.properties`.
- The bundled Debian rootfs asset and checksum.
- The bundled rootfs checksum is copied into APK assets beside the rootfs so
  Android first boot can verify the asset.
- The repository-pinned Termux PRoot payload and adjacent SHA-256 checksum.
- Android first boot verifies that checksum before extracting the payload.
- The expected release APK filename, `hedgeyos-arm64-v8a.apk`.

Current local blocker:

- The Debian rootfs asset is built and bundled by GitHub Actions. Local phone
  release builds still need the rootfs asset under `build/rootfs/` or
  `app/src/main/assets/` before `./scripts/build-hedgeyos.sh` can package it.
- The verified PRoot payload is committed because the Termux package repository
  is rolling and removes old package URLs. Explicit maintainers can refresh it
  from current verified packages with
  `HEDGEYOS_REBUILD_PROOT_PAYLOAD=1 ./scripts/build-hedgeyos.sh`.
- Official SDK/NDK host tools are Linux x86_64, so hedgeyos's on-phone build path
  generates ARM64 JNI libraries with Termux `clang`/`clang++` and packages them
  from `jniLibs`. Conventional CI hosts can opt back into upstream `ndk-build`
  with `HEDGEYOS_USE_EXTERNAL_NATIVE_BUILD=1`.
- X11-enabled builds currently require a conventional Linux CI or workstation
  host. On-phone Gradle can compile the default no-X11 app when passed
  `-Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`,
  but the X11 module's AIDL/CMake/NDK path still invokes official Linux x86_64
  host binaries.
- X11-enabled builds also require the Termux:X11 native source submodules under
  `third_party/termux-x11/lorie/src/main/cpp/`. The build script checks for
  representative files and reports `git submodule update --init --recursive`
  when they are missing.

Release signing must use a dedicated hedgeyos keystore outside the repository.
Do not commit keystores, passwords, or signing properties.

Current hedgeyos release keystore:

- Path: `/data/data/com.termux/files/home/.signing/hedgeyos-release.jks`
- Certificate SHA-256: `5F:33:3B:9B:D8:8C:24:17:4C:FA:CE:14:73:BA:36:17:77:A3:E6:6F:06:F6:73:1E:D8:8F:E0:17:70:C7:2A:42`

GitHub Actions workflow:

- `.github/workflows/build.yml` checks out submodules recursively.
- `.github/workflows/build.yml` installs SDK 36, NDK 29, and CMake 3.22.1.
- It builds the Debian rootfs on `ubuntu-latest`.
- It verifies the repository-pinned Termux PRoot payload.
- It builds an unsigned ARM64 CI APK with `HEDGEYOS_INCLUDE_X11_MODULE=1` and
  `HEDGEYOS_SIGN_RELEASE=0`.
- It verifies the APK structure with `scripts/inspect-hedgeyos-apk.sh`, including
  package id, enabled X11-backed hedgeyos HOME launcher, disabled fallback HOME
  launcher, hidden Termux:X11 `MainActivity`, bundled rootfs/PRoot assets,
  background-protection permissions, overlay artwork, Linux migration assets,
  embedded X11 native library, and absence of obvious VNC/RDP files.
- It verifies the rootfs with `scripts/inspect-hedgeyos-rootfs.sh`, including
  customization files, modes, owners, migration generations, SVG
  loader/cache/package state, actual GTK decode provenance, portrait window
  package, and provenance.
- Unit CI runs `scripts/test-linux-defaults.sh` to prove migration idempotence
  and preservation of unrelated desktop files.
- Unit CI runs `scripts/test-linux-runtime.sh` plus
  `HedgeyosGuestRuntimeTest` to enforce the single PRoot mount contract,
  `/dev/shm` and `/run` ordering, versioned runtime assets, and the absence of
  app-specific Chromium workarounds.
- Unit CI runs `scripts/test-linux-migrations.sh` and ShellCheck. The migration
  test enforces manifest/package identity, exact versions and checksums,
  generation markers, and removal of the former two-package/devilspie shortcut.
- Focused runtime unit tests also verify applied directory modes, stale
  ephemeral-state cleanup, atomic state replacement, parsed capabilities,
  guest-UID-specific `XDG_RUNTIME_DIR`, and exact same-UID process targeting.
- It uploads a small `hedgeyos-apk-inspection` artifact separately from the large
  APK artifact.
- It uploads the APK, SHA-256 file, rootfs manifests, build logs, and inspection
  logs.
