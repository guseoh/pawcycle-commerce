# PS-002 PO 작업 보고서

## 작업 정보

- 작업 ID: `PS-002`
- 역할: Product Planner
- 기준 브랜치: `main`
- 작업 브랜치: `spec/po`
- PR 대상: `main`
- 선행 PR: `#4`
- 자동 병합: 하지 않음

## 작업 목적

PR `#4`로 승인된 PS-001 제품 범위와 Product Decision을 첫 번째 수직 MVP의 기능 요구사항, 인수 조건과 최소 추적성으로 구체화한다.

이번 작업은 요구사항 정의 작업이다. 도메인 상세 설계, API 계약, 데이터베이스 설계와 구현은 수행하지 않는다.

## 시작 전 상태

- PR `#4`: 2026-07-03 07:18:44Z 병합 확인
- `origin/main`: PS-001 제품 문서, 도식, PO → BE 인수인계, 보고서 존재 확인
- 이전 `spec/po`: PR `#4` 병합 후 원격 브랜치 삭제
- 새 `spec/po`: 최신 `origin/main`에서 재생성 후 `origin/spec/po`로 push
- 시작 전 작업 트리: clean
- force push: 하지 않음
- reset과 history rewrite: 하지 않음

## 입력 문서

- `AGENTS.md`
- `CONTRIBUTING.md`
- `docs/roles/product-planner.md`
- `.agents/skills/product-planner/SKILL.md`
- `docs/runbook/collaboration-automation.md`
- `docs/product/README.md`
- `docs/product/PS-001-first-mvp-domain-scope.md`
- `docs/product/diagrams/PS-001-first-mvp-flow.md`
- `docs/handoffs/PS-001/po-to-be.md`
- `docs/reports/PS-001/po-report.md`
- `docs/domain/glossary.md`
- `docs/domain/rules.md`

## 변경 범위

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/reports/PS-002/po-report.md`
- `docs/handoffs/PS-002/po-to-be.md`

기존 PS-001 제품 문서는 수정하지 않았다.

## 변경하지 않은 범위

- JPA Entity와 애플리케이션 코드
- ERD와 데이터베이스 스키마
- API 요청·응답 구조
- HTTP 상태와 오류 코드 확정
- 인증·인가 구현 방식
- UI 컴포넌트와 화면 설계
- 정기 주문 자동 생성
- 결제, 재고와 배송
- 구독 변경·건너뛰기·일시정지·재개·해지
- 구독 상태 모델
- 새로운 기술과 의존성 도입
- PS-001에 요구사항 ID 소급 적용
- 요구사항 추적성 전용 플랫폼 도입
- 요구사항 ID 자동 검증 CI 구현

## 작성한 산출물

### 요구사항 문서

`docs/product/PS-002-first-mvp-requirements.md`에 다음 요구사항을 작성했다.

- `REQ-PRODUCT-001` 상품 목록 조회
- `REQ-PRODUCT-002` 상품 상세와 SKU 조회
- `REQ-SUB-001` 구독 생성
- `REQ-SUB-002` 내 구독 목록 조회
- `REQ-SUB-003` 내 구독 상세 조회
- `REQ-SUB-004` 다음 주문 예정일 확인
- `REQ-AUTH-001` 인증된 회원만 구독 생성·조회
- `REQ-AUTH-002` 다른 회원의 구독 조회 차단

각 요구사항에는 하나 이상의 검증 가능한 `AC-*` 인수 조건을 작성했다.

### DOMAIN-001 인수인계

`docs/handoffs/PS-002/po-to-be.md`에 Backend Engineer가 DOMAIN-001에서 구체화할 입력을 정리했다.

- Member, Product, SKU, Subscription의 의미와 책임
- 상품과 SKU의 관계
- 회원과 구독의 관계
- 단일 SKU 제한
- 수량 1~10 불변 조건
- 구독 가능한 SKU만 허용하는 규칙
- 배송 주기 2주·4주·8주 표현
- 다음 주문 예정일 계산 책임
- Asia/Seoul과 날짜 단위 처리
- 자신의 구독만 조회할 수 있다는 규칙
- 엔티티 후보와 값 객체 후보
- DOMAIN-001에서 확정하거나 구현하지 않을 항목

## 확정 반영한 Product Decision

| 항목 | 결정 |
| --- | --- |
| 구독 수량 범위 | 1~10 |
| 상품 목록 표시 정보 | 상품 ID, 상품명, 대상 동물, 짧은 설명, SKU별 표시 가격, 구독 가능한 SKU 존재 여부 |
| 상품 상세 표시 정보 | 상품명, 상품 설명, 대상 동물, SKU 목록, SKU별 용량 또는 구성, SKU별 표시 가격, 구독 가능 여부, 선택 가능한 배송 주기 |
| 구독 생성 성공 조건 | 로그인한 회원, 존재하는 SKU, 구독 가능한 SKU, 수량 1~10, 배송 주기 2주·4주·8주 중 하나 |
| 구독 생성 실패 조건 | 비로그인, 존재하지 않는 SKU, 구독 불가능한 SKU, 수량 범위 위반, 허용되지 않은 배송 주기, 필수 입력 누락 |
| 내 구독 목록 표시 정보 | 구독 ID, 상품명, SKU, 수량, 배송 주기, 다음 주문 예정일 |
| 내 구독 상세 표시 정보 | 구독 ID, 상품명, SKU, SKU 표시 가격, 수량, 배송 주기, 구독 생성일, 다음 주문 예정일 |
| 비회원 접근 | 구독 생성·조회 화면 접근 시 로그인 화면으로 이동 |
| 다른 회원 구독 접근 | 조회할 수 없는 구독으로 처리 |
| 날짜 화면 표시 | `YYYY. M. D.` |
| 구독 상태 모델 | 첫 번째 MVP에서 사용하지 않음 |

## 후속 작업으로 넘긴 항목

### DOMAIN-001

- Member, Product, SKU, Subscription의 책임과 관계
- 구독 가능 여부의 도메인 표현
- 수량 1~10과 배송 주기 2주·4주·8주의 도메인 표현
- 다음 주문 예정일 계산 책임
- 날짜 값 객체 필요성
- Asia/Seoul 기준 날짜 단위 처리
- 회원과 구독의 소유 관계
- 전역 구독 용어와 첫 번째 MVP 단일 SKU 제한의 정합성

### API-001

- API 요청·응답 구조
- HTTP 상태
- 오류 코드
- 오류 응답 JSON
- 날짜 최종 표현
- 인증 실패와 다른 회원 구독 접근의 API 표현

## 검증 결과

| 검증 | 명령 | 결과 |
| --- | --- | --- |
| 작업 트리 확인 | `git status --short --branch` | 통과. `spec/po...origin/spec/po`, PS-002 세 파일 추가 |
| 공백 오류 확인 | `git diff --cached --check` | 통과 |
| 변경 통계 확인 | `git diff --cached --stat` | 통과. PS-002 세 산출물만 포함 |
| 산출물 확인 | `Write-Output 'PS-002' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| 커밋 메시지 확인 | `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(product): PS-002 MVP 요구사항 정리"` | 통과 |
| 요구사항 ID 중복 확인 | 요구사항 제목과 인수 조건 정의 줄 기준 REQ/AC ID 집계 | 통과. 정의 ID 58개 중복 없음 |
| PR 생성 후 CI | `gh pr checks 5 --watch --interval 5` | 통과. Repository Validation, Collaboration Notification 성공 |

