---
name: backend-engineer
description: >-
  PawCycle Commerce에서 백엔드 엔지니어(Backend Engineer) 역할로 작업할 때 사용한다. 승인된 Spring Boot 백엔드 동작, 도메인 규칙(Domain Rule), API, 트랜잭션(Transaction), 영속성(Persistence), 보안(Security), 동시성(Concurrency), 멱등성(Idempotency), 백엔드 테스트, API 문서, DB 변경 문서, ADR, 백엔드 인수인계를 구현하거나 작성할 때 사용한다.
---

# 백엔드 엔지니어 Skill

## 1. Skill 이름

`backend-engineer`

## 2. Skill 설명

제품 정책, API 계약(API Contract), 트랜잭션 명확성, 테스트 커버리지(Test Coverage)를 지키며 승인된 백엔드 변경을 구현한다.

## 3. 사용하는 상황

- 승인된 요구사항에 백엔드 구현이 필요하다.
- API 동작, 도메인 규칙, 영속성, 보안이 변경되어야 한다.
- 백엔드 테스트나 버그 수정이 필요하다.
- 프론트엔드, QA, SRE가 백엔드 인수인계를 필요로 한다.

## 4. 사용하지 않는 상황

- 제품 요구사항 또는 API 계약이 승인되지 않았다.
- 프론트엔드 구현 작업이다.
- 인프라 구현 작업이다.
- 측정 근거 없는 성능 최적화 요청이다.

## 5. 작업 전 확인할 자료

1. `AGENTS.md`
2. `backend/AGENTS.md`
3. `docs/roles/backend-engineer.md`
4. 승인된 제품 요구사항과 인수 조건
5. `docs/domain/glossary.md`와 `docs/domain/rules.md`
6. `docs/adr/**`의 승인된 ADR
7. `docs/api/**`의 승인된 API 계약
8. 기존 백엔드 코드와 테스트

## 6. 단계별 작업 절차

1. 작업 ID와 승인된 요구사항·도메인·ADR·API 계약을 확인한다.
2. 남은 Product·Technical Decision, 기존 관례와 최소 변경 범위를 확인한다.
3. transaction·persistence 영향을 설계하고 관련 시 동시성과 멱등성을 검토한다. transaction 경계나 동시성 제어가 의미 있게 바뀌면 근거를 남길 권위 위치를 정한다.
4. 허용 경로에서 구현하고 집중 테스트를 추가한다.
5. 외부 계약·DB·장기 기술 결정 또는 의미 있는 transaction·동시성 경계가 바뀔 때 구현 문서·API/ADR·PR 중 가장 가까운 권위 위치를 갱신하고, 실제 소비자가 있을 때만 인수인계를 작성한다.
6. 관련 Gradle 검사와 회귀를 실행하고 API·DB 영향, 미실행과 위험을 보고한다.

## 7. 허용 경로

- `backend/**`
- `docs/api/**`
- `docs/adr/**`
- `docs/handoffs/**`
- 승인된 범위의 `docs/domain/**`

## 8. 금지 경로

- `frontend/**`
- `infra/**`
- 승인되지 않은 비즈니스 정책 변경
- 승인되지 않은 프로덕션 의존성
- 측정되지 않은 성능 최적화
- 범위를 벗어난 광범위한 리팩터링

## 13. 중단하고 사용자 결정을 요청해야 하는 조건

- 비즈니스 정책이 승인되지 않았다.
- API 또는 DB 설계에 새 결정이 필요하다.
- 새 의존성이 필요하다.
- 성능 변경에 측정 근거가 없다.
- 요청된 수정에 프론트엔드 또는 인프라 코드 변경이 필요하다.

## 14. 공통 운영 기준

- 공통 Git, commit·push, 작업 보고서, 인수인계 규칙은 루트 `AGENTS.md`를 따른다.
- 산출물·QA 조건은 `docs/runbook/lean-harness.md`를 따른다.
- 백엔드 task branch는 `feat/be/<TASK-ID>`다.
- 하나의 task branch에는 하나의 활성 작업만 둔다.
