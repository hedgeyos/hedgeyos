#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH='' cd -- "$SCRIPT_DIR/.." && pwd)
ASSET_DIR="$REPO_ROOT/rootfs/runtime-assets/hedgeyos-linux"
MANAGER="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosRuntimeManager.java"
RUNTIME="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosGuestRuntime.java"
ATOMIC_FILE="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosAtomicFile.java"
PROCESS_OWNER="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosProcessOwner.java"
X11_BRIDGE="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosX11Bridge.java"
X11_NATIVE="$REPO_ROOT/third_party/termux-x11/lorie/src/main/cpp/lorie/cmdentrypoint.c"
X11_ACTIVITY_NATIVE="$REPO_ROOT/third_party/termux-x11/lorie/src/main/cpp/lorie/activity.c"
X11_ACTIVITY="$REPO_ROOT/third_party/termux-x11/lorie/src/main/java/com/termux/x11/MainActivity.java"
APP_BUILD="$REPO_ROOT/app/build.gradle"

sh -n "$ASSET_DIR/hedgeyos-runtime-preflight"
bash -n "$ASSET_DIR/hedgeyos-start-desktop"
sh -n "$ASSET_DIR/hedgeyos-session-init"
sh -n "$REPO_ROOT/scripts/start-desktop.sh"

test -x "$ASSET_DIR/hedgeyos-runtime-preflight"
test -x "$ASSET_DIR/hedgeyos-start-desktop"
test -x "$ASSET_DIR/hedgeyos-bounded-log"
test -x "$ASSET_DIR/hedgeyos-session-guard"
test -x "$ASSET_DIR/hedgeyos-app-supervisor"
test -x "$ASSET_DIR/hedgeyos-launch-chromium"
test -x "$ASSET_DIR/hedgeyos-proot-seqpacket-reproducer"
grep -Fq "hedgeyos-linux/hedgeyos-runtime-preflight	/usr/local/libexec/hedgeyos-runtime-preflight	0:0	0755" \
    "$REPO_ROOT/rootfs/customizations.tsv"
grep -Fq "hedgeyos-linux/hedgeyos-start-desktop	/usr/local/libexec/hedgeyos-start-desktop	0:0	0755" \
    "$REPO_ROOT/rootfs/customizations.tsv"
grep -Fq "hedgeyos-linux/hedgeyos-app-supervisor	/usr/local/libexec/hedgeyos-app-supervisor	0:0	0755" \
    "$REPO_ROOT/rootfs/customizations.tsv"
grep -Fq "hedgeyos-linux/hedgeyos-gtk-asset-smoke	/usr/local/libexec/hedgeyos-gtk-asset-smoke	0:0	0755" \
    "$REPO_ROOT/rootfs/customizations.tsv"

test "$(grep -R -F 'command.add("--bind=/dev")' \
    "$REPO_ROOT/app/src/main/java/com/termux/app" | wc -l)" -eq 1
grep -Fq 'command.add("--bind=" + layout.shm.getAbsolutePath() + ":" + GUEST_SHM)' "$RUNTIME"
grep -Fq 'command.add("--bind=" + layout.run.getAbsolutePath() + ":" + GUEST_RUN)' "$RUNTIME"
grep -Fq 'command.add("--bind=" + layout.processes.getAbsolutePath() + ":" + GUEST_PROCESS_DIR)' "$RUNTIME"
if grep -Fq 'command.add("--rootfs=' "$MANAGER"; then
    exit 1
fi
if grep -Eiq -- '--disable-dev-shm-usage|--no-sandbox' \
    "$MANAGER" "$RUNTIME" "$ASSET_DIR/hedgeyos-runtime-preflight" "$ASSET_DIR/hedgeyos-start-desktop"; then
    exit 1
fi

grep -Fq 'check_directory shm /dev/shm 1777' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'from multiprocessing import shared_memory' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'libc.shmget' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'DBUS_SESSION_BUS_ADDRESS is unset' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'dbus-send --session' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'UNSUPPORTED_BY_ANDROID_PROCFS' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'WARNING|%s|%s' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'FATAL|%s|%s' "$ASSET_DIR/hedgeyos-runtime-preflight"
# shellcheck disable=SC2016
grep -Fq 'display_number=${display_number%%.*}' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'gtk-svg-loader' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'gtk-svg-cache' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'gtk-svg-decode' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'x11-diagnostic' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'proot-recvmsg-' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'runtime_logging=BOUNDED' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'orphaned_application_descendants=' "$ASSET_DIR/hedgeyos-runtime-preflight"
if grep -Fq "printf 'orphaned_application_descendants=0" \
    "$ASSET_DIR/hedgeyos-runtime-preflight"; then
    exit 1
fi
grep -Fq 'repeated_messages_suppressed_bytes=' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'exec dbus-run-session -- /usr/local/libexec/hedgeyos-start-desktop' "$MANAGER"
grep -Fq 'exec /etc/xdg/xfce4/xinitrc' "$ASSET_DIR/hedgeyos-start-desktop"
grep -Fq 'start_ticks=' "$ASSET_DIR/hedgeyos-start-desktop"
grep -Fq 'start_new_session=True' "$ASSET_DIR/hedgeyos-app-supervisor"
grep -Fq 'HedgeyOS Chromium compatibility warning' \
    "$ASSET_DIR/hedgeyos-launch-chromium"
grep -Fq -- '-default "Cancel"' "$ASSET_DIR/hedgeyos-launch-chromium"
grep -Fq -- '/usr/bin/chromium --no-sandbox "$@"' \
    "$ASSET_DIR/hedgeyos-launch-chromium"
grep -Fq 'Exec=/usr/local/libexec/hedgeyos-launch-chromium %U' \
    "$ASSET_DIR/chromium.desktop"
if grep -Fq -- '--no-sandbox' "$ASSET_DIR/chromium.desktop"; then
    exit 1
fi
grep -Fq 'StandardCopyOption.ATOMIC_MOVE' "$ATOMIC_FILE"
grep -Fq 'HedgeyosAtomicFile.write' "$MANAGER"
grep -Fq 'matchesOwnedProcess' "$PROCESS_OWNER"
grep -Fq 'environment.remove("TERMUX_X11_DEBUG")' "$X11_BRIDGE"
grep -Fq 'environment.put("TERMUX_X11_DEBUG", "1")' "$X11_BRIDGE"
if grep -Fq 'builder.environment().put("TERMUX_X11_DEBUG", "1")' "$X11_BRIDGE"; then
    exit 1
fi
grep -Fq 'System.getenv("HEDGEYOS_X11_DEBUG") ?: "0"' "$APP_BUILD"
grep -Fq 'HEDGEYOS_X11_DIAGNOSTIC_PID_FILE' "$X11_BRIDGE"
grep -Fq 'recordDiagnosticChild' "$X11_NATIVE"
grep -Fq 'startDiagnosticChildReaper' "$X11_NATIVE"
grep -Fq 'stopRecordedOwnedChild' "$X11_BRIDGE"
grep -Fq 'x11-activity-diagnostic-logcat.pid' "$X11_ACTIVITY"
grep -Fq 'startLogcatChildReaper' "$X11_ACTIVITY_NATIVE"
grep -Fq 'PR_SET_PDEATHSIG' "$X11_ACTIVITY_NATIVE"

normalize_display() {
    display_number=$1
    display_number=${display_number#*:}
    display_number=${display_number%%.*}
    printf '%s\n' "$display_number"
}

test "$(normalize_display :1)" = 1
test "$(normalize_display :1.0)" = 1
test "$(normalize_display localhost:10.0)" = 10

printf 'Linux runtime contract tests passed.\n'
