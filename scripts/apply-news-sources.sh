#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXAMPLE_FILE="$ROOT_DIR/.env.news.example"
LOCAL_FILE="$ROOT_DIR/.env.news.local"

echo "[INFO] Legacy helper: 기본 운영 경로는 Git 정책 파일입니다."
echo "[INFO] 수정 대상: src/main/resources/config/news-sources.yml, news-policy.yml"

if [[ ! -f "$EXAMPLE_FILE" ]]; then
  echo "[ERROR] Missing $EXAMPLE_FILE"
  exit 1
fi

if [[ ! -f "$LOCAL_FILE" ]]; then
  cp "$EXAMPLE_FILE" "$LOCAL_FILE"
  echo "[INFO] Created emergency override file: $LOCAL_FILE"
else
  echo "[INFO] Existing emergency override file found: $LOCAL_FILE"
fi

echo "[WARN] .env.news.local은 긴급 상황에서만 사용하세요."
echo "[NEXT] 표준 운영은 Git commit/push -> 자동 배포입니다."
