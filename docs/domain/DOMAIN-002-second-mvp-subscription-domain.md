# DOMAIN-002 2차 MVP 구독 도메인 설계

## 작업 목적

이 문서는 [PS-004](../product/PS-004-second-mvp-requirements.md)의 Approved Input을 충족하기 위해 채택한 **Accepted Domain Design**을 기록한다. 2차 MVP에서 Pet, 패키지형 PlanVersion, Subscription, Schedule, snapshot과 명령 이력이 지켜야 할 용어·책임·관계·불변 조건을 정한다.

1차 MVP의 용어와 경계는 [PS-002](../product/PS-002-first-mvp-requirements.md), [PS-003](../product/PS-003-ux-product-decisions.md), [DOMAIN-001](DOMAIN-001-first-mvp-subscription-domain.md)을 참고한다. 1차 MVP 문서의 규칙을 변경하거나 2차 MVP 정책을 소급 적용하지 않는다.

## 결정 상태 기준

| 구분 | 의미 |
| --- | --- |
| Approved Input | PS-004가 확정한 제품 정책이다. |
| Accepted Domain Design | Approved Input을 일관되게 보존하기 위해 이 문서가 채택한 용어·책임·불변 조건이다. |
| Deferred Technical Decision | 구현 역할이 정할 Entity, Aggregate, API, DTO, HTTP 오류, table·FK·index·locking의 구체 방식이다. |

## 승인 입력

- Pet은 회원 소유이며 이름과 DOG/CAT 종을 가진다. 새 구독에는 Pet이 필수다.
- SubscriptionPlan은 대상 종, 판매 여부와 판매 기간을 가지며, 가격·구성 변경은 새 PlanVersion으로만 한다. 이전 PlanVersion은 신규 선택 후보가 아니다.
- PlanVersion은 구성 SKU·수량, 허용 배송 주기와 KRW 정수 가격을 가진다. 신규 구독은 허용 배송 주기 중 하나를 선택한다.
- 새 구독과 ACTIVE 구독의 플랜 변경은 현재 판매 중이고 Pet 종에 호환되는 현재 PlanVersion만 선택한다. 플랜 변경은 현재 배송 주기를 유지하고 대상 PlanVersion이 이를 허용해야 한다.
- 구독은 ACTIVE, PAUSED, CANCELED 상태와 SCHEDULED, SKIPPED, HELD, CANCELED Schedule 상태를 사용한다.
- 신규 구독은 선택 당시의 가격·구성·배송 주기를 현재 적용 snapshot으로 보존한다. 플랜 변경은 다음 실행 가능한 SCHEDULED 회차용 pending snapshot을 보존하고, 과거 snapshot과 PlanVersion은 수정하지 않는다.
- 기존 Subscription의 legacy 초기 가격 snapshot은 migration 시점 현재 SKU 가격과 기존 수량·배송 주기를 사용한다. 이는 구독 생성 당시 가격이 아니다.
- 관리 명령은 Member, Subscription ID, 명령 유형, Idempotency-Key 조합으로 식별하고 Subscription version 불일치를 동시 수정 충돌로 다룬다. 신규 구독 생성은 별도 식별 범위를 사용한다.
- 기존 Subscription은 보존하고 이행 직후 ACTIVE 및 첫 실행 가능한 SCHEDULED 회차를 가지며, 기존 데이터에 한해 `pet_id`의 일시적 nullable을 허용한다.

## 용어와 책임

