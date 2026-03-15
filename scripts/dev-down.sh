#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() { echo "[DEV-DOWN] $*"; }

if [[ "${1:-}" == "--volumes" ]]; then
  log "stopping services and removing volumes"
  docker compose down --volumes --remove-orphans
else
  log "stopping services (volumes kept)"
  docker compose down --remove-orphans
fi

log "done"
