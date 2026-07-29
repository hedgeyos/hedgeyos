#!/usr/bin/env sh
set -eu

APK="${1:-}"
OUT_DIR="${2:-}"
ROOTFS_PROVENANCE="${3:-}"
AAPT="${AAPT:-aapt}"

fail() {
    printf 'inspect-hedgeyos-apk: %s\n' "$*" >&2
    exit 1
}

require_file() {
    [ -f "$1" ] || fail "missing $2: $1"
}

require_exec() {
    command -v "$1" >/dev/null 2>&1 || fail "missing executable: $1"
}

contains() {
    grep -Fq "$2" "$1"
}

not_contains_regex() {
    ! grep -Eiq "$2" "$1"
}

write_activity_block() {
    activity_name="$1"
    activity_out="$2"

    awk -v activity_name="$activity_name" '
        /^      E: / {
            if (in_activity && matched) {
                printf "%s", block
            }
            in_activity = ($0 ~ /^      E: activity \(/)
            block = in_activity ? $0 "\n" : ""
            matched = 0
            next
        }
        in_activity {
            block = block $0 "\n"
            if (index($0, "\"" activity_name "\"") > 0) {
                matched = 1
            }
        }
        END {
            if (in_activity && matched) {
                printf "%s", block
            }
        }
    ' "$OUT_DIR/androidmanifest-xmltree.txt" > "$activity_out"

    [ -s "$activity_out" ] || fail "APK manifest missing activity: $activity_name"
}

[ -n "$APK" ] || fail "usage: scripts/inspect-hedgeyos-apk.sh <apk> <out-dir> [rootfs-provenance]"
[ -n "$OUT_DIR" ] || fail "usage: scripts/inspect-hedgeyos-apk.sh <apk> <out-dir> [rootfs-provenance]"
require_file "$APK" "APK"
require_exec "$AAPT"
require_exec unzip
require_exec sha256sum

mkdir -p "$OUT_DIR"

"$AAPT" dump badging "$APK" > "$OUT_DIR/aapt-badging.txt"
"$AAPT" dump xmltree "$APK" AndroidManifest.xml > "$OUT_DIR/androidmanifest-xmltree.txt"
"$AAPT" dump resources "$APK" > "$OUT_DIR/aapt-resources.txt"
unzip -l "$APK" > "$OUT_DIR/apk-contents.txt"
unzip -lv "$APK" > "$OUT_DIR/apk-contents-verbose.txt"
sha256sum "$APK" > "$OUT_DIR/hedgeyos-arm64-v8a.apk.sha256"

APP_HOME_ACTIVITY="$OUT_DIR/activity-com.termux.app.HedgeyosHomeActivity.txt"
X11_HOME_ACTIVITY="$OUT_DIR/activity-com.termux.x11.HedgeyosHomeActivity.txt"
X11_MAIN_ACTIVITY="$OUT_DIR/activity-com.termux.x11.MainActivity.txt"
write_activity_block "com.termux.app.HedgeyosHomeActivity" "$APP_HOME_ACTIVITY"
write_activity_block "com.termux.x11.HedgeyosHomeActivity" "$X11_HOME_ACTIVITY"
write_activity_block "com.termux.x11.MainActivity" "$X11_MAIN_ACTIVITY"

contains "$OUT_DIR/aapt-badging.txt" "package: name='org.hedgeyos'" ||
    fail "APK package id is not org.hedgeyos"
contains "$OUT_DIR/aapt-badging.txt" "application-label:'hedgeyos'" ||
    fail "APK application label is not hedgeyos"
contains "$OUT_DIR/aapt-badging.txt" "sdkVersion:'26'" ||
    fail "APK min SDK is not 26"
contains "$OUT_DIR/aapt-badging.txt" "targetSdkVersion:'28'" ||
    fail "APK target SDK is not 28"
contains "$OUT_DIR/aapt-badging.txt" "native-code: 'arm64-v8a'" ||
    fail "APK is not restricted to arm64-v8a native code"
contains "$OUT_DIR/aapt-badging.txt" "launchable-activity: name='com.termux.x11.HedgeyosHomeActivity'" ||
    fail "APK launcher is not the X11-backed hedgeyos HOME activity"

contains "$APP_HOME_ACTIVITY" "android:enabled(0x0101000e)=(type 0x12)0x0" ||
    fail "fallback hedgeyos HOME activity is enabled in the X11 APK"
contains "$X11_HOME_ACTIVITY" "android:enabled(0x0101000e)=(type 0x12)0xffffffff" ||
    fail "X11-backed hedgeyos HOME activity is not enabled"
contains "$X11_HOME_ACTIVITY" "android.intent.category.LAUNCHER" ||
    fail "X11-backed hedgeyos HOME activity is missing CATEGORY_LAUNCHER"
contains "$X11_HOME_ACTIVITY" "android.intent.category.HOME" ||
    fail "X11-backed hedgeyos HOME activity is missing CATEGORY_HOME"
contains "$X11_HOME_ACTIVITY" "android.intent.category.DEFAULT" ||
    fail "X11-backed hedgeyos HOME activity is missing CATEGORY_DEFAULT"
contains "$X11_HOME_ACTIVITY" "android.intent.category.LEANBACK_LAUNCHER" ||
    fail "X11-backed hedgeyos HOME activity is missing CATEGORY_LEANBACK_LAUNCHER"
not_contains_regex "$OUT_DIR/aapt-badging.txt" "launchable-activity: name='com\\.termux\\.x11\\.MainActivity'" ||
    fail "Termux:X11 MainActivity is still exposed as a launcher"
not_contains_regex "$X11_MAIN_ACTIVITY" "android.intent.category.(LAUNCHER|HOME|LEANBACK_LAUNCHER)" ||
    fail "Termux:X11 MainActivity still contains launcher or HOME categories"

contains "$OUT_DIR/apk-contents.txt" "assets/debian-trixie-arm64-rootfs.tar.zst" ||
    fail "APK does not contain bundled Debian rootfs"
contains "$OUT_DIR/apk-contents.txt" "assets/debian-trixie-arm64-rootfs.tar.zst.sha256" ||
    fail "APK does not contain bundled Debian rootfs checksum"
contains "$OUT_DIR/apk-contents.txt" "assets/termux-proot-aarch64.tar.zst" ||
    fail "APK does not contain bundled PRoot payload"
contains "$OUT_DIR/apk-contents.txt" "assets/termux-proot-aarch64.tar.zst.sha256" ||
    fail "APK does not contain bundled PRoot payload checksum"
contains "$OUT_DIR/apk-contents.txt" "assets/hedgeyos-linux/hedgeyos-apply-defaults" ||
    fail "APK does not contain Linux defaults migration"
contains "$OUT_DIR/apk-contents.txt" "assets/hedgeyos-linux/hedgeyos-window-rules.desktop" ||
    fail "APK does not contain Linux window-rule autostart"
contains "$OUT_DIR/apk-contents.txt" "assets/hedgeyos-linux/hedgeyos-window-rules.lua" ||
    fail "APK does not contain Linux portrait window rules"
contains "$OUT_DIR/apk-contents.txt" "assets/hedgeyos-linux/hedgeyos-menu.png" ||
    fail "APK does not contain the hedgeyos XFCE menu icon"
contains "$OUT_DIR/apk-contents.txt" "assets/hedgeyos-linux/packages/devilspie2_" ||
    fail "APK does not contain the offline devilspie2 migration package"
contains "$OUT_DIR/apk-contents.txt" "assets/hedgeyos-linux/packages/liblua5.1-0_" ||
    fail "APK does not contain the offline Lua migration dependency"
contains "$OUT_DIR/aapt-resources.txt" "drawable/hedgeyos_companion" ||
    fail "APK does not contain the Hitomi-derived hedgehog overlay asset"
contains "$OUT_DIR/androidmanifest-xmltree.txt" "android.permission.WAKE_LOCK" ||
    fail "APK manifest is missing WAKE_LOCK"
contains "$OUT_DIR/androidmanifest-xmltree.txt" "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" ||
    fail "APK manifest is missing battery-optimization exemption access"
contains "$OUT_DIR/apk-contents.txt" "lib/arm64-v8a/libXlorie.so" ||
    fail "APK does not contain embedded Termux:X11 native library"
awk '$NF == "lib/arm64-v8a/libXlorie.so" && $2 == "Stored" { found=1 } END { exit found ? 0 : 1 }' "$OUT_DIR/apk-contents-verbose.txt" ||
    fail "embedded Termux:X11 native library must be stored uncompressed for app_process dlopen"
contains "$OUT_DIR/apk-contents.txt" "lib/arm64-v8a/libtermux.so" ||
    fail "APK does not contain Termux terminal native library"
contains "$OUT_DIR/apk-contents.txt" "lib/arm64-v8a/libtermux-bootstrap.so" ||
    fail "APK does not contain hedgeyos bootstrap native library"
contains "$OUT_DIR/apk-contents.txt" "lib/arm64-v8a/liblocal-socket.so" ||
    fail "APK does not contain local socket native library"
not_contains_regex "$OUT_DIR/apk-contents.txt" "vnc|tigervnc|x11vnc|novnc|xrdp" ||
    fail "APK contents contain VNC/RDP-related files"

if [ -n "$ROOTFS_PROVENANCE" ] && [ -f "$ROOTFS_PROVENANCE" ]; then
    cp "$ROOTFS_PROVENANCE" "$OUT_DIR/debian-trixie-arm64-rootfs.provenance"
    not_contains_regex "$ROOTFS_PROVENANCE" "vnc|tigervnc|x11vnc|novnc|xrdp" ||
        fail "rootfs package provenance contains VNC/RDP-related packages"
    contains "$ROOTFS_PROVENANCE" "devilspie2" ||
        fail "rootfs package provenance is missing devilspie2"
fi

{
    printf 'apk=%s\n' "$APK"
    printf 'sha256=%s\n' "$(cut -d ' ' -f 1 "$OUT_DIR/hedgeyos-arm64-v8a.apk.sha256")"
    printf 'package=org.hedgeyos\n'
    printf 'launcher=com.termux.x11.HedgeyosHomeActivity\n'
    printf 'fallback_home_activity_enabled=false\n'
    printf 'home_category=present\n'
    printf 'x11_main_launcher=absent\n'
    printf 'native_code=arm64-v8a\n'
    printf 'bundled_rootfs=assets/debian-trixie-arm64-rootfs.tar.zst\n'
    printf 'bundled_proot=assets/termux-proot-aarch64.tar.zst\n'
    printf 'embedded_x11=lib/arm64-v8a/libXlorie.so\n'
    printf 'power_protection=wake_lock_and_battery_setup\n'
    printf 'overlay_asset=drawable/hedgeyos_companion\n'
    printf 'linux_menu_icon=assets/hedgeyos-linux/hedgeyos-menu.png\n'
    printf 'linux_defaults=assets/hedgeyos-linux/hedgeyos-apply-defaults\n'
    printf 'vnc_files=absent_in_apk_listing\n'
} > "$OUT_DIR/summary.properties"

printf 'Inspection written to %s\n' "$OUT_DIR"
cat "$OUT_DIR/summary.properties"
