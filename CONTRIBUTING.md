# Contributing Guide

## PR 규칙

- 기능/수정 단위로 작은 PR을 권장합니다.
- 변경 목적, 영향 범위, 검증 방법을 PR 본문에 포함합니다.
- 운영 설정 변경은 관련 문서(`docs/operations.md`, `docs/kubernetes.md`, `docs/ci-cd.md`)를 함께 업데이트합니다.

## Commit Convention

- 기본 형식:
  - `feat:`
  - `fix:`
  - `docs:`
  - `refactor:`
  - `test:`
  - `chore:`
- 예시:
  - `fix: startup lock scope for catch-up flow`
  - `docs: split README into multi-doc structure for GitHub tabs`

## CI Policy

- PR과 push는 `ci.yml`을 통과해야 합니다.
- `main` 반영 후 `image.yml`이 정상적으로 이미지 빌드를 완료해야 합니다.
- Kubernetes 배포는 `deploy.yml`의 `workflow_dispatch`로만 진행합니다.
- 운영 변경은 테스트와 문서 갱신 없이 merge하지 않습니다.
