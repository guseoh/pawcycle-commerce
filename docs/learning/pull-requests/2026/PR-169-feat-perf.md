---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 169
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH8-002
mergedAt: 2026-08-21T15:28:08Z
mergeCommit: 45c36f30f78feef43c882361297e223e29b6531a
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #169 feat(perf): 배포 환경 처리 용량 측정 하네스 추가

## 작업 목적

## 작업  - 작업 ID: PERF-PH8-002 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: deployed/Production public HTTPS의 read-only 처리 용량을 안전 경계 안에서 단계 측정할 저장소 하네스를 준비한다. - 변경 범위: `GET /api/products` 전용 Production runner·scenario, target/approval fail-close, fixed-rate warm-up, harness validator, 실행 전후 `READY → NORMAL` 진단 Runbook. - 제외 범위: 실제 Production/AWS/RDS load 실행, nginx·Compose·Application·RDS 설정 및 성능 tuning 변경, 250 RPS 초과, authenticated/write workload.  ## 결정과 영향  - 중요한 결정: 기존 Loc…

## 주요 변경

기록 없음

## 변경 파일

- docs/runbook/PERF-PH8-002-production-k6-capacity.md
- infra/performance/k6/lib/production-capacity.js
- infra/performance/k6/production-capacity-api-products.js
- infra/performance/k6/run-production-capacity.sh
- infra/performance/k6/validate-harness.ps1

## 리뷰 결과

- COMMENTED: 2

## CI 및 검증

- Discord collaboration report: in_progress
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

https://github.com/guseoh/pawcycle-commerce/pull/169
