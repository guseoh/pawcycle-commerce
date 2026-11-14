# PAYMENT-TOSS-TEST-001 Toss 테스트 환경 결제 검증 (로컬 통합)

로컬 통합 환경에서 **Toss 테스트 키**로 일반 결제의 승인·조회 흐름을 실제 Toss 테스트 API와 연결해 확인하는 절차다. 결제는 테스트 결제이며 실제 돈이 오가지 않는다.

> **실행 승인:** 실제 Toss 테스트 키를 넣어 실행하는 것은 별도의 명시적 사용자 승인 대상이다. 키는 사용자가 자신의 Toss 개발자 계정에서 발급해 **로컬 환경 변수로만** 넣는다. 저장소, 대화, PR, 로그, 보고서에 키 값을 남기지 않는다. `live_` 키는 절대 쓰지 않는다.

## 범위

| 검증한다 | 이 Runbook으로는 검증하지 않는다 |
| --- | --- |
| 결제위젯 → 승인(confirm) → 결과 화면 → 주문·재고·배송·쿠폰 반영 | 취소·환불(Toss 테스트 환불 어댑터 없음) |
| 결제창 취소 뒤 결제 대기 만료 | 정기결제·빌링(Toss 테스트 빌링 어댑터 없음) |
| 새로고침·재방문 시 같은 결과 재사용(효과 중복 없음) | 운영 Live 결제, 운영 DB |
| 금액이 다른 confirm 요청 거부 | 결제사 timeout·5xx(가짜 결제사 테스트로 검증됨) |
| 고립된 `PROCESSING` 결제 복구 명령 리허설(재현되면) | |

## 시작 조건

1. `infra/local-integration/.env.local`이 준비돼 있고 기존 로컬 통합 환경이 뜬다(FOUNDATION-004).
2. Toss 개발자 센터에서 발급한 **테스트** 키 두 개를 현재 셸 환경 변수로 넣는다.
   - `PAWCYCLE_TOSS_TEST_CLIENT_KEY` = `test_ck_...` (브라우저 결제위젯용, 공개 키)
   - `PAWCYCLE_TOSS_TEST_SECRET_KEY` = `test_sk_...` (backend 승인·조회용, 비밀 키)
3. 키 접두사를 확인한다. 값은 출력되지 않는다.

```bash
pwsh infra/local-integration/toss-test/verify-toss-test.ps1 -Preflight
```

`PREFLIGHT OK`가 아니면 진행하지 않는다. backend 자체도 `test_sk_`로 시작하지 않는 키로는 시작을 거부한다(`TossTestPaymentAdapter`).

## 환경 시작

Toss 테스트 overlay를 함께 지정해 이미지를 다시 빌드한다. client key는 frontend 빌드 때 들어간다.

```bash
cd infra/local-integration && docker compose --env-file .env.local -f compose.yaml -f compose.toss-test.yaml up --build -d
```

확인: QA 계정으로 로그인한 뒤 상품을 장바구니에 담고 Checkout까지 가면 Toss 결제위젯이 보여야 한다. 위젯 대신 "결제 준비 전" 안내가 나오면 overlay나 키 설정을 다시 확인한다.

상태 확인 스크립트는 MySQL 접속 정보를 쓰므로 `.env.local`의 `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`를 셸에 불러 둔다. 스크립트는 DB를 읽기만 한다.

## 시나리오

각 시나리오마다 결제 결과 화면이나 주문 목록에서 주문번호(`O-...`)를 확인하고, 아래 명령으로 DB 상태를 판정한다.

```bash
pwsh infra/local-integration/toss-test/verify-toss-test.ps1 -OrderNumber <주문번호> -Expect <기대 상태>
```

