---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 72
status: merged
taskId: OPS-021
author: guseoh
base: main
head: ops/sre
mergedAt: 2026-07-29T04:39:59Z
mergeCommit: ef9b15a767cec44525b19cacc11f9f84ff47c104
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #72 feat(sre): OPS-021 운영 계약 기준선 분리

## 작업 목적

## 작업 정보  - 작업 ID: OPS-021 - 작업 등급: 고위험 - 역할: Platform/SRE - 작업 브랜치: `ops/sre` - 대상 브랜치: `main`  등급·산출물·QA·검증 기준은 `docs/runbook/lean-harness.md`를 따릅니다.  > 이 PR은 운영 배포·rollback 계약을 변경하는 고위험 저장소 준비 작업입니다. 실제 Production 적용은 포함하지 않으며, 병합 후 별도의 명시적 승인과 적용 전후 검증을 거칩니다.  ## 관련 이슈  - Closes: 해당 없음 - Related: OPS-010, OPS-017, OPS-019, OPS-020  ## 목적  Production의 Application Release 상태와 Production Control 계약 상태를 분리합니다.  기존에는 실행 중인 Application SHA를 `infra/production/**` 전체의 비교 기준으로 함께 사용해, 실제 Container 활…

## 주요 변경

기록 없음

## 변경 파일

- docs/reports/OPS-021/sre-report.md
- docs/runbook/OPS-010-production-single-release.md
- infra/production/deploy.sh
- infra/production/release-common.sh
- infra/production/rollback.sh
- infra/production/test-production-scripts.sh
- infra/production/validate-production-contracts.py

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

https://github.com/guseoh/pawcycle-commerce/pull/72
