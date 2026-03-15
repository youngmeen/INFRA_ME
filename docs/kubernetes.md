# Kubernetes

## 개요

Kubernetes 배포는 `deploy.yml` workflow 또는 로컬 검증용 스크립트로 수행합니다.

## 매니페스트

- `k8s/app-deployment.yaml`
- `k8s/app-service.yaml`
- `k8s/app-ingress.yaml`
- `k8s/traefik-*.yaml`

## 배포 경로

### 1. GitHub Actions 경로

- Actions -> `Deploy To Kubernetes`
- `workflow_dispatch` 실행

입력 예시:

```text
image_ref=ghcr.io/youngmeen/infra_me:sha-ea5da72
namespace=infra-me
deploy_traefik=true
rollout_timeout=300
```

### 2. 로컬 검증 경로

```bash
./scripts/k8s-check.sh
./scripts/k8s-deploy.sh
./scripts/k8s-destroy.sh
```

## 확인 명령

```bash
kubectl -n infra-me get deploy
kubectl -n infra-me get pods
kubectl -n infra-me get svc
kubectl -n infra-me get ingress
kubectl -n infra-me rollout status deployment/app
kubectl -n infra-me describe pod <pod-name>
```

## 접근 확인

```bash
curl http://app.localtest.me:32080/actuator/health
curl http://app.localtest.me:32080/actuator/health/readiness
curl http://app.localtest.me:32080/actuator/health/liveness
```

## 주의사항

- 운영 반영은 `deploy.yml` 경유를 기본으로 사용
- 로컬 `kubectl apply`는 개발/검증 전용
- `KUBE_CONFIG`와 Actions secrets가 없으면 deploy workflow는 시작 단계에서 실패함
