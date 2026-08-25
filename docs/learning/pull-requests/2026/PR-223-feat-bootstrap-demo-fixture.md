---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 223
status: merged
taskId: FOUNDATION-004
author: guseoh
base: main
head: feat/be/MVP4-DEMO-001
mergedAt: 2026-08-25T04:37:30Z
mergeCommit: e1199e5f8d4f4cf34556ee0aad4012305c591098
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #223 feat(bootstrap): 로컬 Demo fixture 추가

## 작업 목적

## 작업  - 작업 ID: MVP4-DEMO-001 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend Engineer - 기준: 최신 main  ## 목적과 범위  - 목적: local-integration에서 실제 상품 탐색·추천·Plan·Checkout 주문 생성 경계를 검증할 수 있는 Demo dataset을 제공한다. - 변경 범위: local-only bootstrap service, 기존 bootstrap runner 연결, 중복·충돌·profile 보호 테스트, 상품 목록 cache commit 후 무효화. - 제외 범위: FOUNDATION-004 fixture, Frontend, infra, DB migration, API 계약, Recommendation/Checkout business logic, Toss/AI/Production 데이터.  ## 결정과 영향  - 중요한 결정: JDBC로 별도 business key fixture를 생…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalCommerceDemoFixtureService.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfiguration.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalCommerceDemoFixtureServiceIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfigurationTests.java

## 리뷰 결과

기록 없음

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

https://github.com/guseoh/pawcycle-commerce/pull/223
