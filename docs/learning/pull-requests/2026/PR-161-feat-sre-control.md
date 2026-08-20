---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 161
status: merged
taskId: OPS-DB-006
author: guseoh
base: main
head: ops/sre/OPS-DB-006
mergedAt: 2026-08-20T09:09:51Z
mergeCommit: bb3cbdfff558fcce61baf0f8f5b5ffe5cc50407b
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #161 feat(sre): Control 전용 계약 채택

## 작업 목적

## 작업  - 작업 ID: OPS-DB-006 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: 현재 Application Release SHA를 유지한 채 명시적으로 승인된 새 Production Control SHA를 안전하게 채택한다. - 변경 범위: 기존 Production Deploy/SSM contract, release harness, focused lifecycle regression, OPS-010 Runbook과 validator. - 제외 범위: Application·API·도메인·DB schema/Flyway 변경, 실제 AWS/RDS/Production/SSM/Secret 실행과 실제 deploy·cutover.  ## 결정과 영향  - 중요한 결정: `control-adopt`는 이전 `contract-sha`, clean Control HEAD, 현재 `current-sha`를 모두 명…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/production-deploy.yml
- docs/runbook/OPS-010-production-single-release.md
- infra/production/deploy.sh
- infra/production/pawcycle-production-deploy-ssm-document.json
- infra/production/release-common.sh
- infra/production/test-production-scripts.sh
- infra/production/validate-production-contracts.py
- infra/production/validate-production-ssm-document.py

## 리뷰 결과

기록 없음

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

https://github.com/guseoh/pawcycle-commerce/pull/161
