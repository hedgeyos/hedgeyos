#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/data/data/com.termux/files/home/android-tooling/android-sdk}"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
JAVA_HOME="${JAVA_HOME:-/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk}"
GRADLE_BIN="${GRADLE_BIN:-$REPO_ROOT/gradlew}"
AAPT2_OVERRIDE="${AAPT2_OVERRIDE:-}"
if [ -z "$AAPT2_OVERRIDE" ] && [ -x /data/data/com.termux/files/usr/bin/aapt2 ]; then
    AAPT2_OVERRIDE=/data/data/com.termux/files/usr/bin/aapt2
fi
ZIPALIGN="${ZIPALIGN:-/data/data/com.termux/files/usr/bin/zipalign}"
APKSIGNER="${APKSIGNER:-/data/data/com.termux/files/usr/bin/apksigner}"
HEDGEYOS_KEYSTORE_PROPERTIES="${HEDGEYOS_KEYSTORE_PROPERTIES:-/data/data/com.termux/files/home/.signing/hedgeyos-release.properties}"
HEDGEYOS_SIGN_RELEASE="${HEDGEYOS_SIGN_RELEASE:-1}"
HEDGEYOS_USE_EXTERNAL_NATIVE_BUILD="${HEDGEYOS_USE_EXTERNAL_NATIVE_BUILD:-0}"
HEDGEYOS_INCLUDE_X11_MODULE="${HEDGEYOS_INCLUDE_X11_MODULE:-0}"
BUILD_LOG_DIR="$REPO_ROOT/build/hedgeyos-logs"
ROOTFS_NAME="debian-trixie-arm64-rootfs.tar.zst"
ROOTFS_ASSET="$REPO_ROOT/app/src/main/assets/debian-trixie-arm64-rootfs.tar.zst"
ROOTFS_ASSET_SHA="$ROOTFS_ASSET.sha256"
ROOTFS_SHA_FILE="$REPO_ROOT/rootfs/manifests/debian-trixie-arm64-rootfs.tar.zst.sha256"
PROOT_NAME="termux-proot-aarch64.tar.zst"
PROOT_ASSET="$REPO_ROOT/app/src/main/assets/$PROOT_NAME"
PROOT_ASSET_SHA="$PROOT_ASSET.sha256"
PROOT_BUILD_SHA_FILE="$REPO_ROOT/rootfs/manifests/$PROOT_NAME.sha256"
X11_CPP_DIR="$REPO_ROOT/third_party/termux-x11/lorie/src/main/cpp"

mkdir -p "$BUILD_LOG_DIR"

fail() {
    printf 'build-hedgeyos: %s\n' "$*" >&2
    exit 1
}

require_file() {
    [ -e "$1" ] || fail "missing $2: $1"
}

require_exec() {
    [ -x "$1" ] || fail "missing executable $2: $1"
}

refresh_proot_asset() {
    "$SCRIPT_DIR/build-proot-payload.sh"
    mkdir -p "$(dirname "$PROOT_ASSET")"
    cp "$REPO_ROOT/build/proot/$PROOT_NAME" "$PROOT_ASSET"
    cp "$PROOT_BUILD_SHA_FILE" "$PROOT_ASSET_SHA"
}

require_exec "$GRADLE_BIN" "Gradle"
require_exec "$JAVA_HOME/bin/java" "Java"
require_file "$ANDROID_SDK_ROOT/platforms/android-36/android.jar" "Android SDK platform android-36"
if [ -n "$AAPT2_OVERRIDE" ]; then
    require_exec "$AAPT2_OVERRIDE" "aapt2 override"
fi
if [ "$HEDGEYOS_SIGN_RELEASE" = 1 ]; then
    require_exec "$ZIPALIGN" "zipalign"
    require_exec "$APKSIGNER" "apksigner"
    require_file "$HEDGEYOS_KEYSTORE_PROPERTIES" "hedgeyos signing properties"
fi
if [ "$HEDGEYOS_INCLUDE_X11_MODULE" = 1 ]; then
    require_file "$X11_CPP_DIR/xorgproto/include/X11/Xpoll.h.in" "Termux:X11 xorgproto submodule; run git submodule update --init --recursive"
    require_file "$X11_CPP_DIR/xserver/dix/main.c" "Termux:X11 xserver submodule; run git submodule update --init --recursive"
    require_file "$X11_CPP_DIR/libx11/src/OpenDis.c" "Termux:X11 libx11 submodule; run git submodule update --init --recursive"
    require_file "$X11_CPP_DIR/pixman/pixman/pixman.c" "Termux:X11 pixman submodule; run git submodule update --init --recursive"
fi

if [ ! -e "$ROOTFS_ASSET" ] && [ -e "$REPO_ROOT/build/rootfs/$ROOTFS_NAME" ]; then
    mkdir -p "$(dirname "$ROOTFS_ASSET")"
    cp "$REPO_ROOT/build/rootfs/$ROOTFS_NAME" "$ROOTFS_ASSET"
