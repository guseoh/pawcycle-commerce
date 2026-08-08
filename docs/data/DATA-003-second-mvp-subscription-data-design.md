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
| `subscription_plans` | `id`, `target_pet_type`, `on_sale`, `sale_starts_on`, `sale_ends_on`, `current_plan_version_id`; Plan 정체성·판매 조건과 현재 판매 Version pointer |
| `plan_versions` | `id`, `plan_id`, `package_price_krw`, `is_migration_only`; 가격·구성 immutable version. `is_current`를 갱신하지 않음 |
| `plan_items` | `plan_version_id`, `sku_id`, `quantity`; package SKU·수량 |
| `plan_version_delivery_cycles` | `plan_version_id`, `delivery_cycle_weeks`; 허용 2·4·8주 |
| `subscriptions` | 기존 ID·Member·legacy 연결을 보존하면서 `pet_id` nullable, `status`, `version`, `current_snapshot_id`, `legacy_api_visible`, `mvp2_managed` |
| `subscription_snapshots` | `id`, `subscription_id`, `source_plan_version_id`, `package_total_krw`, `delivery_cycle_weeks`; 적용·legacy initial snapshot의 관계형 header |
| `subscription_snapshot_items` | `snapshot_id`, `sku_id`, `quantity`; snapshot의 immutable SKU·수량 구성 |
| `pending_plan_changes` | `subscription_id` unique, `snapshot_id` unique, `target_schedule_id` unique; subscription별 최대 하나의 pending과 적용 target |
| `subscription_schedules` | `id`, `subscription_id`, `scheduled_date`, `status`; 계획 이력 |
| `subscription_command_history` | command type·성공 결과 요약·발생 시각·Subscription version 전후; 사용자 노출 이력의 원본 |
| `subscription_creation_idempotency_results` | `member_id`, `idempotency_key`, fingerprint, Subscription/result reference, response status·headers, nullable `completed_at`; 생성 scope 전용 |
| `subscription_command_idempotency_results` | `member_id`, `subscription_id`, `command_type`, `idempotency_key`, fingerprint, response status·headers, nullable `completed_at`; 관리 scope 전용 |

`LegacyInitialSnapshot`은 `subscription_snapshots`와 `subscription_snapshot_items`의 legacy origin으로 표현하고, `PendingPlanChange`는 `pending_plan_changes`로 표현한다. snapshot item JSON, current snapshot 값 복제, nullable scope unique에 의존하지 않는다. 과거 snapshot은 PlanVersion·SKU 가격 변경에 의해 변경되지 않고 pending은 최대 하나다. `subscription_schedules.effective_snapshot_id`는 해당 회차에 실제 적용되는 snapshot을 가리켜, pending 승격 뒤에도 과거 회차와 snapshot 관계를 보존한다.

## 3. 타입과 불변 조건

- 가격은 KRW 정수이며 음수와 JavaScript 안전 정수 상한 `9,007,199,254,740,991` 초과를 허용하지 않는다. 수량은 양의 정수, 배송 주기는 2·4·8 중 하나다.
- 날짜는 SQL `DATE`로 저장하고 Asia/Seoul 날짜를 기준으로 계산한다. command 발생 시각은 UTC로 정규화한 `DATETIME(6)` instant로 저장하고, API에서만 Asia/Seoul `+09:00` ISO-8601 offset으로 변환한다.
- Subscription status는 `ACTIVE`, `PAUSED`, `CANCELED`; Schedule status는 `SCHEDULED`, `SKIPPED`, `HELD`, `CANCELED`만 저장한다.
- 신규 Subscription은 `pet_id` non-null이다. migration 완료 legacy Subscription에 한해서만 `pet_id` nullable을 일시 허용한다.
- ACTIVE는 실행 가능한 미래 SCHEDULED 하나, PAUSED는 미래 SCHEDULED 없음과 HELD 하나, CANCELED는 실행 가능한 미래 Schedule 없음이라는 cardinality를 transaction과 DB 제약의 조합으로 보장한다. 과거 SKIPPED·CANCELED·SCHEDULED 이력은 여러 개일 수 있다.
- current snapshot은 `subscriptions.current_snapshot_id` 한 개, pending은 `pending_plan_changes.subscription_id` unique로 최대 한 개다. pending 적용 시 current pointer를 바꾸고 pending row를 제거한다. cancel은 pending을 적용하지 않는다.
- `next_scheduled_date`를 별도 캐시 열로 둘 경우 ACTIVE의 실행 가능한 SCHEDULED에서만 파생하고, PAUSED·CANCELED에서는 null을 유지한다. source of truth는 Schedule이다.

