#!/usr/bin/env bash
set -euo pipefail

echo "[CLEAN] starting docker cleanup"

docker container prune -f
docker image prune -f
docker image prune -a -f
docker volume prune -f
docker builder prune -f

echo "[CLEAN] docker cleanup completed"
