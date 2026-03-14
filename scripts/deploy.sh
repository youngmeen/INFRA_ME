#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() {
  echo "[DEPLOY] $*"
}

fail_with_logs() {
  echo "[DEPLOY][ERROR] $*"
  echo "[DEPLOY][ERROR] docker compose ps"
  docker compose ps || true
  echo "[DEPLOY][ERROR] docker compose logs --tail=200 mail-server"
  docker compose logs --tail=200 mail-server || true
  exit 1
}

if [[ ! -f .env ]]; then
  fail_with_logs ".env not found. create runtime secrets first."
fi

log "syncing main branch"
git fetch origin main
# runner working copy 기준으로 main 최신 상태를 fast-forward 반영
if ! git checkout main; then
  fail_with_logs "failed to checkout main"
fi
if ! git pull --ff-only origin main; then
  fail_with_logs "failed to pull latest main"
fi

log "building jar"
if ! ./mvnw clean package -DskipTests; then
  fail_with_logs "maven build failed"
fi

log "rebuilding containers"
if ! docker compose up -d --build; then
  fail_with_logs "docker compose up failed"
fi

log "waiting for health"
healthy="false"
for _ in {1..30}; do
  if curl -fsS http://localhost:3000/health >/dev/null; then
    healthy="true"
    break
  fi
  sleep 2
done

if [[ "$healthy" != "true" ]]; then
  fail_with_logs "health check timeout"
fi

log "health ok"
log "effective config"
docker compose logs --tail=120 mail-server | rg "\\[CONFIG\\]" || true
log "deploy completed"
