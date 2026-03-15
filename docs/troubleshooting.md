# Troubleshooting

## 1. Deploy workflow가 시작 직후 실패함

증상:
- `Validate required inputs and secrets` 실패

확인:
- GitHub Actions Secrets 누락 여부
  - `DB_PASSWORD`
  - `REDIS_PASSWORD`
  - `TELEGRAM_BOT_TOKEN`
  - `TELEGRAM_CHAT_ID`
  - `KUBE_CONFIG`

## 2. kubectl가 `EOF` 또는 `connection refused`

확인:

```bash
kubectl config current-context
kubectl cluster-info
kubectl get nodes
```

점검 포인트:
- Docker Desktop Kubernetes 실행 여부
- kubeconfig context 유효 여부
- 운영 클러스터 API endpoint 접근 가능 여부

## 3. ImagePullBackOff / ErrImagePull

확인:

```bash
kubectl -n infra-me describe pod <pod-name>
```

점검 포인트:
- `image_ref` 오타
- GHCR 권한
- 비공개 레지스트리라면 imagePullSecret 필요 여부

## 4. readiness/liveness 실패

확인:

```bash
kubectl -n infra-me describe pod <pod-name>
curl http://app.localtest.me:32080/actuator/health/readiness
curl http://app.localtest.me:32080/actuator/health/liveness
```

점검 포인트:
- DB 연결 가능 여부
- Redis 연결 가능 여부
- Flyway migration 완료 여부
- env/secret 누락 여부

## 5. startup catch-up 또는 digest 중복 실행 우려

확인 로그:
- `[STARTUP]`
- `[DIGEST_LOCK]`
- `[DIGEST_GUARD]`
- `[SEND_HISTORY]`

점검 포인트:
- Redis 락 충돌 시 `REPLICA_LOCK_CONFLICT`
- Redis 장애 시 `REDIS_UNAVAILABLE`
- DB unique 제약 차단 시 `DB_DUPLICATE_GUARD`
