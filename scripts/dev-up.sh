#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() { echo "[DEV-UP] $*"; }
fail() { echo "[DEV-UP][ERROR] $*"; exit 1; }

if [[ ! -f .env ]]; then
  fail ".env not found. create from .env.example first"
fi

command -v docker >/dev/null 2>&1 || fail "docker command not found"
docker info >/dev/null 2>&1 || fail "docker daemon unreachable. start Docker Desktop"
docker compose version >/dev/null 2>&1 || fail "docker compose plugin unavailable"

log "starting app + mariadb + redis"
docker compose up -d --build --remove-orphans

log "service status"
docker compose ps app mariadb redis

log "waiting for app health"
APP_OK="false"
for _ in {1..30}; do
  if curl -fsS http://localhost:3000/health >/dev/null; then
    APP_OK="true"
    break
  fi
  sleep 2
done

if [[ "$APP_OK" != "true" ]]; then
  log "app health not ready yet; showing logs"
  docker compose logs --tail=120 app mariadb redis || true
  fail "health check timeout"
fi

log "health ok"
curl -sS http://localhost:3000/health

echo
log "check points"
log "app health:        http://localhost:3000/health"
log "mariadb port:      localhost:${MARIADB_PORT:-13306}"
log "redis port:        localhost:${REDIS_EXTERNAL_PORT:-16379}"
log "flyway logs:       docker compose logs --tail=200 app | rg -n 'Flyway|migration|schema'"
