---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 165
status: merged
taskId: OPS-DB-005
author: guseoh
base: main
head: fix/ops-db-005-rds-preflight-stdin
mergedAt: 2026-08-21T04:35:37Z
mergeCommit: 4dae9fc24663b284d866cfcb6a6f3cef57b98727
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #165 fix(production): RDS preflight stdin 전달 보장

## 작업 목적

작업 ID: OPS-DB-005 작업 등급: 고위험 실행 구분: 저장소 변경  ## 목적과 범위 목적: Production RDS Scheduler preflight에서 ephemeral MySQL client가 SQL stdin을 받지 못해 실제 RDS schema query가 실행되지 않는 결함을 수정합니다. 변경 범위: `subscription-automation-preflight.sh`의 RDS read-only `docker run` stdin 연결과 해당 회귀 테스트만 포함합니다. 실제 Scheduler 활성화, RDS/Docker/Production runtime 변경, schema/migration/Application code 변경은 포함하지 않습니다.  ## 변경 - RDS ephemeral MySQL client `docker run`에 `--interactive`를 추가해 pipe로 전달한 SQL이 container stdin에 연결되도록 수정 - RDS da…

## 주요 변경

기록 없음

## 변경 파일

- infra/production/subscription-automation-preflight.sh
- infra/production/test-subscription-automation-preflight-datasource.sh

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

https://github.com/guseoh/pawcycle-commerce/pull/165
