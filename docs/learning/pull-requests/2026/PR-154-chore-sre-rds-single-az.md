---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 154
status: merged
taskId: OPS-DB-002
author: guseoh
base: main
head: ops/sre/OPS-DB-002
mergedAt: 2026-08-18T07:28:16Z
mergeCommit: cde612f8fb65404926dffb4942c7881fde94cabd
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #154 chore(sre): RDS Single-AZ 전환 저장소 준비

## 작업 목적

## 작업  - 작업 ID: OPS-DB-002 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Platform/SRE - Related: #153  ## 목적과 범위  - 목적: 현재 Production Docker MySQL에 결합된 datasource runtime 계약을 RDS MySQL Single-AZ 전환이 가능한 구조로 확장합니다. - 변경 범위: 기존 Docker MySQL 기본 경로와 rollback source를 유지하면서 RDS datasource runtime, read-only preflight, migration rehearsal, cutover/rollback readiness 계약, ADR/Runbook, fixture·validator·CI 검증을 저장소에 준비합니다. - 제외 범위: 실제 RDS·DB Subnet Group·Security Group·IAM·SSM 생성/수정, Production DB dump/import/query/…

## 주요 변경

기록 없음

## 변경 파일

- .coderabbit.yaml
- .github/workflows/validate-conventions.yml
- docs/adr/ARCH-013-rds-single-az.md
- docs/runbook/OPS-010-production-single-release.md
- docs/runbook/OPS-DB-002-rds-migration-cutover.md
- docs/runbook/README.md
- infra/production/compose.yaml
- infra/production/materialize-ssm-env.sh
- infra/production/rds-read-only-preflight.sh
- infra/production/rds-transition-gate.sh
- infra/production/release-common.sh
- infra/production/ssm-parameters.env.example
- infra/production/test-production-compose.sh
- infra/production/test-production-scripts.sh
- infra/production/test-rds-readiness.sh
- infra/production/test-rollback-control-compatibility.sh
- infra/production/validate-production-contracts.py

## 리뷰 결과

- COMMENTED: 7

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

https://github.com/guseoh/pawcycle-commerce/pull/154
