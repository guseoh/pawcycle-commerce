# OBS-002 로컬 알림 대응

## 목적과 경계

로컬 통합 환경(`compose.yaml` + `compose.observability.yaml`)의 알림을 받았을 때 처음 무엇을 확인할지 정리한다. 2단계 장애 관측(OBS-STACK-001)의 알림 규칙과 짝을 이룬다. 운영(Production) 절차가 아니다. 운영에는 아직 자동 알림이 없다([알려진 위험](../architecture/known-risks.md) 참고).

임계값은 잠정값이다. 2026-11-17 로컬 장애 리허설([OBS-STACK-002](../reports/OBS-STACK-002/README.md))에서 근거가 확인된 `PawCycleApiLatencyP95`에만 요청량 하한을 추가했고, 나머지는 리허설에서 누락·오탐이 없어 그대로 둔다.

## 알림이 오는 길

```text
backend 지표 → Prometheus 규칙 ─┐
                                 ├→ Alertmanager → Discord
backend 로그 → Alloy → Loki 규칙 ┘    (critical / warning 채널, Watchdog은 보내지 않음)
```

| 심각도 | 의미 | 채널 | 반복 |
| --- | --- | --- | --- |
| critical | 고객의 돈·주문에 지금 영향 | `PAWCYCLE_LOCAL_DISCORD_WEBHOOK_FILE` | 30분 |
| warning | 지연, 곧 영향 가능 | `PAWCYCLE_LOCAL_DISCORD_WARNING_WEBHOOK_FILE`(없으면 critical 파일과 같은 채널) | 2시간 |
| none | Watchdog(알림 경로 확인용) | 보내지 않음 | — |

- **억제:** `PawCycleBackendScrapeUnavailable`이 firing이면 다른 로컬 알림은 보내지 않는다. backend가 죽으면 나머지 신호는 모두 그 결과이기 때문이다.
- **해소 지연:** 적체·상태 지표 알림 5개(고립 PROCESSING, UNKNOWN 경과, 결제 만료 적체, 정기배송 지연, 작업 정지)는 조건이 풀려도 2분 더 firing을 유지한다(`keep_firing_for`). backend가 재시작하는 1분 남짓 동안 지표가 사라져 원인이 그대로인데 resolved가 가던 문제를 막는다. 그래서 조치 후 resolved는 지표 갱신 1분을 더해 약 3분 뒤에 온다.
- **링크:** 알림 메시지의 "대시보드·로그"는 Grafana 기본 포트 3001 기준이다. 포트를 바꿨다면 주소의 포트만 바꿔 연다.

## 공통 첫 확인

1. 알림의 "대시보드·로그" 링크를 연다. 로그 대시보드(`PawCycle Logs`)는 서비스·레벨·검색어로 거를 수 있다.
2. 로그에서 `requestId`, `jobRunId`, `paymentId` 중 하나를 찾아 검색어에 넣는다. 같은 요청이나 같은 실행의 로그가 모두 모인다.
3. 같은 시각에 다른 알림이 함께 왔는지 Alertmanager(`http://127.0.0.1:9094`)에서 본다.
4. 조치한 뒤 resolved 알림이 오는지 확인한다.

## 알림별 첫 확인

### PawCycleBackendScrapeUnavailable

- `docker compose ... ps backend`로 상태를 보고, `logs backend`로 마지막 ERROR와 종료 원인을 본다.
- DB가 원인이면 `ps mysql`과 Hikari 지표를 함께 본다.
- 자세한 재현과 복구는 [INC-BASE-001](INC-BASE-001-local-incident-response.md)을 따른다.

### PawCycleStuckProcessingPayment

