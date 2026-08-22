---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 182
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH9-004
mergedAt: 2026-08-22T12:50:55Z
mergeCommit: 3dadcfc417ebf457fc67c217c2f98d2dc06116bc
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #182 feat(sre): Phase 9 Tomcat128 실험 준비

## 작업 목적

## 작업  - 작업 ID: PERF-PH9-004 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: 기존 Production-like local envelope에서 Tomcat max threads 128 단일 변수 실험을 준비한다. - 변경 범위: local-only Tomcat128 overlay, expected parameter 기반 Tomcat validation 일반화, Phase 9 실행 경계 문서. - 제외 범위: base local compose, production compose, application.properties, Hikari/SQL/index/JVM/CPU/memory/PID/accept queue/max-connections, 실제 250 RPS, Production/Cloud/AWS.  ## 결정과 영향  - 중요한 결정: 기존 envelope에 `SERVER_TOMCAT_THREA…

## 주요 변경

기록 없음

## 변경 파일

- infra/local-integration/compose.phase9-tomcat128.yaml
- infra/performance/phase9/README.md
- infra/performance/phase9/run-products-diagnostic.ps1

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

https://github.com/guseoh/pawcycle-commerce/pull/182
