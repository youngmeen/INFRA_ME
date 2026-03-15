# Support

## 장애 대응

- 앱 상태 확인:
```bash
docker compose ps
curl http://localhost:3000/actuator/health
curl http://localhost:3000/actuator/health/readiness
```

- 최근 로그 확인:
```bash
docker compose logs --tail=200 app
```

## 로그 확인 포인트

- `[STARTUP]`: startup catch-up 상태
- `[DIGEST_LOCK]`: digest 락 충돌/skip
- `[DIGEST_GUARD]`: DB duplicate guard
- `[SEND_HISTORY]`: 발송 결과 저장 상태
- `[REDIS_LOCK]`: Redis 락 오류

## kubectl 명령어

```bash
kubectl -n infra-me get deploy
kubectl -n infra-me get pods
kubectl -n infra-me get svc
kubectl -n infra-me get ingress
kubectl -n infra-me rollout status deployment/app
kubectl -n infra-me describe pod <pod-name>
kubectl -n infra-me get events --sort-by=.metadata.creationTimestamp | tail -n 100
```

## 추가 문서

- Operations: [docs/operations.md](docs/operations.md)
- Kubernetes: [docs/kubernetes.md](docs/kubernetes.md)
- Troubleshooting: [docs/troubleshooting.md](docs/troubleshooting.md)
