#!/usr/bin/env sh
set -eu

HEDGEYOS_FILES_DIR="${HEDGEYOS_FILES_DIR:-/data/data/org.hedgeyos/files}"
HEDGEYOS_PREFIX_DIR="${HEDGEYOS_PREFIX_DIR:-$HEDGEYOS_FILES_DIR/usr}"
HEDGEYOS_ROOTFS_DIR="${HEDGEYOS_ROOTFS_DIR:-$HEDGEYOS_FILES_DIR/debian}"
HEDGEYOS_TMP_DIR="${HEDGEYOS_TMP_DIR:-$HEDGEYOS_FILES_DIR/tmp}"
HEDGEYOS_LOG_DIR="${HEDGEYOS_LOG_DIR:-$HEDGEYOS_FILES_DIR/logs}"
HEDGEYOS_LOCK_DIR="${HEDGEYOS_LOCK_DIR:-$HEDGEYOS_FILES_DIR/run}"
DISPLAY="${DISPLAY:-:1}"

mkdir -p "$HEDGEYOS_TMP_DIR" "$HEDGEYOS_LOG_DIR" "$HEDGEYOS_LOCK_DIR" "$HEDGEYOS_FILES_DIR/export" "$HEDGEYOS_ROOTFS_DIR/home/hedgeyos/Downloads"

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
export PROOT_TMP_DIR="$HEDGEYOS_TMP_DIR"
export HOME=/home/hedgeyos
export USER=hedgeyos
export LOGNAME=hedgeyos
export SHELL=/bin/bash
export LANG=C.UTF-8
export TMPDIR=/tmp
export XDG_RUNTIME_DIR=/tmp/hedgeyos-runtime

exec "$HEDGEYOS_PREFIX_DIR/bin/proot" \
    --rootfs="$HEDGEYOS_ROOTFS_DIR" \
    --link2symlink \
    --kill-on-exit \
    --sysvipc \
    --ashmem-memfd \
    --change-id=1000:1000 \
    --bind=/dev \
    --bind=/proc \
    --bind=/sys \
    --bind="$HEDGEYOS_TMP_DIR:/tmp" \
    --bind="$HEDGEYOS_FILES_DIR/export:/home/hedgeyos/Downloads" \
    --cwd=/home/hedgeyos \
    /usr/bin/env -i \
    HOME="$HOME" USER="$USER" LOGNAME="$LOGNAME" SHELL="$SHELL" \
    DISPLAY="$DISPLAY" LANG="$LANG" TMPDIR="$TMPDIR" XDG_RUNTIME_DIR="$XDG_RUNTIME_DIR" \
    PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin \
    /bin/bash -lc 'mkdir -p "$XDG_RUNTIME_DIR" /home/hedgeyos/Downloads && chmod 700 "$XDG_RUNTIME_DIR" && dbus-launch --exit-with-session startxfce4' \
    >> "$HEDGEYOS_LOG_DIR/desktop.log" 2>&1
