---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 136
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: feat/be/MVP3-CATALOG-001
mergedAt: 2026-08-12T12:07:33Z
mergeCommit: 3d5f84f24ed7c401a964079d99466dd482300338
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #136 feat(catalog): 관리자 Catalog 운영 기능 구현

## 작업 목적

## 작업  - 작업 ID: MVP3-CATALOG-001 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: MVP3 첫 Commerce 기능군인 관리자 Category/Product/SKU 운영 API와 USER/ADMIN RBAC 구현 - 변경 범위: Admin Catalog CRUD·상태 관리, `/api/admin/**` ADMIN 인가, `/api/auth/me` role additive 응답, V12 backfill·제약, 공개 Product ACTIVE SKU 필터, Lombok의 변경 코드 중심 도입, API·도메인 계약 갱신 - 제외 범위: Inventory·Cart·Wishlist·Coupon·Membership·Order·Payment·Delivery·Admin Dashboard, 회원 관리·role 변경 API/UI, 실제 ADMIN 계정 생성, JWT/OAuth, Production …

## 주요 변경

기록 없음

## 변경 파일

- backend/build.gradle
- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogController.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogRequests.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogViews.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogConflictException.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogException.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogNotFoundException.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogService.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogValidationException.java
- backend/src/main/java/com/pawcycle/backend/catalog/category/domain/Category.java
- backend/src/main/java/com/pawcycle/backend/catalog/category/infra/CategoryRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductQueryService.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/domain/Product.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/domain/ProductStatus.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/infra/ProductRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/sku/domain/Sku.java
- backend/src/main/java/com/pawcycle/backend/catalog/sku/domain/SkuStatus.java
- backend/src/main/java/com/pawcycle/backend/catalog/sku/infra/SkuRepository.java
- backend/src/main/java/com/pawcycle/backend/common/security/SecurityConfig.java
- 외 10개

## 리뷰 결과

- COMMENTED: 2

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

https://github.com/guseoh/pawcycle-commerce/pull/136
