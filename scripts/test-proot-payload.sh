#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH='' cd -- "$SCRIPT_DIR/.." && pwd)
ASSET="$REPO_ROOT/app/src/main/assets/termux-proot-aarch64.tar.zst"
CHECKSUM="$ASSET.sha256"
BUILD_SCRIPT="$SCRIPT_DIR/build-proot-payload.sh"
TMP_DIR=$(mktemp -d)

cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT INT TERM

expected=$(awk 'NR == 1 {print $1}' "$CHECKSUM")
actual=$(sha256sum "$ASSET" | awk '{print $1}')
test "$expected" = "$actual"

zstd -dc "$ASSET" | tar -xOf - usr/bin/proot > "$TMP_DIR/proot"
chmod 0700 "$TMP_DIR/proot"
strings "$TMP_DIR/proot" | grep -Fxq '5.1.107.89'

zstd -dc "$ASSET" | tar -tf - > "$TMP_DIR/listing"
grep -Fxq 'usr/bin/proot' "$TMP_DIR/listing"
grep -Fxq 'usr/libexec/proot/loader' "$TMP_DIR/listing"
grep -Fxq 'usr/lib/libandroid-shmem.so' "$TMP_DIR/listing"
grep -Fxq 'usr/lib/libtalloc.so.2.4.3' "$TMP_DIR/listing"

grep -Fq \
    'proot 5.1.107.89 pool/main/p/proot/proot_5.1.107.89_aarch64.deb ec9fe38c50cfd49dd31fe360ffbcc3124a945dc1ea16293a8a769303dd724f46' \
    "$BUILD_SCRIPT"
grep -Fq 'version=5.1.107.89' \
    "$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosRuntimeManager.java"

printf 'Pinned PRoot payload tests passed (%s).\n' "$actual"