| # | 시나리오 | 절차 | 기대 상태(`-Expect`) | 추가 확인 |
| --- | --- | --- | --- | --- |
| S1 | 정상 승인 | 결제위젯에서 테스트 결제를 완료한다 | `Paid` | 결과 화면 SUCCEEDED, 쿠폰을 썼다면 `Coupon=USED`, 장바구니에서 결제한 수량이 빠짐 |
| S2 | 결제창 취소 | 결제창을 연 뒤 사용자가 닫거나 취소한다 | 직후 `Pending` | `/checkout/fail` 화면. 결제 대기 만료 시간(Checkout 후 30분)이 지나 만료 스케줄러가 돈 뒤 `Expired` |
| S3 | 새로고침 재사용 | S1의 결과 화면(`/checkout/success?...`)을 새로고침하거나 같은 주소로 다시 연다 | `Paid` 그대로 | `Movements=DEDUCT,RESERVE`, `Deliveries=1` 유지(효과 중복 없음) |
| S4 | 금액 불일치 거부 | 새 주문을 결제위젯에서 승인 직전까지 진행한 뒤, 결과 주소의 `paymentKey`·`orderId`로 다른 금액을 넣어 confirm API를 직접 호출한다(로그인 세션·CSRF 필요) | `Pending` | 응답 `409 PAYMENT_CONFIRM_CONFLICT`. Toss 승인 요청이 나가기 전에 거부된다. 이후 정상 금액으로 결제를 마치면 `Paid` |
| S5 | 고립 `PROCESSING` 리허설(선택) | 결제 승인 버튼을 누른 직후 `docker compose kill backend`로 backend를 멈추고 다시 띄운다 | 결과에 따라 다름 | 아래 "S5 판정" 참고 |

화면에서 결제위젯은 서버의 Checkout 금액을 기준으로 승인 요청을 보낸다. 결과 주소의 `amount`를 바꾸는 것만으로는 화면 단계에서 막히므로, S4는 API를 직접 호출해 backend의 금액 대조를 확인한다.

### S5 판정

backend를 멈추는 시점에 따라 결과가 갈린다. 매번 재현되지는 않는다.

- `Pending`(`READY`): confirm 요청 전에 멈췄다. Toss 쪽 승인도 없다. 만료를 기다리면 `Expired`.
- `Paid`: 멈추기 전에 확정까지 끝났다. 정상 동작이다.
- `Processing`: **고립 결제가 재현됐다.** 결제 대기 만료 시각(`expires_at`)에서 10분이 지난 뒤, [PAYMENT-RECOVERY-001](PAYMENT-RECOVERY-001-processing-success.md)의 명령을 로컬 통합 DB에 대해 실행한다. 실행할 때는 이 Runbook과 같은 Toss 테스트 키 환경 변수, `local-integration` profile, `PAWCYCLE_TOSS_TEST_ENABLED=true`를 쓴다. 결과가 `RECOVERED`이면 `-Expect Paid`로 다시 확인한다. Toss 쪽에서 승인이 끝나지 않았다면 `STOPPED_PROVIDER_*`가 나오고 상태는 바뀌지 않아야 한다.

## 중단 조건

- `PREFLIGHT FAIL`이거나 `live_` 키가 보이면 즉시 중단한다.
- 기대 상태와 다르게 나오면(`STATE FAIL`) 같은 시나리오를 반복하지 말고, 출력된 상태를 기록해 원인을 조사한다.
- 결제위젯이나 Toss 응답에 개인정보 입력을 요구하는 단계가 나오면 테스트용 값만 넣는다.

## 정리

```bash
cd infra/local-integration && docker compose --env-file .env.local -f compose.yaml -f compose.toss-test.yaml down
```

셸에서 Toss 키 환경 변수를 지운다. overlay 없이 다시 빌드하면 frontend 결제위젯은 꺼진 상태로 돌아간다.

## 보존할 증거

시나리오별로 다음만 남긴다. 키, `paymentKey` 원문, 결제사 원시 응답, 개인정보는 남기지 않는다.

- 실행 일시와 커밋 SHA
- 시나리오 번호, 주문번호, `verify-toss-test.ps1` 출력(`STATE OK`/`STATE FAIL`과 상태 줄)
- S4의 HTTP 상태와 오류 코드
- S5를 시도했다면 결과 분류와 복구 명령의 `RESULT=` 줄

## 남은 위험

- 취소·환불과 정기결제는 Toss 테스트 어댑터가 없어 이 절차로 검증되지 않는다. 각각 어댑터 구현 결정이 필요하다.
- S2의 만료 확인은 결제 대기 만료 시간과 만료 스케줄러 주기만큼 기다려야 한다.
- 테스트 환경 통과는 운영 Live 결제 검증이 아니다.
