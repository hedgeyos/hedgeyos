#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH='' cd -- "$SCRIPT_DIR/.." && pwd)
ASSET_DIR="$REPO_ROOT/rootfs/runtime-assets/hedgeyos-linux"
LOGGER="$ASSET_DIR/hedgeyos-bounded-log"
SUPERVISOR="$ASSET_DIR/hedgeyos-app-supervisor"
TMP_DIR=$(mktemp -d)
unrelated_pid=
xfce_pid=
dbus_pid=
shutdown_pid=
stale_session_pid=
stubborn_pid=

cleanup() {
    if [ -r "$TMP_DIR/survivor.pid" ]; then
        survivor=$(cat "$TMP_DIR/survivor.pid")
        kill "$survivor" 2>/dev/null || true
    fi
    for pid in "$unrelated_pid" "$xfce_pid" "$dbus_pid" "$shutdown_pid" "$stale_session_pid" "$stubborn_pid"; do
        if [ -n "$pid" ]; then
            kill "$pid" 2>/dev/null || true
        fi
    done
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT INT TERM

log="$TMP_DIR/managed.log"
python3 - <<'PY' | "$LOGGER" \
    --name test \
    --path "$log" \
    --max-bytes 4096 \
    --rotations 2 \
    --rate-bytes-per-second 1048576
for index in range(2000):
    print(f"unique bounded line {index:04d} " + ("x" * 40))
PY
test "$(stat -c %s "$log")" -le 4096
test "$(find "$TMP_DIR" -maxdepth 1 -name 'managed.log*' -type f -printf '%s\n' |
    awk '{sum += $1} END {print sum + 0}')" -le 12288
test -s "$TMP_DIR/.managed.log.status.json"

flood_log="$TMP_DIR/flood.log"
dd if=/dev/zero bs=1048576 count=32 2>/dev/null |
    "$LOGGER" \
        --name flood \
        --path "$flood_log" \
        --max-bytes 4096 \
        --rotations 1 \
        --rate-bytes-per-second 4096
test "$(stat -c %s "$flood_log")" -le 4096
grep -Fq '"suppressedBytes":' "$TMP_DIR/.flood.log.status.json"

repeat_log="$TMP_DIR/repeat.log"
yes 'same repeated diagnostic line' | head -n 5000 |
    "$LOGGER" \
        --name repeat \
        --path "$repeat_log" \
        --max-bytes 4096 \
        --rotations 1 \
        --rate-bytes-per-second 1048576
grep -Fq 'collapsed' "$repeat_log"
test "$(wc -l < "$repeat_log")" -lt 30

budget_dir="$TMP_DIR/global-budget"
mkdir -p "$budget_dir"
for index in 1 2 3; do
    python3 - <<'PY' | "$LOGGER" \
        --name "budget-$index" \
        --path "$budget_dir/budget-$index.log" \
        --max-bytes 4096 \
        --rotations 2 \
        --rate-bytes-per-second 1048576 \
        --global-budget-bytes 16384
for line in range(1000):
    print(f"global budget line {line:04d} " + ("b" * 32))
PY
done
test "$(du -sb "$budget_dir" | awk '{print $1}')" -le 16384

nested_budget_dir="$TMP_DIR/nested-global-budget"
mkdir -p "$nested_budget_dir/apps"
truncate -s 12000 "$nested_budget_dir/existing.log"
dd if=/dev/zero bs=1048576 count=32 2>/dev/null |
    "$LOGGER" \
        --name nested-budget-flood \
        --path "$nested_budget_dir/apps/flood.log" \
        --max-bytes 4096 \
        --rotations 1 \
        --rate-bytes-per-second 1048576 \
        --global-root "$nested_budget_dir" \
        --global-budget-bytes 16384
test "$(find "$nested_budget_dir" -type f -printf '%s\n' |
    awk '{sum += $1} END {print sum + 0}')" -le 16384
grep -Eq '"suppressedBytes": [1-9][0-9]*' \
    "$nested_budget_dir/apps/.flood.log.status.json"

legacy="$TMP_DIR/legacy.log"
truncate -s 1048576 "$legacy"
printf '%s\n' 'preserved diagnostic tail' >> "$legacy"
"$LOGGER" \
    --repair \
    --path "$legacy" \
    --max-bytes 4096 \
    --rotations 2 \
    --legacy-threshold 8192
