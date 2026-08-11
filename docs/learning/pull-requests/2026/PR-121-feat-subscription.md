---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 121
status: merged
taskId: SUB-AUTO-001
author: guseoh
base: main
head: feat/be-SUB-AUTO-001
mergedAt: 2026-08-11T02:55:32Z
mergeCommit: 8b8a460d2ae2be8d37ebe32f2b40930846931250
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #121 feat(subscription): 정기배송 주문 자동화

## 작업 목적

## 작업  - 작업 ID: `SUB-AUTO-001` - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: 예정일이 도래한 ACTIVE 구독 회차를 Schedule당 한 건의 CREATED 정기 Order로 원자적으로 처리하고, 다음 미래 회차까지 안전하게 이어집니다. - 변경 범위: 최소 정기 Order 영속성·자동화 Scheduler·독립 transaction·중복 방지·실패 재시도·reconciliation 책임 분리·관측성·승인 계약 문서·runbook입니다. - 제외 범위: PG/Payment, 재고, 배송, 일반 Order API/UI, Redis/Kafka, Production Scheduler 활성화, Production DB migration·배포입니다.  ## 결정과 영향  - Schedule은 예정 회차, Order는 실제 처리 결과이며 DB unique constraint로 Schedul…

## 주요 변경

기록 없음

## 변경 파일

- .github/scripts/collect-discord-context.py
- .github/scripts/record-merged-pr.py
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapService.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/SubscriptionOrderAutomationMetrics.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/SubscriptionOrderAutomationService.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/SubscriptionOrderAutomationTrigger.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2ScheduleReconciliationTrigger.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionService.java
- backend/src/main/resources/db/migration/V10__create_subscription_order_items.sql
- backend/src/main/resources/db/migration/V11__index_due_subscription_schedules.sql
- backend/src/main/resources/db/migration/V9__create_subscription_orders.sql
- backend/src/test/java/com/pawcycle/backend/foundation/DatabaseFoundationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/ObservabilityIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/V9SubscriptionOrderMigrationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/SubscriptionOrderAutomationServiceIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionCommandIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionReconciliationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionServiceIntegrationTests.java
- docs/adr/ARCH-008-subscription-order-automation-consistency.md
- docs/data/DATA-004-subscription-order-automation-data-design.md
- 외 10개

## 리뷰 결과

- COMMENTED: 2

## CI 및 검증

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

https://github.com/guseoh/pawcycle-commerce/pull/121
