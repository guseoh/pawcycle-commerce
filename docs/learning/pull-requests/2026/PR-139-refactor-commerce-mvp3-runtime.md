---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 139
status: merged
taskId: API-006
author: guseoh
base: main
head: refactor/be-MVP3-CLEANUP-001
mergedAt: 2026-08-13T13:03:33Z
mergeCommit: 683e3cade76d4d10ca9288714cd0b0eeba1910b9
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #139 refactor(commerce): MVP3 runtime 구조 정리

## 작업 목적

## 작업 정보  - 작업 ID: MVP3-CLEANUP-001 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend  ## 목적과 범위  - 목적: API-006/API-007 및 Flyway V1~V19 계약을 유지하면서 Commerce runtime의 책임 경계와 검증 가능성을 개선한다. - 변경 범위: 거대 Commerce controller를 쇼핑·체크아웃·Billing·사후처리·회원·Admin HTTP adapter로 분리하고, 재고 이동/CAS·membership 평가·checkout 만료 처리를 application service로 추출한다. V13~V19 Commerce JPA entity/repository mapping과 ADR ARCH-009를 추가한다. - 제외 범위: API URI/method/status/JSON, V1~V19, V20, Provider API, frontend/infra, Production/AWS/Secret/T…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/commerce/AdminCommerceController.java
- backend/src/main/java/com/pawcycle/backend/commerce/AdminCommerceService.java
- backend/src/main/java/com/pawcycle/backend/commerce/AdminOrderQueryService.java
- backend/src/main/java/com/pawcycle/backend/commerce/AfterSalesController.java
- backend/src/main/java/com/pawcycle/backend/commerce/BillingController.java
- backend/src/main/java/com/pawcycle/backend/commerce/BillingMethodQueryService.java
- backend/src/main/java/com/pawcycle/backend/commerce/BillingPaymentMethodPreparationRepository.java
- backend/src/main/java/com/pawcycle/backend/commerce/BillingPaymentMethodRepository.java
- backend/src/main/java/com/pawcycle/backend/commerce/CancellationService.java
- backend/src/main/java/com/pawcycle/backend/commerce/CartRepository.java
- backend/src/main/java/com/pawcycle/backend/commerce/CheckoutController.java
- backend/src/main/java/com/pawcycle/backend/commerce/CheckoutExpirationProcessor.java
- backend/src/main/java/com/pawcycle/backend/commerce/CheckoutExpirationService.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceController.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceFinalController.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceMemberController.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceOrderRepository.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommercePersistenceEntities.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceRequests.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceService.java
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

https://github.com/guseoh/pawcycle-commerce/pull/139
