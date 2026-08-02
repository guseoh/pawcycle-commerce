# PS-004 2차 MVP 제품 요구사항

## 작업 목적

보호자가 반려동물을 등록하고 종에 맞는 패키지형 정기배송 플랜을 선택해 구독한 뒤, 선택 당시의 가격과 구성을 보존하면서 구독을 관리할 수 있게 한다. 이 문서는 확정된 2차 MVP 제품·도메인 정책을 후속 설계와 구현의 **Approved Input**으로 정리한다.

1차 MVP의 공개 상품 탐색, 세션 인증, 단일 SKU 구독 생성·조회 범위는 [PS-002](PS-002-first-mvp-requirements.md), UX 결정은 [PS-003](PS-003-ux-product-decisions.md), 1차 도메인 해석은 [DOMAIN-001](../domain/DOMAIN-001-first-mvp-subscription-domain.md)을 따른다. 이 문서는 그 문서들을 수정하거나 2차 MVP 규칙을 소급 적용하지 않는다. 도메인 용어와 불변 조건은 [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md)에서 해석한다.

## 대상 사용자와 제품 결과

- 로그인한 보호자는 자신의 Pet을 등록하고 본인 Pet의 목록·상세를 확인한다.
- 보호자는 Pet의 종에 호환되고 현재 판매 중인 PlanVersion을 골라 새 구독을 만든다.
- 보호자는 자신의 구독에서 상태에 맞는 다음 예정일 또는 회차 이력과 현재 적용·pending 가격·구성 snapshot을 확인하고, 플랜 변경·건너뛰기·일시정지·재개·해지를 관리한다.

## 결정 상태 기준

| 구분 | 의미 |
| --- | --- |
| Approved Input | Product Owner가 확정한 제품 정책으로, 후속 역할이 임의로 바꾸지 않는다. |
| Accepted Domain Design | Approved Input을 만족하도록 DOMAIN-002가 채택한 도메인 해석이다. |
| Deferred Technical Decision | 구현 경계·API·저장 구조처럼 후속 기술 역할이 결정할 항목이다. |

## 승인된 제품 범위

### 포함 범위

- Pet 등록, 본인 Pet 목록·상세 조회, 이름과 종(DOG/CAT), 회원 소유권
- 신규 구독 대상 Pet 선택과 Pet이 없는 기존 Subscription의 플랜 변경 시 본인 Pet 선택
- SubscriptionPlan, PlanVersion, PlanItem으로 표현하는 종별 패키지 플랜
- Plan의 대상 종, 판매 여부와 판매 기간, PlanVersion의 구성 SKU·수량·허용 배송 주기
- 현재 판매 중이고 Pet 종에 호환되는 PlanVersion을 선택한 신규 구독 생성
- 본인 구독 목록·상세, 다음 예정일, 가격·구성 snapshot 조회
- ACTIVE 구독의 플랜 변경·다음 회차 건너뛰기·일시정지·재개·해지와 상태·명령 이력
- 기존 Subscription 보존과 2차 MVP 정책으로의 손실 없는 연결 결과

### 제외 범위

- Pet 수정·삭제, 건강·의료 정보, 추천 AI
- Plan·PlanVersion·PlanItem 관리자 관리 화면
- 사용자가 패키지 구성 SKU 수량을 임의로 변경하는 기능
- 실제 주문·결제·재고·배송 객체와 자동 Batch 생성
- 휴일·영업일 보정
- 기존 구독 삭제·초기화, 과거 PlanVersion 수정, 기존 구독 가격의 자동 변경, 추측에 의한 가격·종·구성 보정
- 1차 MVP 문서의 변경 또는 2차 MVP 규칙의 소급 적용

## 승인된 Product Decision

