---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 42
status: merged
taskId: API-003
author: guseoh
base: main
head: feat/be
mergedAt: 2026-07-14T04:55:40Z
mergeCommit: 63e1956e7ccd3b6cdd3518c55b5244ab0599160f
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #42 feat(subscription): 구독 생성과 조회 API 구현

## 작업 목적

## 작업 정보  - 작업 ID: `SUBSCRIPTION-001` - 역할: Backend Engineer - 작업 브랜치: `feat/be` - 대상 브랜치: `main`  ## 관련 이슈  - Closes # - Related #  ## 목적  API-003에서 승인된 구독 생성·내 목록·내 상세 API 3개와 `subscriptions` migration을 하나의 Backend 수직 기능으로 구현합니다.  ## 변경 사항  - `POST /api/subscriptions`, `GET /api/subscriptions`, `GET /api/subscriptions/{subscriptionId}` 구현 - session principal 소유권, POST CSRF, 서울 날짜 계산과 동일 조건 복수 생성 적용 - Subscription Entity·Repository·transactional service와 Entity 비노출 read model 추가 - Flyway V2의 회…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/subscription/api/AllowedDeliveryCycle.java
- backend/src/main/java/com/pawcycle/backend/subscription/api/DeliveryCycleValidator.java
- backend/src/main/java/com/pawcycle/backend/subscription/api/StrictIntegralJsonDeserializers.java
- backend/src/main/java/com/pawcycle/backend/subscription/api/SubscriptionController.java
- backend/src/main/java/com/pawcycle/backend/subscription/api/SubscriptionCreateRequest.java
- backend/src/main/java/com/pawcycle/backend/subscription/api/SubscriptionExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SkuNotFoundException.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SkuNotSubscribableException.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SubscriptionApplicationService.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SubscriptionClockConfiguration.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SubscriptionCreateFailedException.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SubscriptionCreateResult.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SubscriptionDetailUnavailableException.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SubscriptionDetailView.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SubscriptionListUnavailableException.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SubscriptionListView.java
- backend/src/main/java/com/pawcycle/backend/subscription/application/SubscriptionNotFoundException.java
- backend/src/main/java/com/pawcycle/backend/subscription/domain/Subscription.java
- backend/src/main/java/com/pawcycle/backend/subscription/infra/SubscriptionRepository.java
- backend/src/main/resources/db/migration/V2__create_subscriptions.sql
- 외 8개

## 리뷰 결과

- COMMENTED: 10

## CI 및 검증

기록 없음

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/42
