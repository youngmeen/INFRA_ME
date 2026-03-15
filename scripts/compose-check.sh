#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() { echo "[COMPOSE-CHECK] $*"; }
fail() { echo "[COMPOSE-CHECK][ERROR] $*"; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker command not found"

docker info >/dev/null 2>&1 || fail "docker daemon unreachable. start Docker Desktop"

if ! docker compose version >/dev/null 2>&1; then
  fail "docker compose plugin unavailable"
fi

log "root compose config"
ROOT_CFG="$(docker compose -f docker-compose.yml config)"

log "stack compose config"
STACK_CFG="$(docker compose -f compose/docker-compose.yml config)"

log "root services"
docker compose -f docker-compose.yml config --services

log "stack services"
docker compose -f compose/docker-compose.yml config --services

echo
log "root env key check (app/mariadb/redis)"
for key in DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD REDIS_HOST REDIS_PORT REDIS_PASSWORD; do
  if echo "$ROOT_CFG" | rg -q "^\s+${key}:"; then
    echo "  - ${key}: OK"
  else
    echo "  - ${key}: MISSING"
  fi
done

if echo "$ROOT_CFG" | rg -q "service_healthy"; then
  echo "  - depends_on health condition: OK"
else
  echo "  - depends_on health condition: MISSING"
fi

echo
log "healthcheck lines (root compose)"
echo "$ROOT_CFG" | rg -n "healthcheck|test:|interval:|timeout:|retries:|start_period:" || true

echo
log "healthcheck lines (stack compose)"
echo "$STACK_CFG" | rg -n "healthcheck|test:|interval:|timeout:|retries:|start_period:" || true

log "done"