| 항목 | Approved Input |
| --- | --- |
| Pet | 이름과 DOG/CAT 종을 가지며 회원이 소유한다. 신규 구독에는 Pet 선택이 필수다. |
| Plan | SubscriptionPlan은 대상 종, 판매 여부·기간을 가진다. 신규 선택에는 Plan마다 현재 PlanVersion 하나만 제공한다. 가격·구성 변경은 새 PlanVersion으로만 제공하며, 이전 PlanVersion은 신규 구독·플랜 변경 후보가 아니다. |
| Plan 구성 | PlanVersion 가격은 패키지 전체 가격이며, 구성 SKU·수량과 허용 배송 주기를 함께 제공한다. 신규 구독은 허용 배송 주기 중 하나를 선택하고 사용자는 구성 수량을 임의 변경하지 않는다. |
| 판매 가능성 | 신규 구독·플랜 변경에는 판매 상태가 활성이고 Asia/Seoul 기준 요청일이 판매 시작일 이상·종료일 이하이며 Pet 종에 호환되는 현재 PlanVersion만 선택할 수 있다. 판매 시작일과 종료일은 모두 포함한다. |
| 가격과 snapshot | 금액은 KRW 정수다. 신규 구독은 선택 PlanVersion의 패키지 전체 가격·구성·배송 주기를 현재 적용 snapshot으로 보존한다. 플랜 변경은 다음 실행 가능한 SCHEDULED 회차에 적용할 pending snapshot으로 보존한다. |
| 플랜 변경 주기 | 플랜 변경은 현재 구독 배송 주기를 유지한다. 대상 PlanVersion이 그 주기를 허용하지 않으면 변경을 거부하며, 플랜 변경으로 배송 주기를 다시 계산하거나 바꾸지 않는다. |
| pending 변경 | Subscription마다 pending 플랜 변경은 최대 하나다. 적용 전 새 플랜 변경이 성공하면 기존 pending을 대기열로 쌓지 않고 최신 요청의 snapshot으로 교체하며, 교체 전 명령 결과는 명령 이력에 보존한다. |
| 기존 구독 가격·구성 | 기존 Subscription의 SKU 가격은 migration 시점 단가다. 기존 수량을 곱한 총액을 패키지 전체 가격으로 사용하고, 같은 SKU와 기존 수량을 가진 migration-only PlanVersion·PlanItem 구성 및 legacy 초기 snapshot으로 보존한다. 이는 구독 생성 당시 가격이 아니다. |
| 기존 구독 이행 안전성 | migration 전에 가격·종·SKU 연결을 검증한다. 손실 없는 변환이 불가능한 행이 하나라도 있으면 값을 추측·반올림·절삭·격리하지 않고 쓰기 전에 전체 migration을 중단한다. |
| 기존 구독 | 판매 또는 구독 가능 상태가 바뀌어도 기존 구독을 자동 해지하거나 다른 플랜으로 전환하지 않는다. migration-only PlanVersion은 기존 구독 보존용이며 신규 선택 후보가 아니다. |
| 날짜·주기 | Asia/Seoul 날짜 단위와 2·4·8주 배송 주기를 유지하며, 휴일·영업일 보정은 하지 않는다. |
| 구독 상태 | ACTIVE, PAUSED, CANCELED만 사용한다. CANCELED는 복원하지 않고 재이용은 새 구독 생성이다. |

## 공통 비즈니스 규칙

### 소유권과 노출

- 회원은 자신의 Pet과 Subscription만 등록·조회·상태 명령 대상으로 사용한다.
- 타인 Pet 또는 Subscription은 목록·상세·명령 결과에서 노출되지 않는다.

### 상태와 명령

