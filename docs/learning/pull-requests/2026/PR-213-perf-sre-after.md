---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 213
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH10-006
mergedAt: 2026-08-24T06:27:32Z
mergeCommit: 17e4bdb3c88690875aa87864d0c4518ed29ab9e8
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #213 perf(sre): 구독 스케줄러 After 실험 준비

## 작업 목적

## 작업  - 작업 ID: PERF-PH10-006 / Issue #212 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: sequential 구조를 유지한 scheduler batch/cadence synthetic 10k After first-result 1회 실행 준비 - 변경 범위: measurement profile batch/fixed-delay 계약, After 전용 one-shot harness·isolated compose·durable evidence·runbook - 제외 범위: 실제 workload/Docker runtime, 과거 workload 재실행, Production scheduler·domain/API/DB·async/queue 변경  ## 결정과 영향  - 중요한 결정: cohort 10000, batch 500, fixed delay 15000ms, decision target…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/subscription/v2/performance/SubscriptionBurstMeasurementService.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/performance/SubscriptionBurstMeasurementMarkerContractTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/performance/SubscriptionBurstMeasurementServiceIntegrationTests.java
- docs/performance/PERF-PH10-006-subscription-scheduler-tuning-after-10k.md
- infra/local-integration/compose.phase10-subscription-burst-scheduler-tuning-after-10k.yaml
- infra/performance/phase10/run-subscription-burst-before.ps1
- infra/performance/phase10/run-subscription-burst-decision-10k.ps1
- infra/performance/phase10/run-subscription-burst-scheduler-tuning-after-10k.ps1

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

https://github.com/guseoh/pawcycle-commerce/pull/213
