---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 228
status: merged
taskId: DATA-001
author: guseoh
base: main
head: feat/commerce/MVP4-COMPLETE-001
mergedAt: 2026-08-26T00:44:32Z
mergeCommit: 878e702e95ad28514214b6d2f5ba30a28d27ee85
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #228 feat(commerce): 최종 상품 완성

## 작업 목적

## 작업  - 작업 ID: `MVP4-COMPLETE-001` - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer / Frontend Engineer / QA  ## 목적과 범위  - 목적: 현재 Commerce 기능을 실제 사용자 흐름에서 연결하고 Toss Test 연동 기반까지 준비한 뒤, 후속 Product Completion 작업의 `main` baseline을 만든다. - 변경 범위:   - Toss v2 Test opt-in adapter와 Checkout/confirm callback   - Checkout 서버 금액·Order projection 연계와 Cart 재고 validation   - 공개 Category/Product 권위 조건 보강   - Product / Cart / Checkout / Order / My / Notification / Subscription / Trust 화면 연결   - Recent / Re…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/category/api/CategoryController.java
- backend/src/main/java/com/pawcycle/backend/catalog/category/application/CategoryListView.java
- backend/src/main/java/com/pawcycle/backend/catalog/category/application/CategoryQueryService.java
- backend/src/main/java/com/pawcycle/backend/catalog/category/infra/CategoryRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductDiscoveryReader.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/infra/ProductRepository.java
- backend/src/main/java/com/pawcycle/backend/commerce/CheckoutController.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceService.java
- backend/src/main/java/com/pawcycle/backend/commerce/TossPaymentAdapter.java
- backend/src/main/java/com/pawcycle/backend/commerce/TossSandboxPaymentAdapter.java
- backend/src/main/java/com/pawcycle/backend/commerce/TossTestPaymentAdapter.java
- backend/src/main/java/com/pawcycle/backend/common/security/SecurityConfig.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapService.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationRepository.java
- backend/src/main/resources/application-local-integration.properties
- backend/src/main/resources/application-local.example.properties
- backend/src/main/resources/application.properties
- backend/src/test/java/com/pawcycle/backend/catalog/category/api/CategoryApiIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductApiIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/commerce/CommercePurchaseIntegrationTests.java
- 외 10개

## 리뷰 결과

- COMMENTED: 9

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

https://github.com/guseoh/pawcycle-commerce/pull/228
