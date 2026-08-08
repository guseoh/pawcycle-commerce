---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 108
status: merged
taskId: API-004
author: guseoh
base: main
head: feat/be-OPS-IDEMP-001
mergedAt: 2026-08-08T23:36:51Z
mergeCommit: b8d074fed407cc6bb06ae4a5bac0e5ca64220776
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #108 feat(be): 멱등 결과 30일 보관 및 정리 경계 추가

## 작업 목적

## 작업  - 작업 ID: `OPS-IDEMP-001` - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: 성공 idempotency 결과를 최초 완료부터 30일 보관하고 bounded cleanup 경계를 추가 - 변경 범위: V4→V8 `completed_at`·backfill·cleanup index 분할, rollback-era 성공 row bounded repair, 신규 완료 시각 저장, 별도 cleanup service, 회귀·migration 테스트, API-004·DATA-003·Backend 보고서 - 제외 범위: Scheduler, 운영 batch size, Micrometer/Actuator, retry/backoff, alert, Production DB·Cloud·AWS·실제 cleanup  ## 결정과 영향  - 중요한 결정: reservation은 `completed_at=NUL…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2IdempotencyCleanupService.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionService.java
- backend/src/main/resources/db/migration/V4__add_creation_idempotency_completed_at.sql
- backend/src/main/resources/db/migration/V5__add_command_idempotency_completed_at.sql
- backend/src/main/resources/db/migration/V6__backfill_idempotency_completed_at.sql
- backend/src/main/resources/db/migration/V7__index_creation_idempotency_completed_at.sql
- backend/src/main/resources/db/migration/V8__index_command_idempotency_completed_at.sql
- backend/src/test/java/com/pawcycle/backend/foundation/DatabaseFoundationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/V4IdempotencyRetentionMigrationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2IdempotencyCleanupConcurrencyIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionCommandIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionServiceIntegrationTests.java
- docs/api/API-004-second-mvp-api-contract.md
- docs/data/DATA-003-second-mvp-subscription-data-design.md
- docs/reports/OPS-IDEMP-001/be-report.md

## 리뷰 결과

- COMMENTED: 3

## CI 및 검증

- Discord collaboration report: in_progress
- publish: in_progress

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/108
