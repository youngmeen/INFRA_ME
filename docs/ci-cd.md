# CI/CD

## Workflow 구성

- `ci.yml`
  - trigger: `pull_request`, `push`
  - 역할: `./mvnw test`
- `image.yml`
  - trigger: `push` on `main`, `workflow_dispatch`
  - 역할: 테스트 후 GHCR 이미지 빌드/푸시
- `deploy.yml`
  - trigger: `workflow_dispatch`
  - 역할: Kubernetes 배포

## 흐름

1. `main` push
2. `CI Test` 성공
3. `Build And Push Image` 성공
4. `Deploy To Kubernetes` 수동 실행

## 이미지 태그 정책

- `ghcr.io/youngmeen/infra_me:sha-<commit>`
- `ghcr.io/youngmeen/infra_me:main`
- 운영 배포는 `sha-*` 태그 고정 사용 권장

## Deploy workflow 입력값

- `image_ref`
- `namespace`
- `deploy_traefik`
- `rollout_timeout`

예시:

```text
image_ref=ghcr.io/youngmeen/infra_me:sha-ea5da72
namespace=infra-me
deploy_traefik=true
rollout_timeout=300
```

## GitHub Actions Secrets

- 필수
  - `KUBE_CONFIG`
  - `DB_PASSWORD`
  - `REDIS_PASSWORD`
  - `TELEGRAM_BOT_TOKEN`
  - `TELEGRAM_CHAT_ID`
- 선택
  - `SPRING_MAIL_HOST`
  - `SPRING_MAIL_USERNAME`
  - `SPRING_MAIL_PASSWORD`
  - `APP_MAIL_FROM`
  - `OPENAI_API_KEY`

## GitHub Actions Variables

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `REDIS_HOST`
- `REDIS_PORT`
- `SPRING_MAIL_PORT`
- `SPRING_MAIL_STARTTLS_ENABLE`
- `OPENAI_MODEL`
- `APP_STARTUP_CATCHUP_ENABLED`
