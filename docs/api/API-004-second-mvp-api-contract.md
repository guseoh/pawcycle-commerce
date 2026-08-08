# API-004 2차 MVP Backend API 계약

- 작업 ID: `API-004`
- 문서 상태: **Proposed**
- 기준 입력: [PS-004](../product/PS-004-second-mvp-requirements.md), [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md)
- 관련 기존 계약: [API-001](API-001-first-mvp-api-contract.md)
- 관련 데이터 설계: [DATA-003](../data/DATA-003-second-mvp-subscription-data-design.md)
- 관련 ADR: [ARCH-007](../adr/ARCH-007-second-mvp-subscription-consistency.md)

이 문서는 승인된 2차 MVP 정책을 구현 가능한 HTTP 계약으로 제안한다. 구현, Flyway SQL, endpoint 배포 또는 API-001 원본 수정은 이 문서의 범위가 아니다. `Proposed` 상태이므로 구현 전에 사용자 또는 Tech Lead 검토가 필요하다.

## 1. 호환성, 공통 규칙과 오류 형식

### 1.1 API-001과의 관계

API-001의 1차 MVP `POST /api/subscriptions`, `GET /api/subscriptions`, `GET /api/subscriptions/{subscriptionId}`는 기존 SKU 단위 계약으로 보존한다. API-004는 이를 수정하거나 2차 정책을 소급 적용하지 않는다.

2차 MVP의 Pet, PlanVersion, snapshot, 상태 명령은 모두 `/api/v2` 아래의 새 계약으로 제공한다. 구현 전환 시 클라이언트는 한 Subscription 흐름에서 API-001과 API-004 생성 계약을 혼용하지 않는다. legacy Subscription은 API-004 조회·상태 명령의 대상이 될 수 있으나, API-004 생성은 Pet과 판매 가능한 현재 PlanVersion을 반드시 사용한다.

### 1.2 인증·소유권·표현

- 모든 endpoint는 기존 session 인증과 CSRF 계약을 사용한다. client가 member ID를 전달하지 않으며 서버 principal의 `memberId`가 소유권 기준이다.
- Pet과 Subscription의 타인 소유·부재·형식이 맞지 않는 식별자는 모두 해당 자원의 `404` 오류로 처리하여 존재를 노출하지 않는다.
- JPA Entity, 내부 ID 관계, SQL 오류, replay 원 요청 body와 payload fingerprint는 응답에 노출하지 않는다.
- 날짜는 `YYYY-MM-DD`이며 Asia/Seoul 날짜 단위다. command 발생 시각은 DB의 UTC instant를 Asia/Seoul offset으로 변환한 ISO-8601 `YYYY-MM-DDTHH:mm:ss[.SSS]+09:00`으로 반환한다. 금액은 KRW 정수 JSON number이고 패키지 전체 가격이며 JavaScript 안전 정수 상한 `9,007,199,254,740,991` 이하여야 한다.
- 성공 응답은 아래 DTO 구조를 사용한다. 페이지 목록은 `page`, `size`, `totalElements`, `items`를 포함하며 `page`는 0부터, 기본 `size`는 20, 최대 100이다.

