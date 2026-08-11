---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 129
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/OPS-AUTO-005
mergedAt: 2026-08-11T15:48:23Z
mergeCommit: 18d653434e3de490931bf3a5fc5e3936416f564e
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #129 fix(ops): Production 배포 승인 경계 보완

## 작업 목적

## 작업  - 작업 ID: OPS-AUTO-005 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: Production Deploy가 Release contract·Flyway boundary를 실제 서비스·DB 변경 전에 탐지하고, 정확한 SHA 승인 없이는 fail-closed 하도록 보완 - 변경 범위: workflow_dispatch 승인 입력 3개, SSM preflight/deploy 2단계, deploy.sh·release-common.sh boundary 검증, plugin Output 진단, 관련 테스트·Runbook·validator - 제외 범위: Production/AWS/DB/Secret 실행, IAM 변경, V3~V11 수정, Scheduler ON, 자동 rollback 확대  ## 결정과 영향  - 중요한 결정: Control checkout 전환은 자동화하지 않고 사용자가 승인된 c…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/production-deploy.yml
- docs/runbook/OPS-010-production-single-release.md
- docs/runbook/SUB-AUTO-002-production-subscription-automation.md
- infra/production/deploy.sh
- infra/production/pawcycle-production-deploy-ssm-document.json
- infra/production/release-common.sh
- infra/production/test-production-scripts.sh
- infra/production/validate-production-contracts.py

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

https://github.com/guseoh/pawcycle-commerce/pull/129
