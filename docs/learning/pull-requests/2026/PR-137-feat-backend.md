---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 137
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: feat/be/MVP3-COMMERCE-002
mergedAt: 2026-08-13T00:17:15Z
mergeCommit: 2472c7f2cd778df45bce7864c815cfb488fea518
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #137 feat(backend): 커머스 주문 기반 구현

## 작업 목적

## 작업  - 작업 ID: MVP3-COMMERCE-002 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: 일반 구매와 구독 회차 주문을 공통 Order/Payment/Inventory 모델로 연결하고, Category·배송지 snapshot·Billing hold 계약을 반영합니다. - 변경 범위: V13~V15 migration, Commerce API/서비스, 구독 주문 자동화, admin catalog 제약, 관련 직접 테스트와 API 계약입니다. - 제외 범위: Production/AWS/실제 Toss 결제 실행, merge, 리뷰 코멘트 수정입니다.  ## 결정과 영향  - 중요한 결정: 기존 미분류 상품은 비활성 시스템 Category로만 보정하며, 신규 상품에는 활성 실 Category가 필요합니다. 구독은 배송지 snapshot과 ACTIVE billing method를 선검증하고, 결제…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogRequests.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/application/AdminCatalogService.java
- backend/src/main/java/com/pawcycle/backend/catalog/category/infra/CategoryRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/domain/Product.java
- backend/src/main/java/com/pawcycle/backend/commerce/CheckoutExpirationService.java
- backend/src/main/java/com/pawcycle/backend/commerce/CheckoutExpirationTrigger.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceController.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceException.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceService.java
- backend/src/main/java/com/pawcycle/backend/commerce/SubscriptionBillingService.java
- backend/src/main/java/com/pawcycle/backend/commerce/TossPaymentAdapter.java
- backend/src/main/java/com/pawcycle/backend/commerce/TossSandboxPaymentAdapter.java
- backend/src/main/java/com/pawcycle/backend/commerce/TossUnavailablePaymentAdapter.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapService.java
- backend/src/main/java/com/pawcycle/backend/subscription/v2/SubscriptionOrderAutomationService.java
- backend/src/main/resources/db/migration/V13__add_commerce_supporting_domains.sql
- backend/src/main/resources/db/migration/V14__add_common_order_payment_and_billing.sql
- backend/src/main/resources/db/migration/V15__backfill_legacy_subscription_orders.sql
- backend/src/test/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogApiIntegrationTests.java
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

https://github.com/guseoh/pawcycle-commerce/pull/137
