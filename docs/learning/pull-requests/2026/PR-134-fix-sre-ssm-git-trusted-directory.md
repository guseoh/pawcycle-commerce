---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 134
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/OPS-AUTO-008-delta2
mergedAt: 2026-08-12T06:19:48Z
mergeCommit: 747b07e96a5346ba1f3c672f98ce88286d4da3a8
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #134 fix(sre): SSM Git trusted directory 상속 보강

## 작업 목적

## 작업  - 작업 ID: OPS-AUTO-008 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: SRE  ## 목적과 범위  - 목적: Production SSM Agent의 Git ownership 경계로 인해 control worktree 접근이 실패하는 문제를 exact trusted-directory 계약으로 보완한다. - 변경 범위: Production SSM document와 관련 validator 2개. - 제외 범위: 실제 AWS 변경, Production 실행, repository ownership 변경, global/system Git config 변경, 제품 코드 변경.  ## 결정과 영향  - 중요한 결정: process-scoped Git config로 `/opt/pawcycle/control` 하나만 `safe.directory`로 허용하고 기존 5개 positional SSM parameter 계약을 유지한다. wildcard 및 per…

## 주요 변경

기록 없음

## 변경 파일

- infra/production/pawcycle-production-deploy-ssm-document.json
- infra/production/validate-production-contracts.py
- infra/production/validate-production-ssm-document.py

## 리뷰 결과

- COMMENTED: 3

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

https://github.com/guseoh/pawcycle-commerce/pull/134
