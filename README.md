# News Intelligence

````markdown

Spring Boot + RSS + MariaDB + Redis 기반 **Telegram 개발자 뉴스 브리핑 서버**

기술 흐름 + 시장 흐름(투자 관점)을 함께 보는 **아침 뉴스 브리핑 채널**을 목표로 운영합니다.

---

# 1. Quick Start (로컬 실행)

```bash
cd /Users/developer/Desktop/codex/projects/mail-server

cp .env.example .env

./scripts/compose-check.sh
./scripts/dev-up.sh
````

실행 확인

```bash
curl http://localhost:3000/health
```

뉴스 테스트 발송

```bash
curl -X POST http://localhost:3000/telegram/send-daily-news
```

종료

```bash
./scripts/dev-down.sh
```

---

# 2. 실운영 실행 (Mac Mini)

```bash
cd /Users/developer/Desktop/codex/projects/mail-server

./scripts/start-prod.sh
./scripts/status-prod.sh
```

운영 중지

```bash
./scripts/stop-prod.sh
```

운영 문서

```
docs/operations.md
```

⚠️ 운영 서버에서는 반드시 **system sleep 비활성화**

Sleep 발생 시 scheduler 실행이 지연될 수 있음

---

# 3. 시스템 구성

```
Spring Boot
      │
      ▼
RSS Collector
      │
      ▼
News Processor
      │
      ▼
Telegram Sender
```

사용 인프라

| 구성요소         | 역할                |
| ------------ | ----------------- |
| MariaDB      | 뉴스 데이터 및 발송 이력 저장 |
| Redis        | 캐시 / 분산락 / 중복 방지  |
| Telegram Bot | 뉴스 브리핑 전송         |

---

# 4. 환경변수

민감정보는 `.env`에서만 관리합니다.

예시

```
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD

REDIS_HOST
REDIS_PORT
REDIS_PASSWORD

TELEGRAM_BOT_TOKEN
TELEGRAM_CHAT_ID
```

⚠️ `.env` 파일은 Git에 커밋 금지

---

# 5. 뉴스 정책 관리

뉴스 정책은 Git에서 관리됩니다.

```
src/main/resources/config/news-sources.yml
src/main/resources/config/news-policy.yml
src/main/resources/config/interest-policy.yml
```

운영 정책

```
정책 수정
     ↓
commit/push
     ↓
CI/CD 실행
     ↓
자동 배포
```

운영자가 RSS를 **로컬에서 직접 수정하지 않습니다**

---

# 6. Docker Compose 실행

개발용

```bash
./scripts/dev-up.sh
```

전체 인프라 실행

```bash
docker compose -f compose/docker-compose.yml up -d --build
```

종료

```bash
./scripts/dev-down.sh
```

데이터 삭제

```bash
./scripts/dev-down.sh --volumes
```

---

# 7. Kubernetes 실행 (Docker Desktop)

Docker Desktop Kubernetes 활성화

```
Settings → Kubernetes → Enable Kubernetes
```

클러스터 확인

```bash
kubectl cluster-info
```

배포

```bash
./scripts/k8s-deploy.sh
```

삭제

```bash
./scripts/k8s-destroy.sh
```

접속

```
http://app.localtest.me:32080
```

Traefik Dashboard

```
http://localhost:32081/dashboard/
```

---

# 8. CI/CD 파이프라인

```
main push
     │
     ▼
CI (test)
     │
     ▼
Image Build (GHCR push)
     │
     ▼
Deploy workflow (manual)
     │
     ▼
Kubernetes deploy
```

Workflow 구성

```
.github/workflows/ci.yml
.github/workflows/image.yml
.github/workflows/deploy.yml
```

---

# 9. Deploy 실행

GitHub Actions → **Deploy To Kubernetes**

입력값

```
image_ref: ghcr.io/<owner>/<repo>:sha-xxxxxxx
namespace: infra-me
deploy_traefik: true
rollout_timeout: 300
```

Deploy 단계

1. kubeconfig 설정
2. namespace 생성
3. ConfigMap 적용
4. Secret 적용
5. Deployment 적용
6. rollout status 확인

---

# 10. 로그 확인

```bash
docker compose logs --tail=120 app
```

중요 로그

```
[CONFIG]
[MARKET]
[MACRO]
[INTEREST]
[DIGEST]
[STARTUP]
[SCHEDULER]
[SEND_HISTORY]
```

---

# 11. 장애 대응

### Pod Crash

```bash
kubectl -n infra-me get pods
kubectl -n infra-me logs deploy/app
```

### Image Pull 실패

```
ErrImagePull
ImagePullBackOff
```

확인

```
docker build
image tag
registry push
```

---

### rollout timeout

```bash
kubectl rollout status deployment/app
kubectl describe deploy app
kubectl get events
```

---

### cluster unreachable

확인

```bash
kubectl config current-context
kubectl cluster-info
```

---

# 12. 운영 정책

운영 서버에서 직접 실행 금지

```
docker compose up
kubectl apply
```

반드시 **CI/CD 경유 배포**

```
main push
↓
CI
↓
image build
↓
deploy workflow
```

---

# 13. 아키텍처 원칙

* 애플리케이션은 **stateless**
* 영속 데이터는 **MariaDB**
* 임시 상태는 **Redis**

중복 발송 방지

```
Redis lock
+
MariaDB unique constraint
```

---

# 14. 향후 확장

향후 Kubernetes 확장 계획

```
replicas >= 2
rolling update
readiness probe
liveness probe
```

---

# 15. 보안 정책

금지

```
.env commit
secret hardcoding
password source code 저장
```

허용

```
.env.example
GitHub secrets
Kubernetes secrets
```

---

# Legacy

`scripts/apply-news-sources.sh`는 긴급 override용 보조 도구입니다.

평상시 운영 경로

```
Git 정책 수정
↓
commit/push
↓
자동 배포
```

````

---

## commit 예시

```bash
git add README.md
git commit -m "docs: simplify README structure and improve readability"
git push
````
