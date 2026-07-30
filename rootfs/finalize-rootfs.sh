#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
ROOTFS_TREE="${1:-}"
ROOTFS_TAR="${2:-}"
ROOTFS_TMP="${3:-}"
GTK_SMOKE_OUTPUT="${4:-}"
MIGRATION_ASSET_DIR="${5:-}"
MIGRATION_MANIFEST="${6:-}"
PUBLISHED_BASELINE_STATUS="${7:-}"

fail() {
    echo "rootfs/finalize-rootfs.sh: $*" >&2
    exit 1
}

[ -d "$ROOTFS_TREE" ] || fail "missing rootfs tree"
[ -n "$ROOTFS_TAR" ] || fail "missing output tar path"
[ -n "$ROOTFS_TMP" ] || fail "missing compressed output path"

"$SCRIPT_DIR/configure-rootfs.sh" "$ROOTFS_TREE"
"$SCRIPT_DIR/verify-gtk-svg.sh" "$ROOTFS_TREE" "$GTK_SMOKE_OUTPUT"
"$SCRIPT_DIR/verify-migration-packages.sh" \
    "$ROOTFS_TREE" \
    "$MIGRATION_ASSET_DIR" \
    "$MIGRATION_MANIFEST" \
    "$PUBLISHED_BASELINE_STATUS"
mkdir -p "$ROOTFS_TREE/dev" "$ROOTFS_TREE/proc" "$ROOTFS_TREE/sys"

chown -R 0:0 "$ROOTFS_TREE"
chown -R 1000:1000 "$ROOTFS_TREE/home/hedgeyos"
chmod 4755 "$ROOTFS_TREE/usr/bin/sudo"
tar --numeric-owner --hard-dereference --exclude='./dev/*' \
    -C "$ROOTFS_TREE" -cf "$ROOTFS_TAR" .

for root_owned_path in ./etc/sudo.conf ./etc/sudoers ./usr/bin/sudo ./var/lib/dpkg/status; do
    archived_owner=$(tar --numeric-owner -tvf "$ROOTFS_TAR" "$root_owned_path" |
        awk 'NR == 1 { print $2 }')
    [ "$archived_owner" = "0/0" ] ||
        fail "$root_owned_path is archived as $archived_owner instead of 0/0"
done

sudo_mode=$(tar --numeric-owner -tvf "$ROOTFS_TAR" ./usr/bin/sudo |
    awk 'NR == 1 { print $1 }')
[ "$sudo_mode" = "-rwsr-xr-x" ] ||
    fail "./usr/bin/sudo is archived with mode $sudo_mode instead of -rwsr-xr-x"

home_owner=$(tar --numeric-owner -tvf "$ROOTFS_TAR" ./home/hedgeyos/ |
    awk 'NR == 1 { print $2 }')
[ "$home_owner" = "1000/1000" ] ||
    fail "./home/hedgeyos is archived as $home_owner instead of 1000/1000"

zstd -19 -T0 -f "$ROOTFS_TAR" -o "$ROOTFS_TMP"

archive_size=$(wc -c < "$ROOTFS_TMP" | tr -d ' ')
[ "$archive_size" -gt 52428800 ] ||
    fail "rootfs archive is unexpectedly small: $archive_size bytes"
printf '%s\n' "$archive_size"
