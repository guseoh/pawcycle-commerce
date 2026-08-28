---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 247
status: merged
taskId: API-012
author: guseoh
base: main
head: fix/be/MVP4-FINAL-002
mergedAt: 2026-08-28T21:29:28Z
mergeCommit: c0097542c82e811e490aaa67a97e7cff1b6b3e6b
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #247 fix(backend): SCHEDULED Add-on 액션 노출 보정

## 작업 목적

작업 ID: MVP4-FINAL-002 작업 등급: 고위험 실행 구분: 저장소 변경 역할: Backend Engineer  ## 목적과 범위  - 목적: SCHEDULED Subscription의 Add-on command availability projection을 실제 command 계약과 일치시킨다. - 변경 범위: Subscription detail availableActions, focused tests, API-012. DB·infra·Production은 제외한다.  ## 검증  - 실행 결과: `V2SubscriptionQueryApplicationServiceTests` 통과, `build -x test` 성공, `git diff --check` 통과. Commit: `3b9deeb69812907d55e27d4257d777ea949548b1`. - 미실행 이유: Spring `V2SubscriptionCommandIntegrationTests`와 Add-on com…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionQueryApplicationService.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionCommandIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionQueryApplicationServiceTests.java
- docs/api/API-012-mvp4-final-product-backend-api.md

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

https://github.com/guseoh/pawcycle-commerce/pull/247
