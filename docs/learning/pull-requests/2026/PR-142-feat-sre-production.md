---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 142
status: merged
taskId: OPS-OBS-001A
author: guseoh
base: main
head: ops/sre-OPS-OBS-001A
mergedAt: 2026-08-14T04:55:06Z
mergeCommit: aeb2d40b83fa6abe9bcec02064b4d4ad1e09e0df
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #142 feat(sre): Production 관측성 저장소 기반

## 작업 목적

## 작업  - 작업 ID: OPS-OBS-001A - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: Production runtime 계약을 보존한 Observability repository preparation correction입니다. - 변경 범위: runtime permission/startup validation, metrics-proxy activation·release contract, cached metric refresh state, scheduler isolation, harness task ID consumers, isolated observability validator, Runbook입니다. - 제외 범위: AWS/Production/Secret/DB 실행과 merge입니다.  ## 검증  - 실행 결과: Repository Validation #1028 전체 통과. Backend + M…

## 주요 변경

기록 없음

## 변경 파일

- .github/scripts/collect-discord-context.py
- .github/scripts/record-merged-pr.py
- .github/workflows/validate-conventions.yml
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SchedulingConfiguration.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/V2SubscriptionMetrics.java
- backend/src/main/resources/application.properties
- backend/src/test/java/com/pawcycle/backend/foundation/ObservabilityIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionMetricsTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/v2/V2SubscriptionServiceIntegrationTests.java
- docs/adr/ARCH-012-production-observability-boundary.md
- docs/runbook/OPS-OBS-001-production-observability.md
- infra/production-observability/compose.yaml
- infra/production-observability/grafana/dashboards/pawcycle-operations.json
- infra/production-observability/grafana/dashboards/production-overview.json
- infra/production-observability/grafana/dashboards/runtime.json
- infra/production-observability/grafana/provisioning/dashboards/pawcycle.yaml
- infra/production-observability/grafana/provisioning/datasources/prometheus.yaml
- infra/production-observability/prometheus/prometheus.yml.tpl
- infra/production-observability/validate-observability.sh
- infra/production/compose.yaml
- 외 7개

## 리뷰 결과

- COMMENTED: 3

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

https://github.com/guseoh/pawcycle-commerce/pull/142
