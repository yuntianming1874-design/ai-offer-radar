#!/usr/bin/env bash
set -euo pipefail

# Install a launchd agent that checks the published GLM snapshot every 15
# minutes and dispatches the collector through repository_dispatch when the
# snapshot is older than 75 minutes. GitHub's schedule remains the fallback.
#
# The GitHub token is read from the login Keychain at runtime; it is never
# written into this plist or this repository.

LABEL="com.aioffer.radar.collector-heartbeat"
INTERVAL_SECONDS="${HEARTBEAT_INTERVAL_SECONDS:-900}"
THRESHOLD_MINUTES="${HEARTBEAT_THRESHOLD_MINUTES:-75}"
KEYCHAIN_SERVICE="ai-offer-radar-heartbeat"
KEYCHAIN_ACCOUNT="github-token"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
HEARTBEAT_SCRIPT="$SCRIPT_DIR/heartbeat.mjs"
PLIST_PATH="$HOME/Library/LaunchAgents/$LABEL.plist"
LOG_DIR="$HOME/Library/Logs/ai-offer-radar"
LOG_PATH="$LOG_DIR/heartbeat.log"
UID_VALUE="$(id -u)"

usage() {
  cat <<'EOF'
Usage: tools/glm-collector/install-heartbeat.macos.sh [command]

Commands:
  install     Install and start the launchd heartbeat (default)
  uninstall   Stop and remove the launchd heartbeat
  status      Show whether the launchd job is loaded
  --help      Show this help

Before installing, store a GitHub token with Actions write access in the
login Keychain (the token is never written to the repository):

  security add-generic-password -U \
    -s ai-offer-radar-heartbeat \
    -a github-token \
    -w '<token>'

Environment:
  HEARTBEAT_INTERVAL_SECONDS  launchd interval, default 900 (15 minutes)
  HEARTBEAT_THRESHOLD_MINUTES dispatch threshold, default 75 minutes
EOF
}

require_macos() {
  if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "This installer only supports macOS launchd." >&2
    exit 1
  fi
}

find_node() {
  local node_bin
  node_bin="$(command -v node || true)"
  if [[ -z "$node_bin" ]]; then
    echo "node was not found on PATH." >&2
    exit 1
  fi
  local major
  major="$("$node_bin" -p 'process.versions.node.split(".")[0]')"
  if (( major < 20 )); then
    echo "Node.js 20+ is required; found $($node_bin --version)." >&2
    exit 1
  fi
  echo "$node_bin"
}

check_token() {
  if ! security find-generic-password -s "$KEYCHAIN_SERVICE" -a "$KEYCHAIN_ACCOUNT" >/dev/null 2>&1; then
    cat >&2 <<EOF
GitHub token is missing from the login Keychain.

Create a token with Actions write access (fine-grained PAT scoped to this
repository is preferred), then store it:

  security add-generic-password -U \\
    -s $KEYCHAIN_SERVICE \\
    -a $KEYCHAIN_ACCOUNT \\
    -w '<token>'

The heartbeat reads it at runtime and never writes it to disk.
EOF
    exit 1
  fi
}

write_plist() {
  local node_bin="$1"
  mkdir -p "$HOME/Library/LaunchAgents" "$LOG_DIR"
  /usr/bin/python3 - "$PLIST_PATH" "$node_bin" "$HEARTBEAT_SCRIPT" "$LOG_PATH" \
    "$INTERVAL_SECONDS" "$THRESHOLD_MINUTES" <<'PY'
import plistlib
import sys

(
    plist_path,
    node_bin,
    heartbeat_script,
    log_path,
    interval_seconds,
    threshold_minutes,
) = sys.argv[1:]

data = {
    "Label": "com.aioffer.radar.collector-heartbeat",
    "ProgramArguments": [
        node_bin,
        heartbeat_script,
        "--threshold-minutes",
        str(threshold_minutes),
    ],
    "EnvironmentVariables": {
        "PATH": "/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin",
        "HEARTBEAT_THRESHOLD_MINUTES": str(threshold_minutes),
    },
    "StartInterval": int(interval_seconds),
    "RunAtLoad": True,
    "ProcessType": "Background",
    "ThrottleInterval": 60,
    "StandardOutPath": log_path,
    "StandardErrorPath": log_path,
}
with open(plist_path, "wb") as handle:
    plistlib.dump(data, handle, sort_keys=True)
PY
  chmod 644 "$PLIST_PATH"
  plutil -lint "$PLIST_PATH" >/dev/null
}

bootout_if_loaded() {
  launchctl bootout "gui/$UID_VALUE/$LABEL" >/dev/null 2>&1 || true
}

load_agent() {
  if ! launchctl bootstrap "gui/$UID_VALUE" "$PLIST_PATH" >/dev/null 2>&1; then
    # Older macOS releases use load -w instead of bootstrap.
    launchctl load -w "$PLIST_PATH"
  fi
  launchctl enable "gui/$UID_VALUE/$LABEL" >/dev/null 2>&1 || true
  launchctl kickstart -k "gui/$UID_VALUE/$LABEL" >/dev/null 2>&1 || true
}

install_agent() {
  require_macos
  local node_bin
  node_bin="$(find_node)"
  check_token
  write_plist "$node_bin"
  bootout_if_loaded
  load_agent
  echo "Installed $LABEL"
  echo "  plist:  $PLIST_PATH"
  echo "  script: $HEARTBEAT_SCRIPT"
  echo "  log:    $LOG_PATH"
  echo "  every ${INTERVAL_SECONDS}s; dispatch when snapshot age >= ${THRESHOLD_MINUTES}m"
  echo "  check:  launchctl print gui/$UID_VALUE/$LABEL"
}

uninstall_agent() {
  require_macos
  bootout_if_loaded
  rm -f "$PLIST_PATH"
  echo "Removed $LABEL (log left at $LOG_PATH)"
}

status_agent() {
  require_macos
  if [[ -f "$PLIST_PATH" ]]; then
    echo "plist: $PLIST_PATH"
  else
    echo "plist: missing"
  fi
  if launchctl print "gui/$UID_VALUE/$LABEL" >/dev/null 2>&1; then
    echo "launchd: loaded"
  else
    echo "launchd: not loaded"
  fi
  if [[ -f "$LOG_PATH" ]]; then
    echo "last log lines:"
    tail -n 5 "$LOG_PATH"
  fi
}

case "${1:-install}" in
  install) install_agent ;;
  uninstall) uninstall_agent ;;
  status) status_agent ;;
  --help|-h|help) usage ;;
  *)
    echo "unknown command: $1" >&2
    usage >&2
    exit 2
    ;;
esac
