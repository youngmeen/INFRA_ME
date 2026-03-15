# Mail Server

Developer News Intelligence

Spring Boot + RSS + MariaDB + Redis 기반 Telegram 개발자 뉴스 서버입니다.
개발자 뉴스, 시장 흐름, startup catch-up, daily digest 발송을 운영 환경에서 안정적으로 처리하는 것을 목표로 합니다.

## Quick Start

```bash
./scripts/dev-up.sh
```

## 로컬 실행

```bash
cp .env.example .env
./scripts/compose-check.sh
./scripts/ci-local-check.sh
./scripts/dev-up.sh
curl http://localhost:3000/actuator/health
```

종료:

```bash
./scripts/dev-down.sh
```

## Kubernetes 실행 링크

- [docs/kubernetes.md](docs/kubernetes.md)

## 문서 링크

- Architecture: [docs/architecture.md](docs/architecture.md)
- Operations: [docs/operations.md](docs/operations.md)
- Kubernetes: [docs/kubernetes.md](docs/kubernetes.md)
- CI/CD: [docs/ci-cd.md](docs/ci-cd.md)
- Troubleshooting: [docs/troubleshooting.md](docs/troubleshooting.md)
- Security: [SECURITY.md](SECURITY.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Support: [SUPPORT.md](SUPPORT.md)