## 4. 제약과 index 제안

다음은 측정 없는 최적화가 아닌 소유권, 고유성, 상태 무결성에 필요한 proposed 제약이다.

| 목적 | 제약 또는 index 제안 | 근거 |
| --- | --- | --- |
| 소유권·참조 | Member, Pet, Plan, PlanVersion, SKU, Subscription FK | 고아 관계와 타인 참조 방지 |
| Plan 판매 기간 | 시작·종료일 nullability 및 시작일 ≤ 종료일 CHECK | 선택 조건의 물리적 기본값 |
| 값 범위 | price ≥ 0, quantity > 0, cycle IN (2,4,8), enum CHECK | 잘못된 직접 write 차단 |
| current PlanVersion | `subscription_plans.current_plan_version_id` FK와 plan-version 소속 일치 검증 | immutable Version을 수정하지 않고 현재 판매 후보를 하나로 선택 |
| pending | `subscription_id` unique 또는 동등 partial-unique 전략 | pending 최대 하나 |
| 미래 실행 Schedule | Subscription당 실행 가능한 미래 SCHEDULED 하나를 application transaction + DB 고유 전략으로 보장 | ACTIVE cardinality |
| member 목록 | `(member_id, id DESC)` 또는 동등 조회 index | 본인 목록 |
| 판매 Plan 조회 | 판매 상태·대상 종·현재 version·기간 조건을 지원하는 최소 index | 선택 후보 |
| Schedule/이력 | `(subscription_id, scheduled_date)`와 `(subscription_id, occurred_at)` | 상세 이력 |
| 생성 idempotency | `subscription_creation_idempotency_results(member_id, idempotency_key)` unique | nullable Subscription ID 없이 생성 replay 경합 차단 |
| 관리 idempotency | `subscription_command_idempotency_results(member_id, subscription_id, command_type, idempotency_key)` unique | command replay·다른 scope key 재사용 구분 |
| 생성 idempotency cleanup | `subscription_creation_idempotency_results(completed_at, member_id, idempotency_key)` | 완료 시각 기준 bounded delete와 안정된 삭제 순서 지원 |
| 관리 idempotency cleanup | `subscription_command_idempotency_results(completed_at, member_id, subscription_id, command_type, idempotency_key)` | 완료 시각 기준 bounded delete와 안정된 삭제 순서 지원 |

MySQL의 partial unique 표현, generated column과 index 이름은 실제 schema 버전과 테스트 결과를 본 뒤 확정한다. nullable unique 처리에는 의존하지 않는다. 애플리케이션 검증만으로 상태 cardinality를 보장하지 않으며, DB가 직접 표현할 수 없는 시간 의존 조건은 transaction locking과 reconciliation으로 보완한다. API-001 목록·상세는 `legacy_api_visible=true`만 조회하고 새 2차 MVP row는 false로 둔다. migration된 legacy row는 기존 V1 열과 visibility를 보존하면서 `mvp2_managed=true`로 API-004 조회·명령에 참여하므로, V2 패키지 row가 API-001 단일 SKU DTO에 섞이지 않는다.

## 5. Idempotency·locking record

생성 scope는 `subscription_creation_idempotency_results(member_id, idempotency_key)`, 관리 명령 scope는 `subscription_command_idempotency_results(member_id, subscription_id, command_type, idempotency_key)`로 물리적으로 분리한다. 같은 key가 다른 command·Subscription에 사용되면 다른 관리 row가 가능하다.

