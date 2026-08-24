---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 209
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH10-004
mergedAt: 2026-08-24T04:00:03Z
mergeCommit: 4347f0d2bf9ac85e2ef6bffc243d5365d84814ad
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #209 perf(harness): Subscription Burst 10k decision baseline 준비

## 작업 목적

## 작업  - 작업 ID: PERF-PH10-004 / Issue #208 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: 기존 5,000건 first-result를 재실행하지 않고 Subscription Burst 10,000건 decision first-result용 repository harness 준비 - 변경 범위: 전용 workload identity·Compose project/volume·authoritative marker/evidence state, ApprovedSourceSha binding, 15분 raw decision 판정, durable redacted evidence promotion, synthetic fail-close validation과 계약 문서 - 제외 범위: 실제 performance workload·Docker runtime capability 실행, 기존 worklo…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/subscription/v2/performance/SubscriptionBurstMeasurementService.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/performance/SubscriptionBurstMeasurementMarkerContractTests.java
- docs/performance/PERF-PH10-004-subscription-burst-10k-decision-baseline.md
- infra/local-integration/compose.phase10-subscription-burst-decision-10k.yaml
- infra/performance/phase10/run-subscription-burst-decision-10k.ps1

## 리뷰 결과

- COMMENTED: 9

## CI 및 검증

- Discord collaboration report: in_progress
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

https://github.com/guseoh/pawcycle-commerce/pull/209
