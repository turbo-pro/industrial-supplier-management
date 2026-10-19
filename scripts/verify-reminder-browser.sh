#!/usr/bin/env bash
set -euo pipefail
# Starts only its own browser session; backend and Vite are caller-owned.
if [[ $# != 2 ]]; then echo "Usage: bash scripts/verify-reminder-browser.sh CONTROL_DIR http://127.0.0.1:FRONTEND_PORT"; exit 2; fi
command -v npx >/dev/null
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTROL="$(cd "$1" && pwd)"
FRONTEND="$2"
[[ "$FRONTEND" =~ ^http://127\.0\.0\.1:[0-9]+/?$ ]] || { echo "Only loopback frontend URLs are allowed"; exit 2; }
[[ -f "$CONTROL/ready" && -f "$CONTROL/scenario" && "$(<"$CONTROL/scenario")" == failure-recovery ]] || { echo "An active recovery fixture is required"; exit 2; }
[[ ! -e "$CONTROL/finish" && ! -e "$CONTROL/abort" && ! -e "$CONTROL/restore" ]] || { echo "Use a fresh fixture"; exit 2; }
cd "$ROOT"
SESSION="ism-reminder-recovery-$$"
passed=false
cli() { npx --yes --package @playwright/cli@0.1.22 playwright-cli -s="$SESSION" "$@"; }
cleanup() {
  if [[ "$passed" != true ]]; then touch "$CONTROL/abort"; fi
  cli close >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p output/playwright
cli open "$FRONTEND" --browser chrome
cli snapshot
cli run-code --filename scripts/browser/reminder-failure.js
touch "$CONTROL/restore"
for _ in {1..120}; do
  [[ -f "$CONTROL/restored" ]] && break
  sleep 0.25
done
[[ -f "$CONTROL/restored" ]] || { echo "Fixture failed to restore recipients"; exit 1; }
cli snapshot
cli run-code --filename scripts/browser/reminder-recover.js
touch "$CONTROL/finish"
passed=true
echo "Browser checks passed; backend fixture must still finish its database assertions."
