# PAYMENT-RECOVERY-001 고립된 NORMAL/PROCESSING 결제의 성공 확정

[#417](https://github.com/guseoh/pawcycle-commerce/issues/417) Phase 2a. 일반 결제가 `PROCESSING`인 채로 남아 있을 때, 결제사 조회로 **같은 결제의 DONE이 확인된 경우에만** 로컬에서 성공으로 확정하는 단발 유지보수 명령이다.

> 이 문서는 절차다. **운영 환경에서 실행하는 것은 별도의 명시적 사용자 승인**이 필요하다. 운영 DB 쓰기, 실제 Toss 조회, Secret 사용도 각각 승인 대상이다.

## 범위

| 한다 | 하지 않는다 |
| --- | --- |
| 결제사 조회 결과가 같은 주문 ID·결제 키·금액의 `DONE`이면, 정상 결제 확정과 같은 효과를 같은 순서로 한 번 적용한다 | 결제사에 승인 요청(confirm)을 다시 보내지 않는다 |
| 감사 로그 `PAYMENT_PROCESSING_RECOVERED`를 남긴다 | 실패 확정, 재고 예약 해제, 결제 상태 되돌리기를 하지 않는다 |
| 대상이 아니거나 조회 결과가 확정되지 않으면 아무것도 바꾸지 않고 멈춘다 | 여러 건을 한 번에 처리하지 않는다 |

적용 효과(정상 확정과 같음): 재고 `DEDUCT` → 결제 `SUCCEEDED` → 주문 `PAID` → 배송 `PREPARING` → 쿠폰 `USED` → `ORDER_PAID` 알림 → 장바구니 소비 → 멤버십 평가.

## 시작 조건

명령이 스스로 확인하는 조건이며, 하나라도 맞지 않으면 결제사 호출 없이 `REFUSED_*`로 끝난다.

- 실행자(`operator-member-id`)가 `ADMIN` 역할 회원이다.
- 결제 `type=NORMAL`, `status=PROCESSING`이고 주문은 `PAYMENT_PENDING`이다.
- `provider_order_id`, `payment_key`, `amount`가 모두 있다.
- 결제창 만료 시각(`expires_at`)으로부터 10분 이상 지났다. `expires_at`이 비어 있으면 대상이 아니다.
- 주문 항목마다 `RESERVE` 이동이 있고, `DEDUCT`·`RELEASE` 이동은 없다.
- 같은 프로세스 안에 결제 만료·정기결제·구독 주문 생성·회차 보정 스케줄러가 켜져 있지 않다. 켜져 있으면 `REFUSED_BACKGROUND_TRIGGER_ACTIVE`로 끝난다.

## 권고 (강제하지 않음)

- 가능하면 실행 전에 결제 요청 유입과 진행 중 작업을 멈추고 확인한다. 성공 확정은 결제 행 잠금과 `PROCESSING` 재확인 때문에 동시 실행에도 한 번만 적용된다. 그래서 이 확인은 정확성 조건이 아니라 추가 안전장치다.
- 실행 전후의 결제·주문·재고 상태를 조회해 보존한다(아래 "보존할 증거").

## 실행

웹 서버 없이 단발 프로세스로 실행한다. 백그라운드 스케줄러 설정은 반드시 끈다.

```bash
java -jar backend.jar \
  --spring.main.web-application-type=none \
  --pawcycle.commerce.checkout-expiration.enabled=false \
  --pawcycle.commerce.billing.enabled=false \
  --pawcycle.subscription.automation.enabled=false \
  --pawcycle.subscription.reconciliation.enabled=false \
  --pawcycle.maintenance.recover-processing-payment.enabled=true \
  --pawcycle.maintenance.recover-processing-payment.payment-id=<결제 id> \
  --pawcycle.maintenance.recover-processing-payment.operator-member-id=<관리자 회원 id> \
  --pawcycle.maintenance.recover-processing-payment.reason="<조사 메모. 개인정보·결제 키를 쓰지 않는다>"
```

`reason`은 명령 입력 검증용이며 DB에는 저장하지 않는다. 조사 기록은 Issue나 운영 기록에 따로 남긴다.

## 결과 해석

| 출력 `RESULT=` | 종료 코드 | 의미 | 다음 행동 |
| --- | --- | --- | --- |
| `RECOVERED` | 0 | 성공 확정 완료 | 사후 확인 |
| `ALREADY_FINALIZED` | 0 | 조회하는 사이 정상 확정이 먼저 끝남 | 사후 확인만 |
| `CHANGED_DURING_QUERY` | 3 | 조회 중 결제·주문·예약이 바뀜 | 상태를 다시 조사 |
| `STOPPED_PROVIDER_UNKNOWN` | 3 | 조회 불가·미발견·identity 불일치·진행 중·취소 | 결제사 콘솔에서 직접 확인. 다시 실행하지 말고 조사 |
| `STOPPED_PROVIDER_FAILED` | 3 | 결제사가 ABORTED·EXPIRED로 확정 | 실패 확정은 범위 밖. 별도 승인으로 처리 |
| `STOPPED_PROVIDER_OTHER` | 3 | 예상하지 못한 분류 | 조사 |
| `REFUSED_*` | 3 | 시작 조건 불충족 | 사유에 맞게 조사 |
| `REFUSED_INVALID_INPUT`, `REFUSED_BACKGROUND_TRIGGER_ACTIVE` | 2 | 입력 또는 프로세스 설정 오류 | 명령을 고쳐 다시 실행 |

같은 결제에 명령을 다시 실행해도 안전하다. 이미 `SUCCEEDED`이면 `REFUSED_NOT_PROCESSING`으로 끝나고 효과가 중복되지 않는다.

## 중단 조건

- `STOPPED_*`가 나오면 같은 결제에 반복 실행하지 않는다. 결제사 상태가 바뀌었다는 근거가 생기면 그때 다시 실행한다.
- 명령이 예외로 끝나면 확정 트랜잭션 전체가 롤백된다. 결제 상태를 다시 조회해 `PROCESSING`인지 확인한다. 연결이 끊겨 커밋 여부가 불확실해도 상태를 다시 읽어 판정하고, 필요하면 다시 실행한다.

## 보존할 증거

실행 전과 후에 다음을 조회해 남긴다. 결제 키 원문, 개인정보, 결제사 원시 응답은 남기지 않는다.

- `payments`: status, provider_status, amount, expires_at
- `orders`: status
- `inventories`와 해당 결제의 `inventory_movements`
- `deliveries`, `member_coupons`(해당 주문)
- `admin_audit_logs`의 `PAYMENT_PROCESSING_RECOVERED` 행

## 남은 위험

- 결제사에서 실패로 끝난 고립 결제는 이 명령으로 처리되지 않는다. `PROCESSING`과 재고 예약이 그대로 남는다.
- 결제사 응답 해석은 Phase 1의 조회 분류(`TossTestPaymentAdapter.queryPayment`)와 가짜 결제사 테스트에 기대고 있다. 실제 Toss 테스트 환경 검증은 별도 승인 대상이다.
- 검증 근거: `ProcessingPaymentRecoveryIntegrationTests`(일회용 MySQL, 가짜 결제사), `ProcessingPaymentRecoveryCommandTests`.
