#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOTFS="${1:-}"
CUSTOMIZATION_MANIFEST="$SCRIPT_DIR/customizations.tsv"
RUNTIME_ASSETS="$SCRIPT_DIR/runtime-assets"

[ -n "$ROOTFS" ] || {
    echo "usage: rootfs/configure-rootfs.sh <rootfs-dir>" >&2
    exit 1
}

[ -d "$ROOTFS" ] || {
    echo "rootfs directory does not exist: $ROOTFS" >&2
    exit 1
}

[ -r "$CUSTOMIZATION_MANIFEST" ] || {
    echo "customization manifest does not exist: $CUSTOMIZATION_MANIFEST" >&2
    exit 1
}

install -d -o 1000 -g 1000 "$ROOTFS/home/hedgeyos"
install -d "$ROOTFS/etc/sudoers.d" "$ROOTFS/etc/apt/sources.list.d"
install -d -m 1777 "$ROOTFS/dev/shm" "$ROOTFS/run/shm" "$ROOTFS/tmp"

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
chmod 0440 "$ROOTFS/etc/sudoers"
chmod 0644 "$ROOTFS/etc/sudo.conf"
chmod 4755 "$ROOTFS/usr/bin/sudo"

tab=$(printf '\t')
while IFS="$tab" read -r source target owner mode policy; do
    case "$source" in
        ''|'#'*) continue ;;
    esac

    source_path="$RUNTIME_ASSETS/$source"
    target_path="$ROOTFS$target"
    [ -f "$source_path" ] || {
        echo "customization source does not exist: $source_path" >&2
        exit 1
    }

    owner_user=${owner%%:*}
    owner_group=${owner##*:}
    install -D -o "$owner_user" -g "$owner_group" -m "$mode" "$source_path" "$target_path"
done < "$CUSTOMIZATION_MANIFEST"

cp -a "$ROOTFS/etc/skel/." "$ROOTFS/home/hedgeyos/"

rm -rf "$ROOTFS/var/cache/apt/archives"/*.deb \
       "$ROOTFS/var/lib/apt/lists"/* \
       "$ROOTFS/tmp"/* \
       "$ROOTFS/var/tmp"/*
rm -f "$ROOTFS/etc/machine-id" "$ROOTFS/var/lib/dbus/machine-id"

printf 'Configured %s\n' "$ROOTFS"
