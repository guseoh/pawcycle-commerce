---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 238
status: merged
taskId: API-009
author: guseoh
base: main
head: feat/be/MVP4-CATALOG-002
mergedAt: 2026-08-27T08:25:24Z
mergeCommit: a050ea9a792c276b264cc1edd6a2ef16f8877aaf
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #238 feat(catalog): 공개 상품 탐색 메타데이터 API 추가

## 작업 목적

## 작업  - 작업 ID: MVP4-CATALOG-002 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: Customer Frontend가 공개 Catalog 탐색 조건을 구성할 수 있는 discovery metadata API 추가 - 변경 범위: `GET /api/catalog/discovery`, 공개 보안 허용, Catalog 조회 record/service/controller, 통합 회귀 테스트, API-009 additive 문서 - 제외 범위: Frontend, schema/migration, dependency, Catalog 데이터 변경, Redis/Kafka/Search, Production 실행  ## 검증  - 실행 결과: discovery targeted integration 2개, Category/Product/Security 관련 회귀 22개, compileJava/compile…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/catalog/discovery/api/CatalogDiscoveryController.java
- backend/src/main/java/com/pawcycle/backend/catalog/discovery/application/CatalogDiscoveryQueryService.java
- backend/src/main/java/com/pawcycle/backend/catalog/discovery/application/CatalogDiscoveryView.java
- backend/src/main/java/com/pawcycle/backend/common/security/SecurityConfig.java
- backend/src/test/java/com/pawcycle/backend/catalog/discovery/api/CatalogDiscoveryApiIntegrationTests.java
- docs/api/API-009-mvp4-recommendation-and-product-discovery-api.md

## 리뷰 결과

- COMMENTED: 4

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

https://github.com/guseoh/pawcycle-commerce/pull/238