- 결제창이 닫힌 지 10분이 넘었는데 PROCESSING인 일반 결제가 있다(#417).
- 로그에서 `payment.confirm.finished`가 없는 `paymentId`와 `External call failed`를 찾는다.
- 결제사에서 승인(DONE)된 것이 확인되면 [PAYMENT-RECOVERY-001](PAYMENT-RECOVERY-001-processing-success.md)의 성공 확정 명령을 쓴다. 결제사 실패로 끝난 건의 실패 확정은 아직 없다(R1).

### PawCycleUnknownPaymentAging

- 결제사 결과를 모르는 결제(UNKNOWN)가 30분 넘게 남았다.
- 관리자 결제 대사로 결제사 상태를 다시 조회한다. 로그의 `payment.reconciled` 이벤트에서 `providerStatus`를 확인한다.

### PawCycleExternalCallInternalFailure

- 결제사·AI 호출 경계에서 **우리 코드의 예외**가 났다. 결제사 장애가 아니다.
- 로그 레벨 ERROR에서 `External call failed; ... failureClass=INTERNAL`을 찾고 stack trace를 본다(예외 메시지는 지워져 있다).
- 해당 결제·환불은 UNKNOWN으로 남아 있으므로, 수정 뒤 대사로 정리한다.

### PawCycleExternalCallFailures

- 최근 5분 동안 timeout·연결 실패·결제사 5xx가 3건을 넘었다.
- 로그의 `failureClass`로 원인을 나눈다. 결제사 상태 페이지와 네트워크를 확인한다. UNKNOWN 결제가 늘면 대사로 정리한다.

### PawCycleApiServerErrorRate

- API 5xx 비율이 5%를 넘었다(요청이 초당 0.2건을 넘을 때만 판정).
- 오류 응답의 `requestId`로 로그를 찾는다. 같은 시각의 DB 커넥션 대기, 외부 호출 실패 알림이 있는지 본다.

### PawCycleApiLatencyP95

- API p95가 1초를 넘었다.
- 요청이 초당 0.2건을 넘을 때만 판정한다. 요청이 적으면 느린 요청 한두 건이 p95가 되어, 이미 복구된 장애 뒤에 늦게 울린다(리허설에서 DB 복구 3분 뒤 firing).
- 요청량이 늘었는지, Hikari 대기·느린 SQL이 있는지 기존 대시보드(`PawCycle Local Observability`)에서 본다.

### PawCycleDbConnectionWait

- DB 커넥션을 기다리는 요청이 2분 넘게 있다.
- **MySQL이 멈췄을 때 오는 첫 알림이다.** backend 지표는 scrape 때 DB를 읽지 않으므로 MySQL이 멈춰도 `PawCycleBackendScrapeUnavailable`은 울리지 않는다. 이 알림과 `PawCycleScheduledJobFailing`(지표 갱신·결제 만료 작업), `PawCycleBackendErrorLogSpike`가 함께 오면 `ps mysql`부터 본다. 지금은 모두 warning이다(리허설 결과, 아래 "알려진 한계").
- 오래 걸리는 트랜잭션이나 잠금 대기가 있는지 MySQL `SHOW PROCESSLIST`로 본다. 스케줄 작업이 겹쳤는지 `Scheduled job` 로그를 본다.

### PawCycleScheduledJobStalled

- 알림의 `job_name` 작업이 10분 넘게 성공하지 못했다. 로컬에서 1시간 주기인 `idempotency-cleanup`과 `subscription-reconciliation`은 제외한다. 회차 보정 실패는 `PawCycleReconciliationFailure`가 따로 알린다.
- 설정으로 꺼진 작업과, 재시작 뒤 한 번도 성공하지 못한 작업은 마지막 성공 시각이 NaN이라 이 알림이 울리지 않는다. 결제 만료 작업이 꺼지면 `PawCycleCheckoutExpirationBacklog`로 감지한다.
- 로그에서 `jobName`으로 거르고 `Scheduled job failed`를 찾는다. 성공 로그도 실패 로그도 없으면 작업이 시작조차 못 하는 것이다. 같은 스레드의 다른 작업이 오래 걸리는지 본다(스레드 배치는 [상태 전이 문서](../domain/state-machines.md) 6절).

### PawCycleScheduledJobFailing

- 최근 10분 안에 해당 작업 실행이 실패했다. 다음 실행에서 다시 시도된다.
- 같은 알림 그룹에 작업이 추가되거나 풀릴 때마다 메시지가 다시 온다(DB 장애 리허설 한 번에 이 알림만 6건: 발생 3, 해소 중 갱신 2, resolved 1). 원인이 하나인지 `jobName`별 `cause`로 본다.
- `jobRunId`로 그 실행의 로그를 모아 원인을 본다. 반복되면 정지 알림으로 이어진다.

### PawCycleCheckoutExpirationBacklog

- 결제창이 닫힌 지 1분이 넘었는데 만료되지 않은 결제가 있다.
- `checkout-expiration` 작업의 마지막 성공 시각과 실패 로그를 본다. 재고 예약과 쿠폰이 풀리지 않은 상태다.

### PawCycleSubscriptionOverdue

- 날짜가 지났는데 주문이 없는 정기배송 회차가 30분 넘게 남았다. 앞 회차 결제를 기다리는 회차도 포함한다.
- 구독 자동화가 켜져 있는지, `subscription-order-automation` 작업이 도는지 본다. 앞 회차 결제가 처리 중이거나 UNKNOWN이면 그 결제를 먼저 정리한다. [SUB-AUTO-001](SUB-AUTO-001-subscription-automation.md)을 따른다.

### PawCycleSubscriptionAutomationFailure

- 구독 회차 주문 생성이 실패했다.
- 로그에서 `Subscription order automation failed`와 `subscriptionId`, `scheduleId`, `failureCategory`를 본다.

### PawCycleReconciliationFailure

- 구독 회차 보정이 실패했다. [INC-BASE-001](INC-BASE-001-local-incident-response.md)이 이 알림으로 장애 재현을 검증한다. 이 규칙의 조건은 그 검증 타이밍에 맞춰 두었다.

### PawCycleBackendErrorLogSpike

- 최근 5분 동안 backend ERROR 로그가 10건을 넘었다(Loki 규칙).
- 로그 대시보드에서 레벨 ERROR로 거르고 같은 메시지가 반복되는지 본다. 외부 호출·스케줄 작업 알림이 함께 왔는지 확인한다.

### Watchdog

- 항상 firing인 알림이다. Discord로 보내지 않는다.
- Alertmanager 화면에 **보이지 않으면** Prometheus 규칙 평가나 Alertmanager 전달이 멈춘 것이다. `ps prometheus alertmanager`와 각 로그를 본다.

## 알려진 한계

2026-11-17 리허설에서 확인했고 아직 바꾸지 않은 것이다. 바꾸려면 심각도·라우팅 결정이 필요하다.

- MySQL 전체 중단이 warning 알림으로만 온다. 고객 요청은 그동안 응답하지 않는다(요청이 적어 5xx 비율 규칙은 판정하지 않음).
- MySQL 7분 중단 한 번에 중단 중 warning 5건, 복구 뒤 resolved·그룹 갱신·늦은 p95까지 합쳐 12건이 왔다. DB 대기 알림이 다른 warning을 억제하지는 않는다.
- 꺼진 작업은 정지 알림 대상이 아니다(위 `PawCycleScheduledJobStalled`).

## 설정 검증

알림 규칙이나 Alertmanager 설정을 바꾸면 다음을 실행한다. CI의 production contract lane도 같은 스크립트를 실행한다.

```bash
bash infra/local-integration/observability/test-observability-config.sh
```

규칙과 대시보드가 쓰는 PawCycle 지표 이름이 실제 backend 지표에 있는지는 `ObservabilityIntegrationTests`가 확인한다.
