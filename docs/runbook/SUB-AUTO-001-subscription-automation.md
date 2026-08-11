# SUB-AUTO-001 정기배송 주문 자동화 Runbook

## 범위

이 문서는 local observability에서 정기배송 자동화의 실패를 확인하고 다음 자동 재시도를 관찰하는 절차다. Production Scheduler, Production DB, Cloud 또는 Secret을 실행·조회하지 않는다.

## 증상

`PawCycleSubscriptionAutomationFailure` alert 또는 `pawcycle_subscription_automation_failures_total` 증가가 보인다. application log에는 payload 없이 `subscriptionId`, `scheduleId`, `failureCategory`만 남는다.

## 사용자 영향

실패한 due Schedule은 Order와 다음 Schedule을 만들지 않은 채 남는다. 다른 Subscription의 독립 transaction은 계속 처리될 수 있다.

## 첫 확인 절차

1. Grafana의 `Subscription automation totals`와 `Subscription automation duration` panel에서 failures, orders created, duration을 확인한다.
2. 같은 시간대의 Backend log에서 safe identifier와 failure category를 찾고 DB 제약·snapshot·연결 원인을 확인한다.
3. 해당 Schedule에 `subscription_orders` row가 없고 future Schedule·pending promotion·Subscription version이 advance되지 않았는지 확인한다.

## 완화 조치

원인을 제거한 뒤 automation enable 설정이 유지된 local 환경에서 다음 tick을 관찰한다. 같은 Schedule의 Order 한 건, pending 승격(해당 시), 미래 Schedule 한 건과 version 증가가 한 transaction 결과로 나타나는지 확인한다. 수동 Production DB update를 정상 재처리 방법으로 사용하지 않는다.

## 롤백

지속 실패가 발생하면 automation enable property를 `false`로 두고 조사한다. 코드 rollback은 일반 revert PR로 수행하며 Production DB migration 또는 down migration을 실행하지 않는다.

## 에스컬레이션

snapshot 불변 조건, duplicate Order, rollback 불완전, 데이터 손실·보안 위험이 보이면 자동화를 중지하고 Backend Engineer와 Product Owner에게 escalation한다. Payment·Inventory·Delivery 정책이 필요해지면 이 작업 범위에서 결정하지 않는다.

## 보존할 증거

alert 시각, failure counter, safe log identifier/category, Order·Schedule cardinality, 다음 tick의 retry 결과와 관련 test/CI 결과를 남긴다. 개인정보·payload·Secret은 보존하지 않는다.

## 후속 작업

관리자 Retry API, dead-letter queue, retry count, exponential backoff 및 Production alert threshold는 이번 범위 밖이다.
