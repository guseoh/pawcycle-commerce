---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 219
status: merged
taskId: API-009
author: guseoh
base: main
head: feat/be/MVP4-REC-BE-001
mergedAt: 2026-08-24T13:13:12Z
mergeCommit: 0ef1805a19593dd97aa8208906fe491d656fb9a5
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #219 feat(be): AI 상품 추천과 탐색 구현

## 작업 목적

## 작업  - 작업 ID: MVP4-REC-001 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend Engineer  ## 목적과 범위  - 목적: 인증 회원의 Pet 기반 상품 추천과 공개 상품 탐색 필터를 제공한다. - 변경 범위: 추천 API·AI 안전 fallback·Micrometer metric, Product 검색/Category 응답/cache key v2, 관련 테스트와 ADR/API/FE 인수인계 - 제외 범위: Flyway·DB/index, 추천 전용 Redis, Queue/Kafka, frontend/infra, 실제 OpenAI API Key·Production 활성화  ## 결정과 영향  - 중요한 결정: 후보 검증과 PII·의료 경계는 서버가 맡고, AI는 최대 10개 서버 후보의 순서와 짧은 한국어 이유만 생성한다. 외부 AI 호출 중 DB transaction을 유지하지 않고, 호출 후 최신 구매 가능 상태를 재검증한다. …

## 주요 변경

기록 없음

## 변경 파일

- backend/build.gradle
- backend/src/main/java/com/pawcycle/backend/catalog/product/api/ProductController.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductDetailView.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListCache.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListReader.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductListView.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/application/ProductQueryService.java
- backend/src/main/java/com/pawcycle/backend/catalog/product/infra/ProductRepository.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationAiClient.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationAiConfiguration.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationCandidate.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationController.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationExceptionHandler.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationMetrics.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationPetNotFoundException.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationRepository.java
- backend/src/main/java/com/pawcycle/backend/recommendation/RecommendationService.java
- backend/src/main/resources/application.properties
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductApiIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/catalog/product/api/ProductControllerTests.java
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

https://github.com/guseoh/pawcycle-commerce/pull/219
