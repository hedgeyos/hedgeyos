# Upstreams

Retrieved: 2026-07-25T09:17:35+07:00

hedgeyos is an independent derivative. These upstreams are recorded before hedgeyos
code changes so releases can be audited against the exact sources used.

## Git Sources

| Upstream | URL | Default branch | Commit SHA | Use in hedgeyos |
| --- | --- | --- | --- | --- |
| Termux application | https://github.com/termux/termux-app | master | `3df69d1da197dd9bd71a3bafd902dffd720576b4` | Base Android application and terminal/runtime code. |
| Termux:X11 | https://github.com/termux/termux-x11 | master | `a7a81d352fef6b3cb983c4998d1d50c3d3fe800f` | Embedded X server source. |
| PRoot Distro | https://github.com/termux/proot-distro | master | `08c90a1d8e6b2554f3d16bf3bac6e4908b594eb8` | Reference for PRoot distribution installation and shared `/tmp` behavior. |
| Termux package build sources | https://github.com/termux/termux-packages | master | `20fc68a481cbca943046244a0893a6470ce1d315` | Reference for Termux bootstrap/native package provenance. |
| Termux website/docs source | https://github.com/termux/termux.github.io | master | `f8d8791c10945c56ebb85775d70c3e504e9747f1` | Termux public documentation source. |

## Web Sources

| Source | URL | Retrieval evidence | Use in hedgeyos |
| --- | --- | --- | --- |
| Termux website and documentation | https://termux.dev/en/ | HTTP 200, `Last-Modified: Tue, 14 Jul 2026 20:53:50 GMT` | Product attribution and user-facing Termux behavior baseline. |
| Android custom Home documentation | https://developer.android.com/work/dpc/dedicated-devices/cookbook#custom-home | HTTP 200, `Last-Modified: Thu, 05 Mar 2026 10:39:12 GMT` | Home activity manifest and launcher-role behavior. |
| Debian stable information | https://www.debian.org/releases/stable/ | HTTP 200, `Last-Modified: Sat, 11 Jul 2026 16:00:30 GMT` | Debian 13 Trixie stable release and ARM64 target confirmation. |

## Debian Rootfs Pin

No release rootfs has been built or pinned yet. The first hedgeyos release must
record the exact Debian 13 Trixie ARM64 rootfs build source, package manifest,
SHA-256, build date, and architecture here before an APK is tagged.

## Termux PRoot Payload Pin

`scripts/build-proot-payload.sh` builds `termux-proot-aarch64.tar.zst` from the
Termux main repository and verifies these package hashes before unpacking:

| Package | Version | Termux repository path | SHA-256 |
| --- | --- | --- | --- |
| `proot` | `5.1.107.86` | `pool/main/p/proot/proot_5.1.107.86_aarch64.deb` | `d436cef349b14df0cde18dffe105e64cade3638b3193d870e8f748fde489ded1` |
| `libandroid-shmem` | `0.7` | `pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb` | `0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6` |
| `libtalloc` | `2.4.3` | `pool/main/libt/libtalloc/libtalloc_2.4.3_aarch64.deb` | `ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da` |
