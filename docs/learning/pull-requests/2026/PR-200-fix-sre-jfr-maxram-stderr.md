---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 200
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH9-013
mergedAt: 2026-08-23T12:10:01Z
mergeCommit: 625b630fb71a22ea265d513bcaeee172e68f4bd3
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #200 fix(sre): JFR MaxRAM stderr 처리

## 작업 목적

## 작업  - 작업 ID: PERF-PH9-013 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: PowerShell 7.6.5에서 정상 JVM startup stderr가 NativeCommandError로 승격되는 JFR MaxRAM guard 오탐 수정 - 변경 범위: `Assert-EffectiveMaxRamPercentage`의 JVM stderr 처리 - 제외 범위: actual JFR/k6 profiling, CPU·Hikari·memory·Tomcat·PID·JVM tuning, application/DB/schema, Production/Cloud/AWS/RDS  ## 결정과 영향  - 중요한 결정: container 내부 `sh -lc`에서 JVM stderr를 stdout으로 합친 뒤 host에서 flag line만 parse하고 Docker exit code를 즉시 보존 - 영향 영역: lo…

## 주요 변경

기록 없음

## 변경 파일

- infra/performance/phase9/run-products-jfr-diagnostic.ps1

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

https://github.com/guseoh/pawcycle-commerce/pull/200
