---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 259
status: merged
taskId: DATA-005
author: guseoh
base: main
head: feat/be/MVP4-DATA-005
mergedAt: 2026-08-30T09:17:18Z
mergeCommit: c516d0e9bb67926afcd95148a49719b55997a373
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #259 feat(catalog): Canonical Customer Catalog 공통 적재

## 작업 목적

## 작업  - 작업 ID: MVP4-DATA-005 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer - 대체 관계: Draft PR #258의 Ready 전환이 GitHub connector GraphQL 오류로 실패해, 동일 HEAD `7a2efa57641d9368fb629f0e7f88631e8a4597a8`을 그대로 사용하는 비-Draft 대체 PR입니다. #258은 코드 변경 없이 닫았습니다.  ## 목적과 범위  - 목적: 기존 Data V1과 Customer Catalog V3를 별도 검증용/실사용 데이터로 복제하지 않고 하나의 Canonical Customer Catalog로 정의합니다. - 변경 범위: Customer Catalog V1+V3 조합 service, profile-neutral V3 importer, 기존 local V3 compatibility wrapper, MySQL 통합 테스트, 데이터 계약 문서. -…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/application/CustomerCatalogImportService.java
- backend/src/main/java/com/pawcycle/backend/catalog/application/CustomerCatalogV3ImportService.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalCustomerCatalogV3FixtureService.java
- backend/src/test/java/com/pawcycle/backend/catalog/application/CustomerCatalogImportServiceIntegrationTests.java
- docs/data/MVP4-DATA-005-canonical-customer-catalog.md

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

https://github.com/guseoh/pawcycle-commerce/pull/259
