#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH='' cd -- "$SCRIPT_DIR/.." && pwd)
BUILD_DIR="$REPO_ROOT/build/proot-seqpacket-reproducer"
SOURCE="$SCRIPT_DIR/proot-seqpacket-reproducer.c"
CC=${CC:-cc}

mkdir -p "$BUILD_DIR"
"$CC" -std=c11 -O2 -Wall -Wextra -Werror \
    "$SOURCE" -o "$BUILD_DIR/proot-seqpacket-reproducer-host"

for mode in normal browser-abort browser-kill; do
    result="$BUILD_DIR/host-$mode.txt"
    "$BUILD_DIR/proot-seqpacket-reproducer-host" \
        --mode "$mode" \
        --output "$result"
    grep -Fq "mode=$mode" "$result"
    grep -Fq 'recv_success=1' "$result"
    grep -Fq 'eof=1' "$result"
    grep -Fq 'enosys_count=0' "$result"
    grep -Fq 'other_errno=0' "$result"
done

printf 'Native SOCK_SEQPACKET/recvmsg control tests passed.\n'
