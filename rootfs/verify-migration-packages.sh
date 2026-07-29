#!/usr/bin/env sh
set -eu

ROOTFS="${1:-}"
PACKAGE_DIR="${2:-}"

[ -d "$ROOTFS/var/lib/dpkg" ] || {
    echo "rootfs/verify-migration-packages.sh: missing rootfs dpkg database: $ROOTFS" >&2
    exit 1
}
[ -d "$PACKAGE_DIR" ] || {
    echo "rootfs/verify-migration-packages.sh: missing migration package directory: $PACKAGE_DIR" >&2
    exit 1
}
command -v dpkg-deb >/dev/null 2>&1 || {
    echo "rootfs/verify-migration-packages.sh: dpkg-deb is required" >&2
    exit 1
}
command -v dpkg-query >/dev/null 2>&1 || {
    echo "rootfs/verify-migration-packages.sh: dpkg-query is required" >&2
    exit 1
}

package_count=$(find "$PACKAGE_DIR" -maxdepth 1 -type f -name '*.deb' | wc -l)
[ "$package_count" -eq 2 ] || {
    echo "rootfs/verify-migration-packages.sh: expected exactly two packages, found $package_count" >&2
    exit 1
}

for package_name in devilspie2 liblua5.1-0; do
    package_file=$(find "$PACKAGE_DIR" -maxdepth 1 -type f -name "${package_name}_*.deb" -print)
    [ -n "$package_file" ] && [ "$(printf '%s\n' "$package_file" | wc -l)" -eq 1 ] || {
        echo "rootfs/verify-migration-packages.sh: expected one $package_name package" >&2
        exit 1
    }

    bundled_name=$(dpkg-deb --field "$package_file" Package)
    bundled_version=$(dpkg-deb --field "$package_file" Version)
    bundled_architecture=$(dpkg-deb --field "$package_file" Architecture)
    installed_version=$(dpkg-query \
        --admindir="$ROOTFS/var/lib/dpkg" \
        --showformat='${Version}' \
        --show "$package_name")

    [ "$bundled_name" = "$package_name" ] ||
        { echo "rootfs/verify-migration-packages.sh: package identity mismatch for $package_file" >&2; exit 1; }
    [ "$bundled_architecture" = "arm64" ] ||
        { echo "rootfs/verify-migration-packages.sh: $package_file is not ARM64" >&2; exit 1; }
    [ "$bundled_version" = "$installed_version" ] ||
        { echo "rootfs/verify-migration-packages.sh: $package_name bundle is $bundled_version but rootfs has $installed_version" >&2; exit 1; }
done

printf 'Offline rootfs migration packages match the built rootfs.\n'
