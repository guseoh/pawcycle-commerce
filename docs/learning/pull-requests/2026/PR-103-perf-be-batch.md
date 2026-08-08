---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 103
status: merged
taskId: PERF-001
author: guseoh
base: main
head: feat/be
mergedAt: 2026-08-08T08:01:51Z
mergeCommit: 83da2d80c09a7ff8af59c166b54d62c2f112be99
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #103 perf(be): 목록 batch 조회 최적화

## 작업 목적

## 작업  - 작업 ID: OPS-PERF-001 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend Engineer - 연계 Issue: `#88`  ## 목적과 범위  - 목적: MVP2 `plans()`·`subscriptions()` 목록 조회의 측정된 N+1을 API 계약 유지 범위에서 제거한다. - 변경 범위: 현재 page related row를 JdbcTemplate batch 조회로 조립하고, V2 회귀 테스트와 기존 측정 문서의 Before/After 비교를 추가한다.  ## 결정과 영향  - `plans()`는 page PlanVersion ID의 item·delivery cycle을 batch 조회한다. - `subscriptions()`는 page Subscription의 Pet·Snapshot·SnapshotItem·다음 Schedule을 batch 조회한다. - API 응답 구조·정렬·pagination·소유권/인가·DB sche…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionService.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionServiceIntegrationTests.java
- docs/performance/OPS-PERF-001-local-query-measurement.md

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

https://github.com/guseoh/pawcycle-commerce/pull/103
