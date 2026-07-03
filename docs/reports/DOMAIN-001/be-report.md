# DOMAIN-001 BE 작업 보고서

## 작업 정보

- 작업 ID: `DOMAIN-001`
- 역할: Backend Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `feat/be`
- PR 대상: `main`
- 선행 PR: `#6 fix(validation): DOMAIN과 API 작업 ID 인식 추가`
- 자동 병합: 하지 않음

## 작업 목적

PS-001과 PS-002에서 승인된 첫 번째 수직 MVP 구독 흐름을 기준으로 회원, 상품, SKU, 구독의 도메인 의미와 책임을 정리했다.

이번 작업은 도메인 발견과 설계 문서화 작업이다. Java, Spring, JPA, API, 데이터베이스와 애플리케이션 코드는 작성하지 않았다.

## 시작 전 상태

- PR `#6`: `main` 병합 확인
- `origin/main`: 최신 원격 상태 확인
- `main`: PS-002 요구사항 문서와 PS-002, BOOTSTRAP-006 인수인계 문서 존재 확인
- 새 `feat/be`: 최신 `origin/main`에서 생성 후 `origin/feat/be`로 push
- 시작 전 작업 트리: clean
- force push: 하지 않음
- reset과 history rewrite: 하지 않음

## 입력 문서

- `AGENTS.md`
- `backend/AGENTS.md`
- `docs/roles/backend-engineer.md`
- `.agents/skills/backend-engineer/SKILL.md`
- `docs/product/PS-001-first-mvp-domain-scope.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/handoffs/PS-001/po-to-be.md`
- `docs/handoffs/PS-002/po-to-be.md`
- `docs/handoffs/BOOTSTRAP-006/tl-to-be.md`
- `docs/domain/glossary.md`
- `docs/domain/rules.md`

## 변경 범위

- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/domain/glossary.md`
- `docs/domain/rules.md`
- `docs/reports/DOMAIN-001/be-report.md`
- `docs/handoffs/DOMAIN-001/be-to-tl.md`

## 변경하지 않은 범위

- `docs/product/**`
- Java, Spring, JPA, Controller, Service, Repository 코드
- 데이터베이스, Flyway, 물리 ERD
- API 요청·응답, HTTP 상태, 오류 JSON
- `backend/**`, `frontend/**`, `infra/**`
- PS-001 또는 PS-002 요구사항
- 구독 상태, 결제, 재고, 배송, 정기 주문 자동 생성 설계

## 설계 결과

- 회원과 고객 용어를 구분했다. 첫 번째 MVP에서는 로그인한 회원이 구독 고객 역할을 수행한다.
- Product는 사용자에게 보이는 공통 상품 정보, SKU는 실제 구독 선택 단위로 정리했다.
- 첫 번째 MVP에서 구독 하나는 SKU 하나만 대상으로 한다.
- Subscription은 소유자, SKU 하나, 수량, 배송 주기, 생성일, 다음 주문 예정일을 가진다.
- SKU는 구독 가능 여부를 제공해야 한다.
- SubscriptionQuantity는 수량 1~10을 보장하는 값 객체 설계로 채택했다.
- DeliveryCycle은 2주, 4주, 8주와 날짜 계산 기간을 보장하는 값 객체 설계로 채택했다.
- 다음 주문 예정일 계산은 구독 도메인 책임으로 정리했다.
- 인증 회원 식별, SKU 조회, API 오류 표현과 HTTP 상태는 애플리케이션 또는 API 경계 책임으로 분리했다.

## 요구사항 추적성

| 요구사항 | 설계 반영 |
| --- | --- |
| `REQ-PRODUCT-001` | Product와 SKU 표시 책임으로 반영 |
| `REQ-PRODUCT-002` | 상품 상세의 SKU 목록, 구독 가능 여부, 배송 주기 표시 책임으로 반영 |
| `REQ-SUB-001` | 구독 생성 불변 조건과 실패 책임으로 반영 |
| `REQ-SUB-002` | 자신의 구독 목록 조회와 소유자 식별 의미로 반영 |
| `REQ-SUB-003` | 구독 상세 구성과 단일 SKU 제한으로 반영 |
| `REQ-SUB-004` | 다음 주문 예정일 계산 책임과 배송 예정일 구분으로 반영 |
| `REQ-AUTH-001` | 인증 회원 식별을 애플리케이션 경계 책임으로 분리 |
| `REQ-AUTH-002` | Subscription 소유자 의미와 조회 차단 책임 분리로 반영 |

## Accepted Domain Design

- 회원과 고객은 관점에 따라 구분한다.
- Product는 상품 공통 정보, SKU는 구독 선택 단위로 본다.
- SKU는 구독 가능 여부를 제공한다.
- Subscription은 소유자, SKU 하나, 수량, 배송 주기, 생성일, 다음 주문 예정일을 가진다.
- SubscriptionQuantity와 DeliveryCycle을 값 객체 설계로 채택한다.
- 다음 주문 예정일 계산은 구독 도메인 내부 책임으로 둔다.
- 첫 번째 MVP에는 구독 상태 모델을 두지 않는다.

## Proposed Technical Design

- Product와 SKU Aggregate 경계는 구현 설계에서 결정한다.
- Subscription이 SKU 전체를 참조할지 SKU 식별자와 구독 가능 의미만 참조할지 구현 설계에서 결정한다.
- 날짜 공급 추상화는 구현 단계에서 결정한다.
- 구독 가능 여부의 Boolean, enum, 값 객체 표현은 구현 또는 데이터 설계에서 결정한다.

## Deferred

- 복수 SKU 구독
- 구독 상태 모델
- 구독 변경, 건너뛰기, 일시정지, 재개, 해지
- 정기 주문 자동 생성
- 결제, 재고, 배송 설계
- DB 테이블, 컬럼, FK와 물리 ERD
- API 요청·응답, HTTP 상태와 오류 JSON
- 인증 토큰, 세션과 Spring Security 설계

## 검증 결과

| 검증 | 명령 | 결과 |
| --- | --- | --- |
| 브랜치와 작업 트리 확인 | `git status --short --branch` | 통과 |
| 공백 오류 확인 | `git diff --check` | 통과 |
| 변경 통계 확인 | `git diff --stat` | 통과 |
| 작업 ID 검증 회귀 | `py -3 scripts/test-validate-task-artifacts.py` | 통과 |
| DOMAIN-001 산출물 검사 | `Write-Output 'DOMAIN-001' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| API-001 stdin 감지 | 임시 산출물 디렉터리를 사용한 `API-001` stdin 검증 | 통과 |
| 커밋 메시지 검증 | `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(domain): DOMAIN-001 구독 도메인 설계"` | 통과 |
| 요구사항 추적성 확인 | 8개 PS-002 요구사항 ID 존재 확인 | 통과 |
| 미완성 표기 확인 | `rg -n "(T[B]D|TO[D]O|추후[[:space:]]+작성|검증[[:space:]]+후[[:space:]]+작성)" docs/domain docs/reports/DOMAIN-001 docs/handoffs/DOMAIN-001` | 통과 |
| 범위 외 구현 확인 | `git diff --name-only`와 도메인 문서 검토 | 통과 |
| PR Checks | `gh pr checks 7 --watch --interval 5` | 통과. Commit and PR conventions, Discord collaboration notification 성공 |

## 위험과 제한

- 이번 작업은 도메인 문서화만 수행했다.
- API 오류 표현, HTTP 상태, 요청·응답 구조는 API-001에서 확정해야 한다.
- Product와 SKU의 물리 데이터 모델은 DATA 또는 구현 설계에서 확정해야 한다.
- 날짜 공급 방식과 테스트 더블 전략은 구현 단계에서 확정해야 한다.
- 구독 가능 여부의 실제 저장 표현은 아직 확정하지 않았다.

## Git 결과

- 브랜치: `feat/be`
- 주요 변경 커밋: `f58bf8fc61218905a1900144840ed700817db8ec`
- 주요 변경 커밋 메시지: `docs(domain): DOMAIN-001 구독 도메인 설계`
- push: `origin/feat/be` 반영 완료
- PR: `#7` 생성 완료
- 자동 병합: 하지 않음

보고서 자신을 최종 갱신하는 커밋 SHA는 재귀적으로 기록하지 않는다.

## PR 상태

- PR 번호: `#7`
- PR 제목: `docs(domain): DOMAIN-001 구독 도메인 설계`
- PR 방향: `feat/be` → `main`
- PR 상태: Open, Ready for review
- Commit and PR conventions: 통과
- Discord collaboration notification: 통과
- 자동 병합: 하지 않음

## 다음 작업

### API-001

- DOMAIN-001의 도메인 용어와 책임을 바탕으로 API 요청·응답, HTTP 상태, 오류 코드와 날짜 표현을 확정한다.

### DATA 또는 구현 설계

- Product와 SKU 저장 구조, Subscription 참조 방식, DeliveryCycle과 SubscriptionQuantity 저장 표현을 확정한다.
