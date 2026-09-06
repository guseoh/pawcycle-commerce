---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 280
status: merged
taskId: 기록 없음
author: guseoh
base: main
head: refactor/http-client-contract-convergence
mergedAt: 2026-09-06T04:06:07Z
mergeCommit: 950da224d46601d660f499519353fc0fca792d78
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #280 refactor: HTTP 계약과 frontend client 구조 수렴

## 작업 목적

## 작업  - 작업 ID: `BACKEND-REFACTOR-004` - 작업 등급: 고위험 - 실행 구분: Repository Preparation  ## 목적과 범위  - 목적: PawCycle Commerce의 Backend HTTP validation/error/status 계약과 Frontend HTTP client를 하나의 일관된 계약으로 수렴하고, 해당 계약 변경이 Repository Validation에서 Backend와 Frontend를 함께 검증하도록 보강합니다. - 변경 범위: Spring MVC 공통 validation advice, 회원 배송지 요청 경계, Coupon PATCH partial-update 계약, Review/Question/Pet 상태·Location, bodyless DELETE 204, shared frontend HTTP client와 feature API module, frontend test discovery/CI, cross-st…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/validate-conventions.yml
- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogController.java
- backend/src/main/java/com/pawcycle/backend/catalog/admin/api/AdminCatalogExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/catalog/engagement/api/ProductEngagementExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/catalog/engagement/api/ProductQuestionController.java
- backend/src/main/java/com/pawcycle/backend/catalog/engagement/api/ProductReviewController.java
- backend/src/main/java/com/pawcycle/backend/commerce/CommerceExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/commerce/cancellation/api/CancellationController.java
- backend/src/main/java/com/pawcycle/backend/commerce/coupon/api/AdminCouponController.java
- backend/src/main/java/com/pawcycle/backend/commerce/coupon/api/CouponExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/commerce/coupon/api/CouponPatchRequest.java
- backend/src/main/java/com/pawcycle/backend/commerce/coupon/application/CouponAdminApplicationService.java
- backend/src/main/java/com/pawcycle/backend/commerce/coupon/application/CouponPatchCommand.java
- backend/src/main/java/com/pawcycle/backend/commerce/coupon/application/CouponValidationException.java
- backend/src/main/java/com/pawcycle/backend/commerce/coupon/persistence/CouponPersistenceAdapter.java
- backend/src/main/java/com/pawcycle/backend/commerce/returnrequest/api/ReturnRequestController.java
- backend/src/main/java/com/pawcycle/backend/common/api/CommonValidationExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/member/address/api/MemberAddressController.java
- backend/src/main/java/com/pawcycle/backend/member/address/api/MemberAddressExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/member/address/api/MemberAddressRequest.java
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

https://github.com/guseoh/pawcycle-commerce/pull/280
