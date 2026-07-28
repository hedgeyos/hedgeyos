#!/usr/bin/env sh
set -eu

ROOTFS="${1:-}"
[ -n "$ROOTFS" ] || {
    echo "usage: rootfs/configure-rootfs.sh <rootfs-dir>" >&2
    exit 1
}

[ -d "$ROOTFS" ] || {
    echo "rootfs directory does not exist: $ROOTFS" >&2
    exit 1
}

install -d "$ROOTFS/home/hedgeyos" "$ROOTFS/etc/sudoers.d" "$ROOTFS/etc/apt/sources.list.d"

grep -q '^hedgeyos:' "$ROOTFS/etc/group" || printf 'hedgeyos:x:1000:\n' >> "$ROOTFS/etc/group"
grep -q '^hedgeyos:' "$ROOTFS/etc/passwd" || printf 'hedgeyos:x:1000:1000:hedgeyos User:/home/hedgeyos:/bin/bash\n' >> "$ROOTFS/etc/passwd"

cat > "$ROOTFS/etc/apt/sources.list" <<'EOF'
deb http://deb.debian.org/debian trixie main
deb http://deb.debian.org/debian trixie-updates main
deb http://security.debian.org/debian-security trixie-security main
EOF

cat > "$ROOTFS/etc/sudoers.d/hedgeyos" <<'EOF'
hedgeyos ALL=(ALL) NOPASSWD:ALL
EOF
chmod 0440 "$ROOTFS/etc/sudoers.d/hedgeyos"

rm -rf "$ROOTFS/var/cache/apt/archives"/*.deb \
       "$ROOTFS/var/lib/apt/lists"/* \
       "$ROOTFS/tmp"/* \
       "$ROOTFS/var/tmp"/*
rm -f "$ROOTFS/etc/machine-id" "$ROOTFS/var/lib/dbus/machine-id"

printf 'Configured %s\n' "$ROOTFS"
