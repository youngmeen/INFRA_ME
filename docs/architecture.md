# Architecture

## 개요

이 프로젝트는 개발자 뉴스 RSS를 수집하고, 정제/중복 제거 후 Telegram digest를 발송하는 Spring Boot 애플리케이션입니다.

## 주요 구성 요소

- Spring Boot application
- MariaDB
- Redis
- Telegram integration
- GitHub Actions
- Kubernetes manifests

## 저장소 역할 분리

- MariaDB
  - `news`
  - `send_history`
  - Flyway migration 기반 영속 스키마 관리
- Redis
  - digest 날짜별 락
  - startup catch-up 락
  - 임시 상태/중복 실행 제어

## 중복 방지 구조

1. Redis 락으로 1차 실행 제어
2. MariaDB `send_history(send_date, send_type)` unique 제약으로 2차 보장
3. multi-replica 환경에서도 최종 1회만 성공하도록 설계

## 실행 흐름

1. RSS 수집
2. 뉴스 정제/선정
3. digest 생성
4. Redis 락 확인
5. Telegram 발송
6. MariaDB send_history 저장

## 운영 확장 관점

- 앱은 stateless하게 유지
- 영속 데이터는 MariaDB에만 저장
- 락/임시 상태는 Redis 사용
- Kubernetes replica 확장 가능 구조를 전제로 함
