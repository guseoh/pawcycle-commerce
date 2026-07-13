---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 36
status: merged
taskId: API-002
author: guseoh
base: main
head: feat/be
mergedAt: 2026-07-13T06:25:01Z
mergeCommit: 9be2d7ba9b293469568c87d26db30ba5b5d43153
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #36 feat(product): 공개 상품 조회 API 구현

## 작업 목적

## 작업 정보  - 작업 ID: `PRODUCT-001` - 역할: Backend Engineer - 작업 브랜치: `feat/be` - 대상 브랜치: `main`  현재 PR head와 GitHub Checks 상태는 GitHub 원격을 권위 있는 원본으로 확인합니다.  ## 목적  API-002에서 승인한 공개 상품 목록·상세 API를 구현합니다.  - `GET /api/products` - `GET /api/products/{productId}`  ## 변경 사항  - 정확히 `PUBLIC`인 Product만 조회하는 MySQL native query - 상품 `id ASC`, SKU `display_order ASC, id ASC` 정렬 - 목록 Product 1회 + SKU batch 1회 조회로 N+1 방지 - read-only application transaction과 승인 응답 전용 read model - SKU 없는 공개 상품, nullable 필드, JSON …

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/product/api/ProductController.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/api/ProductExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductDetailUnavailableException.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductDetailView.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListUnavailableException.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListView.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductNotFoundException.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductQueryService.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/domain/Product.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/infra/ProductRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/sku/domain/Sku.java
- backend/src/main/java/com/pawcycle/backend/catalog/sku/infra/SkuRepository.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductApiIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductControllerTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/application/ProductQueryServiceTests.java
- docs/handoffs/PRODUCT-001/be-to-qa.md
- docs/reports/PRODUCT-001/be-report.md

## 리뷰 결과

- COMMENTED: 10

## CI 및 검증

- Discord collaboration notification: in_progress

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/36
