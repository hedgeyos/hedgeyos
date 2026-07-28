#!/usr/bin/env sh
set -eu

HEDGEYOS_FILES_DIR="${HEDGEYOS_FILES_DIR:-/data/data/org.hedgeyos/files}"
HEDGEYOS_ROOTFS_ASSET="${HEDGEYOS_ROOTFS_ASSET:-$HEDGEYOS_FILES_DIR/assets/debian-trixie-arm64-rootfs.tar.zst}"
HEDGEYOS_ROOTFS_SHA256="${HEDGEYOS_ROOTFS_SHA256:-$HEDGEYOS_FILES_DIR/assets/debian-trixie-arm64-rootfs.tar.zst.sha256}"
HEDGEYOS_ROOTFS_DIR="${HEDGEYOS_ROOTFS_DIR:-$HEDGEYOS_FILES_DIR/debian}"
HEDGEYOS_STAGING_DIR="${HEDGEYOS_STAGING_DIR:-$HEDGEYOS_FILES_DIR/debian.staging}"
HEDGEYOS_STATE_DIR="${HEDGEYOS_STATE_DIR:-$HEDGEYOS_FILES_DIR/hedgeyos-state}"
HEDGEYOS_STATE_FILE="$HEDGEYOS_STATE_DIR/firstboot.state"

state() {
    mkdir -p "$HEDGEYOS_STATE_DIR"
    printf '%s\n' "$1" > "$HEDGEYOS_STATE_FILE"
    printf '%s\n' "$1"
}

[ -d "$HEDGEYOS_ROOTFS_DIR" ] && {
    state READY
    exit 0
}

state VERIFYING_ASSET
(cd "$(dirname "$HEDGEYOS_ROOTFS_ASSET")" && sha256sum -c "$HEDGEYOS_ROOTFS_SHA256")

state EXTRACTING
rm -rf "$HEDGEYOS_STAGING_DIR"
mkdir -p "$HEDGEYOS_STAGING_DIR"
zstd -dc "$HEDGEYOS_ROOTFS_ASSET" | tar --no-same-owner --no-same-permissions --delay-directory-restore -C "$HEDGEYOS_STAGING_DIR" -xf -

state CONFIGURING
mkdir -p "$HEDGEYOS_STAGING_DIR/tmp" "$HEDGEYOS_STAGING_DIR/home/hedgeyos"
chmod 1777 "$HEDGEYOS_STAGING_DIR/tmp"

mv "$HEDGEYOS_STAGING_DIR" "$HEDGEYOS_ROOTFS_DIR"
state READY
