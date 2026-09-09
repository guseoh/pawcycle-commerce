#!/usr/bin/env bash

set -Eeuo pipefail
set +x

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
LIBEXEC_DIR="/usr/local/libexec"
SYSTEMD_DIR="/etc/systemd/system"
RENEWAL_SCRIPT="pawcycle-production-https-renew"
SERVICE_UNIT="pawcycle-production-https-renew.service"
TIMER_UNIT="pawcycle-production-https-renew.timer"

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

[[ "${EUID:-$(id -u)}" == 0 ]] || die "installation requires root"
command -v install >/dev/null 2>&1 || die "install is unavailable"
command -v systemctl >/dev/null 2>&1 || die "systemctl is unavailable"

install -d -m 0755 "$LIBEXEC_DIR" "$SYSTEMD_DIR"
install -m 0755 "$SCRIPT_DIR/renew-production-certificate.sh" "$LIBEXEC_DIR/$RENEWAL_SCRIPT"
install -m 0644 "$SCRIPT_DIR/systemd/$SERVICE_UNIT" "$SYSTEMD_DIR/$SERVICE_UNIT"
install -m 0644 "$SCRIPT_DIR/systemd/$TIMER_UNIT" "$SYSTEMD_DIR/$TIMER_UNIT"
systemctl daemon-reload

printf 'HTTPS renewal service and timer installed; timer remains disabled until explicitly enabled\n'
