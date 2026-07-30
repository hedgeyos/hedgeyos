# First Boot

hedgeyos first boot is transactional: install private runtime tools, verify bundled
assets, extract Debian into a staging directory, configure it, then start
embedded X11 and XFCE.

Target states:

- `NOT_INSTALLED`
- `VERIFYING_ASSET`
- `INSTALLING_PROOT`
- `EXTRACTING`
- `CONFIGURING`
- `READY`
- `STARTING_X11`
- `STARTING_DESKTOP`
- `RUNNING`
- `STOPPING`
- `FAILED`

`HedgeyosRuntimeManager` persists these states under `$HEDGEYOS_FILES_DIR/hedgeyos-state/`,
and `HedgeyosRuntimeService` runs the state machine from a foreground service. The
Home activity starts the service, polls status, and exposes recovery controls.

Before the user dismisses first-boot onboarding, a scrollable Background setup
mini-window:

- requests Android's battery-optimization exemption;
- reports notification and background-restriction status;
- links to app, notification, battery, and known vendor autostart settings;
- gives manufacturer-specific manual instructions; and
- permits continuing with a reliability warning when Android cannot change a
  vendor setting directly.

The guide remains available from the permanent hedgehog control. While the
desktop is intentionally running, the foreground runtime service owns a partial
wake lock. Explicit Stop and Reset release it, and a requested running state is
restored after Android boot.

Implemented and device-proven behavior on 2026-07-26:

- Installs the embedded Termux bootstrap into hedgeyos's private `files/usr` path
  when needed so bundled `bash`, `tar`, and `zstd` are available.
- Copies, verifies, and extracts the pinned `termux-proot-aarch64.tar.zst`
  payload into hedgeyos's private prefix.
- Copies `debian-trixie-arm64-rootfs.tar.zst` and its `.sha256` file from APK
  assets into hedgeyos private storage.
- Verifies the rootfs SHA-256 before extraction.
- Extracts the rootfs with Android-safe tar flags:
  `--no-same-owner --no-same-permissions --delay-directory-restore`.
- Builds the rootfs archive without populated `/dev` entries and with hardlinks
  dereferenced so Android app storage can extract it.
- Extracts into `debian.staging`.
- Ensures `/home/hedgeyos`, `/tmp`, `passwd`, `group`, sudoers, resolver config,
  and a hedgeyos rootfs version marker exist.
- Moves the staging rootfs into `debian` only after health checks pass.
- Leaves an existing healthy rootfs in place if a new extraction fails.
- Starts embedded X11 on display `:1` through Android `app_process`.
- Points embedded X11 `TMPDIR` at HedgeyOS's host-backed runtime `/tmp` and
  `XKB_CONFIG_ROOT` at the bundled Debian XKB directory.
- Starts the XFCE supervisor through bundled PRoot with `PROOT_LOADER`,
  `PROOT_TMP_DIR`, and `LD_LIBRARY_PATH` pointed at the hedgeyos private prefix.
- Starts the desktop through a foreground
  `dbus-run-session -> hedgeyos-start-desktop -> xinitrc -> xfce4-session`
  chain. The principal XFCE session is never backgrounded.
- Defaults the phone display to scaled mode, `displayScale=240`, fullscreen, and
  visible extra-key bar.

The X11 `TMPDIR` is the host-backed HedgeyOS runtime `/tmp` shared with the
guest, not persistent rootfs storage.

Before any new or existing rootfs starts XFCE, Android repairs oversized known
HedgeyOS-managed logs. It preserves a bounded diagnostic tail and metadata,
atomically replaces the dangerous file, and leaves Debian packages, home data,
and unrelated files untouched. Repair is idempotent and runs before the
128 MiB low-free-storage safety gate. If storage remains critically low,
startup enters `FAILED` with a recovery message instead of repeatedly launching
the desktop into the same unsafe condition.

On every update, `ensureLinuxBranding()` refreshes the versioned guest runtime
helpers, including the bounded logger, foreground session entry point, D-Bus
guard, session initializer, GUI application supervisor, Chromium desktop
wrapper, and PRoot socket reproducer. This does not replace the rootfs and does
not remove user-installed packages.

## Existing Rootfs Migrations

An existing rootfs is never replaced to apply package repairs. Before X11 or
XFCE starts, `HedgeyosRuntimeManager.ensureLinuxMigrations()`:

- parses the packaged tab-separated migration manifest;
- rejects missing, duplicate, unexpected, wrong-version, wrong-architecture,
  or wrong-checksum `.deb` assets;
- copies one generation into a private temporary directory;
- installs it offline through the common PRoot command builder;
- completes dpkg configuration and package triggers;
- runs generation-specific verification;
- atomically writes `/var/lib/hedgeyos/migrations/<generation>` only after all
  checks pass; and
- removes temporary packages after success.

An interrupted generation has no durable marker and retries on the next start.
A failed migration leaves the existing rootfs and user packages in place,
publishes `linux-migration.log`, sets runtime state to `FAILED`, and blocks a
falsely healthy XFCE start. Fresh rootfs builds already contain the complete
packages and generation markers, so they do not reinstall migration assets on
first boot.

The test-signed `v0.1.0-alpha.2` artifact reached `RUNNING` from a true
uninstall/reinstall on the attached CPH2499 phone. The onboarding mini-window
appeared automatically, the bundled rootfs reached the complete themed XFCE
session, and the built-in acceptance check completed `apt update`, installed
and ran `hello`, and verified its dpkg record.
Evidence is recorded in `docs/TEST-REPORT.md` and
`docs/images/hedgeyos-scaled-firstboot-xfce.png`.

Required invariant:

An existing healthy rootfs must never be destroyed because a new extraction
failed. Extraction must happen into a staging directory and move into place only
after verification and health checks pass.

Runtime safety invariant:

An existing rootfs may be repaired and receive replaceable HedgeyOS system
helpers, but ordinary APK update startup must not delete its home directory,
remove installed packages, or require Reset Debian. Known managed logs are the
only files eligible for automatic oversized-log repair.

Remaining first-boot/recovery evidence needed before final `v0.1.0`:

- In-app Reset Debian.
- Physical keyboard behavior.
- Broader device coverage and prolonged GUI responsiveness.
