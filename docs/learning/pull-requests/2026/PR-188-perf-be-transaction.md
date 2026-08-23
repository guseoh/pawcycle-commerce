---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 188
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: feat/be/PERF-PH9-007
mergedAt: 2026-08-23T05:31:44Z
mergeCommit: 0dda1e2ea24efa5208b643cc2f06a41fac29177b
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #188 perf(be): 상품 목록 transaction 범위 축소

## 작업 목적

## 작업  - 작업 ID: PERF-PH9-007 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: `GET /api/products`의 API 계약과 조회 의미를 유지하면서 DB read와 scalar snapshot materialization만 짧은 read-only transaction에서 수행한다. - 변경 범위: 별도 `ProductListReader` Spring bean, immutable product/SKU snapshot, transaction 밖의 grouping/`ProductListView` 조립, 관련 backend tests. - 제외 범위: `findProduct(productId)` 상세 path, API contract, Hikari/CPU/memory/Tomcat/PID, SQL/index rewrite, cache/async/dependency/schema/fronten…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListReader.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductQueryService.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/application/ProductListReaderTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/application/ProductQueryServiceTests.java

## 리뷰 결과

- COMMENTED: 1

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

https://github.com/guseoh/pawcycle-commerce/pull/188
