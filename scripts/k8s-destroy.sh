#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

NS="infra-me"

log() { echo "[K8S-DESTROY] $*"; }
fail() { echo "[K8S-DESTROY][ERROR] $*"; exit 1; }

command -v kubectl >/dev/null 2>&1 || fail "kubectl not found. macOS: brew install kubectl"

if ! kubectl get ns "$NS" >/dev/null 2>&1; then
  log "namespace $NS not found. nothing to delete"
  exit 0
fi

log "delete ingress/workloads/services/rbac"
kubectl delete -f k8s/app-ingress.yaml --ignore-not-found=true
kubectl delete -f k8s/traefik-service.yaml --ignore-not-found=true
kubectl delete -f k8s/traefik-deployment.yaml --ignore-not-found=true
kubectl delete -f k8s/app-service.yaml --ignore-not-found=true
kubectl delete -f k8s/app-deployment.yaml --ignore-not-found=true
kubectl delete -f k8s/traefik-rbac.yaml --ignore-not-found=true
kubectl delete -f k8s/traefik-ingressclass.yaml --ignore-not-found=true

log "delete namespace"
kubectl delete -f k8s/namespace.yaml --ignore-not-found=true

log "remaining namespaces"
kubectl get ns | rg "$NS" || true
