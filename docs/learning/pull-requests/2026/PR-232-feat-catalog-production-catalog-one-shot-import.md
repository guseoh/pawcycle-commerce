---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 232
status: merged
taskId: DATA-002
author: guseoh
base: main
head: feat/be/MVP4-DATA-002
mergedAt: 2026-08-26T06:03:20Z
mergeCommit: 8c8783b3edd77738a94888796bb3a192b3eace95
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #232 feat(catalog): Production Catalog one-shot Import 추가

## 작업 목적

Closes #231  ## 작업  - 작업 ID: `MVP4-DATA-002` - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend + Platform/SRE  ## 목적과 범위  - 목적: 기존 Demo Catalog manifest 적재 로직을 공용화하고, Production에서는 startup 자동 seed 없이 명시적 one-shot `validate/apply` 경로로만 Catalog 데이터를 적재할 수 있게 한다. 이번 PR은 저장소 준비까지만 수행한다. - 변경 범위: 공용 `DemoCatalogManifestImportService`, Local bootstrap 호환 wrapper, Production import command/configuration, `import-demo-catalog.sh`, Production contract validation, 통합 테스트, Runbook. - 제외 범위: 실제 Production 데이터 appl…

## 주요 변경

기록 없음

## 변경 파일

- .github/workflows/validate-conventions.yml
- backend/src/main/java/com/pawcycle/backend/PawcycleBackendApplication.java
- backend/src/main/java/com/pawcycle/backend/catalog/application/CatalogManifestImportException.java
- backend/src/main/java/com/pawcycle/backend/catalog/application/DemoCatalogManifestImportService.java
- backend/src/main/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportCommand.java
- backend/src/main/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportConfiguration.java
- backend/src/main/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportResultHolder.java
- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalCommerceDemoFixtureService.java
- backend/src/test/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportCommandTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/maintenance/ProductionDemoCatalogImportConfigurationTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductDiscoveryApiIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalCommerceDemoFixtureServiceIntegrationTests.java
- docs/runbook/MVP4-DATA-002-demo-catalog-import.md
- docs/runbook/README.md
- infra/production/import-demo-catalog.sh
- infra/production/release-common.sh
- infra/production/test-demo-catalog-import.sh

## 리뷰 결과

- COMMENTED: 13

## CI 및 검증

- Discord collaboration report: in_progress
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

https://github.com/guseoh/pawcycle-commerce/pull/232
