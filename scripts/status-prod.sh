#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[STATUS] docker compose ps"
docker compose ps

echo "[STATUS] health"
if curl -fsS http://localhost:3000/health; then
  echo
else
  echo "[WARN] health check failed"
fi

echo "[STATUS] recent app logs"
docker compose logs --tail=120 mail-server || true

echo "[STATUS] key signal logs"
docker compose logs --tail=200 mail-server | rg '\[CONFIG\]|\[DIGEST\]|\[LANG\]|\[MARKET\]|\[MACRO\]|\[SCHEDULER\]' || true
