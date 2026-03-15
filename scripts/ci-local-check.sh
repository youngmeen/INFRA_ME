#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() { echo "[CI-LOCAL] $*"; }
fail() { echo "[CI-LOCAL][ERROR] $*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker command not found"
command -v java >/dev/null 2>&1 || fail "java command not found"

if [[ ! -x ./mvnw ]]; then
  fail "./mvnw not found or not executable"
fi

log "running mvn test"
SPRING_MAIL_HOST="${SPRING_MAIL_HOST:-localhost}" \
SPRING_MAIL_USERNAME="${SPRING_MAIL_USERNAME:-test}" \
SPRING_MAIL_PASSWORD="${SPRING_MAIL_PASSWORD:-test}" \
APP_MAIL_FROM="${APP_MAIL_FROM:-CI <ci@example.com>}" \
TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-dummy}" \
TELEGRAM_CHAT_ID="${TELEGRAM_CHAT_ID:-0}" \
OPENAI_API_KEY="${OPENAI_API_KEY:-dummy}" \
./mvnw test

log "building docker image (local verification)"
docker build -t mail-server:ci-local -f Dockerfile .

log "done"
