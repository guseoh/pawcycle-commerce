---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 184
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH9-005
mergedAt: 2026-08-22T14:27:33Z
mergeCommit: bfa226e99b016ffe96ee2aa646a9003dd6a5cbe2
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #184 feat(sre): Phase 9 CPU1.5 실험 준비

## 작업 목적

## 작업  - 작업 ID: PERF-PH9-005 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: PERF-PH9-004의 Tomcat128 + 0.75 CPU first-result를 control로 재사용하고 CPU만 1.5로 변경한 local causality experiment를 준비한다. - 변경 범위: local-only CPU1.5 overlay와 실행 경계 문서. - 제외 범위: 실제 250 RPS, PERF-PH9-004 control 재실행, Tomcat/Hikari/SQL/index/JVM/memory/PID/accept queue/max-connections 추가 tuning, Production/Cloud/AWS.  ## 결정과 영향  - 중요한 결정: `compose.phase9-cpu15.yaml`은 backend CPU limit만 `1.5`로 설정한다. Tomcat max 128, …

## 주요 변경

기록 없음

## 변경 파일

- infra/local-integration/compose.phase9-cpu15.yaml
- infra/performance/phase9/README.md

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

https://github.com/guseoh/pawcycle-commerce/pull/184
