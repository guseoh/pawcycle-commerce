---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 192
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH9-009
mergedAt: 2026-08-23T07:14:33Z
mergeCommit: ccec6fb3b3db8c9df661e59b5b2188acb35a448a
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #192 perf(sre): Phase 9 Hikari20 실험 준비

## 작업 목적

## 작업  - 작업 ID: PERF-PH9-009 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE 구현 및 검증  ## 목적과 범위  - 목적: PERF-PH9-007 first-result control을 재사용한 local-only Hikari maximum pool capacity 10→20 causality experiment 준비 - 변경 범위: Hikari20 backend overlay, expected Hikari max preflight contract와 synthetic validator, candidate/rollback 실행 경계 문서화 - 제외 범위: control 재실행, 실제 250 RPS/k6 workload, Hikari timeout·CPU·memory·Tomcat·PID·JVM 변경, 제품/API/DB/frontend/Production/Cloud/AWS/RDS 변경  ## 결정과 영향  - 중요한 결정: 독립…

## 주요 변경

기록 없음

## 변경 파일

- infra/local-integration/compose.phase9-hikari20.yaml
- infra/performance/phase9/README.md
- infra/performance/phase9/run-products-diagnostic.ps1

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

https://github.com/guseoh/pawcycle-commerce/pull/192
