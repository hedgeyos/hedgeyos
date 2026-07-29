#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH='' cd -- "$SCRIPT_DIR/.." && pwd)
OUT_DIR="${OUT_DIR:-$REPO_ROOT/build/rootfs}"
MANIFEST_DIR="$REPO_ROOT/rootfs/manifests"
ROOTFS_NAME="debian-trixie-arm64-rootfs.tar.zst"
ROOTFS_OUT="$OUT_DIR/$ROOTFS_NAME"
ROOTFS_TREE="$OUT_DIR/debian-trixie-arm64-rootfs"
ROOTFS_TAR="$OUT_DIR/debian-trixie-arm64-rootfs.tar"
ROOTFS_TMP="$ROOTFS_OUT.tmp"
MIGRATION_ASSET_DIR="$REPO_ROOT/rootfs/runtime-assets/hedgeyos-linux/packages"
MIGRATION_MANIFEST="$REPO_ROOT/rootfs/runtime-assets/hedgeyos-linux/migration-packages.tsv"
PUBLISHED_BASELINE_STATUS="$REPO_ROOT/rootfs/baselines/v0.1.0-alpha.4-dpkg-status"
APT_SNAPSHOT="${APT_SNAPSHOT:-}"
DEBIAN_KEYRING="${DEBIAN_KEYRING:-/usr/share/keyrings/debian-archive-keyring.gpg}"

PACKAGES="bash,coreutils,apt,ca-certificates,sudo,curl,wget,git,nano,less,procps,psmisc,iproute2,python3,build-essential,dbus,dbus-x11,xfce4,xfce4-terminal,thunar,xterm,devilspie2,fonts-dejavu,adwaita-icon-theme,librsvg2-common"

command -v mmdebstrap >/dev/null 2>&1 || {
    echo "mmdebstrap is required to build the Debian rootfs." >&2
    echo "Install it in a Debian build environment, then rerun rootfs/build-rootfs.sh." >&2
    exit 1
}

command -v zstd >/dev/null 2>&1 || {
    echo "zstd is required to compress the Debian rootfs." >&2
    exit 1
}

fail() {
    echo "rootfs/build-rootfs.sh: $*" >&2
    exit 1
}

[ "$(id -u)" -eq 0 ] || fail "run as root so Debian ownership metadata can be preserved"

mkdir -p "$OUT_DIR" "$MANIFEST_DIR"

PRIMARY_SOURCE="deb http://deb.debian.org/debian trixie main"
UPDATES_SOURCE="deb http://deb.debian.org/debian trixie-updates main"
SECURITY_SOURCE="deb http://security.debian.org/debian-security trixie-security main"

if [ -n "$APT_SNAPSHOT" ]; then
    PRIMARY_SOURCE="deb $APT_SNAPSHOT trixie main"
    UPDATES_SOURCE=
    SECURITY_SOURCE=
fi

[ -r "$DEBIAN_KEYRING" ] || fail "Debian archive keyring is not readable: $DEBIAN_KEYRING"
KEYRING_ARG="--keyring=$DEBIAN_KEYRING"
DEBIAN_KEYRING_SHA256=$(sha256sum "$DEBIAN_KEYRING" | awk '{ print $1 }')

cleanup() {
    rm -rf "$ROOTFS_TREE" "$ROOTFS_TAR" "$ROOTFS_TMP"
}
trap cleanup EXIT INT TERM
cleanup

# shellcheck disable=SC2086
if [ -n "$UPDATES_SOURCE" ]; then
    mmdebstrap \
        $KEYRING_ARG \
        --architectures=arm64 \
        --variant=important \
        --include="$PACKAGES" \
        --components=main \
        --aptopt='Acquire::Languages "none"' \
        trixie \
        "$ROOTFS_TREE" \
        "$PRIMARY_SOURCE" \
        "$UPDATES_SOURCE" \
        "$SECURITY_SOURCE"
else
    mmdebstrap \
        $KEYRING_ARG \
        --architectures=arm64 \
        --variant=important \
        --include="$PACKAGES" \
        --components=main \
        --aptopt='Acquire::Languages "none"' \
        trixie \
        "$ROOTFS_TREE" \
        "$PRIMARY_SOURCE"
