#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ASSET_DIR="$REPO_ROOT/rootfs/runtime-assets/hedgeyos-linux"
MANAGER="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosRuntimeManager.java"
RUNTIME="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosGuestRuntime.java"
ATOMIC_FILE="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosAtomicFile.java"
PROCESS_OWNER="$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosProcessOwner.java"

sh -n "$ASSET_DIR/hedgeyos-runtime-preflight"
bash -n "$ASSET_DIR/hedgeyos-start-desktop"
sh -n "$REPO_ROOT/scripts/start-desktop.sh"

test -x "$ASSET_DIR/hedgeyos-runtime-preflight"
test -x "$ASSET_DIR/hedgeyos-start-desktop"
grep -Fq "hedgeyos-linux/hedgeyos-runtime-preflight	/usr/local/libexec/hedgeyos-runtime-preflight	0:0	0755" \
    "$REPO_ROOT/rootfs/customizations.tsv"
grep -Fq "hedgeyos-linux/hedgeyos-start-desktop	/usr/local/libexec/hedgeyos-start-desktop	0:0	0755" \
    "$REPO_ROOT/rootfs/customizations.tsv"

test "$(grep -R -F 'command.add("--bind=/dev")' \
    "$REPO_ROOT/app/src/main/java/com/termux/app" | wc -l)" -eq 1
grep -Fq 'command.add("--bind=" + layout.shm.getAbsolutePath() + ":" + GUEST_SHM)' "$RUNTIME"
grep -Fq 'command.add("--bind=" + layout.run.getAbsolutePath() + ":" + GUEST_RUN)' "$RUNTIME"
! grep -Fq 'command.add("--rootfs=' "$MANAGER"
! grep -Eiq -- '--disable-dev-shm-usage|--no-sandbox' \
    "$MANAGER" "$RUNTIME" "$ASSET_DIR/hedgeyos-runtime-preflight" "$ASSET_DIR/hedgeyos-start-desktop"

grep -Fq 'check_directory shm /dev/shm 1777' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'from multiprocessing import shared_memory' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'libc.shmget' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'dbus-run-session' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'UNSUPPORTED_BY_ANDROID_PROCFS' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'WARNING|%s|%s' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'FATAL|%s|%s' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'display_number=${display_number%%.*}' "$ASSET_DIR/hedgeyos-runtime-preflight"
grep -Fq 'StandardCopyOption.ATOMIC_MOVE' "$ATOMIC_FILE"
grep -Fq 'HedgeyosAtomicFile.write' "$MANAGER"
grep -Fq 'matchesOwnedProcess' "$PROCESS_OWNER"

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
