#!/usr/bin/env sh
set -eu

ROOTFS="${1:-}"
PACKAGE_DIR="${2:-}"
MANIFEST="${3:-}"
BASELINE_STATUS="${4:-}"

fail() {
    echo "rootfs/verify-migration-packages.sh: $*" >&2
    exit 1
}

[ -d "$ROOTFS/var/lib/dpkg" ] ||
    fail "missing rootfs dpkg database: $ROOTFS"
[ -d "$PACKAGE_DIR" ] ||
    fail "missing migration package directory: $PACKAGE_DIR"
[ -r "$MANIFEST" ] ||
    fail "missing migration manifest: $MANIFEST"
[ -r "$BASELINE_STATUS" ] ||
    fail "missing published-baseline dpkg status: $BASELINE_STATUS"

for command_name in dpkg-checkbuilddeps dpkg-deb dpkg-query sha256sum; do
    command -v "$command_name" >/dev/null 2>&1 ||
        fail "$command_name is required"
done

temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT INT TERM
expected_files="$temporary/expected-files"
declared_packages="$temporary/declared-packages"
gtk_packages="$temporary/gtk-packages"
: > "$expected_files"
: > "$declared_packages"
: > "$gtk_packages"

tab=$(printf '\t')
row_count=0
while IFS="$tab" read -r package_name required_version architecture generation checksum filename reason; do
    case "$package_name" in
        ''|'#'*) continue ;;
    esac
    if [ -z "$required_version" ] || [ -z "$architecture" ] ||
        [ -z "$generation" ] || [ -z "$checksum" ] ||
        [ -z "$filename" ] || [ -z "$reason" ]; then
        fail "manifest row for $package_name is incomplete"
    fi
    case "$architecture" in
        arm64|all) ;;
        *) fail "$package_name has unsupported architecture $architecture" ;;
    esac
    case "$checksum" in
        *[!0-9a-f]*|'') fail "$package_name has an invalid SHA-256" ;;
    esac
    [ "${#checksum}" -eq 64 ] ||
        fail "$package_name has an invalid SHA-256 length"

    package_file="$PACKAGE_DIR/$filename"
    [ -f "$package_file" ] ||
        fail "declared package file is missing: $filename"

    bundled_name=$(dpkg-deb --field "$package_file" Package)
    bundled_version=$(dpkg-deb --field "$package_file" Version)
    bundled_architecture=$(dpkg-deb --field "$package_file" Architecture)
    actual_checksum=$(sha256sum "$package_file" | awk '{ print $1 }')
    installed_status=$(dpkg-query \
        --admindir="$ROOTFS/var/lib/dpkg" \
        --showformat='${db:Status-Abbrev}' \
        --show "$package_name" 2>/dev/null || true)
    installed_version=$(dpkg-query \
        --admindir="$ROOTFS/var/lib/dpkg" \
        --showformat='${Version}' \
        --show "$package_name" 2>/dev/null || true)

    [ "$bundled_name" = "$package_name" ] ||
        fail "package identity mismatch for $filename"
    [ "$bundled_version" = "$required_version" ] ||
        fail "$filename version is $bundled_version, expected $required_version"
    [ "$bundled_architecture" = "$architecture" ] ||
        fail "$filename architecture is $bundled_architecture, expected $architecture"
    [ "$actual_checksum" = "$checksum" ] ||
        fail "$filename SHA-256 does not match the manifest"
    [ "$installed_status" = "ii " ] ||
        fail "$package_name is not fully installed in the new rootfs"
    [ "$installed_version" = "$required_version" ] ||
        fail "$package_name bundle is $required_version but the new rootfs has $installed_version"

    printf '%s\n' "$filename" >> "$expected_files"
    printf '%s\n' "$package_name" >> "$declared_packages"
    if [ "$generation" = "gtk-svg-loader-v1" ]; then
        printf '%s\n' "$package_name" >> "$gtk_packages"
    fi
    row_count=$((row_count + 1))
done < "$MANIFEST"

[ "$row_count" -gt 0 ] || fail "migration manifest has no package rows"

