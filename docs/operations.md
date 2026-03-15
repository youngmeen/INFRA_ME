# Operations Guide (Mac mini)

## 1) 운영 원칙

- 민감값은 로컬 `.env`만 사용한다. Git에 커밋하지 않는다.
- 뉴스 정책(`news-sources.yml`, `news-policy.yml`)은 Git에서 관리한다.
- 운영 기본 경로는 Git 반영 + GitHub Actions workflow 배포다.
- 운영 반영은 CI/CD 파이프라인 경유만 허용한다.
- 운영 서버에서 수동 `docker compose up` / 수동 `kubectl apply` 직접 반영은 금지한다.
- `docker compose`는 로컬 개발/검증 전용, 운영 반영은 Kubernetes workflow 전용이다.
- 맥미니는 **system sleep을 금지**한다. (display sleep만 허용)
- sleep/wake 발생 시 09:00 scheduler 정시 실행은 보장되지 않는다.

## 2) 시작/중지/상태 확인

프로젝트 루트: `/Users/developer/Desktop/codex/projects/mail-server`

```bash
./scripts/start-prod.sh
./scripts/status-prod.sh
./scripts/stop-prod.sh
```

각 스크립트 역할:
- `start-prod.sh`: 빌드/기동 + `docker compose ps` + `/health` 확인 + 핵심 로그 출력
- `status-prod.sh`: 현재 상태 + `/health` + 최근 로그 + 핵심 태그 로그 확인
- `stop-prod.sh`: 안전 정지(`docker compose stop`)

## 3) Docker 재시작 정책

`docker-compose.yml`의 `app`/`mariadb`/`redis`는 `restart: unless-stopped`를 사용한다.

- 프로세스/도커 데몬 재시작 시 자동 복구됨
- 운영자가 명시적으로 `docker compose stop` 한 경우는 자동 재시작되지 않음

## 4) 맥미니 재부팅 후 복구

권장:
1. Docker Desktop 자동 시작 활성화
2. macOS 로그인 후 Docker Engine 정상 동작 확인
3. `restart: unless-stopped`로 컨테이너 자동 복구 확인
4. 필요 시 `./scripts/start-prod.sh` 1회 실행

보조(선택):
- 무인 복구가 필요하면 `launchd`로 Docker Desktop/운영 스크립트 자동 실행 구성

## 4-1) Sleep 방지 설정 (중요)

권장 설정:
1. `시스템 설정 > 잠금 화면`에서 디스플레이 끄기만 사용
2. `시스템 설정 > 에너지 절약(또는 배터리/전원 어댑터)`에서 자동 잠자기 비활성화
3. 운영 계정 자동 로그인 또는 고정 로그인 세션 유지

보조 명령(임시):

```bash
# 터미널 세션 유지 중 시스템 sleep 방지
caffeinate -dimsu
```

주의:
- `caffeinate`는 실행된 터미널 세션/프로세스가 살아있는 동안만 유효하다.
- 장기 운영 기본 해법은 OS 전원 설정에서 system sleep을 끄는 것이다.

## 5) 운영 확인 명령

```bash
docker compose ps
docker compose logs --tail=100 app
curl http://localhost:3000/health
curl http://localhost:3000/news/today/digest
curl -X POST http://localhost:3000/telegram/send-daily-news
```

## 6) 로그 확인 포인트

핵심 태그:
- `[CONFIG]`: 정책/소스 반영 여부
- `[DIGEST]`: 최종 선정 기사 수/카테고리
- `[LANG]`: 한글/영문 선별 비율
- `[MARKET]`, `[MACRO]`: 시장/거시경제 선정 상태
- `[SCHEDULER]`(존재 시): 스케줄 실행 확인

예시:

```bash
docker compose logs --tail=200 app | rg '\[CONFIG\]|\[DIGEST\]|\[LANG\]|\[MARKET\]|\[MACRO\]|\[SCHEDULER\]'
```

## 7) GitHub Actions Kubernetes Deploy

운영 배포 경로:
`main push -> ci.yml/image.yml` (검증 + GHCR push)
`workflow_dispatch(deploy.yml)` (Kubernetes 반영)

`deploy.yml` 입력값:
1. `image_ref` (권장: `ghcr.io/<owner>/<repo>:sha-xxxxxxx`)
2. `namespace` (기본: `infra-me`)
3. `deploy_traefik` (`true/false`)
4. `rollout_timeout` (초)

필수 Secrets:
1. `KUBE_CONFIG`
2. `DB_PASSWORD`
3. `REDIS_PASSWORD`
4. `TELEGRAM_BOT_TOKEN`
5. `TELEGRAM_CHAT_ID`

권장 Variables:
1. `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`
2. `REDIS_HOST`, `REDIS_PORT`
3. `SPRING_MAIL_PORT`, `SPRING_MAIL_STARTTLS_ENABLE`
4. `OPENAI_MODEL`, `APP_STARTUP_CATCHUP_ENABLED`

## 8) 장애 대응 체크리스트

1. `/health` 실패
- `docker compose logs --tail=200 app`
- 포트/환경변수/MariaDB/Redis 연결 상태 확인

1. Kubernetes rollout 실패
- Actions `Wait rollout` 단계 로그 확인
- `kubectl -n <ns> describe deploy app`
- `kubectl -n <ns> get events --sort-by=.metadata.creationTimestamp | tail -n 100`

1. image pull 실패 (`ErrImagePull`, `ImagePullBackOff`)
- `image_ref` 오타/권한 확인
- GHCR 패키지 접근 권한(공개/비공개) 확인
- 필요 시 imagePullSecret 정책 추가 검토

0. 09:00 발송 지연(예: 09:18 발송)
- `Thread starvation or clock leap detected` 로그 확인
- 호스트 sleep/wake 이력 확인
- 재발 방지를 위해 system sleep 비활성화 적용

2. Telegram 발송 실패
- `.env`의 `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` 확인
- 앱 로그에서 Telegram API 오류 메시지 확인

## 9) 저장소 구조 전환 메모

- 앱 실행에는 MariaDB와 Redis가 모두 필요하다.
- 초기 스키마는 Flyway가 자동 적용한다.
- 필수 환경변수: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- SQLite 파일 경로(`jdbc:sqlite`, `/data/*.db`)는 더 이상 사용하지 않는다.
- Redis는 digest 실행 락과 startup catch-up 락을 담당하고, 최종 중복 방지는 MariaDB `send_history` unique 제약이 보장한다.
- `mail-server:digest:lock:{date}` 와 `mail-server:startup-catchup-lock` 키가 중복 실행을 1차 차단한다.
- Redis 장애 시 digest 날짜 락은 fail-open, startup catch-up 락은 fail-closed 정책으로 동작한다.

3. digest 품질 저하
- `news-policy.yml`, `news-sources.yml` 최신 반영 여부
- `[CONFIG]`, `[DIGEST]`, `[LANG]` 로그 동시 확인
