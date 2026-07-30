# Debian Base Rebuild Guide

This is the maintainer checklist for replacing the bundled Debian base while
preserving hedgeyos behavior. The rootfs is generated output; Linux behavior
must remain reproducible from source files in this repository.

## Sources Of Truth

- `rootfs/build-rootfs.sh`: Debian suite, repositories, architecture, package
  set, clean-tree construction, checksum, and provenance.
- `rootfs/Containerfile`: reproducible privileged Debian rootfs build
  environment for hosts that do not have the required tools.
- `rootfs/finalize-rootfs.sh`: customization, GTK decode, migration closure,
  ownership, archive, and inspection gates shared by rootfs builds.
- `rootfs/configure-rootfs.sh`: accounts, APT sources, sudo policy, temporary
  directory modes, cleanup, and application of the customization manifest.
- `rootfs/customizations.tsv`: destination, numeric owner, mode, and policy for
  every hedgeyos Linux asset.
- `rootfs/runtime-assets/hedgeyos-linux/`: XFCE defaults, branding, desktop
  startup, runtime preflight, GTK decode smoke test, migration manifest and
  packages, bounded logger, session guard, GUI application supervisor, PRoot
  reproducer, window rules, and terminal launcher.
- `rootfs/baselines/v0.1.0-alpha.4-dpkg-status`: exact package database used to
  prove the currently published baseline's offline migration closure.
- `app/src/main/java/com/termux/app/HedgeyosRuntimeManager.java`: Android
  extraction health checks, rootfs asset name, migrations, and existing-rootfs
  asset refresh.
- `app/src/main/java/com/termux/app/HedgeyosGuestRuntime.java`: the single PRoot
  command and host-backed `/tmp`, `/dev/shm`, and `/run` contract.
- `scripts/build-hedgeyos.sh`, the inspection scripts, and
  `.github/workflows/build.yml`: packaging and release gates.

Gradle packages `rootfs/runtime-assets` directly as APK assets. Do not maintain
a second hand-copied version under `app/src/main/assets`.

## Current Linux Contract

A new Debian base must preserve all of these behaviors:

- ARM64 Debian with APT, sudo, networking, Git, Python, build tools, XFCE,
  xfce4-terminal, Thunar, xterm, fonts, icons, D-Bus, `devilspie2`, and
  `librsvg2-common`.
- Debian system files archived as numeric owner `0:0`.
- `/home/hedgeyos` archived as `1000:1000`.
- `/usr/bin/sudo` mode `4755`, `/etc/sudoers` and the hedgeyos sudoers fragment
  mode `0440`, and `/etc/sudo.conf` mode `0644`.
- Android-safe extraction: no populated `/dev`, hardlinks dereferenced, and
  extraction with `--no-same-owner --no-same-permissions
  --delay-directory-restore`.
- Host-backed `/tmp` and `/dev/shm` mode `1777`; `/run` mode `0755`;
  `/run/lock` mode `1777`; per-user runtime directories mode `0700`.
- PRoot options and bind order remain centralized in `HedgeyosGuestRuntime`.
- The foreground desktop chain remains
  PRoot -> `dbus-run-session` -> HedgeyOS setup -> XFCE `xinitrc` ->
  `xfce4-session`; do not reintroduce a background principal session.
- Desktop output is piped through the bounded logger. No GUI descendant may
  inherit a direct descriptor to an unlimited persistent file.
- The session guard, application supervisor, Chromium wrapper, session
  initializer, and ARM64 PRoot socket reproducer remain installed as root-owned
  runtime assets.
- The preflight verifies POSIX shared memory, memfd, System V shared memory,
  D-Bus, procfs, sysfs, X11, SVG loader/cache registration, and real GTK asset
  decoding. Interactive `DISPLAY=:1.0` and startup
  `DISPLAY=:1` must both resolve to `/tmp/.X11-unix/X1`.
- XFCE keeps the hedgeyos wallpaper, storybook theme, hedgehog-only menu,
  terminal-only desktop, single-click launch, left-side window controls,
  portrait sizing, and maximized primary applications.
- Existing installations receive idempotent runtime/default updates without a
  Debian reset. Offline package repairs use generation-specific durable markers
  and block desktop startup until post-install verification succeeds.
- PRoot package provenance is separate from Debian rootfs provenance. The
  committed payload must report the same version as its build pin, checksum,
  installed marker, and generated provenance.

## Rebase Checklist

1. Choose the Debian release, codename, repositories, and archive name.
2. Update the suite, repository lines, output names, release metadata, and
   provenance in `rootfs/build-rootfs.sh`.
3. Update the installed APT sources in `rootfs/configure-rootfs.sh`.
4. Update the rootfs name and Debian health check in
   `HedgeyosRuntimeManager`.
5. Update rootfs names in `scripts/build-hedgeyos.sh`,
   `scripts/firstboot.sh`, `scripts/inspect-hedgeyos-apk.sh`,
   `scripts/inspect-hedgeyos-rootfs.sh`, and `.github/workflows/build.yml`.
