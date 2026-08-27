---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 242
status: merged
taskId: FOUNDATION-004
author: guseoh
base: main
head: test/qa/MVP4-QA-003
mergedAt: 2026-08-27T15:35:21Z
mergeCommit: faf996c94bee7486992c8c720b3bb9b8e1ebc536
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #242 fix(qa): FOUNDATION 로컬 smoke 호환성 복구

## 작업 목적

## 작업  - 작업 ID: MVP4-QA-003 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: QA Engineer, 승인된 Backend/Platform 수정 및 로컬 QA  ## 목적과 범위  - 목적: MVP4-QA-002 Q-01의 FOUNDATION-004 local smoke compatibility 복구 - 변경 범위: bootstrap visibility 전달/정렬, 관련 테스트, smoke canonical items 검색, 최소 Runbook 및 QA 보고서 - 제외 범위: V3 data, Product public visibility 정책, schema, frontend UX, F-01, dependency upgrade, Production/AWS/Production DB  ## 결정과 영향  - 중요한 결정: V3 미설정/false에서는 QA fixture 공개, true에서는 숨김. 기존 3-arg bootstrap의 숨김 기본값과 coll…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfiguration.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapService.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfigurationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapIntegrationTests.java
- docs/reports/MVP4-QA-003/qa-report.md
- docs/runbook/FOUNDATION-004-local-integration.md
- infra/local-integration/smoke.ps1

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

https://github.com/guseoh/pawcycle-commerce/pull/242
