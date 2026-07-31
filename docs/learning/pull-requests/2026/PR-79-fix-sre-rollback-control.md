---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 79
status: merged
taskId: OPS-027
author: guseoh
base: main
head: ops/sre
mergedAt: 2026-07-31T09:30:25Z
mergeCommit: b463a692b1a14cecbd467a59c8ac987b503aa434
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #79 fix(sre): rollback Control 호환성 검증 보완

## 작업 목적

## 작업 정보  - 작업 ID: OPS-027 - 작업 등급: 고위험 - 역할: Platform/SRE - 대상 브랜치: `main` - 작업 브랜치: `ops/sre`  ## 목적  Control 채택 뒤 `previous-contract-sha`와 현재 `contract-sha`가 서로 다른 SHA여도 실제 Release 계약이 호환되면 기록된 이전 Application Release로 안전하게 rollback할 수 있도록 합니다. 부분 state 기록이나 비호환 Control은 Docker 활성화와 state write 전에 fail-closed합니다.  ## 원인  기존 로직은 기록된 `previous-sha`라도 `previous-contract-sha == contract-sha`인 경우에만 빠른 경로를 허용했습니다. Control 채택 과정에서 Release 계약 세 파일이 그대로여도 SHA가 달라지면 대상 Application commit과 비교하는 경로로 이동해 …

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/validate-conventions.yml
- docs/reports/OPS-027/sre-report.md
- docs/runbook/OPS-010-production-single-release.md
- infra/production/rollback.sh
- infra/production/test-rollback-control-compatibility.sh

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

https://github.com/guseoh/pawcycle-commerce/pull/79
