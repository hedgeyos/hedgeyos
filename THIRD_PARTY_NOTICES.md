# Third Party Notices

hedgeyos is an independent derivative and is not an official Termux release.

## Termux Application

- Source: https://github.com/termux/termux-app
- License: GPL-3.0-or-later, with included component licenses as provided by upstream.
- Use: Base Android terminal, service, bootstrap, file, and shared utility code.

## Termux:X11

- Source: https://github.com/termux/termux-x11
- License: GPL-3.0-or-later, with included component licenses as provided by upstream.
- Use: Embedded X server source under `third_party/termux-x11`.

## PRoot Distro

- Source: https://github.com/termux/proot-distro
- License: GPL-compatible upstream licensing as provided by the project.
- Use: Reference for PRoot distribution setup and shared `/tmp` behavior.

## PRoot Runtime Packages

- Source packages: Termux `proot`, `libandroid-shmem`, and `libtalloc`.
- Binary source: https://packages.termux.dev/apt/termux-main
- Use: Bundled unprivileged Debian runtime payload under hedgeyos's private prefix.
- License notices: The payload keeps package copyright files under
  `usr/share/doc/*/copyright`.

## Termux Packages

- Source: https://github.com/termux/termux-packages
- License: Package build scripts and patches under upstream-provided terms.
- Use: Reference for Termux bootstrap and native package provenance.

## Debian

- Source: https://www.debian.org/
- Use: Target Debian 13 Trixie ARM64 userspace.

hedgeyos release artifacts must include complete corresponding source and the exact
rootfs provenance recorded in `UPSTREAMS.md` and `rootfs/manifests/`.
