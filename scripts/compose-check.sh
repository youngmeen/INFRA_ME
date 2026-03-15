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
docker compose -f docker-compose.yml config >/dev/null

log "stack compose config"
docker compose -f compose/docker-compose.yml config >/dev/null

log "stack services"
docker compose -f compose/docker-compose.yml config --services

echo
log "healthcheck lines (stack compose)"
docker compose -f compose/docker-compose.yml config | rg -n "healthcheck|test:|interval:|timeout:|retries:|start_period:" || true

log "done"
