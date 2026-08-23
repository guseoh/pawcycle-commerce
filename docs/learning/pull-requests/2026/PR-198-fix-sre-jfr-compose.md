---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 198
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH9-012
mergedAt: 2026-08-23T11:26:10Z
mergeCommit: b4f6c81f5b0635582fe2de412bf1e2761cf8485e
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #198 fix(sre): JFR compose 보간 분리

## 작업 목적

## 작업  - 작업 ID: PERF-PH9-012 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: JFR backend 재생성 뒤 recording-path 환경 변수가 제거된 상태에서 fresh-runtime Prometheus port lookup이 JFR overlay를 재보간하지 않도록 수정 - 변경 범위: JFR helper의 CPU2.0 base compose args/JFR compose args 분리와 회귀 validation - 제외 범위: actual profiling/k6, CPU·Hikari·memory·Tomcat·PID·JVM tuning, application/DB/schema, Production/Cloud/AWS/RDS  ## 결정과 영향  - 중요한 결정: backend recreate에는 JFR overlay를 포함한 args를 유지하고, fresh runtime 및 rollba…

## 주요 변경

기록 없음

## 변경 파일

- infra/performance/phase9/run-products-jfr-diagnostic.ps1

## 리뷰 결과

- COMMENTED: 5

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

https://github.com/guseoh/pawcycle-commerce/pull/198
