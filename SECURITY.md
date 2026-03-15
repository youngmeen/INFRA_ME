# Security Policy

## Secret 관리 정책

- 민감값은 Git에 커밋하지 않습니다.
- 운영용 비밀값은 GitHub Actions Secrets 또는 로컬 `.env`로만 관리합니다.
- 예시:
  - `DB_PASSWORD`
  - `REDIS_PASSWORD`
  - `TELEGRAM_BOT_TOKEN`
  - `TELEGRAM_CHAT_ID`
  - `OPENAI_API_KEY`
  - `KUBE_CONFIG`

## env 정책

- `.env.example`에는 샘플 값만 둡니다.
- 실제 `.env` 파일은 로컬/운영 환경에서만 보관합니다.
- 환경별 설정은 `application.yml`, `application-local.yml`, `application-docker.yml`로 분리합니다.

## Credential Commit 금지

- credential, token, kubeconfig, 개인 계정 정보는 commit 금지입니다.
- `git add .` 전에 `.env`, kubeconfig, secret yaml이 포함되지 않았는지 확인합니다.
- 로그 캡처에도 비밀번호, 토큰, chat id가 노출되지 않도록 주의합니다.

## Vulnerability Report

- 보안 이슈는 공개 issue 대신 비공개 채널로 먼저 공유합니다.
- GitHub Security Advisory 또는 저장소 관리자에게 직접 전달합니다.
- 재현 절차, 영향 범위, 임시 완화책을 함께 제공하는 것을 권장합니다.