기존 공통 오류 shape를 유지한다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력 값을 확인해 주세요.",
  "fieldErrors": [{ "field": "name", "message": "필수 항목입니다." }]
}
```

`401 AUTH_REQUIRED`, `403 ACCESS_DENIED`, `403 CSRF_INVALID`는 기존 인증 계약을 사용한다. 아래 표의 `fieldErrors`는 별도 표기가 없으면 빈 배열이다.

| HTTP | code | 적용 범위 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | JSON 형식, 필수값, 문자열·enum·날짜·pagination·Idempotency-Key 형식 |
| 404 | `PET_NOT_FOUND` | 본인 소유가 아니거나 존재하지 않는 Pet |
| 404 | `SUBSCRIPTION_NOT_FOUND` | 본인 소유가 아니거나 존재하지 않는 Subscription |
| 404 | `PLAN_VERSION_NOT_FOUND` | 존재하지 않는 PlanVersion 식별자 |
| 409 | `PLAN_NOT_AVAILABLE` | 판매 종료·기간 밖·이전 또는 migration-only PlanVersion |
| 409 | `PLAN_PET_TYPE_MISMATCH` | Pet 종과 Plan 대상 종 불일치 |
| 409 | `DELIVERY_CYCLE_NOT_ALLOWED` | PlanVersion이 현재 또는 요청 배송 주기를 허용하지 않음 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 멱등 범위에서 다른 payload를 사용 |
| 409 | `SUBSCRIPTION_COMMAND_NOT_ALLOWED` | 현재 상태에서 허용되지 않는 명령 |
| 400 | `IF_MATCH_INVALID` | `If-Match`가 quoted non-negative integer 형식이 아님 |
| 412 | `SUBSCRIPTION_VERSION_MISMATCH` | 신규 관리 명령의 version precondition 불일치 |
| 428 | `IF_MATCH_REQUIRED` | 관리 명령에 `If-Match`가 없음 |

### 1.3 멱등성과 동시성 header

신규 구독과 모든 관리 명령에는 `Idempotency-Key` header가 필수다. 허용 문자는 ASCII 영문·숫자·`-._`이며 길이는 1~128이다. body 값으로 받지 않는다.

관리 명령은 `If-Match: "<non-negative integer>"`를 필수로 사용한다. 이 문서는 request body version 대신 HTTP precondition header를 **Proposed**로 선택한다. body가 비즈니스 입력만 유지되고, HTTP의 조건부 갱신 의미를 명확히 하며, 동일 command DTO가 version field를 중복하지 않기 때문이다. Subscription 상세, 생성 성공과 관리 명령 성공은 body의 `version`과 같은 `ETag: "<version>"` header를 제공한다.

판정 순서는 고정한다.

1. principal과 command scope로 기존 성공 replay를 찾는다.
2. 같은 scope·같은 canonical payload면 최초 성공의 business status, body, `Location`, `ETag`를 그대로 반환하고 `Idempotency-Replayed: true` header만 추가한다. 이때 현재 version은 검사하지 않는다.
3. 같은 scope·다른 payload면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.
4. replay가 없을 때만 `If-Match`와 Subscription version을 검사한다.
5. version이 맞으면 상태·선행 조건을 검사하고 한 transaction에서 명령 결과와 성공 replay를 저장한다.

관리 scope는 `Member + Subscription ID + command type + Idempotency-Key`이고, 생성 scope는 `Member + CREATE_SUBSCRIPTION + Idempotency-Key`다. 동일 문자열 key는 다른 Subscription 또는 다른 command type에서 재사용할 수 있다. fingerprint에는 endpoint가 정의한 body와 관련 request 식별자를 canonical JSON으로 포함하며, member ID는 server context에서만 포함한다. 실패 결과는 저장하지 않는다. 성공 결과와 필요한 business response·`Location`·`ETag`는 최초 성공 완료 UTC 시각부터 30일간 보관하며 replay나 저장 response body 보정은 보관 기한을 연장하지 않는다. cleanup은 완료 시각이 현재 UTC 시각에서 30일을 뺀 cutoff보다 이른 결과만 삭제하고, 정확히 cutoff인 결과와 완료되지 않은 결과는 보존한다. cleanup으로 삭제된 key는 이후 새 요청으로 처리될 수 있다.

## 2. Pet 계약

| 목적 | Method / path | 성공 |
| --- | --- | --- |
| 본인 Pet 등록 | `POST /api/v2/pets` | `201 Created` |
| 본인 Pet 목록 | `GET /api/v2/pets?page=&size=` | `200 OK` |
| 본인 Pet 상세 | `GET /api/v2/pets/{petId}` | `200 OK` |

등록 요청은 다음과 같다.

```json
{ "name": "보리", "petType": "DOG" }
```

`name`은 trim 후 1~50자 non-blank이며 제어문자를 허용하지 않는다. `petType`은 정확히 `DOG` 또는 `CAT`이다. 성공 Pet DTO는 `{ "petId": 101, "name": "보리", "petType": "DOG" }`다. 목록은 기본 `petId ASC`이며 `items`에 같은 요약 DTO를 담는다. 수정·삭제 endpoint와 건강·의료·추천 AI 필드는 제공하지 않는다.

## 3. 판매 가능한 PlanVersion 조회

| 목적 | Method / path | 성공 |
| --- | --- | --- |
| Pet 호환 판매 Plan 목록 | `GET /api/v2/subscription-plans?petId={petId}&page=&size=` | `200 OK` |
| 선택 전 PlanVersion 상세 | `GET /api/v2/subscription-plan-versions/{planVersionId}?petId={petId}` | `200 OK` |

`petId`는 필수이며 본인 Pet이어야 한다. 목록과 상세 모두 Asia/Seoul 요청일에 판매 상태 활성, 판매 기간의 시작·종료일 포함, Pet 종 호환, Plan의 `current_plan_version_id`가 가리키는 PlanVersion만 반환한다. 이전 PlanVersion과 migration-only PlanVersion은 조회 후보에서 제외한다. 목록은 `planId ASC, planVersionId ASC`로 고정 정렬한다.

Plan DTO는 다음과 같다.

```json
{
  "planId": 10,
  "planName": "성견 기본 패키지",
  "targetPetType": "DOG",
  "planVersionId": 31,
  "packagePriceKrw": 45900,
  "items": [{ "skuId": 2001, "quantity": 2 }],
  "allowedDeliveryCycleWeeks": [2, 4, 8],
  "sale": { "onSale": true, "startsOn": "2026-08-01", "endsOn": null }
}
```

SKU의 현재 단가, 이전 version 식별자, 내부 판매 판단 사유는 반환하지 않는다. Plan·PlanVersion·PlanItem 관리자 등록·수정 API는 없다.

## 4. Subscription 생성·조회

### 4.1 생성

`POST /api/v2/subscriptions`는 `Idempotency-Key`가 필수이며 `201 Created`를 반환한다.

```json
{
  "petId": 101,
  "planVersionId": 31,
  "deliveryCycleWeeks": 4
}
```

Pet 소유권, 현재 판매 가능성, 종 호환, 요청 주기 허용을 모두 통과해야 한다. 성공 시 ACTIVE Subscription, 현재 적용 snapshot, 정확히 하나의 실행 가능한 미래 SCHEDULED Schedule, version 0을 만든다. 첫 `scheduledDate`는 Asia/Seoul 요청일에 선택한 `deliveryCycleWeeks`를 더한 날짜이며, 기존 1차 MVP의 다음 예정일 계산을 유지한다. `Location`은 `/api/v2/subscriptions/{subscriptionId}`다.

### 4.2 목록과 상세

| 목적 | Method / path | 성공 |
| --- | --- | --- |
| 본인 목록 | `GET /api/v2/subscriptions?page=&size=` | `200 OK` |
| 본인 상세 | `GET /api/v2/subscriptions/{subscriptionId}` | `200 OK` |

목록은 `subscriptionId DESC`로 정렬한다. 상세는 현재 snapshot, optional pending snapshot, Schedule 이력, 노출 가능한 command 이력까지 포함한다. Schedule 이력은 `scheduledDate DESC, scheduleId DESC`, command 이력은 `occurredAt DESC, commandHistoryId DESC`로 정렬한다. 각 이력은 독립적으로 기본 20개·최대 100개이며, `schedulePage`, `scheduleSize`, `commandPage`, `commandSize`를 받는다. `schedules`와 `commandHistory`는 각각 `{ "page", "size", "totalElements", "items" }` 페이지 객체다.

```json
{
  "subscriptionId": 501,
  "pet": { "petId": 101, "name": "보리", "petType": "DOG" },
  "status": "ACTIVE",
  "version": 3,
  "currentSnapshot": {
    "planVersionId": 31,
    "packagePriceKrw": 45900,
    "deliveryCycleWeeks": 4,
    "items": [{ "skuId": 2001, "quantity": 2 }]
  },
  "pendingSnapshot": null,
  "nextScheduledDate": "2026-09-01",
  "schedules": { "page": 0, "size": 20, "totalElements": 1, "items": [{ "scheduleId": 701, "scheduledDate": "2026-09-01", "status": "SCHEDULED", "effectiveSnapshotId": 901 }] },
  "commandHistory": { "page": 0, "size": 20, "totalElements": 1, "items": [{ "commandType": "CHANGE_PLAN", "result": "SUCCEEDED", "occurredAt": "2026-08-04T10:30:00+09:00" }] }
}
```

legacy Subscription의 `pet`은 null일 수 있다. 이는 migration 예외일 뿐 신규 생성 결과에는 허용되지 않는다. `nextScheduledDate`는 ACTIVE에서만 실행 가능한 미래 SCHEDULED 일자를 갖고 PAUSED·CANCELED에서는 null이다. command history는 key, fingerprint, 내부 예외나 타인 식별자를 노출하지 않는다.

## 5. Subscription 관리 명령

모든 endpoint는 `Idempotency-Key`와 `If-Match` header가 필수이며 성공 시 최신 Subscription DTO를 반환한다. replay도 최초의 business status·body·`Location`·`ETag`를 그대로 반환하고 `Idempotency-Replayed: true` header로만 표시한다. 인증·소유권 확인에 실패한 경우에는 멱등 결과를 조회하지 않는다.

| command type | Method / path | body | 상태·결과 |
| --- | --- | --- | --- |
| `CHANGE_PLAN` | `POST /api/v2/subscriptions/{id}/commands/change-plan` | `planVersionId`, optional `petId` | ACTIVE만 가능. 현재 주기는 유지하고 대상 version이 그 주기를 허용해야 한다. pending 최대 하나를 최신 snapshot으로 교체하며 다음 실행 가능한 SCHEDULED에 적용한다. Schedule·다음 예정일은 바꾸지 않는다. legacy Pet null이면 본인 `petId`가 필수다. |
| `SKIP_NEXT` | `POST /api/v2/subscriptions/{id}/commands/skip-next` | `{}` | ACTIVE만 가능. 현재 SCHEDULED를 SKIPPED로, 주기 뒤 새 SCHEDULED를 만든다. pending이 있으면 새 SCHEDULED로 적용 대상을 이동한다. |
| `PAUSE` | `POST /api/v2/subscriptions/{id}/commands/pause` | `{}` | ACTIVE만 가능. 즉시 PAUSED, 예정 SCHEDULED는 HELD, pending은 유지한다. |
| `RESUME` | `POST /api/v2/subscriptions/{id}/commands/resume` | `{}` | PAUSED만 가능. Asia/Seoul 재개일+현재 주기로 예정일을 계산하고 HELD를 SCHEDULED로 전환한다. pending은 이 Schedule에 적용한다. |
| `CANCEL` | `POST /api/v2/subscriptions/{id}/commands/cancel` | `{}` | ACTIVE·PAUSED 가능. 즉시 CANCELED, 미래 Schedule은 CANCELED, pending은 적용하지 않으며 이력은 보존한다. |

명령 성공의 공통 응답은 `200 OK`다. pending snapshot이 적용되는 시점에는 이를 current snapshot으로 원자적으로 전환한다. 동일 Idempotency-Key·동일 body의 `CHANGE_PLAN` replay는 새 pending 교체나 version mismatch를 만들지 않는다. 동일 Key·다른 body, optional `petId` 포함 여부 차이도 payload 충돌이다.

`CHANGE_PLAN`의 `petId`는 기존 Pet이 있으면 생략 가능하고, 제공하면 기존 Pet과 같아야 한다. legacy Pet null이면 필수이며 소유 Pet이 아니거나 target Plan과 종이 맞지 않으면 `PET_NOT_FOUND` 또는 `PLAN_PET_TYPE_MISMATCH`를 반환한다. 성공한 legacy 변경은 Pet 연결, target Plan 검증, pending 교체와 같은 transaction에서 `subscription.pet_id`를 영구 저장하고 최신 DTO의 `pet`에 반영한다.

## 6. Schedule 만료와 조회 경계

GET endpoint는 상태를 변경하지 않는다. ACTIVE Subscription의 Schedule cardinality를 지속시키기 위해, 2차 MVP 구현에는 공통 reconciliation application service를 호출하는 scheduler trigger가 필수다. write command도 같은 service를 호출하지만, 명령이 전혀 없는 구독의 overdue Schedule은 scheduler가 처리한다. scheduler 자체의 배포·운영 구현은 후속 작업이지만 API-004 구현 완료 전 이 trigger가 제공되지 않으면 ACTIVE next date 불변 조건을 충족했다고 주장할 수 없다.

catch-up은 Subscription을 version 조건으로 잠그고 다음 순서로 한 transaction에서 수행한다.

1. 현재 실행 가능한 SCHEDULED가 과거면 그 회차의 `effectiveSnapshotId`를 확정한다.
2. 해당 회차가 pending의 `targetScheduleId`이면 pending snapshot을 current로 승격하고 pending row를 제거한 뒤, 그 Schedule의 effective snapshot으로 연결한다. target이 아닌 overdue Schedule에는 기존 current snapshot을 유지한다.
3. 확정된 회차를 계획 이력으로 남기고, 그 effective snapshot의 배송 주기에서 그 회차 날짜를 기준으로 반복 계산해 첫 미래 날짜를 찾는다.
4. 그 날짜에 current snapshot을 가리키는 SCHEDULED 하나를 만들거나 기존 동일 회차를 재사용한다. pending은 새 미래 target에 임의로 이월하지 않으며, target Schedule이 사라진 불변 조건 위반은 version 증가나 다음 회차 생성 전에 실패로 처리한다.

실제 주문·결제·재고·배송 객체는 만들지 않는다.

자동 scheduler, 실행 시각, 여러 누락 회차 처리와 운영 관측성은 구현 전 별도 기술·운영 결정이다. 이 계약은 읽기 요청이 몰래 상태를 바꾸지 않고, command와 미래 scheduler가 같은 reconciliation 경계를 사용해야 한다는 요구만 제안한다.

## 7. 구현 전 확인 항목

- API-004 endpoint, DTO, 오류 code, `If-Match`, Idempotency-Key는 Proposed이며 Frontend 계약 확정 전 구현하지 않는다.
- pagination의 실제 query 전략과 command history 노출 세분화는 구현 PR에서 재검토한다. 성공 idempotency 결과는 최초 성공 완료부터 30일 보관한 뒤 승인된 bounded cleanup으로 삭제할 수 있으며, replay에 의한 retention 연장과 실패 결과 저장은 허용하지 않는다.
- 실제 Flyway SQL, JPA mapping, locking SQL, migration execution, Batch/Scheduler, 주문·결제·배송은 범위 밖이다.
- 문서 변경의 복구 경계는 일반 revert PR이다. Production·AWS·운영 DB·Secret 실행은 수행하지 않는다.
