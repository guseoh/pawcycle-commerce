# PS-004 2차 MVP 제품 요구사항

## 작업 목적

보호자가 반려동물을 등록하고 종에 맞는 패키지형 정기배송 플랜을 선택해 구독한 뒤, 선택 당시의 가격과 구성을 보존하면서 구독을 관리할 수 있게 한다. 이 문서는 확정된 2차 MVP 제품·도메인 정책을 후속 설계와 구현의 **Approved Input**으로 정리한다.

1차 MVP의 공개 상품 탐색, 세션 인증, 단일 SKU 구독 생성·조회 범위는 [PS-002](PS-002-first-mvp-requirements.md), UX 결정은 [PS-003](PS-003-ux-product-decisions.md), 1차 도메인 해석은 [DOMAIN-001](../domain/DOMAIN-001-first-mvp-subscription-domain.md)을 따른다. 이 문서는 그 문서들을 수정하거나 2차 MVP 규칙을 소급 적용하지 않는다. 도메인 용어와 불변 조건은 [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md)에서 해석한다.

## 대상 사용자와 제품 결과

- 로그인한 보호자는 자신의 Pet을 등록하고 본인 Pet의 목록·상세를 확인한다.
- 보호자는 Pet의 종에 호환되고 현재 판매 중인 PlanVersion을 골라 새 구독을 만든다.
- 보호자는 자신의 구독에서 다음 예정일과 당시의 가격·구성 snapshot을 확인하고, 플랜 변경·건너뛰기·일시정지·재개·해지를 관리한다.

## 결정 상태 기준

| 구분 | 의미 |
| --- | --- |
| Approved Input | Product Owner가 확정한 제품 정책으로, 후속 역할이 임의로 바꾸지 않는다. |
| Accepted Domain Design | Approved Input을 만족하도록 DOMAIN-002가 채택한 도메인 해석이다. |
| Deferred Technical Decision | 구현 경계·API·저장 구조처럼 후속 기술 역할이 결정할 항목이다. |

## 승인된 제품 범위

### 포함 범위

- Pet 등록, 본인 Pet 목록·상세 조회, 이름과 종(DOG/CAT), 회원 소유권
- 신규 구독 대상 Pet 선택
- SubscriptionPlan, PlanVersion, PlanItem으로 표현하는 종별 패키지 플랜
- Plan의 대상 종, 판매 여부와 판매 기간, PlanVersion의 구성 SKU·수량·허용 배송 주기
- 현재 판매 중이고 Pet 종에 호환되는 PlanVersion을 선택한 신규 구독 생성
- 본인 구독 목록·상세, 다음 예정일, 가격·구성 snapshot 조회
- ACTIVE 구독의 플랜 변경·다음 회차 건너뛰기·일시정지·재개·해지와 상태·명령 이력
- 기존 Subscription 보존과 2차 MVP 정책으로의 연결 결과

### 제외 범위

- Pet 수정·삭제, 건강·의료 정보, 추천 AI
- Plan·PlanVersion·PlanItem 관리자 관리 화면
- 사용자가 패키지 구성 SKU 수량을 임의로 변경하는 기능
- 실제 주문·결제·재고·배송 객체와 자동 Batch 생성
- 휴일·영업일 보정
- 기존 구독 삭제·초기화, 과거 PlanVersion 수정, 기존 구독 가격의 자동 변경, 강제 가격 migration
- 1차 MVP 문서의 변경 또는 2차 MVP 규칙의 소급 적용

## 승인된 Product Decision

| 항목 | Approved Input |
| --- | --- |
| Pet | 이름과 DOG/CAT 종을 가지며 회원이 소유한다. 신규 구독에는 Pet 선택이 필수다. |
| Plan | SubscriptionPlan은 대상 종, 판매 여부·기간을 가진다. 가격·구성 변경은 새 PlanVersion으로만 제공한다. |
| Plan 구성 | PlanVersion은 구성 SKU·수량과 허용 배송 주기만 제공한다. 사용자는 구성 수량을 임의 변경하지 않는다. |
| 판매 가능성 | 신규 구독·플랜 변경에는 현재 판매 중이고 Pet 종에 호환되는 PlanVersion만 선택할 수 있다. |
| 가격 | 금액은 KRW 정수다. 신규 구독과 플랜 변경은 선택 PlanVersion의 가격·구성을 snapshot으로 보존한다. |
| 기존 구독 | 판매가 종료돼도 기존 구독을 자동 해지하거나 다른 플랜으로 전환하지 않는다. |
| 날짜·주기 | Asia/Seoul 날짜 단위와 2·4·8주 배송 주기를 유지하며, 휴일·영업일 보정은 하지 않는다. |
| 구독 상태 | ACTIVE, PAUSED, CANCELED만 사용한다. CANCELED는 복원하지 않고 재이용은 새 구독 생성이다. |

## 공통 비즈니스 규칙

### 소유권과 노출

- 회원은 자신의 Pet과 Subscription만 등록·조회·상태 명령 대상으로 사용한다.
- 타인 Pet 또는 Subscription은 목록·상세·명령 결과에서 노출되지 않는다.

### 상태와 명령

