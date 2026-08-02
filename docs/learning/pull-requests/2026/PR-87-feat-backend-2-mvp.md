---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 87
status: merged
taskId: API-005
author: guseoh
base: main
head: feat/be/API-005
mergedAt: 2026-08-02T10:43:34Z
mergeCommit: ba8ffe4c2ef73098847016d608eb98ad70e05678
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #87 feat(backend): 2차 MVP 구독 생명주기 구현

## 작업 목적

## 작업  - 작업 ID: `API-005` - 작업 등급: `고위험` - 실행 구분: `저장소 변경` - 역할: Backend Engineer  ## 목적과 범위  - 목적: API-004·DATA-003·ARCH-007 구현 기준에 따라 2차 MVP V2 구독 생명주기, additive schema와 legacy migration 경계를 구현한다. - 변경 범위: `backend/**`의 V3 migration, Pet·Plan·Subscription V2 API, snapshot·pending·Schedule reconciliation, 멱등성, `ETag`·`If-Match`, legacy migration 준비와 관련 테스트 - 제외 범위: Frontend·workflow·validator·승인 계약 문서 변경, 실제 Production migration, AWS·운영 DB·Secret 실행  ## 결정과 영향  - Jackson 3의 주입된 `ObjectMapper`를…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapService.java
- backend/src/main/java/com/pawcycle/backend/subscription/domain/Subscription.java
- backend/src/main/java/com/pawcycle/backend/subscription/infra/SubscriptionRepository.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/LegacyMvp2MigrationService.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2ApiException.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2ScheduleReconciliationTrigger.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SchedulingConfiguration.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionController.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionService.java
- backend/src/main/resources/db/migration/V3__add_second_mvp_subscription_schema.sql
- backend/src/test/java/com/pawcycle/backend/foundation/DatabaseFoundationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/infra/SubscriptionDatabaseIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionCommandIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionIdempotencyConcurrencyIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionServiceIntegrationTests.java

## 리뷰 결과

- COMMENTED: 30

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

https://github.com/guseoh/pawcycle-commerce/pull/87