| 현재 상태 | 허용 명령 | 결과 |
| --- | --- | --- |
| ACTIVE | 플랜 변경 | 현재 판매 중이며 Pet 종에 호환되고 현재 배송 주기를 허용하는 현재 PlanVersion을 선택한다. 현재 적용 snapshot은 유지하고 다음 실행 가능한 SCHEDULED 회차에 적용할 pending snapshot을 보존한다. 기존 pending이 있으면 최신 성공 요청으로 교체한다. |
| ACTIVE | 건너뛰기 | 현재 회차를 SKIPPED로 바꾸고, 주기만큼 뒤에 새 SCHEDULED 회차를 만든다. pending 변경이 있으면 그 새 회차로 적용 대상을 이동한다. |
| ACTIVE | 일시정지 | 즉시 PAUSED가 되며 예정 회차는 HELD가 되고 새 회차 생성을 중단한다. pending 변경은 유지한다. |
| ACTIVE | 해지 | 즉시 CANCELED가 되며 미래 회차를 취소하고 과거 이력은 유지한다. pending 변경은 적용하지 않고 취소 결과와 명령 이력만 보존한다. |
| PAUSED | 재개 | 재개일과 현재 주기로 새 예정일을 계산하고 HELD 회차를 SCHEDULED로 전환한다. pending 변경이 있으면 이 새 SCHEDULED 회차에 적용한다. |
| PAUSED | 해지 | 즉시 CANCELED가 되며 미래 회차를 취소하고 과거 이력은 유지한다. pending 변경은 적용하지 않는다. |
| CANCELED | 없음 | 재개·복원하거나 pending 변경을 적용하지 않는다. 재이용은 새 구독 생성으로만 한다. |

플랜 변경은 ACTIVE에서만 가능하다. ACTIVE에서 PAUSED 또는 CANCELED로, PAUSED에서 ACTIVE 또는 CANCELED로만 전이한다.

### Schedule

- Schedule 상태는 SCHEDULED, SKIPPED, HELD, CANCELED다.
- `실행 가능한 미래 Schedule`은 Asia/Seoul 기준일 당일 또는 이후의 SCHEDULED 회차 중 가장 가까운 회차다.
- ACTIVE Subscription에는 실행 가능한 미래 SCHEDULED 회차가 정확히 하나 있다. PAUSED Subscription에는 실행 가능한 미래 SCHEDULED 회차가 없고 재개 대상 HELD 회차가 하나 있다. CANCELED Subscription에는 실행 가능한 미래 Schedule이 없다.
- 다음 예정일은 ACTIVE의 실행 가능한 미래 SCHEDULED 회차만 뜻한다. PAUSED·CANCELED에는 다음 예정일이 없으며, HELD·CANCELED 또는 과거 회차는 이력으로만 구분한다.
- ACTIVE의 현재 SCHEDULED 회차가 과거가 되면, 실제 주문·결제·재고·배송 또는 Batch를 만들지 않고도 현재 배송 주기로 다음 실행 가능한 SCHEDULED 회차를 이어서 유지한다. 과거 SCHEDULED 회차는 계획 이력으로 남긴다.
- 플랜 변경은 다음 예정일을 즉시 다시 계산하지 않는다. pending snapshot이 적용된 회차 이후의 다음 예정일 계산에도 유지된 현재 배송 주기를 사용한다.
- pending 교체는 적용 대상 회차와 다음 예정일을 바꾸지 않는다. 건너뛰기·일시정지·재개로 실행 가능한 회차가 바뀌면 현재 pending 하나의 적용 대상만 새 회차로 이동한다.
- 건너뛰기·일시정지·재개·해지는 위 상태, 회차 이력, pending 변경 적용 대상을 일관되게 남긴다.

### 명령 안전성

- 기존 Subscription 관리 명령은 `Member + Subscription ID + 명령 유형 + Idempotency-Key` 조합으로 식별한다.
- 명령 식별 범위에서 같은 Key가 확인되면 Subscription version보다 먼저 저장된 멱등 결과를 판정한다.
- 같은 식별 조합과 같은 Payload의 성공 재시도는 현재 Subscription version과 무관하게 최초 성공 결과를 반환한다.
- 같은 식별 조합과 다른 Payload는 충돌이다. 다른 Subscription 또는 다른 명령 유형에서 같은 Key를 사용해도 전역 충돌로 해석하지 않는다.
- 저장된 replay가 아닌 새 요청에만 Subscription version을 검사하며, version이 맞지 않으면 동시 수정 충돌로 처리한다.
- 신규 구독 생성은 Subscription ID가 없으므로 `Member + CREATE_SUBSCRIPTION + Idempotency-Key` 조합으로 별도 식별한다. 같은 조합과 같은 Payload는 같은 생성 결과, 다른 Payload는 충돌이다.

