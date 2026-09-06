---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 279
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: refactor/backend-internal-convergence
mergedAt: 2026-09-06T01:58:47Z
mergeCommit: 1af3022c40f329ab34f247d5450e1b1f29bea851
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #279 refactor: backend persistence 구조 수렴

## 작업 목적

## 작업  - 작업 ID: `BACKEND-REFACTOR-003` - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: JPA와 `JdbcTemplate`이 병존하던 Backend persistence를 제품 동작과 외부 HTTP 계약을 유지하면서 JPA 중심으로 수렴합니다. - 변경 범위: Cart/Wishlist 조회, Checkout, Payment callback/expiration, Billing preparation, Delivery, Product discovery/detail/comparison, Review/문의, Catalog discovery/admin, `ProductQueryService` 권위 경로, Entity/Repository mapping, typed projection, lock/conditional mutation.  ## 문제  일부 feature에 JPA Reposit…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/CategoryFacetEntity.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/CategoryFacetId.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/FacetDefinitionEntity.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/FacetOptionEntity.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/ProductFacetValueEntity.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/ProductFacetValueId.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/ProductImageEntity.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/ProductOptionGroupEntity.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/ProductOptionValueEntity.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/SkuOptionValueEntity.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/domain/SkuOptionValueId.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/persistence/BrandAdminPersistence.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/persistence/CatalogAdminPersistence.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/persistence/CatalogAdminValidation.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/persistence/CatalogFacetAdminPersistence.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/persistence/CatalogFacetPersistenceAdapter.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/persistence/CategoryFacetRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/persistence/FacetDefinitionRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/persistence/FacetOptionRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/persistence/ProductDetailSectionPersistence.java
- 외 10개

## 리뷰 결과

- COMMENTED: 30

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

https://github.com/guseoh/pawcycle-commerce/pull/279
