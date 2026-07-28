#!/usr/bin/env sh
set -eu

HEDGEYOS_FILES_DIR="${HEDGEYOS_FILES_DIR:-/data/data/org.hedgeyos/files}"
HEDGEYOS_ROOTFS_DIR="${HEDGEYOS_ROOTFS_DIR:-$HEDGEYOS_FILES_DIR/debian}"
HEDGEYOS_STAGING_DIR="${HEDGEYOS_STAGING_DIR:-$HEDGEYOS_FILES_DIR/debian.staging}"
HEDGEYOS_EXPORT_DIR="${HEDGEYOS_EXPORT_DIR:-$HEDGEYOS_FILES_DIR/export}"

"$(dirname "$0")/stop-desktop.sh"
mkdir -p "$HEDGEYOS_EXPORT_DIR"
rm -rf "$HEDGEYOS_STAGING_DIR" "$HEDGEYOS_ROOTFS_DIR"
rm -f "$HEDGEYOS_FILES_DIR/hedgeyos-state/firstboot.state"
printf 'hedgeyos Debian environment reset; export directory preserved at %s\n' "$HEDGEYOS_EXPORT_DIR"
