#!/usr/bin/env sh
set -eu

HEDGEYOS_FILES_DIR="${HEDGEYOS_FILES_DIR:-/data/data/org.hedgeyos/files}"
OUT="${1:-$PWD/hedgeyos-logs-$(date +%Y%m%d-%H%M%S).tar.gz}"

tar -czf "$OUT" \
    -C "$HEDGEYOS_FILES_DIR" \
    logs hedgeyos-state run 2>/dev/null || {
        echo "No complete hedgeyos log set found under $HEDGEYOS_FILES_DIR" >&2
        exit 1
    }

printf '%s\n' "$OUT"
