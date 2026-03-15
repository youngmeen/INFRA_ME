#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[STOP] app production stop"
docker compose stop

echo "[STOP] docker compose ps"
docker compose ps

echo "[STOP] done"