fi

"$SCRIPT_DIR/configure-rootfs.sh" "$ROOTFS_TREE"
"$SCRIPT_DIR/verify-gtk-svg.sh" "$ROOTFS_TREE" "$OUT_DIR/gtk-asset-smoke.txt"
"$SCRIPT_DIR/verify-migration-packages.sh" \
    "$ROOTFS_TREE" \
    "$MIGRATION_ASSET_DIR" \
    "$MIGRATION_MANIFEST" \
    "$PUBLISHED_BASELINE_STATUS"
mkdir -p "$ROOTFS_TREE/dev" "$ROOTFS_TREE/proc" "$ROOTFS_TREE/sys"

chown -R 0:0 "$ROOTFS_TREE"
chown -R 1000:1000 "$ROOTFS_TREE/home/hedgeyos"
chmod 4755 "$ROOTFS_TREE/usr/bin/sudo"
tar --numeric-owner --hard-dereference --exclude='./dev/*' -C "$ROOTFS_TREE" -cf "$ROOTFS_TAR" .

for root_owned_path in ./etc/sudo.conf ./etc/sudoers ./usr/bin/sudo ./var/lib/dpkg/status; do
    archived_owner=$(tar --numeric-owner -tvf "$ROOTFS_TAR" "$root_owned_path" | awk 'NR == 1 { print $2 }')
    [ "$archived_owner" = "0/0" ] ||
        fail "$root_owned_path is archived as $archived_owner instead of 0/0"
done

sudo_mode=$(tar --numeric-owner -tvf "$ROOTFS_TAR" ./usr/bin/sudo | awk 'NR == 1 { print $1 }')
[ "$sudo_mode" = "-rwsr-xr-x" ] ||
    fail "./usr/bin/sudo is archived with mode $sudo_mode instead of -rwsr-xr-x"

home_owner=$(tar --numeric-owner -tvf "$ROOTFS_TAR" ./home/hedgeyos/ | awk 'NR == 1 { print $2 }')
[ "$home_owner" = "1000/1000" ] ||
    fail "./home/hedgeyos is archived as $home_owner instead of 1000/1000"

zstd -19 -T0 -f "$ROOTFS_TAR" -o "$ROOTFS_TMP"

archive_size=$(wc -c < "$ROOTFS_TMP" | tr -d ' ')
[ "$archive_size" -gt 52428800 ] || fail "rootfs archive is unexpectedly small: $archive_size bytes"

mv "$ROOTFS_TMP" "$ROOTFS_OUT"
rm -rf "$ROOTFS_TREE" "$ROOTFS_TAR"
trap - EXIT INT TERM

(cd "$OUT_DIR" && sha256sum "$ROOTFS_NAME") > "$MANIFEST_DIR/$ROOTFS_NAME.sha256"
cat > "$MANIFEST_DIR/debian-trixie-arm64-rootfs.provenance" <<EOF
name=$ROOTFS_NAME
debian_release=13
debian_codename=trixie
architecture=arm64
build_date=$(date -u +%Y-%m-%dT%H:%M:%SZ)
primary_source=$PRIMARY_SOURCE
updates_source=$UPDATES_SOURCE
security_source=$SECURITY_SOURCE
debian_keyring_sha256=$DEBIAN_KEYRING_SHA256
packages=$PACKAGES
gtk_svg_loader_package=librsvg2-common
gtk_svg_runtime_package=librsvg2-2
gtk_svg_smoke=PASS
migration_packages=$(find "$MIGRATION_ASSET_DIR" -maxdepth 1 -type f -name '*.deb' -printf '%f ' | sort)
migration_manifest_sha256=$(sha256sum "$MIGRATION_MANIFEST" | awk '{ print $1 }')
migration_baseline=v0.1.0-alpha.4
android_extractable=true
archive_excludes=./dev/*
archive_hardlinks=dereferenced
sha256=$(cut -d ' ' -f 1 "$MANIFEST_DIR/$ROOTFS_NAME.sha256")
size_bytes=$archive_size
EOF

printf 'Built %s\n' "$ROOTFS_OUT"
