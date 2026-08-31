---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 271
status: merged
taskId: UX-007
author: guseoh
base: main
head: feat/fe/MVP4-UX-007
mergedAt: 2026-08-31T15:25:16Z
mergeCommit: 7d18ff7306f6a9879d94aa2aa41ed6dbb8e8dbd2
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #271 fix(frontend): 상품 비교 고객 정보 표시 교정

## 작업 목적

## 작업  - 작업 ID: MVP4-UX-007 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Frontend Engineer / Visual Closure correction  ## 목적과 범위  - 목적: Production Browser Evidence에서 비교 화면이 고객에게 내부 용어 `Facet`과 raw `key:value` 값을 노출한 P1을 교정한다. - 변경 범위: 상품 비교 화면의 특징 라벨/표시 변환과 집중 회귀 테스트 - 제외 범위: 비교 API 계약, facet 저장/필터 query contract, Backend, DB, 신규 디자인 시스템, Production 실행  ## 결정과 영향  - 중요한 결정: API의 canonical `key:value` facet 값은 그대로 소비하되 고객 화면에서는 key를 제거한 실제 값만 중복 없이 `·`로 연결하고 라벨을 `주요 특징`으로 표시한다. - 영향 영역: Desktop/Mobile `/c…

## 주요 변경

기록 없음

## 변경 파일

- frontend/src/components/comparison-screen.tsx
- frontend/src/lib/comparison-presentation.test.mts
- frontend/src/lib/comparison-presentation.ts

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

https://github.com/guseoh/pawcycle-commerce/pull/271
