# DATA-003 2차 MVP Subscription 데이터 설계

- 작업 ID: `API-004`
- 문서 상태: **Proposed**
- 기준 입력: [PS-004](../product/PS-004-second-mvp-requirements.md), [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md)
- 관련 API: [API-004](../api/API-004-second-mvp-api-contract.md)
- 관련 ADR: [ARCH-007](../adr/ARCH-007-second-mvp-subscription-consistency.md)

이 문서는 기존 V2 `subscriptions`를 삭제·reset하지 않는 2차 MVP의 proposed physical design이다. 실제 DDL, Flyway version, JPA Entity/FK/index 명칭과 migration 실행은 후속 구현에서 확정한다.

## 1. Aggregate와 transaction 경계

| Aggregate | 소유·책임 | transaction에서 보호할 상태 |
| --- | --- | --- |
| Pet | Member 소유 Pet의 이름·종 | Pet 소유권과 DOG/CAT 유효성 |
| SubscriptionPlan | 판매 기간·대상 종과 version 계보 | 현재 판매 가능 PlanVersion의 선택 조건 |
| Subscription | Member 소유 구독, 상태·snapshot·pending·Schedule·명령 이력 | version, 상태 전이, 최대 하나 pending, Schedule cardinality, 멱등 결과 |

PlanVersion과 PlanItem은 SubscriptionPlan의 하위 구성이다. Schedule, current snapshot, pending change, command history와 idempotency result는 Subscription aggregate 밖에서 독립 수정하지 않는다. 관리 명령은 Subscription row를 version 조건으로 갱신하고 관련 child row와 성공 replay를 한 transaction에 기록한다. Pet·Plan의 판매 가능성은 생성·변경 명령 transaction에서 재검사한다.

## 2. Proposed 저장 구조

| 구조 | 핵심 필드·책임 |
| --- | --- |
| `pets` | `id`, `member_id`, `name`, `pet_type`; Member 소유와 DOG/CAT |
| `subscription_plans` | `id`, `target_pet_type`, `on_sale`, `sale_starts_on`, `sale_ends_on`; Plan 정체성·판매 조건 |
| `plan_versions` | `id`, `plan_id`, `package_price_krw`, `is_current`, `is_migration_only`; 가격·구성 immutable version |
| `plan_items` | `plan_version_id`, `sku_id`, `quantity`; package SKU·수량 |
| `plan_version_delivery_cycles` | `plan_version_id`, `delivery_cycle_weeks`; 허용 2·4·8주 |
| `subscriptions` | 기존 ID·Member·legacy 연결을 보존하면서 `pet_id` nullable, `status`, `version`, current snapshot 참조 또는 동등 값 |
| `subscription_snapshots` | subscription별 적용 snapshot: source PlanVersion, package total, cycle, item 구성. legacy initial snapshot도 같은 구조로 표현 가능 |
| `pending_plan_changes` | subscription당 최대 하나의 미래 snapshot과 적용 대상 Schedule; 별도 table이 아니라 Subscription child/JSON 구조여도 결과 불변 조건은 같아야 함 |
| `subscription_schedules` | `id`, `subscription_id`, `scheduled_date`, `status`; 계획 이력 |
| `subscription_command_history` | command type·성공 결과 요약·발생 시각·Subscription version 전후; 사용자 노출 이력의 원본 |
| `idempotency_results` | scope, payload fingerprint, 최초 성공 HTTP 결과 또는 재구성 가능한 성공 result; replay 판단 전용 |

`LegacyInitialSnapshot`과 `PendingPlanChange`는 도메인 개념이지 반드시 독립 Entity가 아니다. snapshot item을 정규화 table로 분리할지 JSON으로 저장할지, current snapshot을 Subscription에 직접 복제할지는 미결이다. 단, 과거 snapshot이 PlanVersion·SKU 가격 변경에 의해 변경되지 않고 pending이 최대 하나라는 결과는 유지해야 한다.

## 3. 타입과 불변 조건