test "$(stat -c %s "$legacy")" -le 4096
grep -Fq 'original_bytes=1048602' "$legacy"
grep -Fq 'preserved diagnostic tail' "$legacy"
test -s "$TMP_DIR/legacy.log.legacy-repair.json"
legacy_sha=$(sha256sum "$legacy" | awk '{print $1}')
"$LOGGER" \
    --repair \
    --path "$legacy" \
    --max-bytes 4096 \
    --rotations 2 \
    --legacy-threshold 8192
test "$legacy_sha" = "$(sha256sum "$legacy" | awk '{print $1}')"

sleep 30 &
unrelated_pid=$!
set +e
HEDGEYOS_APP_PROCESS_DIR="$TMP_DIR/processes" \
HEDGEYOS_APP_LOG_DIR="$TMP_DIR/apps" \
HEDGEYOS_APP_STATUS_PATH="$TMP_DIR/app-status.json" \
HEDGEYOS_BOUNDED_LOG="$LOGGER" \
    "$SUPERVISOR" --name containment-test -- \
    python3 -c 'import pathlib,subprocess,time,os; p=subprocess.Popen(["sleep","30"]); pathlib.Path("'"$TMP_DIR"'/survivor.pid").write_text(str(p.pid)); time.sleep(0.5); os._exit(7)'
supervisor_code=$?
set -e
test "$supervisor_code" -eq 7
test -s "$TMP_DIR/app-status.json"
grep -Fq '"classification": "WARNING"' "$TMP_DIR/app-status.json"
grep -Fq '"processGroupId":' "$TMP_DIR/app-status.json"
grep -Fq '"processSessionId":' "$TMP_DIR/app-status.json"
grep -Fq '"leaderStart":' "$TMP_DIR/app-status.json"
grep -Fq '"executable":' "$TMP_DIR/app-status.json"
survivor=$(cat "$TMP_DIR/survivor.pid")
test ! -e "/proc/$survivor"
test -e "/proc/$unrelated_pid"

set +e
HEDGEYOS_APP_PROCESS_DIR="$TMP_DIR/stubborn-processes" \
HEDGEYOS_APP_LOG_DIR="$TMP_DIR/apps" \
HEDGEYOS_APP_STATUS_PATH="$TMP_DIR/stubborn-status.json" \
HEDGEYOS_BOUNDED_LOG="$LOGGER" \
    "$SUPERVISOR" --name stubborn-test -- \
    python3 -c 'import pathlib,subprocess,time,os,sys; p=subprocess.Popen([sys.executable,"-c","import signal,time; signal.signal(signal.SIGTERM, signal.SIG_IGN); time.sleep(30)"]); pathlib.Path("'"$TMP_DIR"'/stubborn.pid").write_text(str(p.pid)); time.sleep(0.5); os._exit(9)'
stubborn_code=$?
set -e
test "$stubborn_code" -eq 9
stubborn_pid=$(cat "$TMP_DIR/stubborn.pid")
test ! -e "/proc/$stubborn_pid"
grep -Eq '"forcedDescendantPids": \[[0-9]+' "$TMP_DIR/stubborn-status.json"
grep -Fq '"message": "stubborn-test leader exited with code 9' \
    "$TMP_DIR/stubborn-status.json"
stubborn_pid=

mkdir -p "$TMP_DIR/stale-processes"
printf '{"leaderPid":%s,"leaderStart":"reused-pid","processes":{"%s":{"start":"reused-pid","comm":"sleep"}}}\n' \
    "$unrelated_pid" "$unrelated_pid" > "$TMP_DIR/stale-processes/stale.json"
HEDGEYOS_APP_PROCESS_DIR="$TMP_DIR/stale-processes" \
HEDGEYOS_APP_LOG_DIR="$TMP_DIR/apps" \
HEDGEYOS_APP_STATUS_PATH="$TMP_DIR/stale-status.json" \
HEDGEYOS_BOUNDED_LOG="$LOGGER" \
    "$SUPERVISOR" --cleanup-stale
test -e "/proc/$unrelated_pid"
test ! -e "$TMP_DIR/stale-processes/stale.json"

sleep 30 &
stale_session_pid=$!
stale_session_ticks=$(awk '{print $22}' "/proc/$stale_session_pid/stat")
printf '{"leaderPid":%s,"leaderStart":"%s","hedgeyosSessionId":"old-session","processes":{"%s":{"start":"%s","comm":"sleep"}}}\n' \
    "$stale_session_pid" "$stale_session_ticks" \
    "$stale_session_pid" "$stale_session_ticks" \
    > "$TMP_DIR/stale-processes/old-session.json"
