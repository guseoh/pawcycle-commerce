---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 44
status: merged
taskId: FOUNDATION-004
author: guseoh
base: main
head: feat/be
mergedAt: 2026-07-14T12:03:15Z
mergeCommit: 06e3522d98448e08fd84cd36d5a906e670a2eee0
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #44 feat(backend): 로컬 QA bootstrap 구성

## 작업 목적

## 작업 ID  FOUNDATION-004  ## 변경 내용  - `local-integration` profile과 명시적 활성화 설정이 함께 있을 때만 QA bootstrap 실행 - 환경 변수 credential을 기존 `PasswordEncoder`로 실행 시 변환하고 전용 QA 회원·상품·SKU를 멱등 생성 - 충돌·모호한 fixture 후보 감지 시 시작 중단 및 전체 트랜잭션 rollback - 별도 reset 설정에서 전용 QA 회원의 구독만 제한적으로 삭제 - 비활성화·profile 격리·credential 검증·멱등성·충돌·reset 범위를 보호하는 단위 및 MySQL 통합 테스트 추가 - 리뷰에서 확인된 local profile cookie 보안 격리와 충돌 회귀 범위 보강  ## 검증  - `py scripts/validate-task-artifacts.py --task-id FOUNDATION-004`: 통과 - `git diff --check`: 통과 -…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/product/domain/Product.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/infra/ProductRepository.java
- backend/src/main/java/com/pawcycle/backend/catalog/sku/domain/Sku.java
- backend/src/main/java/com/pawcycle/backend/catalog/sku/infra/SkuRepository.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfiguration.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapException.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapProperties.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapService.java
- backend/src/main/java/com/pawcycle/backend/member/application/EmailNormalizer.java
- backend/src/main/java/com/pawcycle/backend/member/infra/MemberRepository.java
- backend/src/main/java/com/pawcycle/backend/subscription/infra/SubscriptionRepository.java
- backend/src/main/resources/application-local-integration.properties
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfigurationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapServiceTests.java
- docs/handoffs/FOUNDATION-004/be-to-sre.md
- docs/reports/FOUNDATION-004/be-report.md

## 리뷰 결과

- COMMENTED: 3

## CI 및 검증

기록 없음

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/44
