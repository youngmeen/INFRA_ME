# Operations

## 운영 원칙

- 민감값은 `.env` 또는 GitHub Actions Secrets로만 관리
- 운영 반영은 CI/CD 경유를 기본으로 사용
- 앱은 MariaDB + Redis 기준으로 동작
- startup catch-up, daily digest, Telegram 발송 상태는 로그로 확인

## 로컬/운영 기본 명령

```bash
./scripts/start-prod.sh
./scripts/status-prod.sh
./scripts/stop-prod.sh
```

개발 실행:

```bash
cp .env.example .env
./scripts/compose-check.sh
./scripts/dev-up.sh
./scripts/dev-down.sh
```

## 상태 확인

```bash
docker compose ps
docker compose logs --tail=200 app
curl http://localhost:3000/actuator/health
curl http://localhost:3000/actuator/health/readiness
curl http://localhost:3000/news/today/digest
```

## 로그 포인트

- `[CONFIG]`
- `[STARTUP]`
- `[DIGEST_LOCK]`
- `[DIGEST_GUARD]`
- `[SEND_HISTORY]`
- `[REDIS_LOCK]`

## 저장소 역할

- MariaDB
  - 뉴스 및 발송 이력 영속 저장
  - Flyway migration 적용
- Redis
  - digest 락
  - startup catch-up 락
  - 임시 상태/중복 실행 제어

## 환경변수

- DB
  - `DB_HOST`
  - `DB_PORT`
  - `DB_NAME`
  - `DB_USER`
  - `DB_PASSWORD`
- Redis
  - `REDIS_HOST`
  - `REDIS_PORT`
  - `REDIS_PASSWORD`
- 기타
  - `SPRING_PROFILES_ACTIVE`
  - `TELEGRAM_BOT_TOKEN`
  - `TELEGRAM_CHAT_ID`
  - `OPENAI_API_KEY`
