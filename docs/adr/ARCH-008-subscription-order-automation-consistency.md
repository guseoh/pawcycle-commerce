# ARCH-008 정기배송 Order 자동화 일관성

## 상태

Accepted — `SUB-AUTO-001` 사용자 승인

## 맥락

[PS-005](../product/PS-005-subscription-automation-requirements.md)는 at-least-once Scheduler가 due Schedule을 처리하되 Schedule당 Order 하나, target별 실패 격리, pending 승격과 다음 Schedule의 원자성을 요구한다. 기존 [ARCH-007](ARCH-007-second-mvp-subscription-consistency.md)의 reconciliation은 Order가 없던 MVP2의 cardinality 책임이므로 정상 Order 실행과 분리해야 한다.

## 결정

1. Scheduler batch에는 transaction을 두지 않고, 각 candidate는 `REQUIRES_NEW` transaction으로 처리한다.
2. bounded 후보 조회는 `ACTIVE + mvp2_managed + SCHEDULED + due + Order 없음`을 stable order로 반환하며 Subscription별 가장 오래된 후보 하나만 포함한다.
3. 처리 transaction은 Subscription row를 먼저 잠그고 대상 Schedule을 잠근 뒤 상태와 Order 존재를 재검사한다. 기존 사용자 명령도 Subscription row lock을 사용하므로 같은 aggregate의 경쟁을 직렬화한다.
4. `subscription_orders.schedule_id` unique constraint를 최종 중복 안전망으로 둔다. duplicate는 Order 존재를 재확인한 경우에만 정상 no-op로 취급한다.
5. Order header·items 복사, Schedule effective snapshot, pending 승격·제거, 다음 미래 Schedule과 Subscription version 증가는 한 transaction이다.
6. automation enable, fixed delay와 batch size는 외부 설정이다. enable property가 명시적으로 `true`일 때만 trigger bean을 만든다.
7. 기존 reconciliation은 unprocessed due Schedule을 건드리지 않는다. 이미 Order가 있는 처리 결과를 기준으로 미래 Schedule 누락을 안전하게 복구할 때만 별도 transaction으로 보정한다.
8. 실패는 target별로 rollback하고 다음 target을 계속 처리한다. 다음 tick 자동 재선택 외에 Retry API·backoff·DLQ를 추가하지 않는다.

## 선택 근거와 대안

- batch 전체 transaction은 한 target 실패가 전체 batch를 rollback하므로 채택하지 않는다.
- application memory lock이나 Redis·Kafka·분산 lock은 process 경계를 최종 보호하지 못하거나 새 운영 의존성을 만들므로 채택하지 않는다.
- Scheduler용 HTTP idempotency row는 식별 범위와 retention 의미가 다르므로 재사용하지 않는다.
- 누락된 주기별 Order catch-up은 장기 중단 뒤 의도하지 않은 주문 폭증을 만들므로 채택하지 않는다.
- Schedule 상태에 `PROCESSED`를 추가하지 않고 Order 존재를 실제 처리 결과로 사용한다. 기존 Schedule 이력 상태 계약을 소급 변경하지 않는다.

## 결과와 복구 경계

- Subscription row lock은 같은 구독의 Scheduler와 사용자 명령을 직렬화하고, version 증가는 stale `If-Match`를 보존한다.
- 다른 Subscription은 독립 transaction이므로 병렬 처리와 실패 격리가 가능하다.
- MySQL DDL은 auto-commit될 수 있어 Order header, item, due-query index를 Flyway version별 단일 DDL로 분리한다.
- 저장소 변경은 일반 revert PR로 되돌릴 수 있다. 이미 적용된 Production DDL의 down migration이나 데이터 삭제는 이 ADR이 승인하지 않는다.
- Production Scheduler 활성화, Production DB migration·repair·deploy는 별도 고위험 승인 없이는 수행하지 않는다.
