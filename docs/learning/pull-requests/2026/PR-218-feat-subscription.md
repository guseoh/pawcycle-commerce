---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 218
status: merged
taskId: DOMAIN-004
author: guseoh
base: main
head: feat/be/MVP4-SUB-BE-001
mergedAt: 2026-08-24T10:24:30Z
mergeCommit: f4106585dd1e284099727787d4d56b54ff7f80bc
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #218 feat(subscription): 정기배송 직접 관리 기능 구현

## 작업 목적

## 작업  - 작업 ID: MVP4-SUB-BE-001 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: 회원이 다음 정기배송 날짜와 배송 주기를 직접 관리하고, 결합 pending 변경을 안전하게 다음 주문에 적용할 수 있도록 Backend 계약과 구현을 확장 - 변경 범위: `RESCHEDULE_NEXT`, `CHANGE_DELIVERY_CYCLE`, CHANGE_PLAN과 cycle 결합 pending, 자동 주문 적용 주기, 구독 상세 사용자 projection, 관련 통합 테스트와 DOMAIN-004/API-008 - 제외 범위: DB table/column/Flyway, 의존성, Queue/Kafka/Redis, 별도 retry API, command history payload, frontend/infra, Production 실행  ## 결정과 영향  - 기존 `pending_plan_cha…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/subscription/v2/SubscriptionOrderAutomationService.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionApplicationSupport.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionCommandApplicationService.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionData.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionJdbcStore.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionQueryApplicationService.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/SubscriptionOrderAutomationServiceIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionCommandIntegrationTests.java
- docs/api/API-008-mvp4-subscription-self-service-api-contract.md
- docs/domain/DOMAIN-004-mvp4-subscription-self-service-domain.md

## 리뷰 결과

- COMMENTED: 5

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

https://github.com/guseoh/pawcycle-commerce/pull/218
