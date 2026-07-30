#!/usr/bin/env sh
set -eu

ARCHIVE="${1:-}"
PROVENANCE="${2:-}"
OUT_DIR="${3:-}"

fail() {
    printf 'inspect-hedgeyos-rootfs: %s\n' "$*" >&2
    exit 1
}

[ -f "$ARCHIVE" ] || fail "missing rootfs archive: $ARCHIVE"
[ -f "$PROVENANCE" ] || fail "missing rootfs provenance: $PROVENANCE"
[ -n "$OUT_DIR" ] || fail "usage: scripts/inspect-hedgeyos-rootfs.sh <archive> <provenance> <out-dir>"
command -v zstd >/dev/null 2>&1 || fail "zstd is required"
command -v tar >/dev/null 2>&1 || fail "tar is required"

mkdir -p "$OUT_DIR"
LISTING="$OUT_DIR/rootfs-listing.txt"
zstd -dc "$ARCHIVE" | tar --numeric-owner -tvf - > "$LISTING"
STATUS="$OUT_DIR/dpkg-status"
zstd -dc "$ARCHIVE" | tar -xOf - ./var/lib/dpkg/status > "$STATUS"

grep -Fq " ./usr/local/libexec/hedgeyos-apply-defaults" "$LISTING" ||
    fail "missing versioned defaults helper"
grep -Fq " ./usr/local/libexec/hedgeyos-runtime-preflight" "$LISTING" ||
    fail "missing Linux runtime preflight"
grep -Fq " ./usr/local/libexec/hedgeyos-gtk-asset-smoke" "$LISTING" ||
    fail "missing GTK asset smoke helper"
grep -Fq " ./usr/local/libexec/hedgeyos-start-desktop" "$LISTING" ||
    fail "missing Linux desktop startup helper"
grep -Fq " ./usr/local/libexec/hedgeyos-bounded-log" "$LISTING" ||
    fail "missing bounded Linux logger"
grep -Fq " ./usr/local/libexec/hedgeyos-session-guard" "$LISTING" ||
    fail "missing desktop session guard"
grep -Fq " ./usr/local/libexec/hedgeyos-app-supervisor" "$LISTING" ||
    fail "missing generic GUI app supervisor"
grep -Fq " ./usr/local/libexec/hedgeyos-launch-chromium" "$LISTING" ||
    fail "missing warned Chromium launcher"
grep -Fq " ./usr/local/libexec/hedgeyos-proot-seqpacket-reproducer" "$LISTING" ||
    fail "missing ARM64 PRoot recvmsg reproducer"
grep -Fq " ./etc/xdg/autostart/hedgeyos-session-init.desktop" "$LISTING" ||
    fail "missing XFCE session initializer"
grep -Fq " ./usr/local/share/applications/chromium.desktop" "$LISTING" ||
    fail "missing supervised Chromium launcher"
grep -Fq " ./etc/xdg/autostart/hedgeyos-window-rules.desktop" "$LISTING" ||
    fail "missing portrait window-rule autostart"
grep -Fq " ./etc/hedgeyos/devilspie2/hedgeyos-window-rules.lua" "$LISTING" ||
    fail "missing portrait window rules"
grep -Fq " ./home/hedgeyos/Desktop/Terminal.desktop" "$LISTING" ||
    fail "missing terminal-focused desktop launcher"
grep -Fq " ./usr/share/hedgeyos/defaults/terminalrc" "$LISTING" ||
    fail "missing terminal defaults"
grep -Eq '^-rwxr-xr-x +0/0 +.* ./usr/local/libexec/hedgeyos-apply-defaults$' "$LISTING" ||
    fail "defaults helper ownership or mode is wrong"
grep -Eq '^-rwxr-xr-x +0/0 +.* ./usr/local/libexec/hedgeyos-runtime-preflight$' "$LISTING" ||
    fail "runtime preflight ownership or mode is wrong"
