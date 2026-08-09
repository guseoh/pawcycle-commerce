---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 112
status: merged
taskId: OBS-BASE-001
author: guseoh
base: main
head: feat/be-OBS-BASE-001
mergedAt: 2026-08-09T02:35:03Z
mergeCommit: 8f1f442964efe0a1d32ad928baa62c9e742f4532
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #112 feat(backend): 관측성 기준선 구축

## 작업 목적

## 작업  - 작업 ID: `OBS-BASE-001` - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: Backend HTTP/JVM/CPU/JDBC-Hikari와 구독 reconciliation·idempotency cleanup 흐름의 Prometheus metric 기준선 구축 - 변경 범위: 승인된 Actuator·Micrometer Prometheus registry, 기본/custom metric, `health`·`prometheus` endpoint 접근 경계, cardinality 회귀, Phase B handoff - 제외 범위: Prometheus·Grafana 인프라, Scheduler 정책, 운영 batch size, alert, Production·Cloud·AWS 실행  ## 결정과 영향  - `health`, `prometheus`만 Actuator read-only endpoi…

## 주요 변경

기록 없음

## 변경 파일

- backend/build.gradle
- backend/src/main/java/com/pawcycle/backend/common/security/SecurityConfig.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2IdempotencyCleanupService.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionMetrics.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionService.java
- backend/src/main/resources/application.properties
- backend/src/test/java/com/pawcycle/backend/foundation/ObservabilityIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionMetricsTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionReconciliationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionServiceIntegrationTests.java
- docs/handoffs/OBS-BASE-001/backend-to-sre.md

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

https://github.com/guseoh/pawcycle-commerce/pull/112