- 가격은 signed 64-bit 범위의 KRW 정수이며 음수는 허용하지 않는다. 수량은 양의 정수, 배송 주기는 2·4·8 중 하나다.
- 날짜는 SQL `DATE`로 저장하고 Asia/Seoul 날짜를 기준으로 계산한다. command 발생 시각은 timezone을 잃지 않는 instant type으로 저장한다.
- Subscription status는 `ACTIVE`, `PAUSED`, `CANCELED`; Schedule status는 `SCHEDULED`, `SKIPPED`, `HELD`, `CANCELED`만 저장한다.
- 신규 Subscription은 `pet_id` non-null이다. migration 완료 legacy Subscription에 한해서만 `pet_id` nullable을 일시 허용한다.
- ACTIVE는 실행 가능한 미래 SCHEDULED 하나, PAUSED는 미래 SCHEDULED 없음과 HELD 하나, CANCELED는 실행 가능한 미래 Schedule 없음이라는 cardinality를 transaction과 DB 제약의 조합으로 보장한다. 과거 SKIPPED·CANCELED·SCHEDULED 이력은 여러 개일 수 있다.
- current snapshot은 한 개, pending은 최대 한 개다. pending 적용 시 current snapshot을 바꾸고 pending을 제거한다. cancel은 pending을 적용하지 않는다.
- `next_scheduled_date`를 별도 캐시 열로 둘 경우 ACTIVE의 실행 가능한 SCHEDULED에서만 파생하고, PAUSED·CANCELED에서는 null을 유지한다. source of truth는 Schedule이다.

## 4. 제약과 index 제안

다음은 측정 없는 최적화가 아닌 소유권, 고유성, 상태 무결성에 필요한 proposed 제약이다.

| 목적 | 제약 또는 index 제안 | 근거 |
| --- | --- | --- |
| 소유권·참조 | Member, Pet, Plan, PlanVersion, SKU, Subscription FK | 고아 관계와 타인 참조 방지 |
| Plan 판매 기간 | 시작·종료일 nullability 및 시작일 ≤ 종료일 CHECK | 선택 조건의 물리적 기본값 |
| 값 범위 | price ≥ 0, quantity > 0, cycle IN (2,4,8), enum CHECK | 잘못된 직접 write 차단 |
| current PlanVersion | Plan당 current 판매 후보 최대 하나를 강제할 수 있는 unique 전략 | 신규 선택의 모호성 방지 |
| pending | `subscription_id` unique 또는 동등 partial-unique 전략 | pending 최대 하나 |
| 미래 실행 Schedule | Subscription당 실행 가능한 미래 SCHEDULED 하나를 application transaction + DB 고유 전략으로 보장 | ACTIVE cardinality |
| member 목록 | `(member_id, id DESC)` 또는 동등 조회 index | 본인 목록 |
| 판매 Plan 조회 | 판매 상태·대상 종·현재 version·기간 조건을 지원하는 최소 index | 선택 후보 |
| Schedule/이력 | `(subscription_id, scheduled_date)`와 `(subscription_id, occurred_at)` | 상세 이력 |
| idempotency | member, subscription nullable, command type, key의 scope unique | replay·다른 scope key 재사용 구분 |

MySQL의 partial unique 표현, generated column, nullable unique 처리와 index 이름은 실제 schema 버전과 테스트 결과를 본 뒤 확정한다. 애플리케이션 검증만으로 상태 cardinality를 보장하지 않으며, DB가 직접 표현할 수 없는 시간 의존 조건은 transaction locking과 reconciliation으로 보완한다.

## 5. Idempotency·locking record

`idempotency_results`의 scope는 생성에서 `(member_id, CREATE_SUBSCRIPTION, null subscription_id, key)`, 관리 명령에서 `(member_id, command_type, subscription_id, key)`다. 같은 key가 다른 command·Subscription에 사용되면 다른 row가 가능해야 한다.