fi

if [ "${HEDGEYOS_REBUILD_PROOT_PAYLOAD:-0}" = 1 ]; then
    refresh_proot_asset
fi

require_file "$PROOT_ASSET" "bundled PRoot payload asset"
require_file "$PROOT_ASSET_SHA" "bundled PRoot payload checksum asset"

require_file "$ROOTFS_ASSET" "bundled Debian rootfs asset"
require_file "$ROOTFS_SHA_FILE" "bundled Debian rootfs checksum"
cp "$ROOTFS_SHA_FILE" "$ROOTFS_ASSET_SHA"
require_file "$ROOTFS_ASSET_SHA" "bundled Debian rootfs checksum asset"

if [ "$HEDGEYOS_USE_EXTERNAL_NATIVE_BUILD" != 1 ]; then
    "$SCRIPT_DIR/build-bootstrap-lib.sh"
    "$SCRIPT_DIR/build-terminal-emulator-lib.sh"
    "$SCRIPT_DIR/build-shared-lib.sh"
fi

expected_rootfs_sha=$(cut -d ' ' -f 1 "$ROOTFS_SHA_FILE")
actual_rootfs_sha=$(sha256sum "$ROOTFS_ASSET" | cut -d ' ' -f 1)
if [ "$expected_rootfs_sha" != "$actual_rootfs_sha" ]; then
    fail "rootfs checksum verification failed"
fi

expected_proot_sha=$(cut -d ' ' -f 1 "$PROOT_ASSET_SHA")
actual_proot_sha=$(sha256sum "$PROOT_ASSET" | cut -d ' ' -f 1)
if [ "$expected_proot_sha" != "$actual_proot_sha" ]; then
    fail "PRoot payload checksum verification failed"
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT
export JAVA_HOME
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

cd "$REPO_ROOT"

GRADLE_ARGS="--no-daemon clean :app:downloadBootstraps :app:assembleRelease"
if [ -n "$AAPT2_OVERRIDE" ]; then
    GRADLE_ARGS="$GRADLE_ARGS -Pandroid.aapt2FromMavenOverride=$AAPT2_OVERRIDE"
fi

# shellcheck disable=SC2086
if ! "$GRADLE_BIN" $GRADLE_ARGS > "$BUILD_LOG_DIR/assembleRelease.log" 2>&1; then
    cat "$BUILD_LOG_DIR/assembleRelease.log"
    fail "Gradle release assemble failed"
fi
cat "$BUILD_LOG_DIR/assembleRelease.log"

APK="$REPO_ROOT/app/build/outputs/apk/release/hedgeyos-arm64-v8a.apk"
require_file "$APK" "release APK"

if [ "$HEDGEYOS_SIGN_RELEASE" = 1 ]; then
    set -a
    . "$HEDGEYOS_KEYSTORE_PROPERTIES"
    set +a

    : "${HEDGEYOS_KEYSTORE:?missing HEDGEYOS_KEYSTORE in signing properties}"
    : "${HEDGEYOS_KEY_ALIAS:?missing HEDGEYOS_KEY_ALIAS in signing properties}"
    : "${HEDGEYOS_KEYSTORE_PASSWORD:?missing HEDGEYOS_KEYSTORE_PASSWORD in signing properties}"
    : "${HEDGEYOS_KEY_PASSWORD:?missing HEDGEYOS_KEY_PASSWORD in signing properties}"

    require_file "$HEDGEYOS_KEYSTORE" "hedgeyos release keystore"

    ALIGNED_APK="$REPO_ROOT/app/build/outputs/apk/release/hedgeyos-arm64-v8a-aligned.apk"
    SIGNED_APK="$REPO_ROOT/app/build/outputs/apk/release/hedgeyos-arm64-v8a-signed.apk"

    "$ZIPALIGN" -f -p 4 "$APK" "$ALIGNED_APK"
    "$APKSIGNER" sign \
        --ks "$HEDGEYOS_KEYSTORE" \
        --ks-key-alias "$HEDGEYOS_KEY_ALIAS" \
        --ks-pass env:HEDGEYOS_KEYSTORE_PASSWORD \
        --key-pass env:HEDGEYOS_KEY_PASSWORD \
        --out "$SIGNED_APK" \
        "$ALIGNED_APK"
    "$APKSIGNER" verify --verbose "$SIGNED_APK"
    mv "$SIGNED_APK" "$APK"
    rm -f "$ALIGNED_APK"
fi

(cd "$(dirname "$APK")" && sha256sum "$(basename "$APK")") > "$APK.sha256"
printf 'Built %s\n' "$APK"
printf 'Checksum %s\n' "$APK.sha256"