payload fingerprint에는 canonical request body와 command 식별을 사용하고, raw `Idempotency-Key` 자체와 member ID는 server-controlled scope 열로 저장한다. key reservation row의 nullable `completed_at DATETIME(6)`은 null로 두고, 최초 성공의 business status·body, `Location`, `ETag`와 재구성에 충분한 result reference를 최종 update할 때 같은 transaction에서 최초 완료 UTC 시각을 기록한다. replay와 저장 response body 보정은 `completed_at`을 변경하지 않는다. 실패 결과, 오류 body, stack trace, 원 요청 민감 정보는 저장·재노출하지 않는다.

성공 결과 retention은 최초 `completed_at`부터 30일이다. cleanup 실행은 먼저 `response_status`가 2xx이고 `response_body`가 존재하며 `completed_at IS NULL`인 rollback-era 성공 row를 table별 caller 제공 양의 `batchSize`까지 현재 UTC 시각으로 repair한다. repair 시각이 최초 완료 시각이 되어 30일 grace period가 시작되며, incomplete reservation 또는 성공 완료 여부를 판정할 수 없는 null row는 그대로 보존한다. 그 뒤 같은 현재 시각에서 30일을 뺀 cutoff로 `completed_at < cutoff`만 table별 같은 `batchSize`까지 삭제하므로 방금 repair한 row는 같은 실행에서 삭제되지 않는다. 정확히 cutoff인 row도 보존하고 creation·command의 repair·delete 수를 각각 반환한다. replay는 retention을 연장하지 않으며 cleanup으로 삭제된 key는 이후 새 요청으로 처리될 수 있다. Scheduler, 운영 batch size, retry/backoff와 alert는 이 설계에서 확정하지 않는다.

Flyway는 MySQL 8.4 non-transactional DDL의 partial application 경계를 version별로 분리한다. V4는 creation `completed_at`, V5는 command `completed_at`을 각각 단일 `ALTER TABLE`로 추가한다. V6은 두 column이 존재한 뒤 동일 migration 실행 UTC 시각으로 기존 2xx·body 존재·null 성공 row만 backfill하고 incomplete reservation은 null로 보존한다. V7과 V8은 creation·command cleanup index를 각각 단일 DDL로 생성한다.

V4 성공 뒤 V5가 실패하면 V4 완료 상태를 유지한 채 실패한 V5를 Flyway repair한 뒤 V5부터 재시도한다. V7 성공 뒤 V8 실패도 같은 방식으로 V7 index를 유지하고 V8부터 재시도한다. V6은 DDL과 섞지 않은 backfill 단계이며 실패 시 이후 index 단계로 진행하지 않는다. V3 fixture에서 V4→V8을 순차 적용하는 통합 테스트가 각 column·backfill·index 상태와 최종 fresh migration을 검증한다. 이 repository 재구성은 아직 Production에 적용되지 않았고 실제 Production repair·migration을 실행하지 않는다.

Subscription에는 optimistic `version`을 둔다. 새로운 관리 명령은 `If-Match`와 version을 확인해 조건부 갱신한다. replay lookup은 version 검사보다 앞선다. 생성·관리 idempotency unique 위반 또는 optimistic update 0건이면 같은 scope를 즉시 다시 조회해 같은 fingerprint의 성공 replay 또는 다른 fingerprint의 key 충돌을 먼저 판정하고, 결과가 없을 때만 version 경쟁으로 매핑한다. 구현 검증에는 동시에 같은 생성 key를 제출해 Subscription 하나와 동일 replay 하나만 남는 테스트를 포함한다. 비관적 lock·Redis·message broker는 이 설계의 기본 선택이 아니다.

## 6. Legacy migration 설계

### 6.1 사전 검증

실제 write 전에 전체 대상에서 다음을 검증한다.

- Subscription→SKU, SKU→Product 참조가 모두 유효한지
- 가격이 0 이상 정수 KRW로 손실 없이 변환 가능한지
- migration 시점 SKU 단가와 기존 quantity의 정확한 곱셈 결과가 signed 64-bit 및 API JSON 안전 정수 상한 안인지
- `TRIM + UPPER(pet_type)`가 DOG 또는 CAT인지
- quantity·delivery_cycle_weeks·created_date·next_order_date가 유효한지
- 대상 row의 중복·불일치와 migration-only PlanVersion·PlanItem 연결을 결정할 수 있는지

