---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 118
status: merged
taskId: INC-BASE-001
author: guseoh
base: main
head: ops/sre-OPS-ALERT-001
mergedAt: 2026-08-09T11:19:48Z
mergeCommit: 1fc8836bfb4323ba0f3713a331b6467a7fe923a9
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #118 feat(obs): 로컬 Prometheus 알림 기준

## 작업 목적

## 작업  - 작업 ID: OPS-ALERT-001 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: local-integration Prometheus에서 Backend scrape unavailable과 reconciliation failure를 알림 상태로 확인한다. - 변경 범위: Prometheus rule load·local rule, disposable incident 검증, INC-BASE-001 Runbook의 Alerts 확인 위치. - 제외 범위: Alertmanager·외부 알림 채널, Production·Cloud·AWS, Backend 제품 코드·API·DB schema·Scheduler cadence.  ## 결정과 영향  - 중요 결정: Backend alert는 `up{job="pawcycle-backend"} == 0`에 local 30초 `for`를 적용하고, reconciliat…

## 주요 변경

기록 없음

## 변경 파일

- docs/runbook/INC-BASE-001-local-incident-response.md
- infra/local-integration/compose.incident.yaml
- infra/local-integration/compose.observability.yaml
- infra/local-integration/incident/verify-inc-base-001.ps1
- infra/local-integration/observability/prometheus/prometheus.yml
- infra/local-integration/observability/prometheus/rules/pawcycle-local-alerts.yml

## 리뷰 결과

- COMMENTED: 1

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

https://github.com/guseoh/pawcycle-commerce/pull/118
