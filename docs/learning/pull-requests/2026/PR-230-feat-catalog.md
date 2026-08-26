---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 230
status: merged
taskId: DATA-001
author: guseoh
base: main
head: feat/be/MVP4-DATA-001
mergedAt: 2026-08-26T01:34:04Z
mergeCommit: cf8e987eb059b4d0a0931a7af4df5561af084604
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #230 feat(catalog): 검증용 데모 카탈로그 확장

## 작업 목적

## 작업  - 작업 ID: MVP4-DATA-001 - 작업 등급: 일반 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: UI/UX 및 Commerce 흐름 검증에 충분한 임시 Customer Demo Catalog Dataset 확장 - 변경 범위: 기존 12개 Product/3개 Plan을 보존하고 신규 Product 20개, SKU, Plan 3개를 versioned manifest에 additive하게 추가; bootstrap/discovery 회귀 테스트 보강 - 데이터 성격: 장기 운영 Catalog가 아니라 향후 대량 데이터 도입 전 UI/UX·기능 확인용 임시 Dataset - 제외 범위: frontend, DB migration/schema, Product Detail Content, Review/Rating/Q&A, Recommendation algorithm, Toss, Production/AWS/RDS, …

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/foundation/bootstrap/LocalCommerceDemoFixtureService.java
- backend/src/main/resources/catalog/demo-catalog.json
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductDiscoveryApiIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/foundation/bootstrap/LocalCommerceDemoFixtureServiceIntegrationTests.java

## 리뷰 결과

기록 없음

## CI 및 검증

- Discord collaboration report: queued
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

https://github.com/guseoh/pawcycle-commerce/pull/230