| 용어 | 책임 |
| --- | --- |
| Member | Pet과 Subscription의 소유자다. 타인 자원의 노출을 막는 소유권 기준이다. |
| Pet | Member가 등록한 신규 구독 대상이다. 이름과 DOG/CAT 종을 가진다. |
| SubscriptionPlan | 패키지 플랜의 정체성과 대상 종, 판매 여부·기간을 표현한다. |
| PlanVersion | 특정 시점의 Plan 가격·구성·허용 배송 주기를 표현하는 불변 판매 단위다. |
| PlanItem | PlanVersion 안의 SKU와 수량 구성 항목이다. |
| Subscription | Member의 Pet, 현재 적용 snapshot, 선택적으로 대기 중인 플랜 변경, 상태와 Schedule을 일관되게 관리한다. |
| SubscriptionSnapshot | 신규 구독 시점 또는 pending 변경이 적용된 시점의 KRW 정수 가격, SKU·수량 구성, 배송 주기를 보존한다. |
| LegacyInitialSnapshot | 기존 Subscription 이행 시점의 현재 SKU 가격과 기존 수량·배송 주기를 보존하는 개념이다. 구독 생성 당시 가격을 뜻하지 않는다. |
| PendingPlanChange | 플랜 변경 후 다음 실행 가능한 SCHEDULED 회차에 적용될 PlanVersion snapshot과 적용 대상을 보존하는 개념이다. |
| SubscriptionSchedule | 구독 회차의 예정일과 SCHEDULED·SKIPPED·HELD·CANCELED 상태를 보존한다. 실제 주문·결제·배송이 아니다. |
| SubscriptionCommandHistory | 상태·플랜·일정 명령의 결과와 식별 정보를 보존해 재시도와 이력을 구분한다. |

`LegacyInitialSnapshot`과 `PendingPlanChange`는 도메인 개념과 결과를 설명할 뿐 별도 Entity 구현을 요구하지 않는다. 물리 저장 방식은 Deferred Technical Decision이다.

## 논리적 관계

| 출발 개념 | 관계 | 도착 개념 |
| --- | --- | --- |
| Member | 여러 Pet과 Subscription을 소유한다. | Pet, Subscription |
| Pet | 하나 이상의 Subscription에서 신규 구독 대상이 된다. | Subscription |
| SubscriptionPlan | 여러 PlanVersion을 가진다. | PlanVersion |
| PlanVersion | 여러 PlanItem으로 구성된다. | PlanItem |
| Subscription | 현재 적용 snapshot, 선택적 pending 변경, 여러 Schedule, 명령 이력을 보존한다. | SubscriptionSnapshot, PendingPlanChange, SubscriptionSchedule, SubscriptionCommandHistory |

위 관계는 도메인 책임의 설명이며, Entity·Aggregate·table·FK 구현 경계가 아니다.

## Accepted Domain Design

### Pet과 소유권

- Pet은 한 Member에게만 속한다.
- Pet의 종은 DOG 또는 CAT이며 신규 Subscription은 하나의 본인 Pet을 선택한다.
- Member는 자신의 Pet과 Subscription만 조회하거나 명령할 수 있다. 타인 자원은 목록·상세·명령에서 노출되지 않는다.
- 기존 Subscription만 이행 중 `pet_id`가 비어 있을 수 있다. 신규 Subscription에는 이 예외를 적용하지 않는다.

### Plan, PlanVersion, PlanItem

- SubscriptionPlan은 대상 종과 판매 여부·기간을 통해 신규 선택 가능성을 판단하고, 신규 선택에는 현재 PlanVersion 하나만 제공한다.
- PlanVersion은 가격·구성 변경의 새 버전이다. 과거 PlanVersion과 그 PlanItem은 수정하지 않으며, 이전 PlanVersion은 신규 구독·플랜 변경 후보가 아니다.
- PlanItem은 PlanVersion의 SKU와 수량을 보존한다. 사용자가 구독별로 PlanItem 수량을 임의 변경할 수 없다.
- 현재 판매 중인 PlanVersion은 판매 상태·판매 기간을 만족하고 선택 Pet의 종과 호환되어야 한다.
- 신규 구독은 현재 PlanVersion이 허용한 배송 주기 하나를 선택한다. 플랜 변경은 기존 배송 주기를 유지하므로 대상 PlanVersion이 그 주기를 허용해야 한다.
- 판매가 끝난 Plan은 신규 구독·플랜 변경 후보에서 제외하지만, 기존 Subscription의 상태·snapshot·일정을 자동으로 바꾸지 않는다.

### Subscription과 snapshot