### 기존 데이터 결과 정책

- 기존 Subscription을 삭제하거나 초기화하지 않는다.
- 기존 Subscription이 참조하는 SKU를 기준으로 migration-only PlanVersion과 PlanItem 구성을 연결한다. 현재 `subscribable` 값은 기존 구독 보존 여부를 바꾸지 않는다.
- 기존 SKU 가격은 단가로 해석한다. 기존 수량을 곱한 총액을 migration-only PlanVersion의 패키지 전체 가격과 LegacyInitialSnapshot 총액으로 보존하고, PlanItem에는 같은 SKU와 기존 수량을 보존한다.
- 동일한 SKU·수량·단가 조합의 물리적 PlanVersion 공유 여부는 후속 Backend 설계가 정할 수 있지만, 각 기존 Subscription이 연결되는 가격과 구성 결과는 동일해야 한다.
- SKU 가격은 소수점 이하가 0일 때만 KRW 정수로 손실 없이 변환한다. 0이 아닌 소수 가격은 반올림하거나 절삭하지 않는다.
- 기존 `pet_type`은 앞뒤 공백 제거와 대문자 변환 후 DOG 또는 CAT과 정확히 일치할 때만 정규화한다. 그 밖의 값은 임의 매핑하지 않는다.
- SKU·Product 연결 누락, 0이 아닌 소수 가격, DOG/CAT으로 정규화할 수 없는 `pet_type` 또는 가격·구성 불일치가 발견되면 어떤 이행 데이터도 쓰기 전에 전체 migration을 중단한다.
- migration 사전 검증과 실제 쓰기는 분리하며, 부분 성공이나 문제 행만 제외한 이행은 허용하지 않는다.
- legacy 초기 snapshot은 migration 시점 값이며 구독 생성 당시 가격이 아니다. 이후 가격·구성 변경은 이 snapshot을 자동 변경하지 않는다.
- 기존 데이터에 한해서는 `pet_id`가 일시적으로 nullable일 수 있다.
- 기존 Subscription은 초기 상태를 ACTIVE로 두고 기존 `next_order_date`를 첫 실행 가능한 SCHEDULED 회차로 옮긴다. 해당 날짜가 migration 기준일보다 과거면 기존 배송 주기를 반복해 기준일 당일 또는 이후가 되는 첫 날짜로 보정한다.
- 이행 직후 기존 Subscription마다 실행 가능한 미래 Schedule은 하나만 둔다.
- 기존 구독은 조회와 상태 명령을 사용할 수 있다. Pet이 없는 기존 구독은 플랜 변경 입력에서 본인 Pet을 선택해 연결한 뒤에만 변경할 수 있다.
- 새 구독에는 Pet이 필수다.

실제 컬럼·FK·PlanVersion 공유 방식·사전 검증 SQL·데이터 이행 순서와 rollback은 후속 Backend 설계의 책임이다.

## 사용자 흐름

### 정상 흐름

```text
로그인한 보호자
→ Pet 등록 또는 본인 Pet 선택
→ Pet 종에 호환되고 현재 판매 중인 현재 PlanVersion 선택
→ PlanVersion이 허용한 배송 주기 중 하나 선택
→ 새 구독 생성
→ 다음 예정일과 가격·구성 snapshot 확인
→ 필요 시 허용 상태의 플랜 변경·건너뛰기·일시정지·재개·해지 수행
→ 상태·명령 이력 확인
```

### 예외 흐름

