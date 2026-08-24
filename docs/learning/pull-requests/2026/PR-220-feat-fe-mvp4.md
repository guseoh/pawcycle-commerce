---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 220
status: merged
taskId: API-008
author: guseoh
base: main
head: feat/fe/MVP4-FE-001
mergedAt: 2026-08-24T14:56:27Z
mergeCommit: 1e85c326c446e12a6d33250cc95c86cd2c976cc2
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #220 feat(fe): MVP4 사용자 기능 연결

## 작업 목적

## 작업 식별  - 작업 ID: MVP4-FE-001 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Frontend Engineer  ## 목적과 범위  - 목적: MVP4 canonical Frontend 사용자 기능을 V2 Subscription과 현재 Commerce 계약으로 통일 - 변경 범위: Home, Product/Recommendation, V2 Subscription, Cart, Wishlist, Address, Billing prepare, Checkout, My 연결과 최종 리뷰에서 확인된 인증 상태·추천 race·Cart 수량·필터 history·멱등성 키·pagination 보정  ## 검증  - 실행 결과: 사용자/Codex 기능 correction `d1d40ebe8bfdacb524673b5cc7d880105d35dd43` 기준 `npm run typecheck`, `npm run lint`, `npm test` 24 tests, `npm…

## 주요 변경

기록 없음

## 변경 파일

- frontend/package.json
- frontend/src/app/addresses/page.tsx
- frontend/src/app/billing-methods/page.tsx
- frontend/src/app/cart/page.tsx
- frontend/src/app/checkout/page.tsx
- frontend/src/app/my/page.tsx
- frontend/src/app/page.tsx
- frontend/src/app/products/page.tsx
- frontend/src/app/subscriptions/[subscriptionId]/page.tsx
- frontend/src/app/subscriptions/new/page.tsx
- frontend/src/app/subscriptions/page.tsx
- frontend/src/app/wishlist/page.tsx
- frontend/src/components/app-header.tsx
- frontend/src/components/mvp2-subscription-detail.tsx
- frontend/src/components/mvp2-subscription-list.tsx
- frontend/src/components/mvp2-subscription-start.tsx
- frontend/src/components/product-detail-screen.contract.test.mts
- frontend/src/components/product-detail-screen.tsx
- frontend/src/lib/api.ts
- frontend/src/lib/commerce-final-api.test.mts
- 외 3개

## 리뷰 결과

- COMMENTED: 11

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

https://github.com/guseoh/pawcycle-commerce/pull/220
