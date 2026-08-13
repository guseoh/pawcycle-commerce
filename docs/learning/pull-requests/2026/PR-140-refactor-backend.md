---
type: pull-request
repository: guseoh/pawcycle-commerce
pr: 140
status: merged
taskId: ARCH-010
author: guseoh
base: main
head: refactor/be-MVP3-CLEANUP-002
mergedAt: 2026-08-13T14:09:41Z
mergeCommit: 224808f34088cdbba12a5b9f3c670ffb3261a002
labels:
  - 기록 없음
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #140 refactor(backend): 인증 책임 경계 정리

## 작업 목적

## 작업  - 작업 ID: MVP3-CLEANUP-002 - 작업 등급: 고위험 - 실행 구분: 저장소 변경 - 역할: Backend  ## 목적과 범위  - 목적: 기존 세션 인증 계약을 유지하면서 credential 검증 책임을 Spring Security의 AuthenticationManager/AuthenticationProvider 경계로 이동한다. - 변경 범위: MemberAuthenticationProvider가 회원 조회, BCrypt 비교, unknown-user dummy hash, USER/ADMIN authority 생성을 담당한다. AuthApplicationService는 인증 성공 이후 session fixation 방어, CSRF rotation, SecurityContext 저장과 logout lifecycle을 조율한다. 관련 회귀 테스트와 ARCH-010을 포함한다. - 제외 범위: API/DB/migration, JWT/OAuth2/CORS,…

## 주요 변경

기록 없음

## 변경 파일

- backend/src/main/java/com/pawcycle/backend/common/security/SecurityConfig.java
- backend/src/main/java/com/pawcycle/backend/member/application/AuthApplicationService.java
- backend/src/main/java/com/pawcycle/backend/member/application/InvalidCredentialsException.java
- backend/src/main/java/com/pawcycle/backend/member/application/MemberAuthenticationProvider.java
- backend/src/main/java/com/pawcycle/backend/member/application/MemberCredentialAuthenticator.java
- backend/src/test/java/com/pawcycle/backend/member/application/AuthApplicationServiceTests.java
- backend/src/test/java/com/pawcycle/backend/member/application/MemberAuthenticationProviderTests.java
- backend/src/test/java/com/pawcycle/backend/member/application/MemberCredentialAuthenticatorTests.java
- docs/adr/ARCH-010-authentication-provider-ownership.md

## 리뷰 결과

- COMMENTED: 5

## CI 및 검증

- publish: queued

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

기록 없음

## 연결된 Issue

기록 없음

## GitHub 링크

https://github.com/guseoh/pawcycle-commerce/pull/140
