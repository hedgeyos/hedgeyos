#!/usr/bin/env sh
set -eu

ROOTFS="${1:-}"
OUTPUT="${2:-}"

fail() {
    echo "rootfs/verify-gtk-svg.sh: $*" >&2
    exit 1
}

[ -d "$ROOTFS/var/lib/dpkg" ] || fail "missing rootfs dpkg database: $ROOTFS"
[ -n "$OUTPUT" ] || fail "usage: rootfs/verify-gtk-svg.sh <rootfs-dir> <output-file>"
command -v dpkg-query >/dev/null 2>&1 || fail "dpkg-query is required"

for package_name in librsvg2-common librsvg2-2; do
    status=$(dpkg-query \
        --admindir="$ROOTFS/var/lib/dpkg" \
        --showformat='${db:Status-Abbrev}' \
        --show "$package_name" 2>/dev/null || true)
    [ "$status" = "ii " ] || fail "$package_name is not fully installed"
done

loader=$(find "$ROOTFS/usr/lib" -type f \
    -path '*/gdk-pixbuf-2.0/2.10.0/loaders/libpixbufloader_svg.so' \
    -print -quit)
[ -n "$loader" ] || fail "libpixbufloader_svg.so is missing"

cache=$(find "$ROOTFS/usr/lib" -type f \
    -path '*/gdk-pixbuf-2.0/2.10.0/loaders.cache' \
    -print -quit)
[ -n "$cache" ] || fail "the active GDK-Pixbuf loader cache is missing"
grep -Fq 'libpixbufloader_svg.so' "$cache" ||
    fail "the loader cache does not reference libpixbufloader_svg.so"
grep -Fq '"svg"' "$cache" ||
    fail "the loader cache has no SVG format entry"

query_loader=$(find "$ROOTFS/usr/lib" -type f \
    -path '*/gdk-pixbuf-2.0/gdk-pixbuf-query-loaders' \
    -print -quit)
[ -n "$query_loader" ] ||
    fail "the architecture-specific gdk-pixbuf-query-loaders executable is missing"

mkdir -p "$(dirname "$OUTPUT")"
if chroot "$ROOTFS" \
    /usr/local/libexec/hedgeyos-gtk-asset-smoke > "$OUTPUT" 2>&1; then
    smoke_status=0
else
    smoke_status=$?
fi

if [ "$smoke_status" -ne 0 ]; then
    cat "$OUTPUT" >&2
    fail "GDK-Pixbuf could not decode the deterministic GTK asset smoke set"
fi

grep -Fq 'gtk_svg_loader=PASS' "$OUTPUT" ||
    fail "SVG loader smoke result is missing"
grep -Fq 'gtk_svg_cache=PASS' "$OUTPUT" ||
    fail "SVG cache smoke result is missing"
grep -Fq 'gtk_svg_decode=PASS' "$OUTPUT" ||
    fail "deterministic SVG decode did not pass"
grep -Fq 'gtk_symbolic_icon=PASS' "$OUTPUT" ||
    fail "Adwaita symbolic SVG decode did not pass"
grep -Fq 'gtk_check_indicator=PASS' "$OUTPUT" ||
    fail "Adwaita check indicator decode did not pass"
grep -Fq 'gtk_png_decode=PASS' "$OUTPUT" ||
    fail "ordinary PNG decode did not pass"

printf 'GTK SVG loader and asset decoding verified.\n'
