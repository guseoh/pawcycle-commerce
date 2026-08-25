---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 224
status: merged
taskId: UX-002
author: guseoh
base: main
head: feat/fe/MVP4-UX-002
mergedAt: 2026-08-25T11:11:47Z
mergeCommit: e755f823ab3fbdbc4e2316030c7b964541e9b19b
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #224 feat(fe): 커머스 화면 전면 재설계

## 작업 목적

## 작업  - 작업 ID: MVP4-UX-002 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Tech Lead / Backend·Frontend 협업 - 기준: 최신 origin/main  ## 목적과 범위  - 목적: 기존 PR #224의 Warm Utility Commerce 변경을 유지하면서 Commerce Catalog foundation과 Product Discovery/Detail을 R2+R3 사용자 목적 단위로 완성한다. - 변경 범위:   - 외부 Demo manifest → validation/import/bootstrap → DB 구조로 분리   - Category/Product/SKU/Plan business key 및 idempotent bootstrap   - mutable inventory 보존, QA fixture와 Demo fixture 실행 분리   - DB-native Product filter/sort/pagination과 `i…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogService.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/api/ProductController.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductDetailView.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductDiscoveryReader.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListView.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductQueryService.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductSort.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/domain/Product.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalCommerceDemoFixtureService.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfiguration.java
- backend/src/main/resources/catalog/demo-catalog.json
- backend/src/main/resources/db/migration/V20__add_catalog_business_keys.sql
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductApiIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductControllerTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductDiscoveryApiIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/DatabaseFoundationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/V16V17CommerceFinalMigrationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/V9SubscriptionOrderMigrationIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfigurationTests.java
- backend/src/test/java/com/pawcycle/backend/subscription/api/SubscriptionApiIntegrationTests.java
- 외 10개

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

https://github.com/guseoh/pawcycle-commerce/pull/224
