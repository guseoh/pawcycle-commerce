---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 70
status: merged
taskId: OPS-019
author: guseoh
base: main
head: feat/be
mergedAt: 2026-07-28T07:41:11Z
mergeCommit: 18d50eed0ea75793cce9608446738cda5458a246
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #70 feat(backend): OPS-019 운영 검증 회원 명령 추가

## 작업 목적

## 작업 정보  - 작업 ID: OPS-019 - 작업 등급: 고위험 - 역할: Backend - 작업 브랜치: feat/be - 대상 브랜치: main  ## 관련 이슈  - 해당 없음  ## 목적  Production 인증·session Smoke 전용 회원 한 명을 기존 인증 규칙으로 생성할 one-shot non-web Backend 명령 기반을 준비합니다. 실제 Production DB 연결과 회원 생성은 수행하지 않습니다.  ## 왜 지금 해야 하나요?  - 현재 사용자 가치 또는 위험: Production 회원과 승인 credential이 없어 OPS-018 session Smoke를 안전하게 실행할 수 없습니다. - 이번 단계에서 하지 않으면 생기는 문제: 직접 SQL·외부 hash·임시 endpoint 같은 기존 인증 규칙 우회 가능성이 남습니다. - 이번 변경에 필요한 근거: 사용자가 stdin 입력, 기존 normalizer·encoder·repository·…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/PawcycleBackendApplication.java
- backend/src/main/java/com/pawcycle/backend/common/security/SecurityConfig.java
- backend/src/main/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberBootstrap.java
- backend/src/main/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberCommand.java
- backend/src/main/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberCreationException.java
- backend/src/main/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberMaintenanceConfiguration.java
- backend/src/main/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberService.java
- backend/src/test/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberBootstrapProcessTests.java
- backend/src/test/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberBootstrapTests.java
- backend/src/test/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberCommandTests.java
- backend/src/test/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberMaintenanceConfigurationTests.java
- backend/src/test/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberServiceIntegrationTests.java
- backend/src/test/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberServiceTests.java
- docs/handoffs/OPS-019/be-to-sre.md
- docs/reports/OPS-019/be-report.md

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

https://github.com/guseoh/pawcycle-commerce/pull/70
