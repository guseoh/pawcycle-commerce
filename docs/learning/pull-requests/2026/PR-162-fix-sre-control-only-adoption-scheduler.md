---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 162
status: merged
taskId: OPS-DB-006
author: guseoh
base: main
head: fix/sre/control-adopt-preserve-scheduler
mergedAt: 2026-08-20T10:00:07Z
mergeCommit: 7167b5abefe498905855a042b09d0c545bc9f902
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #162 fix(sre): Control-only adoption에서 Scheduler 상태 보존

## 작업 목적

## 작업 - 작업 ID: OPS-DB-006 - 작업 등급: 고위험 - 실행 구분: 저장소 변경  ## 목적과 범위 - 목적: `control-adopt`가 현재 Application/컨테이너를 재생성하지 않는 계약과 일치하도록 현재 Scheduler runtime 상태를 그대로 허용합니다. - 변경 범위: `infra/production/deploy.sh`에서 Scheduler OFF 강제를 `preflight`/`deploy`에만 유지하고 `control-adopt`는 현재 runtime Scheduler mode를 보존하도록 분기합니다. Scheduler activate/deactivate, Application/DB/Compose activation, RDS cutover, AWS/Production runtime 변경은 제외합니다.  ## 검증 - 실행 결과: Repository Validation #1092 성공. Commit/PR convention, 분류, Prod…

## 주요 변경

기록 없음

## 변경 파일

- infra/production/deploy.sh

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

https://github.com/guseoh/pawcycle-commerce/pull/162
