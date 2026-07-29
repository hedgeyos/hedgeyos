#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH='' cd -- "$SCRIPT_DIR/.." && pwd)
ASSET_DIR="$REPO_ROOT/rootfs/runtime-assets/hedgeyos-linux"
PACKAGE_DIR="$ASSET_DIR/packages"
MANIFEST="$ASSET_DIR/migration-packages.tsv"
ROOTFS_BUILD="$REPO_ROOT/rootfs/build-rootfs.sh"
MANAGER="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosRuntimeManager.java"
GTK_SMOKE="$ASSET_DIR/hedgeyos-gtk-asset-smoke"

sh -n "$REPO_ROOT/rootfs/build-rootfs.sh"
sh -n "$REPO_ROOT/rootfs/configure-rootfs.sh"
sh -n "$REPO_ROOT/rootfs/verify-gtk-svg.sh"
sh -n "$REPO_ROOT/rootfs/verify-migration-packages.sh"

grep -Fq 'librsvg2-common' "$ROOTFS_BUILD"
grep -Fq 'gtk-svg-loader-v1' "$MANIFEST"
grep -Fq 'window-policy-v1' "$MANIFEST"
grep -Fq 'librsvg2-common' "$MANIFEST"
grep -Fq 'librsvg2-2' "$MANIFEST"
grep -Fq 'libdav1d7' "$MANIFEST"
grep -Fq 'load_multiarch_library' "$GTK_SMOKE"
grep -Fq 'libgdk_pixbuf-2.0.so.0' "$GTK_SMOKE"
if grep -Fq 'ctypes.util.find_library' "$GTK_SMOKE"; then
    echo "Linux migration test: GTK smoke helper must not rely on find_library under PRoot" >&2
    exit 1
fi
grep -Fq 'Architecture' "$REPO_ROOT/rootfs/verify-migration-packages.sh"
grep -Fq 'arm64|all' "$REPO_ROOT/rootfs/verify-migration-packages.sh"
grep -Fq 'dpkg-checkbuilddeps' "$REPO_ROOT/rootfs/verify-migration-packages.sh"

if grep -Fq 'ensureLinuxWindowPolicy' "$MANAGER"; then
    exit 1
fi
if grep -Fq 'copiedPackages != 2' "$MANAGER"; then
    exit 1
fi
if grep -Fq 'expected exactly two packages' \
    "$MANAGER" "$REPO_ROOT/rootfs/verify-migration-packages.sh"; then
    exit 1
fi
grep -Fq 'ensureLinuxMigrations' "$MANAGER"
grep -Fq 'HedgeyosMigrationManifest.isGenerationComplete' "$MANAGER"
grep -Fq 'dpkg --configure -a' "$MANAGER"
grep -Fq 'hedgeyos-gtk-asset-smoke' "$MANAGER"

temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT INT TERM
expected="$temporary/expected"
: > "$expected"
tab=$(printf '\t')

while IFS="$tab" read -r package_name required_version architecture _generation checksum filename _reason; do
    case "$package_name" in
        ''|'#'*) continue ;;
    esac
    package_file="$PACKAGE_DIR/$filename"
    test -f "$package_file"
    test "$(dpkg-deb --field "$package_file" Package)" = "$package_name"
    test "$(dpkg-deb --field "$package_file" Version)" = "$required_version"
    test "$(dpkg-deb --field "$package_file" Architecture)" = "$architecture"
    test "$(sha256sum "$package_file" | awk '{ print $1 }')" = "$checksum"
    printf '%s\n' "$filename" >> "$expected"
done < "$MANIFEST"

sort "$expected" -o "$expected"
find "$PACKAGE_DIR" -maxdepth 1 -type f -name '*.deb' -printf '%f\n' |
    sort > "$temporary/actual"
diff -u "$expected" "$temporary/actual"

python3 - "$ASSET_DIR/hedgeyos-gtk-asset-smoke" <<'PY'
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
compile(source, sys.argv[1], "exec")
PY

printf 'Linux migration manifest and package tests passed.\n'
