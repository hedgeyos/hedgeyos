#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
OUT_DIR="${OUT_DIR:-$REPO_ROOT/build/proot}"
MANIFEST_DIR="$REPO_ROOT/rootfs/manifests"
PAYLOAD_NAME="termux-proot-aarch64.tar.zst"
PAYLOAD_OUT="$OUT_DIR/$PAYLOAD_NAME"
PAYLOAD_TMP="$PAYLOAD_OUT.tmp"
TERMUX_REPO_URL="${TERMUX_REPO_URL:-https://packages.termux.dev/apt/termux-main}"

PACKAGES="
proot 5.1.107.88 pool/main/p/proot/proot_5.1.107.88_aarch64.deb f83bbf9029540f3f7c4ff1fa5624621f7d0a477fc910f7c8cca7daffb84f17ef
libandroid-shmem 0.7 pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb 0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6
libtalloc 2.4.3 pool/main/libt/libtalloc/libtalloc_2.4.3_aarch64.deb ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da
"

fail() {
    echo "build-proot-payload: $*" >&2
    exit 1
}

require_exec() {
    command -v "$1" >/dev/null 2>&1 || fail "missing executable: $1"
}

download_package() {
    name="$1"
    version="$2"
    filename="$3"
    expected_sha="$4"
    deb="$DEB_DIR/$(basename "$filename")"
    url="$TERMUX_REPO_URL/$filename"

    if [ ! -e "$deb" ]; then
        curl -fL "$url" -o "$deb"
    fi

    actual_sha=$(sha256sum "$deb" | awk '{print $1}')
    [ "$actual_sha" = "$expected_sha" ] || fail "$name $version checksum mismatch: expected $expected_sha, actual $actual_sha"

    pkg_extract_dir="$EXTRACT_DIR/$name"
    rm -rf "$pkg_extract_dir"
    mkdir -p "$pkg_extract_dir"
    dpkg-deb -x "$deb" "$pkg_extract_dir"

    src_prefix="$pkg_extract_dir/data/data/com.termux/files/usr"
    [ -d "$src_prefix" ] || fail "$name did not contain the expected Termux prefix"
    mkdir -p "$PAYLOAD_ROOT/usr"
    cp -Rp "$src_prefix/." "$PAYLOAD_ROOT/usr/"
}

require_exec curl
require_exec dpkg-deb
require_exec sha256sum
require_exec zstd
require_exec tar

rm -rf "$OUT_DIR/work" "$PAYLOAD_TMP"
mkdir -p "$OUT_DIR/work" "$MANIFEST_DIR"
DEB_DIR="$OUT_DIR/work/debs"
EXTRACT_DIR="$OUT_DIR/work/extract"
PAYLOAD_ROOT="$OUT_DIR/work/payload"
mkdir -p "$DEB_DIR" "$EXTRACT_DIR" "$PAYLOAD_ROOT"

echo "$PACKAGES" | while read -r name version filename sha256; do
    [ -n "${name:-}" ] || continue
    download_package "$name" "$version" "$filename" "$sha256"
done

rm -rf \
    "$PAYLOAD_ROOT/usr/include" \
    "$PAYLOAD_ROOT/usr/lib/pkgconfig" \
    "$PAYLOAD_ROOT/usr/share/man"

[ -x "$PAYLOAD_ROOT/usr/bin/proot" ] || fail "payload is missing usr/bin/proot"
[ -x "$PAYLOAD_ROOT/usr/libexec/proot/loader" ] || fail "payload is missing usr/libexec/proot/loader"
[ -e "$PAYLOAD_ROOT/usr/lib/libtalloc.so.2" ] || fail "payload is missing libtalloc"
[ -e "$PAYLOAD_ROOT/usr/lib/libandroid-shmem.so" ] || fail "payload is missing libandroid-shmem"

tar \
    --sort=name \
    --mtime='@0' \
    --owner=0 \
    --group=0 \
    --numeric-owner \
    -C "$PAYLOAD_ROOT" \
    -cf - usr | zstd -19 -T0 -f -o "$PAYLOAD_TMP"
mv "$PAYLOAD_TMP" "$PAYLOAD_OUT"

(cd "$OUT_DIR" && sha256sum "$PAYLOAD_NAME") > "$MANIFEST_DIR/$PAYLOAD_NAME.sha256"
cat > "$MANIFEST_DIR/termux-proot-aarch64.provenance" <<EOF
name=$PAYLOAD_NAME
architecture=aarch64
termux_repo_url=$TERMUX_REPO_URL
packages=$(echo "$PACKAGES" | awk 'NF { printf "%s=%s ", $1, $2 }')
package_files=$(echo "$PACKAGES" | awk 'NF { printf "%s ", $3 }')
package_sha256=$(echo "$PACKAGES" | awk 'NF { printf "%s=%s ", $1, $4 }')
build_date=$(date -u +%Y-%m-%dT%H:%M:%SZ)
sha256=$(cut -d ' ' -f 1 "$MANIFEST_DIR/$PAYLOAD_NAME.sha256")
size_bytes=$(wc -c < "$PAYLOAD_OUT" | tr -d ' ')
EOF

rm -rf "$OUT_DIR/work"
printf 'Built %s\n' "$PAYLOAD_OUT"
