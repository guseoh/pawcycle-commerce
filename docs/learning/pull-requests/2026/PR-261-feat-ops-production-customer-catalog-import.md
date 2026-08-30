---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 261
status: merged
taskId: DATA-007
author: guseoh
base: main
head: ops/sre/MVP4-DATA-007
mergedAt: 2026-08-30T10:34:52Z
mergeCommit: 21332fc051e0e1d928473752c58e91f04e61bc9c
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #261 feat(ops): Production Customer Catalog import 연결

## 작업 목적

## 작업  - 작업 ID: `MVP4-DATA-007` - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Platform/SRE  ## 목적과 범위  - 목적: PR #260에서 준비된 Production Catalog `customer` target을 기존 one-shot SRE script에 연결해 Canonical Customer Catalog를 명시적으로 `validate/apply`할 수 있는 저장소 경로를 완성합니다. - 변경 범위: `infra/production/import-demo-catalog.sh`의 `demo|customer` target 선택, shell contract 검증 보강, Customer Catalog Production 실행 Runbook 추가. - 제외 범위: Backend 제품 코드, schema/migration, API/Frontend, Secret, AWS/Production DB 접근, 실제 `validate/apply`,…

## 주요 변경

기록 없음

## 변경 파일

- docs/runbook/MVP4-DATA-007-production-customer-catalog-import.md
- infra/production/import-demo-catalog.sh
- infra/production/test-demo-catalog-import.sh

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

https://github.com/guseoh/pawcycle-commerce/pull/261