HEDGEYOS_SESSION_ID=current-session \
HEDGEYOS_APP_PROCESS_DIR="$TMP_DIR/stale-processes" \
HEDGEYOS_APP_LOG_DIR="$TMP_DIR/apps" \
HEDGEYOS_APP_STATUS_PATH="$TMP_DIR/stale-session-status.json" \
HEDGEYOS_BOUNDED_LOG="$LOGGER" \
    "$SUPERVISOR" --cleanup-stale
wait "$stale_session_pid" 2>/dev/null || true
test ! -e "/proc/$stale_session_pid"
test ! -e "$TMP_DIR/stale-processes/old-session.json"
stale_session_pid=

sleep 30 &
shutdown_pid=$!
shutdown_ticks=$(awk '{print $22}' "/proc/$shutdown_pid/stat")
printf '{"leaderPid":%s,"leaderStart":"%s","processes":{"%s":{"start":"%s","comm":"sleep"}}}\n' \
    "$shutdown_pid" "$shutdown_ticks" "$shutdown_pid" "$shutdown_ticks" \
    > "$TMP_DIR/stale-processes/shutdown.json"
HEDGEYOS_APP_PROCESS_DIR="$TMP_DIR/stale-processes" \
HEDGEYOS_APP_LOG_DIR="$TMP_DIR/apps" \
HEDGEYOS_APP_STATUS_PATH="$TMP_DIR/shutdown-status.json" \
HEDGEYOS_BOUNDED_LOG="$LOGGER" \
    "$SUPERVISOR" --shutdown-session
wait "$shutdown_pid" 2>/dev/null || true
test ! -e "/proc/$shutdown_pid"
shutdown_pid=

kill "$unrelated_pid"
wait "$unrelated_pid" 2>/dev/null || true

# shellcheck disable=SC2016
dbus-run-session -- sh -c '
    (exit 0) &
    helper=$!
    wait "$helper"
    dbus-send --session --type=method_call --print-reply \
        --dest=org.freedesktop.DBus / org.freedesktop.DBus.ListNames >/dev/null
    exec sleep 0.1
'

sleep 30 &
xfce_pid=$!
sleep 30 &
dbus_pid=$!
xfce_ticks=$(awk '{print $22}' "/proc/$xfce_pid/stat")
dbus_ticks=$(awk '{print $22}' "/proc/$dbus_pid/stat")
HEDGEYOS_SESSION_GUARD_STATE="$TMP_DIR/session-guard.json" \
HEDGEYOS_CONTAINMENT_STATUS="$TMP_DIR/session-containment.json" \
HEDGEYOS_LIFECYCLE_STATUS="$TMP_DIR/desktop-lifecycle.json" \
HEDGEYOS_SHUTDOWN_REQUEST="$TMP_DIR/no-shutdown-request" \
    "$ASSET_DIR/hedgeyos-session-guard" \
        --xfce-pid "$xfce_pid" \
        --xfce-start-ticks "$xfce_ticks" \
        --dbus-pid "$dbus_pid" \
        --dbus-start-ticks "$dbus_ticks" &
guard_pid=$!
sleep 0.3
kill "$dbus_pid"
wait "$dbus_pid" 2>/dev/null || true
set +e
wait "$guard_pid"
guard_code=$?
set -e
test "$guard_code" -eq 70
for _ in 1 2 3 4 5; do
    [ ! -e "/proc/$xfce_pid" ] && break
    sleep 0.1
done
test ! -e "/proc/$xfce_pid"
grep -Fq 'session-dbus-exited-before-xfce' "$TMP_DIR/session-containment.json"

grep -Fq 'exec dbus-run-session -- /usr/local/libexec/hedgeyos-start-desktop' \
    "$REPO_ROOT/app/src/main/java/com/termux/app/HedgeyosRuntimeManager.java"
grep -Fq 'exec /etc/xdg/xfce4/xinitrc' "$ASSET_DIR/hedgeyos-start-desktop"
if grep -Fq 'startxfce4 &' "$ASSET_DIR/hedgeyos-start-desktop"; then
    exit 1
fi
# shellcheck disable=SC2016
if grep -Fq 'exec >>"$HEDGEYOS_SESSION_LOG"' "$ASSET_DIR/hedgeyos-start-desktop"; then
    exit 1
fi
grep -Fq 'start_ticks=' "$ASSET_DIR/hedgeyos-start-desktop"
grep -Fq 'session-dbus-exited-before-xfce' "$ASSET_DIR/hedgeyos-session-guard"
grep -Fq 'start_new_session=True' "$SUPERVISOR"
grep -Fq 'containedPids' "$SUPERVISOR"

printf 'Runtime containment tests passed.\n'
