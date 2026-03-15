# mail-server (Developer News Intelligence)

Spring Boot + RSS + MariaDB + Redis 기반 Telegram 개발자 뉴스 서버입니다.
기술 흐름 + 시장 흐름(투자 관점)을 함께 보는 아침 브리핑 채널을 목표로 운영합니다.

## 실운영(맥미니) 빠른 시작

```bash
cd /Users/developer/Desktop/codex/projects/mail-server
./scripts/start-prod.sh
./scripts/status-prod.sh
```

- 운영 중지: `./scripts/stop-prod.sh`
- 운영 상세 문서: `docs/operations.md`

## 운영 원칙 (핵심)

- 민감값은 `.env`에서만 관리 (Git 금지)
  - Telegram token
  - DB password
  - Redis password
  - OpenAI API key
- 뉴스 정책은 Git 관리 파일에서 관리
  - `src/main/resources/config/news-sources.yml`
  - `src/main/resources/config/news-policy.yml`
  - `src/main/resources/config/interest-policy.yml`
- 운영자가 RSS를 로컬에서 수동 편집하지 않음
  - 정책 수정 -> commit/push(main) -> 자동 배포
- 맥미니 실운영 시 **system sleep 비활성화 필수**
  - sleep/wake가 발생하면 09:00 스케줄 실행이 09:18처럼 지연될 수 있음
  - display off는 가능, system sleep은 금지
- 운영 환경 반영은 **CI/CD 파이프라인 경유만 허용**
  - 운영 서버에서 수동 `docker compose up` / 수동 `kubectl apply` 직접 반영 금지
  - 운영 이미지는 테스트를 통과한 CI 결과물만 사용

## CI/CD 파이프라인

- `.github/workflows/ci.yml`
  - 트리거: `pull_request`, `push`
  - 단계: checkout -> JDK setup -> `./mvnw test`
- `.github/workflows/image.yml`
  - 트리거: `main` push, `workflow_dispatch`
  - 단계: checkout -> JDK setup -> `./mvnw test` -> docker buildx -> GHCR push
- `.github/workflows/deploy.yml`
  - 트리거: `workflow_dispatch` (수동 승인형)
  - `image_ref`, `namespace`, `deploy_traefik`, `rollout_timeout` 입력값으로 Kubernetes 반영
  - `kubectl apply` + `set image` + `rollout status` 검증 수행

### 이미지 태그 정책

- `latest` 단독 태그는 사용하지 않음
- 기본 태그:
  - `ghcr.io/<owner>/<repo>:sha-<7+chars>`
  - `ghcr.io/<owner>/<repo>:main` (기본 브랜치)
  - 태그 릴리즈 시 `ghcr.io/<owner>/<repo>:<git-tag>`
- 운영 배포 시에는 `sha-*` 또는 명시 버전 태그를 고정 사용

### GitHub Actions Secret/Variable

- 기본 사용:
  - `GITHUB_TOKEN` (GHCR push 권한, workflow permissions에서 `packages: write`)
- Kubernetes deploy 필수 Secret:
  - `KUBE_CONFIG`
  - `DB_PASSWORD`
  - `REDIS_PASSWORD`
  - `TELEGRAM_BOT_TOKEN`
  - `TELEGRAM_CHAT_ID`
- Kubernetes deploy 선택 Secret:
  - `SPRING_MAIL_HOST`
  - `SPRING_MAIL_USERNAME`
  - `SPRING_MAIL_PASSWORD`
  - `APP_MAIL_FROM`
  - `OPENAI_API_KEY`
