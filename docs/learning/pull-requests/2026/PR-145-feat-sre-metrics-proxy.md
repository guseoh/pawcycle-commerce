---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 145
status: merged
taskId: OPS-OBS-001D
author: guseoh
base: main
head: ops/sre-OPS-OBS-001D
mergedAt: 2026-08-14T07:39:56Z
mergeCommit: 837179888d371f79d71e24749ec89b06d3446c1e
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #145 feat(sre): metrics-proxy 실행 경계 분리

## 작업 목적

## 작업  - 작업 ID: OPS-OBS-001D - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Platform/SRE - Related: #144  ## 목적과 범위  - 목적: metrics-proxy lifecycle을 Application release Compose와 완전히 분리합니다. - 변경 범위: standalone `infra/production-metrics-proxy` Compose·external-network lifecycle validator, Application Compose/release lifecycle ownership 제거, Production contract/workflow/Runbook/ADR 갱신입니다. - 제외 범위: AWS, Production EC2/DB, Secret, SG, 실제 container activation, merge입니다.  ## 결정과 영향  - standalone project는 metrics-pr…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/validate-conventions.yml
- docs/adr/ARCH-012-production-observability-boundary.md
- docs/runbook/OPS-OBS-001-production-observability.md
- infra/production-metrics-proxy/compose.yaml
- infra/production-metrics-proxy/metrics-proxy.conf
- infra/production-metrics-proxy/test-metrics-proxy.sh
- infra/production/compose.yaml
- infra/production/release-common.sh
- infra/production/test-production-compose.sh
- infra/production/validate-production-contracts.py
- scripts/classify-validation-changes.py
- scripts/test_validate_conventions_workflow.py

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

https://github.com/guseoh/pawcycle-commerce/pull/145