- Subscription은 Member, 선택 Pet, 현재 적용 snapshot, 선택적 pending 변경, 상태와 Schedule을 일관되게 가진다.
- 신규 생성은 선택 PlanVersion의 KRW 정수 가격, 구성 SKU·수량, 선택 배송 주기를 현재 적용 snapshot으로 보존한다.
- 플랜 변경은 현재 적용 snapshot을 즉시 교체하지 않고, 다음 실행 가능한 SCHEDULED 회차에 적용할 pending snapshot을 보존한다. 적용 회차가 되면 pending snapshot이 현재 적용 snapshot으로 전환된다.
- 건너뛰기는 pending 변경의 적용 대상을 새 SCHEDULED 회차로 옮긴다. 일시정지는 pending 변경을 보존하고 재개로 만든 SCHEDULED 회차에 적용한다. 해지와 CANCELED 상태는 pending 변경을 적용하지 않는다.
- 기존 Subscription의 LegacyInitialSnapshot은 migration 시점의 현재 SKU 가격과 기존 수량·배송 주기를 보존하며, 구독 생성 당시 가격을 복원한 값이 아니다. 이후 가격·구성 변경은 이 값을 자동 변경하지 않는다.
- 기존 Subscription은 기본 Plan, 최초 PlanVersion과 PlanItem 구성에 연결된다. 이행 직후 ACTIVE이며 기존 `next_order_date`를 첫 실행 가능한 SCHEDULED 회차로 옮긴다.
- `next_order_date`가 migration 기준일보다 과거면, 기존 배송 주기를 반복해 기준일 당일 또는 이후가 되는 첫 날짜로 보정한다.
- Pet이 없는 기존 Subscription은 플랜 변경 입력에서 본인 Pet을 선택해 연결한 뒤에만 변경할 수 있다.

### Subscription 상태 불변 조건

| 상태 | 허용 전이 | 불변 조건 |
| --- | --- | --- |
| ACTIVE | PAUSED, CANCELED | 플랜 변경과 건너뛰기는 ACTIVE에서만 가능하다. |
| PAUSED | ACTIVE, CANCELED | 재개는 PAUSED에서만 가능하다. |
| CANCELED | 없음 | 복원·재개하지 않는다. 재이용은 새 Subscription을 만든다. |

- ACTIVE에서 PAUSED 또는 CANCELED로, PAUSED에서 ACTIVE 또는 CANCELED로만 전이한다.
- 해지는 ACTIVE 또는 PAUSED에서 즉시 CANCELED가 되며, 미래 Schedule은 취소하고 과거 이력은 유지한다.

### Schedule 일관성

- SubscriptionSchedule 상태는 SCHEDULED, SKIPPED, HELD, CANCELED다.
- `실행 가능한 미래 Schedule`은 Asia/Seoul 기준일 당일 또는 이후의 SCHEDULED 회차 중 가장 가까운 회차다.
- ACTIVE Subscription은 실행 가능한 미래 SCHEDULED 회차를 정확히 하나 가진다. 현재 SCHEDULED 회차가 과거가 되면 현재 배송 주기로 다음 실행 가능한 SCHEDULED 회차를 이어서 유지하고 과거 회차는 계획 이력으로 남긴다.
- PAUSED Subscription은 실행 가능한 미래 SCHEDULED 회차가 없고 재개 대상 HELD 회차를 하나 가진다. CANCELED Subscription은 실행 가능한 미래 Schedule이 없다.
- SKIPPED·CANCELED와 과거 SCHEDULED 회차는 여러 이력으로 남을 수 있다.
- 플랜 변경은 ACTIVE에서만 하며 현재 배송 주기를 유지한다. 대상 PlanVersion이 그 주기를 허용하면 다음 실행 가능한 SCHEDULED 회차용 pending snapshot을 보존하고, 다음 예정일을 즉시 다시 계산하지 않는다.
- 건너뛰기는 ACTIVE의 현재 회차를 SKIPPED로 바꾸고 현재 주기만큼 뒤의 다음 회차를 SCHEDULED로 만든다. pending 변경이 있으면 적용 대상은 새 회차로 이동한다.
- 일시정지는 즉시 Subscription을 PAUSED로 바꾸고 예정 회차를 HELD로 바꾸며 새 회차 생성을 중단한다. pending 변경은 유지한다.
- 재개는 재개일과 현재 주기로 새 예정일을 계산하고 HELD 회차를 SCHEDULED로 바꾼다. pending 변경이 있으면 그 회차에 적용한다.
- 해지는 미래 Schedule만 CANCELED로 바꾸며 SKIPPED를 포함한 과거 회차 이력은 유지하고, pending 변경은 적용하지 않는다.
- pending snapshot이 적용된 회차 이후의 다음 예정일 계산에도 기존 배송 주기를 사용한다.
- Schedule은 실제 주문·결제·재고·배송 객체가 아니며, Batch 생성 책임도 가지지 않는다.

