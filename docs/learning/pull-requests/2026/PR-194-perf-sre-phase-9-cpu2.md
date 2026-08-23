---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 194
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH9-010
mergedAt: 2026-08-23T08:01:36Z
mergeCommit: d3aa9ad9bba3fe9b2283712ee0b606b4369df1e3
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #194 perf(sre): Phase 9 CPU2 실험 준비

## 작업 목적

## 작업  - 작업 ID: PERF-PH9-010 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE 구현 및 검증  ## 목적과 범위  - 목적: PERF-PH9-007 first-result control을 재사용한 local-only backend CPU 1.5→2.0 causality experiment 준비 - 변경 범위: CPU2.0 local-only compose overlay와 candidate/no-load preflight/rollback README 경계 - 제외 범위: control 재실행, 실제 250 RPS/k6 workload, Hikari·memory·Tomcat·PID·JVM·제품/API/DB/frontend/Production/Cloud/AWS/RDS 변경  ## 결정과 영향  - 중요한 결정: 독립 변수는 backend CPU limit 1.5→2.0 하나다. Hikari max10, memory1GiB, Tom…

## 주요 변경

기록 없음

## 변경 파일

- infra/local-integration/compose.phase9-cpu20.yaml
- infra/performance/phase9/README.md

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

https://github.com/guseoh/pawcycle-commerce/pull/194
