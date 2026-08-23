---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 203
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: ops/sre/PERF-PH10-001
mergedAt: 2026-08-23T15:45:08Z
mergeCommit: 6f7dc1ac1134201daf9a649bb329cf4ab2c4430a
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #203 perf(sre): 상품 목록 Redis 캐시 및 Scale 계약 도입

## 작업 목적

## 작업  - 작업 ID: PERF-PH10-001 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend Engineer, Platform/SRE  ## 목적과 범위  - 목적: Phase 10 Read Scale 후보로 `/api/products` Redis cache를 도입하고 동일 조건 After first-result harness 및 Subscription Burst 측정 계약 준비 - 변경 범위: 상품 목록 cache miss/hit/fail-open, Product/SKU after-commit invalidation, local Redis, cache metric 수집, CPU2.0 Redis After 1회 harness, Phase 10 ADR/측정 계약 - 제외 범위: 실제 k6/JFR/PH9 재실행, Kafka/Queue/async, detail/session cache, DB/schema/migration, Production Redi…

## 주요 변경

기록 없음

## 변경 파일

- backend/build.gradle
- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogService.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListCache.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListCacheInvalidator.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductQueryService.java
- backend/src/main/resources/application.properties
- backend/src/test/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogCacheInvalidationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogCacheInvalidationTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/application/ProductListCacheInvalidatorTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/application/ProductListCacheTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/application/ProductQueryServiceTests.java
- docs/adr/ARCH-014-product-list-redis-cache.md
- docs/performance/PERF-PH10-001-scale-scenario-contract.md
- infra/local-integration/compose.yaml
- infra/performance/phase10/run-products-redis-after.ps1
- infra/performance/phase9/run-products-diagnostic.ps1

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

https://github.com/guseoh/pawcycle-commerce/pull/203
