---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 236
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: feat/be/MVP4-CATALOG-001
mergedAt: 2026-08-27T03:33:42Z
mergeCommit: be0e38b73bdbf1acd5966cabaeb3b7365950ff5d
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #236 feat(catalog): Commerce Catalog Product Model Expansion 구현

## 작업 목적

## 작업 - 작업 ID: MVP4-CATALOG-001 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer - 작업명: Commerce Catalog Product Model Expansion  ## 목적과 범위 목적: V24 catalog expansion의 Admin 관리 API와 public catalog 회귀를 완성한다. 변경 범위: Brand 상세/PATCH, Product image gallery, option/SKU mapping, facet CRUD·배정, category depth validation, compareAtPrice, MAIN image 단일성, thumbnail fallback, selectedOptions 및 rating/price discovery 회귀를 구현했다. 제외 범위: Production 실행, merge, Ready 전환, 새 dependency, Catalog 외 기능 확장.  ## 결정과 영…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogController.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogRequests.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogViews.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogService.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/CatalogExpansionAdminService.java
- backend/src/main/java/com/pawcycle/backend/catalog/application/DemoCatalogManifestImportService.java
- backend/src/main/java/com/pawcycle/backend/catalog/brand/domain/Brand.java
- backend/src/main/java/com/pawcycle/backend/catalog/brand/infra/BrandRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/category/domain/Category.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/api/ProductController.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/api/ProductExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductDetailView.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductDiscoveryReader.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListView.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductQueryService.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductSort.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/domain/Product.java
- backend/src/main/java/com/pawcycle/backend/catalog/sku/domain/Sku.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/performance/SubscriptionBurstMeasurementService.java
- backend/src/main/resources/db/migration/V24__expand_catalog_product_model.sql
- 외 10개

## 리뷰 결과

- COMMENTED: 14

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

https://github.com/guseoh/pawcycle-commerce/pull/236