### 명령 식별과 동시 수정

- 기존 Subscription 관리 명령의 식별자는 `Member + Subscription ID + 명령 유형 + Idempotency-Key` 조합이다.
- 같은 관리 명령 식별자와 같은 Payload는 같은 결과를 보존하고, 같은 식별자와 다른 Payload는 충돌이다.
- 다른 Subscription 또는 다른 명령 유형의 같은 Key는 전역 충돌이 아니다.
- 신규 구독 생성은 Subscription ID가 없으므로 `Member + CREATE_SUBSCRIPTION + Idempotency-Key`로 별도 식별한다. 같은 식별자와 같은 Payload는 같은 생성 결과, 다른 Payload는 충돌이다.
- Subscription version 불일치는 동시 수정 충돌이며 기존 상태·snapshot·Schedule을 덮어쓰지 않는다.

## 명령별 도메인 결과

| 명령 | 선행 조건 | 도메인 결과 |
| --- | --- | --- |
| 신규 구독 | 본인 Pet, 현재 판매 중인 호환 현재 PlanVersion, 허용 배송 주기 하나 | ACTIVE Subscription, 첫 실행 가능한 SCHEDULED Schedule, 현재 적용 snapshot을 만든다. |
| 플랜 변경 | ACTIVE, Pet 선택, 현재 판매 중인 호환 현재 PlanVersion, 기존 배송 주기 허용 | 현재 적용 snapshot은 유지하고 다음 실행 가능한 SCHEDULED 회차용 pending snapshot을 보존한다. |
| 건너뛰기 | ACTIVE | 현재 회차는 SKIPPED, 주기 뒤 다음 회차는 SCHEDULED다. pending 변경은 새 회차로 이동한다. |
| 일시정지 | ACTIVE | Subscription은 PAUSED, 예정 회차는 HELD, 새 회차 생성은 중단되며 pending 변경은 유지된다. |
| 재개 | PAUSED | Subscription은 ACTIVE, 재개일+현재 주기의 예정일과 SCHEDULED 회차를 가진다. pending 변경이 있으면 이 회차에 적용한다. |
| 해지 | ACTIVE 또는 PAUSED | Subscription은 CANCELED, 미래 회차는 CANCELED, 과거 이력은 보존되며 pending 변경은 적용하지 않는다. |

## 요구사항 추적성

| 제품 요구사항 | 도메인 책임 |
| --- | --- |
| REQ-PET-001 | Pet 소유권, 종, 신규 구독 대상 조건 |
| REQ-PLAN-001 | Plan·PlanVersion·PlanItem, 판매 상태·기간, 종 호환성 |
| REQ-SUB-005 | 신규 Subscription, 현재 적용 snapshot, 배송 주기 선택, 다음 예정일 |
| REQ-SUB-006 | Subscription 소유권, 상태별 다음 예정일, 현재·pending snapshot, 상태·명령 이력 |
| REQ-SUB-007 | 플랜 변경 pending snapshot, 배송 주기 유지, 상태 전이와 Schedule 일관성 |
| REQ-SUB-008 | 관리·신규 생성 명령의 분리된 식별 범위, 재시도 결과, 동시 수정 충돌 |
| REQ-SUB-009 | LegacyInitialSnapshot, 초기 ACTIVE·Schedule, Pet 연결 예외 |
| REQ-SUB-010 | 실행 가능한 미래 Schedule의 상태별 cardinality와 연속성 |

## Deferred Technical Decision

다음은 Accepted Domain Design이 아니라 후속 Backend 설계의 책임이다.

- Entity와 Aggregate 구현 경계, 객체 참조 방식
- endpoint, DTO, HTTP 상태·오류 표현
- table, column, FK, index와 실제 물리 관계
- PlanVersion·snapshot·명령 이력·Schedule의 저장 구조
- Idempotency-Key와 Subscription version의 저장, 비교, locking 구현
- 기존 데이터 컬럼·FK·Flyway 이행 순서와 rollback

## 미결정 Product Decision

새로운 Product Decision은 추가하지 않는다. 이 문서는 PS-004의 승인 입력을 도메인 불변 조건으로 해석한다.
