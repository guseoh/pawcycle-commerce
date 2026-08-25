---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 227
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: feat/commerce/MVP4-COMMERCE-001
mergedAt: 2026-08-25T12:43:27Z
mergeCommit: 6705ed704bf2fe5f5a34a5f7841e86348a70985a
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #227 feat(commerce): 구매 후 신뢰 흐름 완성

## 작업 목적

## 작업  - 작업 ID: MVP4-COMMERCE-001 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend + Frontend Engineer  ## 목적과 범위  - 목적: R4~R7을 Product → Cart → Checkout → Order → My/Notification → Subscription → Trust/Convenience의 Commerce 흐름으로 완성한다. - 변경 범위: Backend Cart pricing·재고 projection, Checkout additive pricing, Order payment provider projection과 통합 회귀 테스트; Frontend Cart/Checkout/Order/My/Notification/Subscription 표시, 취소·반품 dialog, badge·recent·reorder·related 상품, 배송/반품/FAQ/공지/고객지원 화면과 Footer 연결. - 제외 범위: …

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/commerce/CommerceService.java
- backend/src/test/java/com/pawcycle/backend/commerce/CommercePurchaseIntegrationTests.java
- frontend/src/app/cart/page.tsx
- frontend/src/app/checkout/page.tsx
- frontend/src/app/faq/page.tsx
- frontend/src/app/globals.css
- frontend/src/app/my/page.tsx
- frontend/src/app/notice/page.tsx
- frontend/src/app/returns/page.tsx
- frontend/src/app/shipping/page.tsx
- frontend/src/app/support/page.tsx
- frontend/src/components/app-footer.tsx
- frontend/src/components/app-header.tsx
- frontend/src/components/commerce-order-detail.tsx
- frontend/src/components/commerce-order-list.tsx
- frontend/src/components/mvp2-subscription-detail.tsx
- frontend/src/components/mvp4-ux-regression.test.mts
- frontend/src/components/notification-screen.tsx
- frontend/src/components/product-detail-screen.tsx
- frontend/src/components/trust-pages.tsx
- 외 3개

## 리뷰 결과

- COMMENTED: 5

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

https://github.com/guseoh/pawcycle-commerce/pull/227