| 현재 상태 | 허용 명령 | 결과 |
| --- | --- | --- |
| ACTIVE | 플랜 변경 | 현재 판매 중이며 Pet 종에 호환되는 PlanVersion을 선택하고, 다음 예정 회차부터 적용한다. |
| ACTIVE | 건너뛰기 | 현재 회차를 SKIPPED로 바꾸고, 주기만큼 뒤의 다음 SCHEDULED 회차를 만든다. |
| ACTIVE | 일시정지 | 즉시 PAUSED가 되며 예정 회차는 HELD가 되고 새 회차 생성을 중단한다. |
| ACTIVE | 해지 | 즉시 CANCELED가 되며 미래 회차를 취소하고 과거 이력은 유지한다. |
| PAUSED | 재개 | 재개일과 현재 주기로 새 예정일을 계산하고 HELD 회차를 SCHEDULED로 전환한다. |
| PAUSED | 해지 | 즉시 CANCELED가 되며 미래 회차를 취소하고 과거 이력은 유지한다. |
| CANCELED | 없음 | 재개·복원하지 않는다. 재이용은 새 구독 생성으로만 한다. |

플랜 변경은 ACTIVE에서만 가능하다. ACTIVE에서 PAUSED 또는 CANCELED로, PAUSED에서 ACTIVE 또는 CANCELED로만 전이한다.

### Schedule

- Schedule 상태는 SCHEDULED, SKIPPED, HELD, CANCELED다.
- 다음 예정일은 사용자에게 보여 주는 구독 관리 정보이며, 이번 범위에서 실제 주문·결제·재고·배송 또는 Batch를 만들지 않는다.
- 건너뛰기·일시정지·재개·해지는 위 상태와 회차 이력을 일관되게 남긴다.

### 명령 안전성

- 명령은 Idempotency-Key, 회원, Subscription ID, 명령 유형으로 식별한다.
- 같은 식별과 같은 Payload는 같은 결과를 반환한다.
- 같은 Key에 다른 Payload를 사용하면 충돌이다.
- Subscription version이 맞지 않으면 동시 수정 충돌로 처리한다.

### 기존 데이터 결과 정책

- 기존 Subscription을 삭제하거나 초기화하지 않는다.
- 구독 가능 SKU를 기준으로 기본 Plan, 최초 PlanVersion, PlanItem 구성을 연결한다.
- 기존 Subscription에는 연결된 PlanVersion과 당시 가격·수량·배송 주기 snapshot을 남긴다.
- 기존 데이터에 한해서는 `pet_id`가 일시적으로 nullable일 수 있다.
- 기존 구독은 조회와 상태 명령을 사용할 수 있다. 플랜 변경 전에 Pet 선택이 필요하다.
- 새 구독에는 Pet이 필수다.

실제 컬럼·FK·데이터 이행 순서와 rollback은 후속 Backend 설계의 책임이다.

## 사용자 흐름

### 정상 흐름

```text
로그인한 보호자
→ Pet 등록 또는 본인 Pet 선택
→ Pet 종에 호환되고 현재 판매 중인 PlanVersion 선택
→ 새 구독 생성
→ 다음 예정일과 가격·구성 snapshot 확인
→ 필요 시 허용 상태의 플랜 변경·건너뛰기·일시정지·재개·해지 수행
→ 상태·명령 이력 확인
```

### 예외 흐름

- 타인의 Pet 또는 Subscription을 조회하거나 명령하려 하면 해당 자원을 노출하지 않는다.
- 신규 구독 또는 플랜 변경에서 판매 종료, 판매 기간 밖, Pet 종 비호환 PlanVersion은 선택할 수 없다.
- ACTIVE가 아닌 구독의 플랜 변경·건너뛰기는 수행하지 않는다.
- CANCELED 구독은 재개하지 않으며 새 구독을 만든다.
- 같은 Idempotency-Key에 다른 Payload를 보내면 충돌로 처리한다.
- Subscription version 불일치 명령은 이전 결과를 덮어쓰지 않는다.

## 기능 요구사항과 인수 조건

### REQ-PET-001 Pet 등록과 본인 조회

로그인한 회원은 이름과 DOG/CAT 종으로 Pet을 등록하고, 본인 Pet의 목록·상세를 조회할 수 있다.

- AC-PET-001-01: Pet 등록에는 이름과 DOG 또는 CAT 종이 필요하다.
- AC-PET-001-02: 회원은 자신의 Pet만 목록과 상세에서 확인한다.
- AC-PET-001-03: 타인 Pet은 목록·상세·신규 구독 선택 대상에 나타나지 않는다.

### REQ-PLAN-001 종별 판매 PlanVersion 선택

보호자는 신규 구독 또는 허용된 플랜 변경에서 Pet 종에 호환되고 현재 판매 중인 PlanVersion만 선택할 수 있다.

- AC-PLAN-001-01: 선택 가능한 PlanVersion에는 대상 종, 구성 SKU·수량, 허용 배송 주기와 가격이 확인 가능하다.
- AC-PLAN-001-02: 판매 종료·판매 기간 밖·판매 중지 또는 Pet 종 비호환 PlanVersion은 신규 선택에서 제외된다.
- AC-PLAN-001-03: 가격·구성이 달라진 Plan은 기존 PlanVersion을 고치지 않고 새 PlanVersion으로 제공된다.