하나라도 손실 없는 변환이 불가능하면 문제가 된 row만 제외하거나 값을 추측·반올림·되돌리지 않는다. write 전에 전체 migration을 중단한다.

### 6.2 결과

- 기존 Subscription ID와 Member 연결을 보존하고, 상태는 ACTIVE로 둔다.
- 기존 SKU·수량·배송 주기를 migration-only PlanVersion·PlanItem과 LegacyInitialSnapshot으로 보존한다. 가격은 생성 당시 가격이 아니라 migration 시점 SKU 단가×기존 수량이며, overflow·정밀도 초과 row는 반올림·절단·추측 없이 write 전에 전체 migration을 중단한다.
- 기존 `subscribable` 값은 legacy 보존 여부를 바꾸지 않는다.
- `pet_id`는 legacy에 한해 null을 허용한다. Pet 없는 legacy Subscription은 조회·상태 명령을 사용할 수 있으나, plan 변경 요청에서 본인 Pet을 연결해야 한다.
- `next_order_date`가 migration 기준일보다 과거면 현재 주기를 반복해 기준일 당일 또는 이후 첫 날짜로 이동한다. 각 legacy Subscription은 첫 실행 가능한 미래 SCHEDULED 하나를 가진다.

### 6.3 Flyway 순서와 복구 경계

실제 SQL은 작성하지 않는다. 구현은 다음 경계를 분리한다.

1. additive DDL을 별도 Flyway 단계로 적용하고 그 auto-commit 경계를 검증한다. 이 단계는 이후 DML 실패 시 자동 rollback되지 않는다.
2. migration maintenance window에서 legacy Subscription source write를 application ingress와 DB 권한·lock 경계로 차단한 뒤, 같은 일관된 source snapshot으로 read-only preflight와 대상 count/fingerprint를 기록한다.
3. write freeze가 유지되는지 재검사한 뒤, 새 row·pointer·Schedule·legacy 연결을 전용 DML transaction에서 모두 write한다. insert·FK·unique 오류나 process 중단이면 이 DML transaction 전체를 rollback하며 부분 migration을 남기지 않는다.
4. commit 뒤 count/fingerprint·참조·Schedule cardinality를 post-validation한다. constraint hardening DDL은 별도 단계이며 실패가 앞선 DML의 전체 자동 rollback을 뜻하지 않으므로, 실패 시 다음 단계로 진행하지 않고 별도 복구 판단을 한다.

이전 코드와 새 schema의 동시 호환 기간, migration version, write freeze 구현, backup·restore 검증은 별도 구현·운영 작업이다.

Flyway down migration을 자동 rollback으로 제안하지 않는다. 데이터 write가 발생한 뒤의 복구는 사전 검증, 승인된 backup/restore 절차와 별도 변경 관리 경계가 필요하다. 이 문서는 Production DB 실행 또는 성공을 주장하지 않는다.

## 7. Schedule reconciliation 경계

GET은 Schedule을 수정하지 않는다. 2차 MVP 구현에는 command가 없는 ACTIVE Subscription도 처리하는 scheduler trigger가 필요하며, write command와 미래 scheduler는 동일 application service의 reconciliation을 호출한다. reconciliation은 Subscription version 조건으로 잠근 뒤 overdue Schedule의 `effective_snapshot_id`를 확정한다. 해당 Schedule이 pending target이면 pending snapshot을 current pointer로 승격하고 pending을 제거한 후 effective snapshot으로 연결한다. 이어 그 확정 snapshot의 주기로 첫 미래 날짜를 계산해 current snapshot을 참조하는 SCHEDULED 하나를 만든다. target이 아닌 overdue Schedule에는 기존 current snapshot을 사용하며, target Schedule 부재는 invariant failure로 중단한다. 이 경계는 중복 생성 방지를 위해 Schedule 고유성 전략과 함께 구현한다.

실제 Batch, schedule trigger, 주문·결제·재고·배송 생성, 여러 누락 회차의 운영 정책은 이번 Proposed 설계의 구현 대상이 아니다. Local/Test Docker MySQL에서 fresh migration·preflight 실패·제약·동시 command 테스트를 계획하되, Production 실행은 별도 고위험 승인 작업이다.