6. Update `UPSTREAMS.md`, `docs/FIRSTBOOT.md`, and release documentation.
7. Search for stale release-specific values:

   ```sh
   rg -n 'debian-trixie|trixie|debian_release=13|Debian 13'
   ```

8. Review every package in `PACKAGES`. Confirm renamed, removed, or split
   packages before changing the list.
9. Compare the published baseline dpkg database with the newly built rootfs.
   Resolve the dependency graph of every new required package; do not guess
   closure members. Update the canonical manifest and `.deb` files under
   `rootfs/runtime-assets/hedgeyos-linux`, including exact version,
   architecture, generation, reason, filename, and SHA-256.
10. Run `rootfs/verify-migration-packages.sh`. It requires a one-to-one
    manifest/directory match, accepts only `arm64` or valid `all` packages,
    checks the new rootfs versions, and validates closure against the published
    baseline with Debian's dependency parser.
11. When GTK remains part of the desktop, verify `librsvg2-common`,
    `librsvg2-2`, `libpixbufloader_svg.so`, the architecture-specific
    query-loader, loader cache, and actual SVG/symbolic/checkmark/PNG decoding.
    Prefer Debian triggers; do not regenerate the cache merely because its
    command is not on the default `PATH`.
12. Apply every hedgeyos file through `rootfs/customizations.tsv`. Add a row
    whenever a new Linux asset is introduced.
13. Increment the defaults migration version in
    `hedgeyos-apply-defaults` when existing installations need new XFCE values.
14. Update Android's existing-rootfs refresh list in `ensureLinuxBranding()` if
    a new replaceable system asset is added.
15. Rebuild `hedgeyos-proot-seqpacket-reproducer` for ARM64 when its C source or
    toolchain contract changes:

    ```sh
    ./scripts/build-proot-seqpacket-reproducer.sh
    ./scripts/test-proot-seqpacket-reproducer.sh
    ```

16. Verify the committed PRoot payload and its embedded binary identity:

    ```sh
    ./scripts/test-proot-payload.sh
    ```

## Build And Inspect

The preferred clean build uses the repository container definition:

```sh
podman build -t hedgeyos-rootfs-builder:trixie -f rootfs/Containerfile .
podman run --rm --privileged --security-opt label=disable \
  -e OUT_DIR=/work/build/rootfs \
  -v "$PWD:/work" \
  localhost/hedgeyos-rootfs-builder:trixie
```

The container is privileged only for `mmdebstrap` namespace and mount work.
Source and output stay in the mounted checkout.

Alternatively, use a Debian build host with `mmdebstrap`, `zstd`, and a
verified Debian archive keyring:

```sh
sudo env \
  OUT_DIR="$PWD/build/rootfs" \
  DEBIAN_KEYRING=/usr/share/keyrings/debian-archive-keyring.gpg \
  ./rootfs/build-rootfs.sh

./scripts/inspect-hedgeyos-rootfs.sh \
  build/rootfs/<rootfs-name>.tar.zst \
  rootfs/manifests/<rootfs-name>.provenance \
  build/hedgeyos-rootfs-inspection

./scripts/test-linux-defaults.sh
./scripts/test-linux-runtime.sh
./scripts/test-linux-migrations.sh
./scripts/test-runtime-containment.sh
./scripts/test-proot-seqpacket-reproducer.sh
./scripts/test-proot-payload.sh
shellcheck rootfs/*.sh scripts/test-linux-*.sh \
  scripts/test-runtime-containment.sh scripts/test-proot-*.sh
```

Then copy the generated archive and checksum into APK assets, or let CI do so,
and build with `./scripts/build-hedgeyos.sh`. Run
`scripts/inspect-hedgeyos-apk.sh` against the resulting APK.

Never bypass an ownership or archive-mode assertion to make a build pass. The
previous sudo failure occurred because root-owned Debian files were archived as
UID 1000; the explicit ownership checks in `build-rootfs.sh` prevent that class
of regression.

## Device Acceptance

Test both a true uninstall/reinstall and an update preserving an existing
rootfs. On OPPO/ColorOS test devices use streamed installation explicitly:

```sh
adb install --no-incremental -r <signed-apk>
```

For each path verify:

- First boot reaches the complete XFCE session with normal cursor, panel,
  wallpaper, and dock.
- `sudo -n true`, `apt update`, package installation, and dpkg state work.
- `hedgeyos-runtime-preflight` ends with `summary=PASS fatal=0` from both
  startup and an interactive terminal.
- Python `multiprocessing.shared_memory`, a GTK application, and a Qt
  application work.
- Restart Desktop and Android force-stop/relaunch recover without deleting
  installed Debian packages.
- Session D-Bus and XFCE retain exact live process identities throughout the
  test.
- Chromium launches through the generic supervisor and exits without a
  continuing zygote/ENOSYS loop.
- Runtime logs remain within their session, application, and aggregate budgets.
- An oversized sparse legacy session log is repaired on update without Reset
  Debian.
- The separate `com.termux` package is unchanged.

Record the archive SHA-256, APK SHA-256, signing certificate, provenance,
inspection output, and device evidence in `docs/TEST-REPORT.md`.