- 타인의 Pet 또는 Subscription을 조회하거나 명령하려 하면 해당 자원을 노출하지 않는다.
- 신규 구독 또는 플랜 변경에서 판매 상태 비활성, Asia/Seoul 기준 판매 시작일 전·종료일 후, 이전 PlanVersion 또는 Pet 종 비호환 PlanVersion은 선택할 수 없다.
- 플랜 변경 대상 PlanVersion이 현재 구독 배송 주기를 허용하지 않으면 변경할 수 없다.
- ACTIVE가 아닌 구독의 플랜 변경·건너뛰기는 수행하지 않는다.
- CANCELED 구독은 재개하지 않으며 새 구독을 만든다.
- 같은 명령 식별 조합에서 다른 Payload를 보내면 충돌로 처리한다.
- 저장된 성공 replay가 아닌 새 명령의 Subscription version이 일치하지 않으면 이전 결과를 덮어쓰지 않는다.
- legacy 사전 검증이 실패하면 일부 행만 변환하지 않고 migration 전체를 시작하지 않는다.

## 기능 요구사항과 인수 조건

### REQ-PET-001 Pet 등록과 본인 조회

로그인한 회원은 이름과 DOG/CAT 종으로 Pet을 등록하고, 본인 Pet의 목록·상세를 조회할 수 있다.

- AC-PET-001-01: Pet 등록에는 이름과 DOG 또는 CAT 종이 필요하다.
- AC-PET-001-02: 회원은 자신의 Pet만 목록과 상세에서 확인한다.
- AC-PET-001-03: 타인 Pet은 목록·상세·신규 구독 선택 대상에 나타나지 않는다.

### REQ-PLAN-001 종별 판매 PlanVersion 선택

보호자는 신규 구독 또는 허용된 플랜 변경에서 Pet 종에 호환되고 현재 판매 중인 PlanVersion만 선택할 수 있다.

- AC-PLAN-001-01: 선택 가능한 현재 PlanVersion에는 대상 종, 구성 SKU·수량, 허용 배송 주기와 패키지 전체 가격이 확인 가능하다.
- AC-PLAN-001-02: 판매 상태가 비활성이거나 Asia/Seoul 기준 요청일이 판매 시작일 전·종료일 후인 Plan, 이전 PlanVersion 또는 Pet 종 비호환 PlanVersion은 신규 선택에서 제외된다.
- AC-PLAN-001-03: 판매 시작일과 종료일 당일은 판매 기간에 포함한다.
- AC-PLAN-001-04: 가격·구성이 달라진 Plan은 기존 PlanVersion을 고치지 않고 새 PlanVersion으로 제공하며, 이전 PlanVersion은 신규 선택 후보가 아니다.

### REQ-SUB-005 Pet 기반 신규 구독과 snapshot

로그인한 회원은 자신의 Pet, 호환 PlanVersion과 그 PlanVersion이 허용한 배송 주기 하나를 선택해 새 구독을 만들고, 다음 예정일 및 선택 당시의 가격·구성 snapshot을 확인한다.

- AC-SUB-005-01: 신규 구독은 본인 Pet, 현재 판매 중인 호환 PlanVersion 및 그 PlanVersion이 허용한 배송 주기 없이는 만들어지지 않는다.
- AC-SUB-005-02: 신규 구독은 KRW 정수 패키지 전체 가격, 구성 SKU·수량, 배송 주기 snapshot과 다음 예정일을 보존한다.
- AC-SUB-005-03: 이후 Plan 가격·구성이 바뀌어도 기존 구독의 snapshot은 자동 변경되지 않는다.

### REQ-SUB-006 본인 구독 조회와 상태·명령 이력

회원은 자신의 구독 목록·상세에서 현재 상태, 상태에 맞는 다음 예정일 또는 회차 이력, 현재 적용·pending 가격·구성 snapshot, 상태·명령 이력을 확인한다.

- AC-SUB-006-01: 회원은 자신의 구독만 목록과 상세에서 확인한다.
- AC-SUB-006-02: ACTIVE 구독 상세에는 실행 가능한 미래 SCHEDULED 회차의 다음 예정일과 현재 적용 snapshot이 포함된다. PAUSED·CANCELED에는 다음 예정일을 유효한 예정일로 표시하지 않는다.
- AC-SUB-006-03: 플랜 변경 직후에는 현재 적용 snapshot과 다음 회차용 pending snapshot을 구분한다. pending snapshot은 적용 회차가 될 때 현재 적용 snapshot으로 전환된다.
- AC-SUB-006-04: 상태와 명령 이력은 과거 회차·해지·교체된 pending 플랜 변경 명령을 지우지 않고 확인 가능하다.

