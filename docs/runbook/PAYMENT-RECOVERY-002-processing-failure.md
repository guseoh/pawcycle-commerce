# PAYMENT-RECOVERY-002 고립된 NORMAL/PROCESSING 결제의 실패 확정

[#417](https://github.com/guseoh/pawcycle-commerce/issues/417) Phase 2b. 결제사에서 실패로 끝났는데 로컬에는 `PROCESSING`으로 남은 일반 결제를 실패로 확정하고 재고 예약을 푼다. 결제사 조회로 **같은 결제의 `ABORTED` 또는 `EXPIRED`가 확인된 경우에만** 바꾸는 단발 유지보수 명령이다. 승인 원본은 [#417 2026-11-17 설계 승인 댓글](https://github.com/guseoh/pawcycle-commerce/issues/417#issuecomment-6941190358)이다(Wiki 결정 PWC-0134).

> 이 문서는 절차다. **운영 환경에서 실행하려면 별도의 명시적 사용자 승인**이 필요하다. 운영 DB 쓰기, 실제 Toss 조회, Secret 사용도 각각 승인 대상이다.

성공 확정(결제사 `DONE`)은 [PAYMENT-RECOVERY-001](PAYMENT-RECOVERY-001-processing-success.md)의 별도 명령으로 처리한다.

## 범위

| 한다 | 하지 않는다 |
| --- | --- |
| 결제사 조회 결과가 같은 주문 ID·결제 키·금액의 `ABORTED`·`EXPIRED`일 때만 실패 확정 효과를 한 트랜잭션으로 적용한다 | 결제사에 승인·취소 요청을 보내지 않는다 |
| 기본 모드(`inspect`)는 조회만 하고 아무것도 바꾸지 않는다 | 장바구니를 바꾸지 않는다 |
| 감사 로그 `PAYMENT_PROCESSING_FAILED_ABORTED` 또는 `PAYMENT_PROCESSING_FAILED_EXPIRED`를 남긴다 | 고객 알림을 만들지 않는다(일반 결제 실패 경로와 같음) |
| 대상이 아니거나 결제사 상태가 맞지 않으면 아무것도 바꾸지 않고 멈춘다 | 여러 건을 한 번에 처리하지 않는다. 자동 실행(scheduler)이 없다 |

적용 효과(한 트랜잭션, 이 순서): 결제 행 잠금과 재확인 → SKU별 재고 `RELEASE` → 결제 `PROCESSING → FAILED`(`provider_status`에 `ABORTED`/`EXPIRED`, `failure_code=RECOVERED_PROVIDER_FAILED`) → 주문 `PAYMENT_PENDING → PAYMENT_FAILED`(조건부 갱신) → 이 주문에 예약된 쿠폰 `RESERVED → AVAILABLE` → 감사 행. 중간에 하나라도 맞지 않으면 전체가 롤백된다.

## 시작 조건

명령이 스스로 확인하는 조건이다. 하나라도 맞지 않으면 결제사 호출 없이 `REFUSED_*`로 끝난다.

- 실행자(`operator-member-id`)가 `ADMIN` 역할 회원이다.
- 결제 `type=NORMAL`, `status=PROCESSING`이고 주문은 `PAYMENT_PENDING`이다.
- `provider_order_id`, `payment_key`, `amount`가 모두 있다.
- 결제창 만료 시각(`expires_at`)으로부터 10분 이상 지났다. `expires_at`이 비어 있으면 대상이 아니다.
- 같은 주문에 `SUCCEEDED` 결제가 없다.
- 이 결제의 `RESERVE` 이동이 주문 항목과 **SKU별 수량까지** 정확히 일치하고, 이 결제의 `DEDUCT`·`RELEASE` 이동은 없다.
- 같은 프로세스에 결제 만료·정기결제·구독 주문 생성·회차 보정 스케줄러가 켜져 있지 않다(`REFUSED_BACKGROUND_TRIGGER_ACTIVE`). 이 명령을 켜면 그 프로세스의 `@Scheduled` 작업 전체가 꺼진다.
- 성공 확정 명령(`recover-processing-payment.enabled`)을 같은 프로세스에서 함께 켜지 않는다(`REFUSED_INVALID_INPUT conflicting=...`).

## 작업자 배제 (쓰기 전 필수)

쓰기(`mode=confirm`)는 **관리자가 다음을 확인한 상태에서만** 실행한다. 명령은 이 사실을 스스로 검증할 수 없으므로, 근거 참조값(`workers-stopped-evidence`)이 없으면 쓰기를 거부한다(`REFUSED_WORKERS_NOT_STOPPED`, 종료 코드 2).

1. 이 결제를 처리할 수 있는 **모든 인스턴스**에서 신규 요청 유입을 막는다(예: 프록시에서 결제 확인 경로 차단 또는 backend 중지).
2. 이미 시작된 결제 확인 작업이 **끝났음**을 확인한다(예: backend 프로세스 종료 확인, 진행 중 요청 0건 확인).
3. 이 상태를 명령의 최종 트랜잭션이 끝날 때까지 유지한다.

경과 시간이나 lease 만료는 작업 종료의 근거가 아니다. 이 근거를 확인할 수 없으면 **`inspect`까지만** 실행한다.

`workers-stopped-evidence`에는 근거를 찾을 수 있는 참조(변경 기록 번호, 운영 기록 링크 등)를 쓴다. 결과 줄에 그대로 출력되고 DB에는 저장하지 않는다. 개인정보·결제 키·Secret을 쓰지 않는다.

## 실행

웹 서버 없이 단발 프로세스로 실행한다. 백그라운드 스케줄러 설정은 반드시 끈다.

1. 먼저 조회만 한다(기본 `mode=inspect`).

```bash
java -jar backend.jar \
  --spring.main.web-application-type=none \
  --pawcycle.commerce.checkout-expiration.enabled=false \
  --pawcycle.commerce.billing.enabled=false \
  --pawcycle.subscription.automation.enabled=false \
  --pawcycle.subscription.reconciliation.enabled=false \
  --pawcycle.maintenance.confirm-processing-payment-failure.enabled=true \
  --pawcycle.maintenance.confirm-processing-payment-failure.payment-id=<결제 id> \
  --pawcycle.maintenance.confirm-processing-payment-failure.operator-member-id=<관리자 회원 id> \
  --pawcycle.maintenance.confirm-processing-payment-failure.reason="<조사 메모. 개인정보·결제 키를 쓰지 않는다>"
```

2. `RESULT=INSPECTED_CONFIRMABLE`이 나오고 작업자 배제를 마쳤으면, 같은 명령에 두 설정을 더해 확정한다. 확정 단계에서도 결제사를 다시 조회한다.

```bash
  --pawcycle.maintenance.confirm-processing-payment-failure.mode=confirm \
  --pawcycle.maintenance.confirm-processing-payment-failure.workers-stopped-evidence="<작업자 배제 근거 참조>"
```

출력 예: `RESULT=FAILURE_CONFIRMED mode=confirm paymentId=123 operatorMemberId=7 reason="#417 조사" workersStoppedEvidence="CHG-12"`

출력된 결과 줄을 조사 기록(Issue나 운영 기록)에 남긴다.

## 결과 해석

| 출력 `RESULT=` | 종료 코드 | 의미 | 다음 행동 |
| --- | --- | --- | --- |
| `INSPECTED_CONFIRMABLE` | 0 | 조회만 함. 같은 결제의 `ABORTED`/`EXPIRED` 확인 | 작업자 배제 후 `mode=confirm` |
| `FAILURE_CONFIRMED` | 0 | 실패 확정 완료 | 사후 확인, 작업자 배제 해제 |
| `ALREADY_CONFIRMED` | 0 | 이 명령으로 이미 확정됨(동시·재실행). 효과는 한 번만 적용됨 | 사후 확인만 |
| `CONFIRMED_AFTER_COMMIT_ERROR` | 0 | 커밋 응답이 유실됐지만 새 트랜잭션에서 읽은 결제·주문·원장·쿠폰·감사가 모두 확정 상태 | 사후 확인 |
| `CHANGED_DURING_QUERY` | 3 | 조회하는 사이 결제·주문·예약이 바뀜. 배제되지 않은 작업자가 있었을 수 있음 | 작업자 배제를 다시 확인하고 상태를 조사 |
| `STOPPED_PROVIDER_DONE` | 3 | 결제사는 같은 결제를 `DONE`으로 확정 | 실패 확정하지 않는다. [PAYMENT-RECOVERY-001](PAYMENT-RECOVERY-001-processing-success.md) 절차로 |
| `STOPPED_PROVIDER_UNKNOWN` | 3 | 미발견·조회 불가·HTTP 오류·타임아웃·주문/키/금액 불일치·진행 중·취소·부분 취소 | 결제사 콘솔에서 직접 확인. 반복 실행하지 말고 조사 |
| `STOPPED_PROVIDER_OTHER` | 3 | 예상하지 못한 분류 | 조사 |
| `REFUSED_*` | 3 | 시작 조건 불충족(`REFUSED_ORDER_HAS_SUCCEEDED_PAYMENT`, `REFUSED_RESERVATION_MISMATCH` 포함) | 사유에 맞게 조사 |
| `REFUSED_INVALID_INPUT`, `REFUSED_WORKERS_NOT_STOPPED`, `REFUSED_BACKGROUND_TRIGGER_ACTIVE` | 2 | 입력·작업자 배제 근거·프로세스 설정 문제 | 명령을 고쳐 다시 실행 |
| `FAILED_ROLLED_BACK` | 4 | 확정 트랜잭션이 실패했고, 새 트랜잭션에서 읽은 상태가 실행 전과 같음(`PROCESSING`·`PAYMENT_PENDING`·`RELEASE` 없음·감사 없음) | 원인(잠금 대기 초과 등)을 확인하고, 작업자 배제를 유지한 채 다시 실행할 수 있다 |
| `UNDETERMINED` | 4 | 커밋 여부를 판정할 수 없음(근거가 엇갈리거나 DB를 읽지 못함) | **다시 실행하지 않는다.** 아래 "커밋 여부 판정"으로 직접 확인 |
| `FAILED_EXCEPTION type=...` | 4 | 사전 조회 단계의 예상하지 못한 예외(DB 연결 등). 쓰기는 시작되지 않음 | 원인 확인 후 다시 실행 |

## 커밋 여부 판정

명령이 종료 코드 4로 끝났거나 출력이 유실됐으면, 쓰기 DB에서 다음을 **함께** 조회해 판정한다.

- `payments`: status, provider_status, failure_code
- `orders`: status
- 이 결제의 `inventory_movements`(`RESERVE`, `RELEASE`의 SKU·수량)
- 이 주문의 `member_coupons`(status, reserved_order_id)
- `admin_audit_logs`에서 `target_type='PAYMENT'`, 이 결제 id, action `PAYMENT_PROCESSING_FAILED_ABORTED`/`_EXPIRED`

| 상태 | 판정 |
| --- | --- |
| 결제 `FAILED`·`RECOVERED_PROVIDER_FAILED`, 주문 `PAYMENT_FAILED`, SKU별 `RELEASE`가 주문 수량과 일치, 예약 쿠폰 없음, 감사 1행 | 확정 완료 |
| 결제 `PROCESSING`, 주문 `PAYMENT_PENDING`, `RELEASE` 없음, 감사 없음 | 적용 안 됨. 작업자 배제와 새 조회를 거쳐 다시 실행할 수 있다 |
| 그 외 | 결과 미확정. 실행을 멈추고 조사한다 |

이 과정에서 결제 승인·취소 요청을 보내지 않는다.

## 보존할 증거

실행 전과 후에 위 "커밋 여부 판정"의 항목과 `inventories`(해당 SKU)를 조회해 남긴다. 결제 키 원문, 개인정보, 결제사 원시 응답은 남기지 않는다.

## 관측

- 업무 이벤트 `payment.failure_confirmation.finished`(`paymentId`, `adminId`, `outcome`, 결제사 상태가 있으면 `providerStatus`)가 실행마다 한 줄 남는다.
- 결제사 조회 실패는 `ExternalCallFailures`(`TOSS_PAYMENT_QUERY`)로 기록된다.
- 확정 트랜잭션이 예외로 끝나면 경고 로그(예외 종류와 결제 id)를 남기고 커밋 여부를 다시 읽는다.

## 남은 위험

- 작업자 배제는 관리자의 확인과 근거 참조값에 기대며, 명령이 다른 인스턴스의 상태를 검증하지 않는다.
- 결제사 응답 해석은 Phase 1 조회 분류(`TossTestPaymentAdapter.queryPayment`)와 가짜 결제사 테스트에 기대고 있다. 실제 Toss 테스트 환경 검증은 별도 승인 대상이다. 운영 profile의 결제 어댑터는 현재 fail-closed라 운영에서는 `REFUSED_PROVIDER_UNAVAILABLE`로 끝난다.
- `expires_at`이 없는 결제는 대상이 아니다.
- 검증 근거: `ProcessingPaymentFailureConfirmationIntegrationTests`(일회용 MySQL 8.4, 가짜 결제사 HTTP 서버와 실제 Toss 조회 파서), `ProcessingPaymentFailureConfirmationCommandTests`, `ProcessingPaymentFailureConfirmationWiringTests`, `PawcycleBackendApplicationRoutingTests`. 실행 jar로 위 명령을 로컬 일회용 MySQL과 `local-integration` sandbox 결제사(provider order id에 `aborted`/`expired`가 들어 있으면 해당 실패를 반환)에 실행해 결과 줄과 종료 코드를 확인했다.
