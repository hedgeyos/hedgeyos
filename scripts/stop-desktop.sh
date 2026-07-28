#!/usr/bin/env sh
set -eu

HEDGEYOS_FILES_DIR="${HEDGEYOS_FILES_DIR:-/data/data/org.hedgeyos/files}"
HEDGEYOS_LOCK_DIR="${HEDGEYOS_LOCK_DIR:-$HEDGEYOS_FILES_DIR/run}"
LOCK_FILE="$HEDGEYOS_LOCK_DIR/desktop.lock"

[ -f "$LOCK_FILE" ] || exit 0
PID=$(cat "$LOCK_FILE")
kill "$PID" 2>/dev/null || true
rm -f "$LOCK_FILE"
