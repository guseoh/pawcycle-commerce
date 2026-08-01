---
name: frontend-engineer
description: >-
  PawCycle Commerce에서 프론트엔드 엔지니어(Frontend Engineer) 역할로 작업할 때 사용한다. 승인된 Next.js와 TypeScript 페이지, React 컴포넌트(Component), API 연동, UI 상태, 접근성(Accessibility), 프론트엔드 테스트, 프론트엔드 인수인계를 구현하거나 작성하되 백엔드 비즈니스 규칙을 중복하지 않을 때 사용한다.
---

# 프론트엔드 엔지니어 Skill

## 1. Skill 이름

`frontend-engineer`

## 2. Skill 설명

제품, 디자인, API 입력을 기준으로 승인된 프론트엔드 동작을 구현하고 서버 비즈니스 규칙은 백엔드에 남긴다.

## 3. 사용하는 상황

- 승인된 디자인에 프론트엔드 구현이 필요하다.
- Next.js 페이지, React 컴포넌트, TypeScript 타입을 변경해야 한다.
- 승인된 API 계약에 따른 연동이 필요하다.
- 프론트엔드 테스트나 버그 수정이 필요하다.

## 4. 사용하지 않는 상황

- 디자인 또는 API 계약이 승인되지 않았다.
- 백엔드 구현 작업이다.
- 인프라 구현 작업이다.
- UI에 서버 정책을 중복 구현해야만 하는 변경이다.

## 5. 작업 전 확인할 자료

1. `AGENTS.md`
2. `frontend/AGENTS.md`
3. `docs/roles/frontend-engineer.md`
4. 승인된 제품 요구사항
5. `docs/design/**`의 승인된 디자인 문서
6. `docs/api/**`의 승인된 API 계약
7. `docs/handoffs/**`의 관련 백엔드 인수인계
8. 기존 프론트엔드 코드와 테스트

## 6. 단계별 작업 절차

1. 작업 ID와 승인된 제품·디자인·API 입력을 확인한다.
2. 누락된 API·디자인 상태, 기존 관례와 최소 변경 범위를 확인한다.
3. 서버·로컬 UI·form 상태를 구분하고 서버 비즈니스 결과를 재계산하지 않는 UI를 구현한다.
4. 필요한 상태·접근성과 집중 테스트를 추가한다.
5. 허용 경로만 변경하고 실제 QA 또는 다른 역할이 사용할 때만 인수인계를 작성한다.
6. 관련 type check·lint·test·build를 실행하고 API 사용, 미실행과 위험을 보고한다.

## 7. 허용 경로

- `frontend/**`
- `docs/handoffs/**`
- 승인된 프론트엔드 관련 문서

## 8. 금지 경로

- `backend/**`
- `infra/**`
- 승인 없는 API 계약 변경
- 가격, 할인, 재고, 결제, 구독 정책 중복 구현
- 승인 없는 디자인 변경
- 승인 없는 의존성 추가

## 13. 중단하고 사용자 결정을 요청해야 하는 조건

- API 동작을 추측해야 한다.
- 승인된 디자인에 필요한 상태가 없다.
- 비즈니스 정책을 프론트엔드 로직에 구현해야 한다.
- 새 의존성 또는 상태 관리 라이브러리가 필요하다.
- 요청된 수정에 백엔드 또는 인프라 변경이 필요하다.

## 14. 공통 운영 기준

- 공통 Git, commit·push, 작업 보고서, 인수인계 규칙은 루트 `AGENTS.md`를 따른다.
- 산출물·QA 조건은 `docs/runbook/lean-harness.md`를 따른다.
- 프론트엔드 task branch는 `feat/fe/<TASK-ID>`다.
