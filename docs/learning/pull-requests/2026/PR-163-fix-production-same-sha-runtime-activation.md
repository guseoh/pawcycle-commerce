---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 163
status: merged
taskId: OPS-DB-005
author: guseoh
base: main
head: fix/ops-db-005-same-sha-runtime-activation
mergedAt: 2026-08-20T15:15:54Z
mergeCommit: 4ab62dbedc6ca299b98ba3299501e5a52cd88df0
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #163 fix(production): same-SHA runtime activation 허용

## 작업 목적

작업 ID: OPS-DB-005 작업 등급: 고위험 실행 구분: 저장소 변경  ## 목적과 범위 목적: Production RDS cutover에서 현재 Application SHA와 target SHA가 동일한 runtime-only activation이 release contract boundary 검사에 막히는 결함을 수정합니다. 변경 범위: `infra/production/deploy.sh`의 same-SHA Control/runtime 전환 판정과 해당 회귀 테스트/CI만 수정합니다. RDS, Docker, Production runtime 실제 실행과 schema/migration/Application code 변경은 포함하지 않습니다.  ## 변경 - `current-sha == target-sha`이고 stored `contract-sha`와 현재 Control HEAD가 같으면 기존 protected same-SHA activation 허용 - same-SHA이지만…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/validate-same-sha-runtime-activation.yml
- infra/production/deploy.sh
- infra/production/test-same-sha-runtime-activation.sh

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

https://github.com/guseoh/pawcycle-commerce/pull/163
