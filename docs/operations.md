# Operations Guide (Mac mini)

## 1) 운영 원칙

- 민감값은 로컬 `.env`만 사용한다. Git에 커밋하지 않는다.
- 뉴스 정책(`news-sources.yml`, `news-policy.yml`)은 Git에서 관리한다.
- 운영 기본 경로는 Git 반영 + 자동 배포(또는 수동 배포)다.

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

`docker-compose.yml`의 `mail-server`는 `restart: unless-stopped`를 사용한다.

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

## 5) 운영 확인 명령

```bash
docker compose ps
docker compose logs --tail=100 mail-server
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
docker compose logs --tail=200 mail-server | rg '\[CONFIG\]|\[DIGEST\]|\[LANG\]|\[MARKET\]|\[MACRO\]|\[SCHEDULER\]'
```

## 7) Self-hosted Runner 연계

자동 배포 경로:
`main push -> GitHub Actions -> self-hosted runner -> scripts/deploy.sh`

확인 항목:
1. Runner 상태가 `online/idle` 인지
2. runner 계정에서 `docker ps` 가능한지
3. runner 작업 경로에 `.env`가 존재하는지

runner 미사용/장애 시 수동 배포:

```bash
git checkout main
git pull --ff-only origin main
./mvnw clean package -DskipTests
docker compose up -d --build
curl -fsS http://localhost:3000/health
```

## 8) 장애 대응 체크리스트

1. `/health` 실패
- `docker compose logs --tail=200 mail-server`
- 포트/환경변수/DB 경로 확인

2. Telegram 발송 실패
- `.env`의 `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` 확인
- 앱 로그에서 Telegram API 오류 메시지 확인

3. digest 품질 저하
- `news-policy.yml`, `news-sources.yml` 최신 반영 여부
- `[CONFIG]`, `[DIGEST]`, `[LANG]` 로그 동시 확인