grep -Eq '^-rwxr-xr-x +0/0 +.* ./usr/local/libexec/hedgeyos-gtk-asset-smoke$' "$LISTING" ||
    fail "GTK asset smoke helper ownership or mode is wrong"
grep -Eq '^-rwxr-xr-x +0/0 +.* ./usr/local/libexec/hedgeyos-start-desktop$' "$LISTING" ||
    fail "desktop startup helper ownership or mode is wrong"
grep -Eq '^-rwxr-xr-x +0/0 +.* ./usr/local/libexec/hedgeyos-app-supervisor$' "$LISTING" ||
    fail "app supervisor ownership or mode is wrong"
grep -Eq '^-rwxr-xr-x +0/0 +.* ./usr/local/libexec/hedgeyos-launch-chromium$' "$LISTING" ||
    fail "Chromium launcher ownership or mode is wrong"
grep -Eq '^-rwxr-xr-x +0/0 +.* ./usr/local/libexec/hedgeyos-proot-seqpacket-reproducer$' "$LISTING" ||
    fail "PRoot recvmsg reproducer ownership or mode is wrong"
grep -Eq '^-rwxr-xr-x +1000/1000 +.* ./home/hedgeyos/Desktop/Terminal.desktop$' "$LISTING" ||
    fail "desktop launcher ownership or mode is wrong"
grep -Fq "devilspie2" "$PROVENANCE" ||
    fail "rootfs provenance does not include devilspie2"
grep -Fq "librsvg2-common" "$PROVENANCE" ||
    fail "rootfs provenance does not include librsvg2-common"
grep -Fq "gtk_svg_smoke=PASS" "$PROVENANCE" ||
    fail "rootfs provenance does not record successful GTK SVG decoding"
grep -Fq "libpixbufloader_svg.so" "$LISTING" ||
    fail "rootfs archive is missing libpixbufloader_svg.so"
grep -Fq "gdk-pixbuf-2.0/2.10.0/loaders.cache" "$LISTING" ||
    fail "rootfs archive is missing the active GDK-Pixbuf loader cache"
for package_name in librsvg2-common librsvg2-2; do
    awk -v package_name="$package_name" '
        BEGIN { RS = "" }
        $0 ~ ("^Package: " package_name "\n") &&
            $0 ~ "\nStatus: install ok installed(\n|$)" {
            found = 1
        }
        END { exit found ? 0 : 1 }
    ' "$STATUS" || fail "$package_name is not installed in the rootfs package database"
done

loader_cache_path=$(awk \
    '$NF ~ /gdk-pixbuf-2.0\/2.10.0\/loaders.cache$/ { print $NF; exit }' \
    "$LISTING")
[ -n "$loader_cache_path" ] || fail "could not locate loader cache in archive"
zstd -dc "$ARCHIVE" | tar -xOf - "$loader_cache_path" > "$OUT_DIR/loaders.cache"
grep -Fq 'libpixbufloader_svg.so' "$OUT_DIR/loaders.cache" ||
    fail "archived loader cache does not contain libpixbufloader_svg.so"
grep -Fq '"svg"' "$OUT_DIR/loaders.cache" ||
    fail "archived loader cache does not advertise SVG"

grep -Fq " ./var/lib/hedgeyos/migrations/window-policy-v1" "$LISTING" ||
    fail "fresh rootfs is missing the window-policy migration marker"
grep -Fq " ./var/lib/hedgeyos/migrations/gtk-svg-loader-v1" "$LISTING" ||
    fail "fresh rootfs is missing the GTK SVG migration marker"
! grep -Eiq "vnc|tigervnc|x11vnc|novnc|xrdp" "$PROVENANCE" ||
    fail "rootfs provenance contains VNC/RDP packages"

cp "$PROVENANCE" "$OUT_DIR/debian-trixie-arm64-rootfs.provenance"
printf 'Rootfs customization inspection passed.\n'