### REQ-SUB-007 구독 관리 명령

회원은 자신의 구독 상태에 맞게 플랜 변경·건너뛰기·일시정지·재개·해지를 수행한다.

- AC-SUB-007-01: ACTIVE 구독의 플랜 변경은 현재 구독 배송 주기를 유지하고, 대상 PlanVersion이 그 주기를 허용할 때만 다음 실행 가능한 SCHEDULED 회차용 pending snapshot을 보존한다. 현재 적용 snapshot과 다음 예정일은 즉시 바꾸지 않는다.
- AC-SUB-007-02: 적용 전 pending이 있는 상태에서 새 플랜 변경이 성공하면 기존 pending을 최신 snapshot으로 교체한다. pending을 여러 개 쌓거나 적용 순서를 대기열로 관리하지 않는다.
- AC-SUB-007-03: pending 교체는 적용 대상 회차를 유지하며, 교체 전 플랜 변경 명령 결과는 이력에 남는다.
- AC-SUB-007-04: ACTIVE 구독 건너뛰기는 현재 회차를 SKIPPED로 바꾸고 주기 뒤 SCHEDULED 회차를 만든다. pending 변경이 있으면 새 회차로 적용 대상을 옮긴다.
- AC-SUB-007-05: 일시정지는 즉시 PAUSED, 예정 회차 HELD, 새 회차 생성 중단을 보장하며 pending 변경은 유지한다.
- AC-SUB-007-06: 재개는 PAUSED에서만 가능하고 재개일과 현재 주기로 새 예정일을 계산해 HELD 회차를 SCHEDULED로 바꾼다. pending 변경이 있으면 이 회차에 적용한다.
- AC-SUB-007-07: ACTIVE 또는 PAUSED의 해지는 즉시 CANCELED가 되며 미래 회차만 취소하고 과거 이력을 유지한다. pending 변경은 적용하지 않는다.
- AC-SUB-007-08: CANCELED 구독은 재개하거나 pending 변경을 적용하지 않으며, 재이용은 새 구독 생성이다.
- AC-SUB-007-09: 플랜 변경 자체는 다음 예정일을 다시 계산하지 않으며, pending snapshot 적용 회차 이후에도 유지된 현재 배송 주기로 다음 예정일을 계산한다.

### REQ-SUB-008 명령 재시도와 동시 수정 보호

회원의 구독 명령은 재시도와 동시 수정에서 이전 결과를 중복 적용하거나 덮어쓰지 않는다.

- AC-SUB-008-01: 같은 `Member + Subscription ID + 명령 유형 + Idempotency-Key`와 같은 Payload의 성공 관리 명령 재시도는 Subscription version 검사보다 먼저 판정하며 최초 성공 결과를 반환한다.
- AC-SUB-008-02: 같은 관리 명령 식별 조합과 다른 Payload는 충돌로 처리한다. 다른 Subscription 또는 다른 명령 유형의 같은 Key는 전역 충돌이 아니다.
- AC-SUB-008-03: 저장된 replay가 아닌 새 요청의 Subscription version 불일치는 동시 수정 충돌로 처리한다.
- AC-SUB-008-04: 신규 구독 생성은 `Member + CREATE_SUBSCRIPTION + Idempotency-Key`와 같은 Payload의 재시도에 같은 생성 결과를, 다른 Payload에 충돌을 반환한다.

### REQ-SUB-009 기존 Subscription 보존

기존 Subscription은 삭제·초기화하지 않고 2차 MVP의 migration-only PlanVersion 및 snapshot 정보와 손실 없이 연결한다.

