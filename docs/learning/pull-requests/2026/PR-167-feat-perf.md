---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 167
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH8-001
mergedAt: 2026-08-21T14:37:16Z
mergeCommit: 568631b47b50f62853416410a3934846446239f8
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #167 feat(perf): 로컬 성능 기준선 및 처리 용량 측정 하네스 구축

## 작업 목적

## 작업  - 작업 ID: PERF-PH8-001 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: Phase 8 Local 성능 기준선과 처리 용량 한계 탐색 하네스를 구축하고 이후 Phase 9 Before/After 비교 증거를 제공한다. - 변경 범위: public product 3개 cohort의 k6 baseline/capacity, measurement-window 처리량 계산, local Grafana HTTP 관측, local MySQL statement digest read-only 비교 절차, Runbook과 validator를 포함한다. - 제외 범위: Production/AWS/RDS 실행, Application·DB schema 변경, SQL·Index·Hikari·JVM·Redis·Kafka 튜닝, authenticated/write capacity 측정은 포함하지 않는다.  ## 결정과…

## 주요 변경

기록 없음

## 변경 파일

- docs/runbook/PERF-PH8-001-local-k6-baseline.md
- infra/local-integration/incident/verify-inc-base-001.ps1
- infra/local-integration/observability/grafana/dashboards/pawcycle-observability.json
- infra/performance/k6/api-product-detail.js
- infra/performance/k6/api-products.js
- infra/performance/k6/capacity-api-product-detail.js
- infra/performance/k6/capacity-api-products.js
- infra/performance/k6/capacity-products-page.js
- infra/performance/k6/lib/baseline.js
- infra/performance/k6/lib/capacity.js
- infra/performance/k6/products-page.js
- infra/performance/k6/run-baseline.sh
- infra/performance/k6/run-capacity.sh
- infra/performance/k6/validate-harness.ps1
- scripts/test_validate_task_artifacts.py
- scripts/validate-task-artifacts.py

## 리뷰 결과

- COMMENTED: 1

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

https://github.com/guseoh/pawcycle-commerce/pull/167
