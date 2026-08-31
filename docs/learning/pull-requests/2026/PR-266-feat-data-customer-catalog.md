---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 266
status: merged
taskId: DATA-008
author: guseoh
base: main
head: feat/data/MVP4-DATA-008-catalog-realism
mergedAt: 2026-08-31T01:14:37Z
mergeCommit: 1e916e285b8d552b8f4459cb9b63f2867306dce9
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #266 feat(data): Customer Catalog 현실성 보정 적용

## 작업 목적

## 작업  - 작업 ID: MVP4-DATA-008 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: Production Visual QA에서 확인된 Customer Catalog의 고객 노출 브랜드명·이미지 현실성 문제를, 기존 V1/V3 importer의 충돌 안전성을 유지한 채 보정한다. - 변경 범위: `CustomerCatalogRealismCorrectionService`와 guarded manifest를 추가하고 `CustomerCatalogImportService`의 V1 → V3 → realism correction 순서를 연결했다. Product 4/5/14/47/97/98의 thumbnail 및 검증된 V3 MAIN image를 stable key와 `expectedBefore → desiredAfter`로 보정하고, 브랜드 slug는 유지한 채 표시명을 `PawCycle`로 변경한다.…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/application/CustomerCatalogImportService.java
- backend/src/main/java/com/pawcycle/backend/catalog/application/CustomerCatalogRealismCorrectionService.java
- backend/src/main/java/com/pawcycle/backend/catalog/application/CustomerCatalogV3ImportService.java
- backend/src/main/java/com/pawcycle/backend/catalog/application/DemoCatalogManifestImportService.java
- backend/src/main/resources/catalog/customer-catalog-realism-v1.json
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

https://github.com/guseoh/pawcycle-commerce/pull/266
