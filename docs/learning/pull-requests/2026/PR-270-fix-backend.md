---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 270
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: feat/be/MVP4-QA-007
mergedAt: 2026-08-31T15:21:18Z
mergeCommit: 769e5b7d0a9ae5ed8d773cbf0b951bb875c6bd7f
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #270 fix(backend): 상품 비교 구매 가능 상태 교정

## 작업 목적

## 작업  - 작업 ID: MVP4-QA-007 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend Engineer / QA correction  ## 목적과 범위  - 목적: Production Browser Evidence에서 `/compare?productId=100&productId=99`의 `구매 가능 여부`가 실제 PDP 옵션 선택 후 구매 가능한 상태와 모순된 P1을 교정한다. - 변경 범위: `ProductComparisonService`의 SQL `EXISTS` 결과 Boolean 변환과 해당 회귀 테스트 - 제외 범위: 재고 정책, SKU 구매 가능 규칙, API 응답 구조, DB/migration, Frontend, Production 실행  ## 결정과 영향  - 중요한 결정: MySQL/JDBC의 `EXISTS` 결과가 `Boolean`뿐 아니라 숫자형 `0/1`로 반환될 수 있으므로 comparison canonical facts에…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductComparisonService.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/application/ProductComparisonServiceTests.java

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

https://github.com/guseoh/pawcycle-commerce/pull/270
