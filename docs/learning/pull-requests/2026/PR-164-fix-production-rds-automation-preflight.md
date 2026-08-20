---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 164
status: merged
taskId: OPS-DB-005
author: guseoh
base: main
head: fix/ops-db-005-rds-automation-preflight
mergedAt: 2026-08-20T15:52:03Z
mergeCommit: afeec7ffe6aa2ffa3db2bd02a7ebe18dafc82655
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #164 fix(production): RDS automation preflight 대상 정합성 보완

## 작업 목적

작업 ID: OPS-DB-005 작업 등급: 고위험 실행 구분: 저장소 변경  ## 목적과 범위 목적: Production RDS cutover 이후 Scheduler activation preflight가 보존 중인 source Docker MySQL이 아니라 현재 active runtime의 datasource를 검사하도록 수정합니다. 변경 범위: `subscription-automation-preflight.sh`의 read-only DB query routing, 전용 회귀 테스트, 전용 CI만 포함합니다. 실제 Scheduler 활성화, RDS/Docker/Production runtime 변경, schema/migration/Application code 변경은 포함하지 않습니다.  ## 변경 - Docker runtime(`host=mysql`, `sslMode=DISABLED`)은 기존 source Docker MySQL read-only query 경로 유지 - R…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/validate-rds-automation-preflight.yml
- infra/production/subscription-automation-preflight.sh
- infra/production/test-subscription-automation-preflight-datasource.sh

## 리뷰 결과

기록 없음

## CI 및 검증

- publish: queued

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/164
