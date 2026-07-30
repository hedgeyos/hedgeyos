#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH='' cd -- "$SCRIPT_DIR/.." && pwd)
SOURCE="$SCRIPT_DIR/proot-seqpacket-reproducer.c"
OUTPUT="$REPO_ROOT/rootfs/runtime-assets/hedgeyos-linux/hedgeyos-proot-seqpacket-reproducer"
CROSS_CC=${CROSS_CC:-aarch64-linux-gnu-gcc}

command -v "$CROSS_CC" >/dev/null 2>&1 || {
    printf 'missing ARM64 cross compiler: %s\n' "$CROSS_CC" >&2
    exit 1
}

"$CROSS_CC" -std=c11 -O2 -Wall -Wextra -Werror "$SOURCE" -o "$OUTPUT"
chmod 0755 "$OUTPUT"
file "$OUTPUT" | grep -Fq 'ARM aarch64'
sha256sum "$OUTPUT"
