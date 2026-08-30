---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 260
status: merged
taskId: DATA-006
author: guseoh
base: main
head: feat/be/MVP4-DATA-006
mergedAt: 2026-08-30T09:59:02Z
mergeCommit: 87fc4b78817b68428c777cd98bed609cc5647d77
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #260 feat(catalog): Production Customer Catalog 대상 지원

## 작업 목적

## 작업  - 작업 ID: `MVP4-DATA-006` - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: 기존 Production one-shot Catalog command의 안전 경계를 유지하면서, `demo` 외에 Canonical `customer` Catalog를 명시적으로 선택해 `validate/apply`할 수 있게 합니다. - 변경 범위: Production Catalog command target 검증, configuration의 Customer Catalog service 연결, 결과 summary holder 일반화, 관련 backend 테스트. - 제외 범위: `infra/**`, Production script/runbook 변경, 실제 Production DB apply, schema/migration, API/Frontend 변경, Secret 변경.  ## 결정과 영향  - …

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportCommand.java
- backend/src/main/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportConfiguration.java
- backend/src/main/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportResultHolder.java
- backend/src/test/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportCommandTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportConfigurationTests.java

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

https://github.com/guseoh/pawcycle-commerce/pull/260
