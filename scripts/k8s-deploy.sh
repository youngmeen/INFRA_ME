#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

NS="infra-me"

log() { echo "[K8S-DEPLOY] $*"; }
fail() { echo "[K8S-DEPLOY][ERROR] $*"; exit 1; }

command -v kubectl >/dev/null 2>&1 || fail "kubectl not found. macOS: brew install kubectl"

CTX="$(kubectl config current-context 2>/dev/null || true)"
[[ -n "$CTX" ]] || fail "kubectl context not set. Enable Docker Desktop Kubernetes first."
log "context=$CTX"

kubectl cluster-info >/dev/null 2>&1 || fail "cluster unreachable. Check Docker Desktop Kubernetes status."

if ! docker image inspect infra-me-app:latest >/dev/null 2>&1; then
  log "infra-me-app:latest not found. building local image"
  command -v docker >/dev/null 2>&1 || fail "docker not found; cannot build app image"
  docker build -t infra-me-app:latest . || fail "docker build failed"
fi

log "apply namespace"
kubectl apply -f k8s/namespace.yaml

log "apply ingress class + rbac"
kubectl apply -f k8s/traefik-ingressclass.yaml
kubectl apply -f k8s/traefik-rbac.yaml

log "apply workloads/services/ingress"
kubectl apply -f k8s/app-deployment.yaml
kubectl apply -f k8s/app-service.yaml
kubectl apply -f k8s/traefik-deployment.yaml
kubectl apply -f k8s/traefik-service.yaml
kubectl apply -f k8s/app-ingress.yaml

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
