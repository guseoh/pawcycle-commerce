# PS-005 정기배송 주문 자동화 요구사항

- 작업 ID: `SUB-AUTO-001`
- 상태: Approved Input
- 승인 출처: 2026-08-10 Product Owner 승인

## 목적과 기존 MVP 경계

ACTIVE Subscription의 예정 회차가 도래하면 실제 처리 결과인 최소 정기 Order를 정확히 하나 만들고, 다음 미래 Schedule까지 안전하게 이어 간다.

[PS-004](PS-004-second-mvp-requirements.md)와 [DOMAIN-002](../domain/DOMAIN-002-second-mvp-subscription-domain.md)는 당시 MVP2에서 실제 Order 자동 생성을 제외한 역사적 계약이다. 이 문서는 그 기록을 소급 수정하지 않고, `SUB-AUTO-001`부터 적용하는 후속 delta를 정의한다.

## 승인된 결과

- SubscriptionSchedule은 예정된 회차이고 Order는 그 회차를 실제로 처리한 결과다.
- 한 Schedule에는 Order가 최대 하나만 존재한다.
- 실행 대상은 Asia/Seoul 기준 `ACTIVE + mvp2_managed + SCHEDULED + scheduledDate <= today + Order 없음`인 회차다.
- 한 Subscription에 여러 실행 대상이 조회되어도 한 Scheduler 실행에서는 가장 오래된 회차 하나만 처리한다.
- Scheduler가 오래 중단됐더라도 누락 주기별 가짜 Order를 만들지 않는다. 가장 오래된 회차의 Order 하나를 만들고 원래 예정일에서 기존 배송 주기를 반복해 `today`보다 미래인 첫 날짜로 이동한다.
- Scheduler 실행은 at-least-once여도 DB 결과는 Schedule당 Order 하나다.

## 원자적 처리 계약

각 Schedule은 다른 Schedule과 분리된 transaction에서 처리한다. 성공 transaction은 다음 결과를 모두 함께 확정한다.

1. Subscription과 Schedule의 최신 상태 및 기존 Order를 다시 확인한다.
2. pending 변경이 대상 Schedule에 연결돼 있으면 pending snapshot을, 아니면 current snapshot을 effective snapshot으로 정한다.
3. effective snapshot의 패키지 가격과 SKU·수량을 고정한 `CREATED` Order를 만든다.
4. Schedule에 effective snapshot을 기록한다.
5. pending을 사용했다면 같은 transaction에서 current로 승격하고 pending을 제거한다.
6. 원래 예정일과 기존 배송 주기로 `today`보다 미래인 첫 Schedule 하나를 만든다.
7. Subscription version을 한 번 증가시킨다.

중간 단계가 하나라도 실패하면 Order, Schedule 결과, current·pending, 다음 Schedule과 version을 모두 rollback한다. 실패 Order row는 남기지 않으며 다음 Scheduler tick에서 같은 회차를 다시 시도할 수 있어야 한다.

## 동시성·재실행 계약

- DB unique constraint가 Schedule당 Order 하나를 최종 강제한다.
- transaction은 Subscription과 대상 Schedule의 최신 상태를 lock 또는 동등한 MySQL 경쟁 제어로 재검사한다.
- 같은 target의 중복 조회, 연속 Scheduler 실행, 두 Scheduler의 동시 실행은 business Order 하나로 수렴한다.
- 사용자 명령과 Scheduler가 경쟁하면 먼저 성공한 transaction의 Subscription version 증가를 보존하고, stale `If-Match`는 기존 계약대로 충돌한다.
- 한 target 실패는 다음 target transaction을 중단하거나 rollback하지 않는다.
- 별도 운영자 Retry API, retry count, dead-letter queue, exponential backoff는 두지 않는다.

## snapshot과 범위

Order는 생성 시점의 effective snapshot ID, source PlanVersion ID, 패키지 전체 KRW 가격, SKU·수량, 원래 scheduled date와 실제 processed instant를 보존한다. 이후 PlanVersion 또는 Subscription current snapshot 변경이 과거 Order를 바꾸지 않는다.

이번 상태는 `CREATED`만 사용한다. 실제 PG 결제, Payment, 재고 차감, 배송 생성, 사용자·관리자 Order API/UI는 포함하지 않는다.

## reconciliation 경계

정상 due 처리는 Order 자동화의 유일한 책임이다. 기존 reconciliation은 Order가 없는 due Schedule에 effective snapshot을 채우거나 미래 Schedule을 만들어 해당 회차를 소비하지 않는다. reconciliation은 이미 처리된 Order 결과에서 안전하게 복구 가능한 미래 Schedule 누락 같은 cardinality 보정만 담당한다.

## 운영 경계와 인수 조건

- 자동화는 enable/disable, cadence, batch size를 외부 설정으로 받으며 기본 설정만으로 Production에서 활성화되지 않는다.
- 실행 수, 처리 후보 수, Order 생성 수, 실패 수, duplicate/no-op 수와 duration을 저카디널리티 metric으로 제공한다.
- 실패 log에는 개인정보나 payload 없이 subscription ID, schedule ID와 failure category만 남긴다.
- failure 증가는 기존 local Prometheus·Alertmanager 경로에서 확인할 수 있어야 한다. 임계값·escalation·repeat 정책은 Production 계약이 아니다.
- 실제 Production Scheduler 활성화, Production DB migration과 deploy는 이번 작업에서 수행하지 않는다.

다음 조건을 만족하면 제품 요구사항을 충족한다.

- 오늘 도래한 ACTIVE 회차는 Order 하나와 정확한 다음 미래 Schedule을 만든다.
- 미래 회차와 PAUSED·CANCELED 구독은 Order를 만들지 않는다.
- pending snapshot 적용, long downtime jump, 실패 rollback·다음 tick 재처리와 동시 중복 방지가 검증된다.
- 기존 MVP2 명령 idempotency·상태 전이와 reconciliation 실패 격리가 유지된다.
