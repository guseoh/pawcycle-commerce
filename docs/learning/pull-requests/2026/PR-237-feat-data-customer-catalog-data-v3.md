---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 237
status: merged
taskId: DATA-004
author: guseoh
base: main
head: feat/be/MVP4-DATA-004
mergedAt: 2026-08-27T06:50:31Z
mergeCommit: 40751b1f96c8f77f77c865866956d90fcd0f53a6
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #237 feat(data): Customer Catalog Data V3 구축

## 작업 목적

## 작업  - 작업 ID: MVP4-DATA-004 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: Customer Frontend QA를 위한 curated Catalog 100개 구성 - 변경 범위: 별도 V3 manifest, local-integration opt-in fixture, 회귀 테스트, 데이터 사용 문서 - 제외 범위: V1/V2 재작성, Frontend, schema/migration, dependency, 실제 운영 실행  ## 결정과 영향  - 중요한 결정: V1 뒤에 V3를 transaction으로 적용하며 business key 충돌 시 덮어쓰지 않고 중단한다. 기존 inventory available/reserved/version은 보존한다. - 영향 영역: 신규 Product 68 / SKU 124 / Brand 9 / Category 23. V1 포함 최종 Product …

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductDiscoveryReader.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalCustomerCatalogV3Configuration.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalCustomerCatalogV3FixtureService.java
- backend/src/main/resources/catalog/customer-catalog-v3.json
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalCustomerCatalogV3FixtureDriftIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalCustomerCatalogV3FixtureIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalQaBootstrapConfigurationTests.java
- docs/data/MVP4-DATA-004-customer-catalog-data-v3.md

## 리뷰 결과

- COMMENTED: 3

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

https://github.com/guseoh/pawcycle-commerce/pull/237
