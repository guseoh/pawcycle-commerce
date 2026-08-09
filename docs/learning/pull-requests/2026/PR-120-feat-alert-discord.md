---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 120
status: merged
taskId: INC-BASE-001
author: guseoh
base: main
head: ops/sre-OPS-ALERT-002
mergedAt: 2026-08-09T14:08:51Z
mergeCommit: bd283ccee773a7e5519cce3385a711d00db5bb4d
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #120 feat(alert): 로컬 Discord 알림 기준

## 작업 목적

## 작업  - 작업 ID: OPS-ALERT-002 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: local Prometheus alert를 Alertmanager의 Discord receiver로 전달하고 local 수신 기준선을 마련한다. - 변경 범위: local Alertmanager, Prometheus Alertmanager 연결, `webhook_url_file` runtime secret mount, local Runbook의 수동 확인 절차. - 제외 범위: Production/Cloud/AWS, Production threshold·grouping·repeat policy, 외부 채널 확장, 제품 코드·API·DB schema·Scheduler cadence.  ## 결정과 영향  - Discord webhook은 Git tracked가 아닌 runtime secret file로만 제공하며 A…

## 주요 변경

기록 없음

## 변경 파일

- docs/runbook/INC-BASE-001-local-incident-response.md
- docs/runbook/OBS-BASE-001-local-observability.md
- infra/local-integration/.env.example
- infra/local-integration/compose.observability.yaml
- infra/local-integration/observability/alertmanager/alertmanager.yml
- infra/local-integration/observability/prometheus/prometheus.yml

## 리뷰 결과

- COMMENTED: 3

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

https://github.com/guseoh/pawcycle-commerce/pull/120