- AC-SUB-009-01: 기존 Subscription은 조회와 허용 상태 명령을 계속 사용할 수 있다.
- AC-SUB-009-02: 기존 SKU 가격을 단가로 사용하고 기존 수량을 곱한 총액을 migration-only PlanVersion의 패키지 전체 가격과 LegacyInitialSnapshot 총액으로 보존한다. PlanItem에는 같은 SKU와 기존 수량을 보존한다.
- AC-SUB-009-03: legacy 가격·구성은 migration 시점 값이며 구독 생성 당시 값이 아니다. 이후 가격 변경으로 자동 변경되지 않는다.
- AC-SUB-009-04: 기존 Subscription은 이행 직후 ACTIVE이고 기존 `next_order_date`를 기준일 당일 또는 이후의 첫 실행 가능한 SCHEDULED 회차로 가진다. 과거 날짜는 기존 배송 주기를 반복해 보정한다.
- AC-SUB-009-05: Pet이 없는 기존 Subscription은 플랜 변경 입력에서 본인 Pet을 선택해 연결한 뒤에만 플랜을 변경할 수 있다.
- AC-SUB-009-06: 신규 구독에는 Pet이 필수이며, 기존 데이터에 한해서만 `pet_id`의 일시적 nullable을 허용한다.
- AC-SUB-009-07: 소수점 이하가 0이 아닌 SKU 가격, DOG/CAT으로 정규화할 수 없는 `pet_type`, SKU·Product 연결 또는 가격·구성 불일치가 있으면 값을 보정하지 않고 어떤 이행 데이터도 쓰기 전에 전체 migration을 중단한다.
- AC-SUB-009-08: migration-only PlanVersion은 신규 구독·플랜 변경 후보에 노출하지 않는다.

### REQ-SUB-010 실행 가능한 미래 Schedule 일관성

각 Subscription은 상태와 무관하게 여러 이력을 보존하되, 다음 예정일의 의미는 실행 가능한 미래 Schedule 하나로만 판단한다.

- AC-SUB-010-01: ACTIVE Subscription은 실행 가능한 미래 SCHEDULED 회차를 정확히 하나 가진다.
- AC-SUB-010-02: PAUSED Subscription은 실행 가능한 미래 SCHEDULED 회차가 없고 HELD 회차를 하나 가진다.
- AC-SUB-010-03: CANCELED Subscription은 실행 가능한 미래 Schedule이 없다. SKIPPED·CANCELED·과거 SCHEDULED 회차는 여러 이력으로 남을 수 있다.
- AC-SUB-010-04: ACTIVE의 현재 SCHEDULED 회차가 과거가 되면 현재 배송 주기로 다음 실행 가능한 SCHEDULED 회차를 이어서 유지하며, 실제 주문·결제·재고·배송 또는 Batch는 만들지 않는다.

## 요구사항 추적성

| 요구사항 | 도메인 해석 | 도메인 문서 |
| --- | --- | --- |
| REQ-PET-001 | Pet 소유권과 신규 구독 대상 조건 | [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md) |
| REQ-PLAN-001 | SubscriptionPlan, PlanVersion, PlanItem과 판매·종 호환성 | [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md) |
| REQ-SUB-005~010 | Subscription 상태·Schedule·현재·pending snapshot·명령·기존 데이터 불변 조건 | [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md) |

## Deferred Technical Decision

다음 항목은 이 제품 요구사항의 결정 범위가 아니며, 후속 기술 설계에서 정한다.

- Entity와 Aggregate 구현 경계
- endpoint, DTO, HTTP 상태·오류 표현
- table, column, FK, index와 실제 데이터 이행·rollback 순서
- 동일 legacy SKU·수량 조합의 PlanVersion 공유 방식과 migration 사전 검증 SQL
- 동시성 제어와 Subscription version, Idempotency-Key의 저장·locking 구현 방식

## 미결정 Product Decision

새로운 Product Decision은 추가하지 않는다. 이 문서는 승인된 2차 MVP 정책과 안전한 legacy 이행 경계를 기록한다.
