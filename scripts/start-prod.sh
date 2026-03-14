#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[START] mail-server production start"

if [[ ! -f .env ]]; then
  echo "[ERROR] .env not found. Prepare local secrets first."
  exit 1
fi

docker compose up -d --build

echo "[START] docker compose ps"
docker compose ps

echo "[START] waiting for /health"
HEALTH_OK="false"
for _ in {1..30}; do
  if curl -fsS http://localhost:3000/health >/dev/null; then
    HEALTH_OK="true"
    break
  fi
  sleep 2
done

if [[ "$HEALTH_OK" != "true" ]]; then
  echo "[ERROR] health check failed"
  docker compose logs --tail=200 mail-server || true
  exit 1
fi

echo "[START] health OK"
curl -sS http://localhost:3000/health

echo "[START] recent logs"
docker compose logs --tail=120 mail-server | rg '\[CONFIG\]|\[DIGEST\]|\[LANG\]|\[MARKET\]|\[MACRO\]' || true

echo "[START] done"
