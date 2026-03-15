#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

NS="infra-me"
APP_IMAGE="${APP_IMAGE:-infra-me-app:latest}"

log() { echo "[K8S-DEPLOY] $*"; }
fail() { echo "[K8S-DEPLOY][ERROR] $*"; exit 1; }

command -v kubectl >/dev/null 2>&1 || fail "kubectl not found. macOS: brew install kubectl"

CTX="$(kubectl config current-context 2>/dev/null || true)"
[[ -n "$CTX" ]] || fail "kubectl context not set. Enable Docker Desktop Kubernetes first."
log "context=$CTX"

kubectl cluster-info >/dev/null 2>&1 || fail "cluster unreachable. Check Docker Desktop Kubernetes status."

if ! docker image inspect "$APP_IMAGE" >/dev/null 2>&1; then
  log "$APP_IMAGE not found. building local image"
  command -v docker >/dev/null 2>&1 || fail "docker not found; cannot build app image"
  docker build -t "$APP_IMAGE" . || fail "docker build failed"
fi

log "apply namespace"
kubectl apply -f k8s/namespace.yaml

log "apply app configmap"
kubectl apply -f k8s/app-configmap.yaml

log "apply app secrets (local defaults; override with env vars)"
kubectl -n "$NS" create secret generic app-secrets \
  --from-literal=DB_PASSWORD="${DB_PASSWORD:-app1234}" \
  --from-literal=REDIS_PASSWORD="${REDIS_PASSWORD:-redis1234}" \
  --from-literal=SPRING_MAIL_HOST="${SPRING_MAIL_HOST:-}" \
  --from-literal=SPRING_MAIL_USERNAME="${SPRING_MAIL_USERNAME:-}" \
  --from-literal=SPRING_MAIL_PASSWORD="${SPRING_MAIL_PASSWORD:-}" \
  --from-literal=APP_MAIL_FROM="${APP_MAIL_FROM:-}" \
  --from-literal=TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}" \
  --from-literal=TELEGRAM_CHAT_ID="${TELEGRAM_CHAT_ID:-}" \
  --from-literal=OPENAI_API_KEY="${OPENAI_API_KEY:-}" \
  --dry-run=client -o yaml | kubectl apply -f -

log "apply ingress class + rbac"
kubectl apply -f k8s/traefik-ingressclass.yaml
kubectl apply -f k8s/traefik-rbac.yaml

log "apply workloads/services/ingress"
kubectl apply -f k8s/app-deployment.yaml
kubectl apply -f k8s/app-service.yaml
kubectl apply -f k8s/traefik-deployment.yaml
kubectl apply -f k8s/traefik-service.yaml
kubectl apply -f k8s/app-ingress.yaml

log "set app image=$APP_IMAGE"
kubectl -n "$NS" set image deployment/app app="$APP_IMAGE"

log "wait rollout"
kubectl -n "$NS" rollout status deployment/app --timeout=180s || fail "app rollout failed"
kubectl -n "$NS" rollout status deployment/traefik --timeout=180s || fail "traefik rollout failed"

log "resources"
kubectl -n "$NS" get pods -o wide
kubectl -n "$NS" get svc
kubectl -n "$NS" get ingress

echo
log "access"
log "app via traefik ingress: http://app.localtest.me:32080"
log "traefik dashboard: http://localhost:32081/dashboard/"