## 위험과 제한

- 이 문서는 요구사항 정의이며, 도메인 모델과 API 계약을 확정하지 않는다.
- HTTP 상태, 오류 코드와 오류 응답 JSON을 확정하지 않았으므로 API-001에서 결정이 필요하다.
- 다음 주문 예정일 계산 책임과 날짜 값 객체 필요성은 DOMAIN-001에서 구체화해야 한다.
- 재고, 품절과 일반 판매 상태는 이번 구독 생성 조건에 연결하지 않는다.
- 첫 번째 MVP에서는 구독 상태 모델을 사용하지 않는다.
- 정기 주문 자동 생성이 없으므로 다음 주문 예정일은 표시용 예정 정보다.

## Git 결과

- 브랜치: `spec/po`
- 주요 변경 커밋: `aa2fed1f2609fddbae8e43e3fecf59c019f80e12`
- 주요 변경 커밋 메시지: `docs(product): PS-002 MVP 요구사항 정리`
- push: `origin/spec/po` 반영 완료
- PR: `#5` 생성 완료
- 자동 병합: 하지 않음

보고서 자신을 최종 갱신하는 커밋 SHA는 재귀적으로 기록하지 않는다.

## PR 상태

- PR 번호: `#5`
- PR 제목: `docs(product): PS-002 MVP 요구사항 정리`
- PR 방향: `spec/po` → `main`
- PR 상태: Open, Ready for review
- mergeable: MERGEABLE
- Repository Validation: 통과
- Collaboration Notification: 통과
- 자동 병합: 하지 않음

## 다음 작업

### DOMAIN-001

- 역할: Backend Engineer
- 작업 브랜치: `feat/be`
- 목표: PS-002 요구사항을 바탕으로 첫 번째 MVP 도메인 책임, 불변 조건, 값 표현과 경계를 구체화

### API-001

- 역할: Backend Engineer 또는 Tech Lead
- 목표: PS-002 요구사항을 바탕으로 API 요청·응답, HTTP 상태, 오류 코드와 날짜 표현을 확정
