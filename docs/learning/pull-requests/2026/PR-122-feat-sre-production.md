---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 122
status: merged
taskId: SUB-AUTO-002
author: guseoh
base: main
head: ops/sre-SUB-AUTO-002
mergedAt: 2026-08-11T06:34:43Z
mergeCommit: 630c2cadb49e664854e3055f26fa590deeaa884f
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #122 feat(sre): 정기배송 자동화 Production 활성화 계약

## 작업 목적

## 작업  - 작업 ID: SUB-AUTO-002 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: SUB-AUTO-001을 Production에서 Scheduler OFF로 배포하고 별도 preflight와 명시 입력으로만 활성화·중단할 수 있는 저장소 계약 준비 - 변경 범위: Production runtime env 검증, deploy·rollback migration boundary, read-only aggregate preflight, activation/deactivation control, Runbook·고위험 보고서, lifecycle/validator 회귀 - 제외 범위: Backend 제품 코드와 V9~V11 SQL, 새 dependency·infra, alert threshold·Discord 정책, Production·AWS·운영 DB·Secret·restore 실행, 자동 병합  ## 결…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/validate-conventions.yml
- docs/reports/SUB-AUTO-002/sre-report.md
- docs/runbook/README.md
- docs/runbook/SUB-AUTO-002-production-subscription-automation.md
- infra/production/compose.yaml
- infra/production/create-production-auth-smoke-member.sh
- infra/production/deploy.sh
- infra/production/materialize-ssm-env.sh
- infra/production/production-db-restore.sh
- infra/production/release-common.sh
- infra/production/rollback.sh
- infra/production/subscription-automation-control.sh
- infra/production/subscription-automation-preflight.sh
- infra/production/test-create-production-auth-smoke-member.py
- infra/production/test-production-compose.sh
- infra/production/test-production-scripts.sh
- infra/production/test-rollback-control-compatibility.sh
- infra/production/validate-production-contracts.py

## 리뷰 결과

- COMMENTED: 24

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

https://github.com/guseoh/pawcycle-commerce/pull/122
