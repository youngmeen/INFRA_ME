#!/usr/bin/env bash
set -euo pipefail

NS="infra-me"

log() { echo "[K8S-CHECK] $*"; }
warn() { echo "[K8S-CHECK][WARN] $*"; }
err() { echo "[K8S-CHECK][ERROR] $*"; }

if ! command -v kubectl >/dev/null 2>&1; then
  err "kubectl not found"
  echo "- install: brew install kubectl"
  echo "- then enable Docker Desktop Kubernetes (Settings > Kubernetes > Enable)"
  exit 1
fi

CTX="$(kubectl config current-context 2>/dev/null || true)"
if [[ -z "$CTX" ]]; then
  err "kubectl context missing"
  echo "- Docker Desktop Kubernetes may be disabled"
  echo "- check: Docker Desktop > Settings > Kubernetes"
  exit 1
fi
log "context=$CTX"

if ! kubectl cluster-info >/dev/null 2>&1; then
  err "cluster unreachable"
  echo "- ensure Docker Desktop is running"
  echo "- ensure Kubernetes is enabled in Docker Desktop"
  echo "- verify context: kubectl config get-contexts"
  exit 1
fi

log "cluster-info ok"

if kubectl get ns "$NS" >/dev/null 2>&1; then
  log "namespace=$NS exists"
  kubectl -n "$NS" get pods -o wide || true
  kubectl -n "$NS" get svc || true
  kubectl -n "$NS" get ingress || true
else
  warn "namespace=$NS not found (deploy not applied yet)"
fi

echo
log "quick summary"
kubectl get nodes -o wide