- Kubernetes deploy 권장 Variable:
  - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`
  - `REDIS_HOST`, `REDIS_PORT`
  - `SPRING_MAIL_PORT`, `SPRING_MAIL_STARTTLS_ENABLE`
  - `OPENAI_MODEL`, `APP_STARTUP_CATCHUP_ENABLED`
- 선택(타 레지스트리 전환 시):
  - `REGISTRY_USERNAME`
  - `REGISTRY_PASSWORD`
  - `IMAGE_NAME`
- 향후 Kubernetes 확장 예약:
  - `KUBE_CONFIG`
  - `K8S_NAMESPACE`
  - `K8S_DEPLOYMENT`

## 자동 배포 경로

`main push` -> `ci.yml`(test) -> `image.yml`(GHCR push)  
`workflow_dispatch(deploy)` -> `deploy.yml`(Kubernetes 반영)

`deploy.yml` 수행 단계:

1. `KUBE_CONFIG` 기반 클러스터 연결
2. namespace 생성/확인
3. `ConfigMap(app-config)` + `Secret(app-secrets)` 반영
4. app/ingress(+선택 traefik) 매니페스트 반영
5. 입력된 이미지(`image_ref`)를 Deployment에 주입
6. `rollout status`로 성공/실패 확인

## Deploy Workflow 실행 예시

Actions -> `Deploy To Kubernetes` -> `Run workflow`

- `image_ref`: `ghcr.io/<owner>/<repo>:sha-<commit>`
- `namespace`: `infra-me`
- `deploy_traefik`: `true|false`
- `rollout_timeout`: `300`

## 설정 반영 확인

```bash
curl http://localhost:3000/health
curl http://localhost:3000/news/today/digest
docker compose logs --tail=120 app | rg '\[CONFIG\]'
```

`[CONFIG]` 로그 확인 항목:

- 카테고리별 RSS source count
- `MARKET/MACRO` RSS source count
- `DIGEST total / topNews`
- `HACKER_NEWS enabled / dailyMax`
- `KOREA_IT lowQualityKeywords` count

선정 품질 로그:

- `[MARKET] selected/dropped`
- `[MACRO] selected/dropped`
- `[LANG] selected ko/en`
- `[INTEREST] matched/selected/keywords`
- `[DIGEST] topNews/total/categories`
- `[DIGEST] interestItems/total`
- `[STARTUP]` missed-send catch-up 상태
- `[SCHEDULER]` 일일 발송/중복 skip 상태
- `[SEND_HISTORY]` DAILY_NEWS 저장 결과(SUCCESS/FAIL/EMPTY)

## 로컬 실행

```bash
cp .env.example .env
./scripts/compose-check.sh
./scripts/ci-local-check.sh
./scripts/dev-up.sh
./scripts/dev-down.sh
./scripts/dev-up.sh
curl -X POST http://localhost:3000/telegram/send-daily-news
```

필수 런타임:
- MariaDB: 영속 저장소 (`news`, `send_history`)
- Redis: 캐시/중복 제어/분산 락 (`mail-server:digest:lock:*`, `mail-server:startup-catchup-lock`)

역할 구분:
- MariaDB: 뉴스/발송 이력 영속 저장, `send_history(send_date, send_type)` unique 제약으로 최종 중복 방지
- Redis: `daily digest`, `startup catch-up` 실행 전 분산 락과 임시 sent 상태 캐시

주요 환경변수:
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `SPRING_PROFILES_ACTIVE` (`local` 또는 `docker`)

초기 스키마 반영:
- Flyway가 앱 시작 시 `src/main/resources/db/migration`을 자동 반영
- SQLite 파일 경로는 더 이상 사용하지 않음
- 확인: `docker compose logs --tail=200 app | rg -n 'Flyway|migration|schema'`

## Compose + Kubernetes 병행 운영

역할 분리:
- 루트 `docker-compose.yml`: 기본 개발 실행(`app + mariadb + redis`)
- `compose/docker-compose.yml`: 확장 인프라 실행(`app + mariadb + redis + traefik + portainer`)
- `k8s/*.yaml`: Kubernetes 로컬 검증용 매니페스트(`infra-me` namespace)

### Docker Compose 사용

```bash
# 설정 유효성 확인
./scripts/compose-check.sh

# 기본 개발 실행
./scripts/dev-up.sh

# 통합 스택(app/traefik/portainer)
docker compose -f compose/docker-compose.yml up -d --build

# 기본 개발 종료(볼륨 유지)
./scripts/dev-down.sh

# 데이터까지 삭제
./scripts/dev-down.sh --volumes
```

로컬 개발 순서:
1. MariaDB와 Redis 기동
2. `.env` 또는 셸 환경변수에 DB/Redis 접속 정보 설정
3. `./mvnw spring-boot:run` 또는 `docker compose up -d --build`
4. `curl http://localhost:3000/health`로 기동 확인

Redis 실제 사용 지점:
- `DailyDigestExecutionService` 시작 시 날짜별 digest 락 획득
- `StartupDailyDigestService` 경유 startup catch-up 실행 전 전역 락 획득
- 락 획득 실패 시 중복 실행을 건너뛰고 로그만 남김

### Kubernetes 사용 (Docker Desktop 기준)

1. Docker Desktop 실행
2. `Settings > Kubernetes > Enable Kubernetes` 활성화
3. 상태가 Running이 될 때까지 대기
4. `kubectl config current-context`가 유효한지 확인

`kubectl` 미설치 시:

```bash
brew install kubectl
```

클러스터 점검:

```bash
./scripts/k8s-check.sh
```

배포/삭제:

```bash
./scripts/k8s-deploy.sh
./scripts/k8s-destroy.sh
```

운영 환경 반영:
- 운영 서버에서 직접 `kubectl apply`를 실행하지 않고, 반드시 `deploy.yml(workflow_dispatch)`를 사용한다.
- 로컬 `k8s-deploy.sh`는 개발/검증 전용이다.

접근 확인(기본):
- App: `http://app.localtest.me:32080`
- Traefik Dashboard: `http://localhost:32081/dashboard/`

설명:
- Docker Desktop Kubernetes 로컬 환경에서 `LoadBalancer` 외부 IP 할당이 불안정할 수 있어, Traefik Service는 `NodePort`(`32080/32081`)로 고정했다.
- App Service는 클러스터 내부 통신용 `ClusterIP`를 유지하고, 외부 접근은 Traefik Ingress로 라우팅한다.

### 대체 옵션: kind (간단 가이드)

Docker Desktop Kubernetes를 사용하지 않을 때:

```bash
brew install kind kubectl
kind create cluster --name infra-me
./scripts/k8s-deploy.sh
```

종료:

```bash
kind delete cluster --name infra-me
```

### 스크립트 역할

- `scripts/docker-clean.sh`: dangling/unused image, stopped container, unused volume, build cache 정리
- `scripts/compose-check.sh`: Docker/Compose 설치·데몬 상태, compose config, 서비스/healthcheck 점검
- `scripts/ci-local-check.sh`: CI 최소 흐름 로컬 재현(`./mvnw test` + `docker build`)
- `scripts/dev-up.sh`: `.env` 확인 후 `app+mariadb+redis` 기동 및 health 요약
- `scripts/dev-down.sh`: 기본 down(볼륨 유지), `--volumes` 옵션으로 데이터까지 정리
- `scripts/k8s-check.sh`: kubectl/cluster-info/namespace/pod/svc/ingress 상태 점검
- `scripts/k8s-deploy.sh`: namespace -> rbac -> workload -> ingress 순서 배포 및 rollout 확인
- `scripts/k8s-destroy.sh`: 리소스 및 namespace 정리

## 장애 대응 체크리스트

### 1) deploy workflow 미실행/권한 오류

- Actions 탭에서 `Deploy To Kubernetes` workflow 실행 이력 확인
- 저장소 Actions 권한(`Read and write permissions`) 확인
- workflow 입력값(`image_ref`, `namespace`) 오타 확인

### 2) workflow 실패

- Actions job 로그에서 실패 스텝 확인
- `Set kubeconfig from secret` 실패 시 `KUBE_CONFIG` 형식(base64/raw) 확인
- `Apply app secrets` 실패 시 필수 Secret 누락(`DB_PASSWORD`, `REDIS_PASSWORD`, Telegram) 확인
- `Wait rollout` 실패 시 `kubectl describe deploy app`, `kubectl get events` 확인

### 3) docker compose 실패

- `docker compose ps`
- `docker compose logs --tail=200 app`
- 포트/이미지/디스크 용량 충돌 확인

### 4) .env 누락/오류

- 로컬 개발에서는 `.env` 확인
- 운영 Kubernetes 배포에서는 GitHub Secrets/Variables 설정 확인

### 5) 운영 복구 원칙

- 운영 반영은 `workflow_dispatch(deploy.yml)`로만 수행
- 운영 서버에서 직접 `docker compose up`/`kubectl apply` 실행 금지
- 긴급 상황에서도 먼저 workflow 재실행 후 원인(Secret/namespace/image) 확인

### 6) kubectl not found

- `brew install kubectl`
- Docker Desktop Kubernetes 활성화 후 `./scripts/k8s-check.sh`

### 7) cluster unreachable

- Docker Desktop 실행 여부 확인
- Docker Desktop Kubernetes 활성화 여부 확인
- `kubectl config current-context` / `kubectl cluster-info` 재확인

### 8) pod crashloop

- `kubectl -n infra-me get pods`
- `kubectl -n infra-me logs deploy/app --tail=200`
- `kubectl -n infra-me describe pod <pod-name>`

### 9) image pull 실패 (ErrImagePull/ImagePullBackOff)

- 로컬 이미지 빌드: `docker build -t infra-me-app:latest .`
- 재배포: `./scripts/k8s-deploy.sh`
- 필요 시 imagePullPolicy 및 이미지 태그 일치 확인

### 10) ingress 미노출

- `kubectl -n infra-me get ingress`
- `kubectl -n infra-me get svc traefik`
- `http://app.localtest.me:32080`로 접근 테스트

### 11) rollout timeout

- `kubectl -n <namespace> rollout status deployment/app --timeout=300s`
- `kubectl -n <namespace> describe deploy app`
- `kubectl -n <namespace> get events --sort-by=.metadata.creationTimestamp | tail -n 100`

### 12) namespace 오류

- workflow 입력 `namespace` 값과 실제 리소스 대상 일치 확인
- `kubectl get ns`
- `kubectl -n <namespace> get all`

## 보안 정책

- `.env`, `.env.news.local` 등 로컬 환경 파일은 Git 추적 금지
- `.env.example`, `.env.news.example`만 템플릿으로 추적
- 민감값 하드코딩/커밋 금지

## 저장소 전환 메모

- 앱은 로컬 SQLite 파일 없이 동작하며 replica 간 공유 가능한 MariaDB/Redis만 사용한다.
- MariaDB는 영속 데이터 저장 전용이다. 현재 `news`, `send_history`를 관리한다.
- Redis는 임시 상태, digest 중복 방지, startup catch-up 분산 락 용도다.
- 일일 발송 중복 방지는 Redis 락과 MariaDB unique 제약을 함께 사용한다.
- 이후 Kubernetes 다중 replica 확장을 위해 애플리케이션은 stateless하게 유지한다.

## 향후 확장 메모

- 현재 문서는 로컬/개발 인프라 기준이다.
- Jenkins는 이번 범위에서 도입하지 않는다.
- 앱은 stateless 전제를 유지하고, MariaDB(영속)/Redis(락·임시 상태) 분리 구조를 유지한다.
- Kubernetes 전환 시 `replicas >= 2`, rolling update, readiness/liveness healthcheck를 기본 전제로 한다.
- 운영 반영 이미지는 CI 테스트를 통과해 registry에 업로드된 결과물만 사용한다.

## Legacy

- `scripts/apply-news-sources.sh`는 긴급 override용 보조 도구입니다.
- 평상시 운영 경로는 Git 정책 수정 + 자동 배포입니다.
