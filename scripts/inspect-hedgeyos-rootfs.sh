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

grep -Fq " ./usr/local/libexec/hedgeyos-apply-defaults" "$LISTING" ||
    fail "missing versioned defaults helper"
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
grep -Eq '^-rwxr-xr-x +1000/1000 +.* ./home/hedgeyos/Desktop/Terminal.desktop$' "$LISTING" ||
    fail "desktop launcher ownership or mode is wrong"
grep -Fq "devilspie2" "$PROVENANCE" ||
    fail "rootfs provenance does not include devilspie2"
! grep -Eiq "vnc|tigervnc|x11vnc|novnc|xrdp" "$PROVENANCE" ||
    fail "rootfs provenance contains VNC/RDP packages"

cp "$PROVENANCE" "$OUT_DIR/debian-trixie-arm64-rootfs.provenance"
printf 'Rootfs customization inspection passed.\n'
