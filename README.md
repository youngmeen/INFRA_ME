# mail-server (Developer News Intelligence)

Spring Boot + RSS + SQLite 기반 Telegram 개발자 뉴스 서버입니다.
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

## 자동 배포 경로

`main` push -> GitHub Actions(`.github/workflows/deploy.yml`) -> self-hosted runner -> `scripts/deploy.sh`

`deploy.sh` 수행 단계:

1. `main` 최신 pull
2. `./mvnw clean package -DskipTests`
3. `docker compose up -d --build`
4. `/health` 재시도 체크
5. `[CONFIG]` 로그 출력

## Self-hosted Runner 등록 체크

1. GitHub 저장소 `Settings > Actions > Runners`에서 Linux runner 토큰 발급
2. runner 머신에서 등록
   `./config.sh --url <repo-url> --token <runner-token> --labels self-hosted,linux`
3. 서비스 등록/자동시작
   `sudo ./svc.sh install && sudo ./svc.sh start`
4. Docker 권한 확인
   `docker ps` / `docker compose version` 이 runner 계정에서 실행 가능해야 함
5. runner online 확인
   `Settings > Actions > Runners`에 `Idle` 또는 `Active` 상태로 표시되어야 함

## 설정 반영 확인

```bash
curl http://localhost:3000/health
curl http://localhost:3000/news/today/digest
docker compose logs --tail=120 mail-server | rg '\[CONFIG\]'
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

## 로컬 실행

```bash
./mvnw clean package -DskipTests
docker compose up -d --build
curl -X POST http://localhost:3000/telegram/send-daily-news
```

## 장애 대응 체크리스트

### 1) runner offline

- GitHub Actions Runner 페이지에서 상태 확인
- runner 머신에서 서비스 상태 확인 (`runsvc.sh` 또는 system service)
- runner 재시작 후 workflow 재실행

### 2) workflow 실패

- Actions job 로그에서 실패 스텝 확인
- `Ensure runtime secrets file exists` 실패 시 `.env` 존재 확인
- `Ensure docker access` 실패 시 runner 계정 docker 권한 확인

### 3) docker compose 실패

- `docker compose ps`
- `docker compose logs --tail=200 mail-server`
- 포트/이미지/디스크 용량 충돌 확인

### 4) .env 누락/오류

- `.env`가 runner 작업 디렉토리에 존재하는지 확인
- TELEGRAM/DB/OPENAI 등 민감값 유효성 확인

### 5) 수동 복구 최소 명령

```bash
cd /path/to/repo/projects/mail-server
git checkout main && git pull --ff-only origin main
./mvnw clean package -DskipTests
docker compose up -d --build
curl -fsS http://localhost:3000/health
```

## 보안 정책

- `.env`, `.env.news.local` 등 로컬 환경 파일은 Git 추적 금지
- `.env.example`, `.env.news.example`만 템플릿으로 추적
- 민감값 하드코딩/커밋 금지

## Legacy

- `scripts/apply-news-sources.sh`는 긴급 override용 보조 도구입니다.
- 평상시 운영 경로는 Git 정책 수정 + 자동 배포입니다.
