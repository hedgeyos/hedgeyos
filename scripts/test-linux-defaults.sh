#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ASSET_DIR="$REPO_ROOT/rootfs/runtime-assets/hedgeyos-linux"
TMP_DIR=$(mktemp -d)

cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT INT TERM

HOME_DIR="$TMP_DIR/home"
DEFAULTS_DIR="$TMP_DIR/defaults"
FAKE_BIN="$TMP_DIR/bin"
XFCONF_LOG="$TMP_DIR/xfconf.log"

mkdir -p "$HOME_DIR/Desktop" "$DEFAULTS_DIR" "$FAKE_BIN"
cp "$ASSET_DIR/Terminal.desktop" "$DEFAULTS_DIR/Terminal.desktop"
cp "$ASSET_DIR/terminalrc" "$DEFAULTS_DIR/terminalrc"

cat > "$HOME_DIR/Desktop/hedgeyos.desktop" <<'EOF'
[Desktop Entry]
Name=hedgeyos
EOF
cat > "$HOME_DIR/Desktop/keep-me.txt" <<'EOF'
user-created
EOF

cat > "$FAKE_BIN/xfconf-query" <<'EOF'
#!/bin/sh
printf '%s\n' "$*" >> "$XFCONF_LOG"
EOF
chmod 0755 "$FAKE_BIN/xfconf-query"

export XFCONF_LOG
HEDGEYOS_HOME="$HOME_DIR" \
HEDGEYOS_DEFAULTS_DIR="$DEFAULTS_DIR" \
PATH="$FAKE_BIN:$PATH" \
    "$ASSET_DIR/hedgeyos-apply-defaults"

test "$(cat "$HOME_DIR/.config/hedgeyos/defaults-version")" = "1"
test -x "$HOME_DIR/Desktop/Terminal.desktop"
test ! -e "$HOME_DIR/Desktop/hedgeyos.desktop"
test -f "$HOME_DIR/Desktop/keep-me.txt"
grep -Fq "MiscDefaultGeometry=72x22" "$HOME_DIR/.config/xfce4/terminal/terminalrc"
grep -Fq "/general/button_layout -n -t string -s CMHO|" "$XFCONF_LOG"
grep -Fq "/desktop-icons/file-icons/show-home -n -t bool -s false" "$XFCONF_LOG"

first_log_sha=$(sha256sum "$XFCONF_LOG" | awk '{print $1}')
HEDGEYOS_HOME="$HOME_DIR" \
HEDGEYOS_DEFAULTS_DIR="$DEFAULTS_DIR" \
PATH="$FAKE_BIN:$PATH" \
    "$ASSET_DIR/hedgeyos-apply-defaults"
second_log_sha=$(sha256sum "$XFCONF_LOG" | awk '{print $1}')
test "$first_log_sha" = "$second_log_sha"

printf 'Linux default migration tests passed.\n'
