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

[ "$(id -u)" -eq 0 ] ||
    fail "run as root so Debian ownership metadata can be preserved"

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

"$SCRIPT_DIR/finalize-rootfs.sh" \
    "$ROOTFS_TREE" \
    "$ROOTFS_TAR" \
    "$ROOTFS_TMP" \
    "$OUT_DIR/gtk-asset-smoke.txt" \
    "$MIGRATION_ASSET_DIR" \
    "$MIGRATION_MANIFEST" \
    "$PUBLISHED_BASELINE_STATUS" \
    > "$OUT_DIR/finalize.log"
cat "$OUT_DIR/finalize.log"
archive_size=$(tail -n 1 "$OUT_DIR/finalize.log")

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
