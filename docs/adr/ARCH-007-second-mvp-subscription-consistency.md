# ARCH-007 2차 MVP Subscription 일관성 경계

## 상태

Proposed

## 날짜

2026-08-02

## 작업 ID

`API-004`

## 맥락(Context)

[PS-004](../product/PS-004-second-mvp-requirements.md)와 [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md)는 Pet 기반 PlanVersion 구독, snapshot, 상태 전이, legacy 보존을 승인했다. 구현은 같은 Subscription에 대한 재시도·동시 명령·pending 교체·Schedule 유지와 기존 V2 데이터 이행을 손실 없이 처리해야 한다. 기존 1차 MVP API와 V2 migration은 소급 수정하거나 reset할 수 없다.

## 결정(Decision)

다음 구현 경계를 Proposed로 채택한다.

1. Subscription을 상태, optimistic version, `current_snapshot_id`, 최대 하나 pending, Schedule, command history와 idempotency result의 일관성 aggregate로 둔다. snapshot item은 관계형 child로 저장하고 Schedule은 `effective_snapshot_id`로 적용 snapshot을 보존한다.
2. 관리 명령은 `If-Match` header의 Subscription version을 사용한다. 누락은 428, 형식 오류는 400, 신규 version 불일치는 412로 구분하며 상세·생성·명령 성공에는 `ETag`를 제공한다.
3. 생성과 관리 idempotency result는 nullable scope unique에 의존하지 않는 별도 table로 둔다. 같은 scope·같은 canonical payload의 성공 replay는 저장한 business status·body·`Location`·`ETag`를 그대로 반환하고 replay header만 추가한다. 실패는 저장하지 않고 성공은 자동 만료하지 않는다.
4. 한 transaction에서 command 상태 전이, snapshot/pending 변경, Schedule cardinality, version 증가, command history와 성공 replay 기록을 완료한다. optimistic update 0건 뒤에는 scope를 재조회해 replay·payload 충돌을 먼저 판정한다. DB unique 제약은 pending·scope·필수 참조를 보조하고, 비관적 lock·Redis·message broker는 추가하지 않는다.
5. Schedule reconciliation은 조회가 아니라 write command와 scheduler trigger가 공유할 application service 책임이다. scheduler trigger는 command 없는 ACTIVE Subscription의 cardinality를 위해 2차 MVP 구현 전에 제공해야 하며, overdue target Schedule에서 pending 승격 후 다음 미래 Schedule을 만든다.
6. legacy migration은 source write freeze 뒤 같은 snapshot의 preflight, 전용 DML transaction, post-validation, 별도 constraint hardening 순서로 둔다. DML 실패는 전체 rollback하지만 DDL auto-commit·hardening 실패가 자동 전체 rollback을 뜻하지 않는다. 자동 down migration은 rollback 전략이 아니다.

구체적인 endpoint·오류는 [API-004](../api/API-004-second-mvp-api-contract.md), 물리 구조·제약·migration 단계는 [DATA-003](../data/DATA-003-second-mvp-subscription-data-design.md)에 제안한다.

## 검토한 대안(Alternatives considered)

### A. request body의 version과 매 명령마다 별도 application lock

- 장점: header 처리 없이 DTO만으로 표현할 수 있다.
- 단점: 비즈니스 body와 concurrency precondition이 섞이고, replay 우선순위와 HTTP 조건부 갱신의 의도가 덜 명확하다.

### B. Subscription별 비관적 DB lock 또는 Redis 분산 lock

- 장점: 동일 row의 경합을 직렬화하기 쉽다.
- 단점: lock 수명·장애·운영 의존성이 늘며, 현재 승인 입력만으로 필요한 근거가 없다. DB version과 unique 제약을 먼저 검증할 수 있다.

### C. GET에서 과거 Schedule을 자동 catch-up

- 장점: 조회 후 next date가 항상 최신처럼 보인다.
- 단점: 읽기 요청이 상태를 변경하고 재시도·권한·관측성 경계를 흐린다. 조회와 write를 분리하는 것이 안전하다.

### D. legacy 오류 row를 건너뛰는 부분 migration

- 장점: 일부 데이터는 빨리 사용할 수 있다.
- 단점: 같은 legacy 집합에서 정책이 갈리고 손실·재실행·복구 판단이 불명확해진다.

## 결과와 영향(Consequences)

- Backend는 Subscription application service 하나의 transaction 경계에서 version·pending·Schedule·history·idempotency를 함께 테스트하고, concurrent create-key 경합과 optimistic 패자 재조회도 검증해야 한다.
- Frontend는 명령마다 `Idempotency-Key`와 현재 `version`을 `If-Match`에 전송하고, replay와 version mismatch를 구분해 처리해야 한다.
- DB는 분리된 idempotency scope, pending 최대 하나, immutable PlanVersion의 current pointer, Schedule-effective snapshot, legacy API visibility와 source write freeze를 지원하는 제약을 제공해야 한다. 실제 index 표현은 MySQL·Flyway 검증 후 확정한다.
- `LegacyInitialSnapshot`과 `PendingPlanChange`는 결과 불변 조건이며, 별도 Entity/table 강제가 아니다.
- scheduler trigger의 배포·운영 cadence, 여러 누락 회차 처리, 주문·결제·배송 생성은 여전히 후속 기술·운영 결정이다.

## 실패와 복구 경계

이 ADR은 문서 Proposed이므로 일반 revert PR로 되돌릴 수 있다. 실제 schema/data migration은 이 ADR만으로 승인되지 않으며, preflight 실패 시 write 이전에 중단한다. write 이후 복구는 reset·자동 down migration이 아니라 승인된 backup/restore·운영 절차의 별도 작업이다. Production·AWS·운영 DB·Secret 실행은 범위 밖이다.

## 후속 구현 작업

1. API-004 승인 후 controller/DTO·session/CSRF·소유권·오류 매핑을 구현한다.
2. DATA-003을 기준으로 additive Flyway와 legacy preflight·migration을 별도 고위험 구현 작업으로 설계·검증한다.
3. MySQL integration test에서 optimistic conflict 뒤 replay 재조회, 생성 idempotency race, pending target overdue 승격, 상태별 Schedule cardinality, UTC 저장·Asia/Seoul 표현, source write freeze와 legacy migration DML 실패 원자성을 검증한다.
4. scheduler와 실제 order/payment/delivery는 별도 승인된 요구사항과 운영 설계가 생긴 뒤 구현한다.