sort "$expected_files" -o "$expected_files"
sort "$declared_packages" -o "$declared_packages"
sort "$gtk_packages" -o "$gtk_packages"
[ -z "$(uniq -d "$expected_files")" ] ||
    fail "migration manifest contains duplicate filenames"
[ -z "$(uniq -d "$declared_packages")" ] ||
    fail "migration manifest contains duplicate package names"

find "$PACKAGE_DIR" -maxdepth 1 -type f -name '*.deb' -printf '%f\n' |
    sort > "$temporary/actual-files"
diff -u "$expected_files" "$temporary/actual-files" >/dev/null ||
    fail "migration manifest and .deb directory do not match exactly"

mkdir -p "$temporary/admindir" "$temporary/baseline-admindir"
cp "$BASELINE_STATUS" "$temporary/baseline-admindir/status"
awk -v package_file="$declared_packages" '
    BEGIN {
        RS = ""
        while ((getline package < package_file) > 0) {
            declared[package] = 1
        }
        close(package_file)
    }
    {
        package = ""
        count = split($0, lines, "\n")
        for (line_index = 1; line_index <= count; line_index++) {
            if (lines[line_index] ~ /^Package: /) {
                package = substr(lines[line_index], 10)
                break
            }
        }
        if (!declared[package]) {
            print $0 "\n"
        }
    }
' "$BASELINE_STATUS" > "$temporary/admindir/status"

while IFS="$tab" read -r package_name required_version architecture generation checksum filename reason; do
    case "$package_name" in
        ''|'#'*) continue ;;
    esac
    package_file="$PACKAGE_DIR/$filename"
    {
        dpkg-deb --field "$package_file"
        printf 'Status: install ok installed\n\n'
    } >> "$temporary/admindir/status"
done < "$MANIFEST"

while IFS="$tab" read -r package_name required_version architecture generation checksum filename reason; do
    case "$package_name" in
        ''|'#'*) continue ;;
    esac
    package_file="$PACKAGE_DIR/$filename"
    dependencies=$(dpkg-deb --field "$package_file" Depends 2>/dev/null || true)
    pre_dependencies=$(dpkg-deb --field "$package_file" Pre-Depends 2>/dev/null || true)
    if [ -n "$dependencies" ] && [ -n "$pre_dependencies" ]; then
        dependencies="$pre_dependencies, $dependencies"
    elif [ -n "$pre_dependencies" ]; then
        dependencies="$pre_dependencies"
    fi
    if [ -n "$dependencies" ]; then
        dpkg-checkbuilddeps \
            --admindir="$temporary/admindir" \
            -a arm64 \
            -d "$dependencies" \
            /dev/null >/dev/null ||
            fail "offline dependency closure is incomplete for $package_name"
    fi
done < "$MANIFEST"

awk '/^Package: / { print $2 }' "$BASELINE_STATUS" |
    sort -u > "$temporary/baseline-packages"
awk '/^Package: / { print $2 }' "$ROOTFS/var/lib/dpkg/status" |
    sort -u > "$temporary/new-packages"
comm -13 "$temporary/baseline-packages" "$temporary/new-packages" \
    > "$temporary/new-package-names"

while IFS= read -r package_name; do
    [ -z "$package_name" ] && continue
    grep -Fxq "$package_name" "$gtk_packages" ||
        fail "new package $package_name is absent from the published baseline but not declared in gtk-svg-loader-v1"
done < "$temporary/new-package-names"

while IFS= read -r package_name; do
    [ -z "$package_name" ] && continue
    if grep -Fxq "$package_name" "$temporary/baseline-packages"; then
        baseline_version=$(dpkg-query \
            --admindir="$temporary/baseline-admindir" \
            --showformat='${Version}' \
            --show "$package_name" 2>/dev/null || true)
        [ "$baseline_version" != "$(dpkg-query \
            --admindir="$ROOTFS/var/lib/dpkg" \
            --showformat='${Version}' \
            --show "$package_name")" ] ||
            fail "$package_name is already compatible in the published baseline and should not be bundled in gtk-svg-loader-v1"
    fi
done < "$gtk_packages"

printf 'Manifest-driven offline migration packages and dependency closure verified.\n'
