---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 160
status: merged
taskId: OPS-DB-005
author: guseoh
base: main
head: ops/sre/OPS-DB-005
mergedAt: 2026-08-18T09:46:38Z
mergeCommit: 5610e2b96d00dc67fbd48d53236c7d14556fb821
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #160 fix(ops): candidate restore runtime env 파싱 정합성 복원

## 작업 목적

- 작업 등급: 고위험 - 실행 구분: 저장소 변경  ## 목적과 범위 - 목적: OPS-DB-005 RDS Migration Rehearsal에서 실제 materialized runtime의 `MYSQL_DATABASE='...'` 형식을 raw 값으로 처리해 `candidate restore database name is invalid`로 중단된 저장소 계약 결함을 수정한다. - 변경 범위: `infra/production/db-backup-restore.sh`, `infra/production/test-db-backup-restore.sh` 두 파일에서 managed single-quoted runtime parsing과 해당 회귀 테스트만 수정한다. API, DB schema, Flyway, Application, RDS 리소스, Production datasource는 변경하지 않는다.  ## 원인 - `materialize-ssm-env.sh`는 runtime 값을 ma…

## 주요 변경

기록 없음

## 변경 파일

- infra/production/db-backup-restore.sh
- infra/production/test-db-backup-restore.sh

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

https://github.com/guseoh/pawcycle-commerce/pull/160