### REQ-SUB-005 Pet 기반 신규 구독과 snapshot

로그인한 회원은 자신의 Pet과 호환 PlanVersion을 선택해 새 구독을 만들고, 다음 예정일 및 선택 당시의 가격·구성 snapshot을 확인한다.

- AC-SUB-005-01: 신규 구독은 본인 Pet과 현재 판매 중인 호환 PlanVersion 없이는 만들어지지 않는다.
- AC-SUB-005-02: 신규 구독은 KRW 정수 가격, 구성 SKU·수량, 배송 주기 snapshot과 다음 예정일을 보존한다.
- AC-SUB-005-03: 이후 Plan 가격·구성이 바뀌어도 기존 구독의 snapshot은 자동 변경되지 않는다.

### REQ-SUB-006 본인 구독 조회와 상태·명령 이력

회원은 자신의 구독 목록·상세에서 현재 상태, 다음 예정일, 가격·구성 snapshot, 상태·명령 이력을 확인한다.

- AC-SUB-006-01: 회원은 자신의 구독만 목록과 상세에서 확인한다.
- AC-SUB-006-02: 구독 상세에는 다음 예정일과 현재 적용 snapshot이 포함된다.
- AC-SUB-006-03: 상태와 명령 이력은 과거 회차·해지 이력을 지우지 않고 확인 가능하다.

### REQ-SUB-007 구독 관리 명령

회원은 자신의 구독 상태에 맞게 플랜 변경·건너뛰기·일시정지·재개·해지를 수행한다.

- AC-SUB-007-01: ACTIVE 구독의 플랜 변경은 다음 예정 회차부터 적용되고 새 PlanVersion 가격·구성 snapshot을 보존한다.
- AC-SUB-007-02: ACTIVE 구독 건너뛰기는 현재 회차를 SKIPPED로 바꾸고 주기 뒤 SCHEDULED 회차를 만든다.
- AC-SUB-007-03: 일시정지는 즉시 PAUSED, 예정 회차 HELD, 새 회차 생성 중단을 보장한다.
- AC-SUB-007-04: 재개는 PAUSED에서만 가능하고 재개일과 현재 주기로 새 예정일을 계산해 HELD 회차를 SCHEDULED로 바꾼다.
- AC-SUB-007-05: ACTIVE 또는 PAUSED의 해지는 즉시 CANCELED가 되며 미래 회차만 취소하고 과거 이력을 유지한다.
- AC-SUB-007-06: CANCELED 구독은 재개하지 않으며 재이용은 새 구독 생성이다.

### REQ-SUB-008 명령 재시도와 동시 수정 보호

회원의 구독 명령은 재시도와 동시 수정에서 이전 결과를 중복 적용하거나 덮어쓰지 않는다.

- AC-SUB-008-01: 같은 Idempotency-Key, 회원, Subscription ID, 명령 유형, Payload의 재시도는 최초 결과와 같다.
- AC-SUB-008-02: 같은 Key와 다른 Payload는 충돌로 처리한다.
- AC-SUB-008-03: Subscription version 불일치 명령은 동시 수정 충돌로 처리한다.

### REQ-SUB-009 기존 Subscription 보존

기존 Subscription은 삭제·초기화하지 않고 2차 MVP의 PlanVersion 및 snapshot 정보와 연결한다.

- AC-SUB-009-01: 기존 Subscription은 조회와 허용 상태 명령을 계속 사용할 수 있다.
- AC-SUB-009-02: 기존 Subscription은 PlanVersion, 당시 가격·수량·배송 주기 snapshot을 가진다.
- AC-SUB-009-03: Pet이 없는 기존 Subscription은 플랜 변경 전에 Pet을 선택해야 한다.
- AC-SUB-009-04: 신규 구독에는 Pet이 필수이며, 기존 데이터에 한해서만 `pet_id`의 일시적 nullable을 허용한다.

## 요구사항 추적성

| 요구사항 | 도메인 해석 | 상태 |
| --- | --- | --- |
| REQ-PET-001 | Pet 소유권과 신규 구독 대상 조건 | [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md) |
| REQ-PLAN-001 | SubscriptionPlan, PlanVersion, PlanItem과 판매·종 호환성 | [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md) |
| REQ-SUB-005~009 | Subscription 상태·Schedule·snapshot·명령·기존 데이터 불변 조건 | [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md) |

## Deferred Technical Decision

다음 항목은 이 제품 요구사항의 결정 범위가 아니며, 후속 기술 설계에서 정한다.

- Entity와 Aggregate 구현 경계
- endpoint, DTO, HTTP 상태·오류 표현
- table, column, FK, index와 실제 데이터 이행·rollback 순서
- 동시성 제어와 Subscription version, Idempotency-Key의 저장·locking 구현 방식

## 미결정 Product Decision

새로운 Product Decision은 추가하지 않는다. 이 문서는 승인된 2차 MVP 정책만 기록한다.
