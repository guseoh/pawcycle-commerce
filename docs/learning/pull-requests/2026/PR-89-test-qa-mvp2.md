---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 89
status: merged
taskId: OPS-031
author: guseoh
base: main
head: ops/tl/OPS-031
mergedAt: 2026-08-03T10:55:14Z
mergeCommit: 33c5cf4da7efff90838706026f311d0ca3973302
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #89 test(qa): MVP2 로컬 통합 검증 기반 보강

## 작업 목적

## 작업  - 작업 ID: OPS-031 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Tech Lead + QA  ## 목적과 범위  - 목적: PR #86·#87로 병합된 MVP2를 기존 로컬 통합 환경에서 실제 검증할 수 있도록 기능 gate와 판매 Plan fixture를 준비하고, 확인된 HTTP·DTO·멱등 replay 계약을 회귀 테스트로 고정한다. - 변경 범위: local-integration Compose, local QA bootstrap, V2 command header 경계, snapshot replay 정규화, Schedule 타입과 관련 Backend·Frontend 테스트 - 제외 범위: Production·AWS·운영 DB·Secret 실행, 실제 legacy migration, scheduler 운영 cadence 결정, DATA-003 legacy 열 nullable 전략, aggregate composite FK 설계  ## 결…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfiguration.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaMvp2FixtureService.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionController.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionService.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfigurationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaMvp2FixtureServiceIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionCommandIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionControllerTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionServiceIntegrationTests.java
- frontend/src/lib/v2-api.test.mts
- frontend/src/lib/v2-api.ts
- infra/local-integration/compose.yaml

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

https://github.com/guseoh/pawcycle-commerce/pull/89