payload fingerprint에는 canonical request body와 command 식별을 사용하고, raw `Idempotency-Key` 자체와 member ID는 server-controlled scope 열로 저장한다. 최초 성공의 status·안전한 response payload 또는 재구성에 충분한 result reference만 저장한다. 오류 body, stack trace, 원 요청 민감 정보는 저장·재노출하지 않는다.

Subscription에는 optimistic `version`을 둔다. 새로운 관리 명령은 `If-Match`와 version을 확인해 조건부 갱신한다. replay lookup은 version 검사보다 앞선다. DB unique 위반과 optimistic update 0건은 각각 idempotency 경쟁과 version 경쟁으로 application layer가 안전하게 매핑해야 하며, 비관적 lock·Redis·message broker는 이 설계의 기본 선택이 아니다.

## 6. Legacy migration 설계

### 6.1 사전 검증

실제 write 전에 전체 대상에서 다음을 검증한다.

- Subscription→SKU, SKU→Product 참조가 모두 유효한지
- 가격이 0 이상 정수 KRW로 손실 없이 변환 가능한지
- `TRIM + UPPER(pet_type)`가 DOG 또는 CAT인지
- quantity·delivery_cycle_weeks·created_date·next_order_date가 유효한지
- 대상 row의 중복·불일치와 migration-only PlanVersion·PlanItem 연결을 결정할 수 있는지

하나라도 손실 없는 변환이 불가능하면 문제가 된 row만 제외하거나 값을 추측·반올림·되돌리지 않는다. write 전에 전체 migration을 중단한다.

### 6.2 결과

- 기존 Subscription ID와 Member 연결을 보존하고, 상태는 ACTIVE로 둔다.
- 기존 SKU·수량·배송 주기를 migration-only PlanVersion·PlanItem과 LegacyInitialSnapshot으로 보존한다. 가격은 생성 당시 가격이 아니라 migration 시점 SKU 단가×기존 수량이다.
- 기존 `subscribable` 값은 legacy 보존 여부를 바꾸지 않는다.
- `pet_id`는 legacy에 한해 null을 허용한다. Pet 없는 legacy Subscription은 조회·상태 명령을 사용할 수 있으나, plan 변경 요청에서 본인 Pet을 연결해야 한다.
- `next_order_date`가 migration 기준일보다 과거면 현재 주기를 반복해 기준일 당일 또는 이후 첫 날짜로 이동한다. 각 legacy Subscription은 첫 실행 가능한 미래 SCHEDULED 하나를 가진다.

### 6.3 Flyway 순서와 복구 경계

실제 SQL은 작성하지 않는다. 구현은 (1) additive schema, (2) read-only preflight, (3) all-or-nothing data migration, (4) 검증 후 FK·NOT NULL·unique 강화 순서를 검토한다. 이전 코드와 새 schema의 동시 호환 기간, migration version, backup·restore 검증은 별도 구현·운영 작업이다.

Flyway down migration을 자동 rollback으로 제안하지 않는다. 데이터 write가 발생한 뒤의 복구는 사전 검증, 승인된 backup/restore 절차와 별도 변경 관리 경계가 필요하다. 이 문서는 Production DB 실행 또는 성공을 주장하지 않는다.

## 7. Schedule reconciliation 경계

GET은 Schedule을 수정하지 않는다. write command와 미래 scheduler가 동일 application service의 reconciliation을 호출해, ACTIVE의 과거 SCHEDULED를 이력으로 남기고 하나의 다음 실행 가능한 미래 SCHEDULED를 보장한다. 이 경계는 중복 생성 방지를 위해 Subscription aggregate transaction·version 및 Schedule 고유성 전략과 함께 구현한다.

실제 Batch, schedule trigger, 주문·결제·재고·배송 생성, 여러 누락 회차의 운영 정책은 이번 Proposed 설계의 구현 대상이 아니다. Local/Test Docker MySQL에서 fresh migration·preflight 실패·제약·동시 command 테스트를 계획하되, Production 실행은 별도 고위험 승인 작업이다.
