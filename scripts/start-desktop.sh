#!/usr/bin/env sh
set -eu

HEDGEYOS_FILES_DIR="${HEDGEYOS_FILES_DIR:-/data/data/org.hedgeyos/files}"
HEDGEYOS_PREFIX_DIR="${HEDGEYOS_PREFIX_DIR:-$HEDGEYOS_FILES_DIR/usr}"
HEDGEYOS_ROOTFS_DIR="${HEDGEYOS_ROOTFS_DIR:-$HEDGEYOS_FILES_DIR/debian}"
HEDGEYOS_PROOT_TMP_DIR="${HEDGEYOS_PROOT_TMP_DIR:-$HEDGEYOS_FILES_DIR/tmp}"
HEDGEYOS_RUNTIME_DIR="${HEDGEYOS_RUNTIME_DIR:-$HEDGEYOS_FILES_DIR/linux-runtime}"
HEDGEYOS_TMP_DIR="${HEDGEYOS_TMP_DIR:-$HEDGEYOS_RUNTIME_DIR/tmp}"
HEDGEYOS_SHM_DIR="${HEDGEYOS_SHM_DIR:-$HEDGEYOS_RUNTIME_DIR/shm}"
HEDGEYOS_RUN_DIR="${HEDGEYOS_RUN_DIR:-$HEDGEYOS_RUNTIME_DIR/run}"
HEDGEYOS_LOG_DIR="${HEDGEYOS_LOG_DIR:-$HEDGEYOS_FILES_DIR/logs}"
HEDGEYOS_LOCK_DIR="${HEDGEYOS_LOCK_DIR:-$HEDGEYOS_RUNTIME_DIR/processes}"
DISPLAY="${DISPLAY:-:1}"

mkdir -p "$HEDGEYOS_PROOT_TMP_DIR" "$HEDGEYOS_TMP_DIR" "$HEDGEYOS_SHM_DIR" \
    "$HEDGEYOS_RUN_DIR/lock" "$HEDGEYOS_RUN_DIR/dbus" "$HEDGEYOS_RUN_DIR/user/0" \
    "$HEDGEYOS_RUN_DIR/user/1000" "$HEDGEYOS_LOG_DIR" "$HEDGEYOS_LOCK_DIR" \
    "$HEDGEYOS_FILES_DIR/export" "$HEDGEYOS_ROOTFS_DIR/home/hedgeyos/Downloads" \
    "$HEDGEYOS_ROOTFS_DIR/home/hedgeyos/Logs" "$HEDGEYOS_ROOTFS_DIR/dev/shm" \
    "$HEDGEYOS_ROOTFS_DIR/run/shm"
chmod 1777 "$HEDGEYOS_TMP_DIR" "$HEDGEYOS_SHM_DIR" "$HEDGEYOS_RUN_DIR/lock"
chmod 0755 "$HEDGEYOS_RUN_DIR" "$HEDGEYOS_RUN_DIR/dbus"
chmod 0700 "$HEDGEYOS_RUN_DIR/user/0" "$HEDGEYOS_RUN_DIR/user/1000" "$HEDGEYOS_LOCK_DIR"

LOCK_FILE="$HEDGEYOS_LOCK_DIR/desktop.lock"
if [ -e "$LOCK_FILE" ] && kill -0 "$(cat "$LOCK_FILE")" 2>/dev/null; then
    echo "hedgeyos desktop already running with supervisor pid $(cat "$LOCK_FILE")"
    exit 0
fi

echo $$ > "$LOCK_FILE"
trap 'rm -f "$LOCK_FILE"' EXIT INT TERM

export DISPLAY
export PATH="$HEDGEYOS_PREFIX_DIR/bin:/system/bin"
export PREFIX="$HEDGEYOS_PREFIX_DIR"
export LD_LIBRARY_PATH="$HEDGEYOS_PREFIX_DIR/lib"
export PROOT_LOADER="$HEDGEYOS_PREFIX_DIR/libexec/proot/loader"
export PROOT_TMP_DIR="$HEDGEYOS_PROOT_TMP_DIR"
export HOME=/home/hedgeyos
export USER=hedgeyos
export LOGNAME=hedgeyos
export SHELL=/bin/bash
export LANG=C.UTF-8
export TMPDIR=/tmp
export XDG_RUNTIME_DIR=/run/user/0

exec "$HEDGEYOS_PREFIX_DIR/bin/proot" \
    --rootfs="$HEDGEYOS_ROOTFS_DIR" \
    --link2symlink \
    --kill-on-exit \
    --sysvipc \
    --ashmem-memfd \
    --change-id=0:0 \
    --bind=/dev \
    --bind="$HEDGEYOS_SHM_DIR:/dev/shm" \
    --bind=/proc \
    --bind=/sys \
    --bind="$HEDGEYOS_TMP_DIR:/tmp" \
    --bind="$HEDGEYOS_RUN_DIR:/run" \
    --bind="$HEDGEYOS_SHM_DIR:/run/shm" \
    --bind="$HEDGEYOS_FILES_DIR/export:/home/hedgeyos/Downloads" \
    --bind="$HEDGEYOS_LOG_DIR:/home/hedgeyos/Logs" \
    --cwd=/home/hedgeyos \
    /usr/bin/env -i \
    HOME="$HOME" USER=root LOGNAME=root SHELL="$SHELL" \
    DISPLAY="$DISPLAY" LANG="$LANG" TMPDIR="$TMPDIR" XDG_RUNTIME_DIR="$XDG_RUNTIME_DIR" \
    HEDGEYOS_SESSION_LOG=/home/hedgeyos/Logs/xfce-session.log \
    HEDGEYOS_RUNTIME_REPORT=/home/hedgeyos/Logs/linux-runtime-report.txt \
    PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin \
    /bin/bash -lc 'set -e
        report="$HEDGEYOS_RUNTIME_REPORT"
        temporary="${report}.tmp.$$"
        trap '\''rm -f "$temporary"'\'' EXIT INT TERM
        /usr/local/libexec/hedgeyos-runtime-preflight >"$temporary" 2>&1
        mv -f "$temporary" "$report"
        trap - EXIT INT TERM
        exec /usr/local/libexec/hedgeyos-start-desktop' \
    >> "$HEDGEYOS_LOG_DIR/desktop.log" 2>&1
