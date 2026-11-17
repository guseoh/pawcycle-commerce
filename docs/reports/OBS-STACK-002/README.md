# OBS-STACK-002 Platform/SRE 작업 보고서

## 작업

- 작업 ID: OBS-STACK-002 (2단계 장애 관측 마무리: 로컬 장애 리허설)
- 작업 등급: 일반
- 실행 구분: 저장소 변경
- 실행 범위: 로컬 격리 환경 리허설 포함. 운영·OCI·운영 DB는 건드리지 않았다.
- 역할: Platform/SRE
- 기준: `main` `7f380077`(#543 병합) 위에서 실행했다. 실행일 2026-11-17 UTC, Docker Engine 29.9.2.

## 목적

#543에서 만든 로컬 관측 스택(Prometheus·Loki·Alertmanager·Grafana)으로 "장애 재현 → 감지 → 알림 전달 → 원인 확인 → 복구 → resolved"가 실제로 이어지는지 확인하고, 근거가 있는 임계값만 고친다.

## 리허설 환경

- **격리:** 공유 local stack이 아니라 일회용 Compose project(`pawcycle-obs-reh`)를 썼다. `compose.yaml` + `compose.observability.yaml` + `compose.incident.yaml`(이번에 Loki·Alloy 볼륨까지 격리하도록 보강)과 리허설 전용 override를 합쳤다. 볼륨 다섯 개 모두 project 범위였고, 종료 후 컨테이너·볼륨·네트워크 0개, 공유 `pawcycle-local-integration-*` 볼륨의 생성 시각 불변을 확인했다.
- **자격 증명:** 일회용 env 파일에 무작위 DB 비밀번호와 QA 계정을 만들어 썼다. 사용자 `.env.local`은 읽지 않았다.
- **알림 수신:** Discord webhook 파일이 로컬에 없었다. Alertmanager의 두 webhook 파일을 같은 Docker 네트워크의 가짜 수신기(`python:3.13-alpine`, 요청 본문만 기록하고 204 응답)로 향하게 했다. 따라서 Alertmanager → Discord 형식 요청 → 채널별 수신까지는 확인했지만 **실제 Discord 서버 수신은 확인하지 않았다.**
- **외부 호출 차단:** backend 컨테이너에서 `api.tosspayments.com`과 `api.openai.com`을 `127.0.0.1`로 고정했다(접속 시 1ms 만에 `Couldn't connect`). 결제사 역할은 local-integration의 가짜 결제사(`TossSandboxPaymentAdapter`)가 맡았고, 외부 호출 실패 시나리오에서만 Toss 테스트 adapter를 가짜 키(`test_gsk_` 접두사만 맞춘 문자열)로 켜서 차단된 주소로 보냈다. 실제 Toss·AI 호출, 결제·환불·정기결제는 없었다.

## 정상 기준선

| 항목 | 값 |
| --- | --- |
| scrape | `up=1`, Prometheus 규칙 15개 + Loki 규칙 1개 모두 `health=ok`, Watchdog만 활성(`blackhole`) |
| 적체 지표 | 고립 PROCESSING·UNKNOWN·정기결제 PROCESSING 0, 결제 만료 적체 0, 정기배송 지연 0 |
| 스케줄 작업 | 실행 중 작업의 마지막 성공 20초 이내. 꺼진 작업(`subscription-billing` 등)은 NaN이며 0과 구분된다 |
| 읽기 부하 6분(초당 2.14건, 654건 모두 200) | p50 32ms, **p95 151ms**, 5xx 0, Hikari 대기 최대 0, 작업 실패 0, 작업 최대 소요 1.0s(`commerce-metrics-refresh`) |
| 재기동 직후 1분 | p95 1.17s(워밍업). p95 규칙의 `for: 10m`이 흡수해 firing하지 않았다 |
| Loki | 해당 project 컨테이너 10개의 로그만 수집, backend `level` 라벨 ERROR/INFO/WARN |

## 시나리오 결과

시각은 UTC. "감지"는 Prometheus/Loki 규칙 firing, "전달"은 가짜 수신기 도착, "복구"는 업무 상태 정상화다.

| # | 시나리오 | 재현 방법 | 감지 | 전달 | 복구·resolved | 판정 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Backend 중단 | `stop backend` 02:55:56 | `ScrapeUnavailable` pending 02:56:23 → firing 02:56:53. Proxy 502 | critical 02:56:54 | `up` 02:58:17 → healthy·Proxy 200 02:58:50, resolved 02:58:54 | **PASS** |
| 2 | MySQL 중단 | `stop mysql` 03:00:15(Backend 프로세스 유지) | `DbConnectionWait` firing 03:02:23, `ScheduledJobFailing` 4개 작업, Loki `ErrorLogSpike` 03:02:56. scrape는 `up=1` 유지, health·Proxy 10초 timeout | warning 5건(03:01:25~03:02:59) | `up mysql` 03:07:27 → Proxy 200 03:07:55, `DbConnectionWait` resolved 03:08:27, 나머지는 각 창이 지난 뒤 resolved | **PASS**(감지·복구). 심각도는 아래 결함 D5 |
| 3 | 고립 PROCESSING 결제 | 실제 checkout으로 만든 결제를 일회용 DB에서 `PROCESSING`, `expires_at`=11분 전으로 변경 03:11:53 | firing 03:13:08 | critical 03:13:13 | PAYMENT-RECOVERY-001 성공 확정 명령(가짜 결제사 DONE) 03:18:02 → `RESULT=RECOVERED`, 결제 SUCCEEDED·주문 PAID·예약 DEDUCT·감사 로그 1건, resolved 03:19:15 | **PASS** |
| 4 | 정기배송 지연 | ACTIVE 구독 + 어제 날짜 SCHEDULED 회차 fixture | 지표 1 → firing 03:41:08(`for: 30m`, Backend 재시작으로 03:11:08부터 다시 셈) | warning 03:41:19 | 구독 자동화를 켜서 재시작 → 회차가 `HELD / MISSING_BILLING_METHOD`로 처리, 지연 지표 0(03:57) | **PASS**(감지·전달). resolved는 결함 D4로 재시작 중 먼저 전송됨 |
| 5 | Checkout 만료 지연 | 결제 만료 작업을 끈 채 재시작, 만료 2분 지난 READY 결제 | firing 03:17:08 | warning 03:17:14 | 작업을 다시 켜서 재시작 → 결제 FAILED/EXPIRED, 주문 EXPIRED, 예약 6개 RELEASE. 재고 RESERVE 56 = DEDUCT 50 + RELEASE 6, reserved 0 | **PASS**(감지·전달·불변식). resolved는 D4 |
| 6 | 외부 호출 실패 | 차단된 Toss로 결제 승인 5건 03:12:02 | 지표 `failure_class=connect` 5, firing 03:12:23. 로그 WARN `failureClass=CONNECT, subject=paymentId=N`과 `requestId` | warning 03:12:28 | resolved 03:17:14(5분 창). 남은 UNKNOWN 5건은 `UnknownPaymentAging` critical 03:47:34로 이어짐 → 가짜 결제사로 관리자 대사 03:56:12, 5건 SUCCEEDED | **PASS** |
| 6b | 외부 호출 INTERNAL 결함 | 코드 결함 없이 재현할 방법이 없음 | promtool 규칙 테스트만 | — | — | **SKIP**(규칙 검증만) |

Grafana: 데이터 소스 `pawcycle-prometheus`·`pawcycle-loki`, 대시보드 `pawcycle-local-observability`·`pawcycle-logs` 프로비저닝 확인. 알림 메시지의 대시보드·Runbook 링크는 기본 포트 3001 기준이라, 포트를 바꾼 이번 격리 스택에서는 링크 포트를 바꿔 열어야 했다(OBS-002에 이미 적힌 제약).

### 상관관계

- 외부 호출 실패: Loki에서 `|= "External call failed"`로 `paymentId`와 `requestId`를 바로 얻고, 같은 `requestId`로 요청 전체 로그를 모을 수 있었다.
- MySQL 중단: ERROR 로그 상위가 `Scheduled job failed; job=..., cause=CannotCreateTransactionException`과 `Unexpected exception while querying public product list`로 나뉘어 원인(DB 연결)이 바로 드러났다.
- 정기배송: `event=subscription.order.processed subscriptionId=1 scheduleId=1 outcome=NO_OP`와 `jobRunId`로 자동화 처리 결과를 확인했다.

## 발견한 결함과 수정

| ID | 결함 | 근거 | 조치 |
| --- | --- | --- | --- |
| D1 | **로컬 회차 보정이 꺼져 있었다.** 백엔드 설정이 `pawcycle.subscription.reconciliation.*`로 바뀐 뒤에도 `compose.yaml`이 옛 `PAWCYCLE_MVP2_RECONCILIATION_*`를 썼다 | 기준선에서 `subscription-reconciliation` 마지막 성공이 NaN. INC-BASE-001 스크립트가 "failure signals were not observed"로 실패 | 환경변수 이름 교정(값 그대로). 고친 뒤 INC-BASE-001 전 항목 PASS |
| D2 | **INC-BASE-001 스크립트가 #543 이후 격리 검사에서 멈춘다.** Loki·Alloy 고정 이름 볼륨이 disposable override에 없고, webhook 파일 변수가 필수가 됐다 | merged model 검사 로직, `config` 단계 오류 재현 | `compose.incident.yaml`에 Loki·Alloy disposable 볼륨, 스크립트의 기대 볼륨 5개·공유 볼륨 목록 확장, webhook 파일이 없으면 일회용 placeholder, Loki 포트 0(임의 포트) |
| D3 | INC-BASE-001 fixture가 현재 스키마와 맞지 않는다(`mvp2_managed` → `runtime_managed`) | `ERROR 1054 Unknown column` | 컬럼명 교정 |
| D4 | **원인이 그대로인데 resolved가 간다.** Backend 재시작으로 지표가 1분 남짓 사라지면 적체·상태 알림이 resolved로 끝나고, scrape 중단 알림은 30초 뒤에 firing해 억제가 늦다 | 03:55:35 정기배송 지연·UNKNOWN 경과·만료 적체 resolved 3건 전송, 재기동 후 지표는 여전히 1이라 다시 pending | 상태 지표 알림 5개에 `keep_firing_for: 2m`. 재검증: 고립 PROCESSING firing 중 재시작 → firing 유지, scrape 중단 동안 suppressed, 거짓 resolved 0건. 실제 복구(04:03:11) 후 resolved 04:06:25 |
| D5 | p95 알림이 요청이 적을 때 늦은 오탐을 낸다 | DB 복구(03:07:55) 3분 뒤 03:10:58 firing, 값 24.6s. 당시 요청은 상태 확인 probe뿐(초당 약 0.03건) | 5xx 규칙과 같은 하한(초당 0.2건 초과)을 추가. 정상 부하 p95 151ms는 영향 없음 |
| D6 | 1시간 주기로 다시 켜진 회차 보정이 10분 정지 규칙에 걸린다(D1 수정의 부작용) | 04:10:53 `ScheduledJobStalled{job_name="subscription-reconciliation"}` pending | `idempotency-cleanup`처럼 제외. 실패는 `PawCycleReconciliationFailure`가 따로 알린다. 재로드 후 pending 해소, 전송 0건 |
| — | Alloy가 project 이름 `pawcycle-local-integration`으로 고정 필터 | 격리 project 로그가 수집되지 않음 | `COMPOSE_PROJECT_NAME`을 Alloy 환경변수로 넘기고 기본값은 기존 이름. 격리 project 10개 서비스 수집 확인 |

임계값 수치(10분·30분·1초·5%·3건 등)는 바꾸지 않았다. 리허설에서 이 수치로 인한 누락·오탐은 없었다.

## 고치지 않은 것(결정 필요)

- **MySQL 전체 중단이 warning으로만 온다.** #538 이후 scrape가 유지되어 critical인 `ScrapeUnavailable`이 울리지 않는다. 그동안 고객 요청은 10초 넘게 응답하지 않았다. 새 critical 규칙이나 `DbConnectionWait` 심각도 상향은 심각도·라우팅 결정이라 이번에 바꾸지 않았다.
- **경보 폭주.** MySQL 7분 중단 한 번에 warning 12건(중단 중 5건, 복구 뒤 resolved·그룹 갱신·늦은 p95 포함)이 왔다. `ScheduledJobFailing`은 그룹의 작업이 늘거나 줄 때마다 다시 보낸다. DB 대기 알림으로 다른 warning을 억제할지는 별도 결정이다. D5 수정으로 늦은 p95 2건은 사라진다.
- **꺼진 작업은 정지 알림 대상이 아니다.** 마지막 성공이 NaN이면 규칙이 계산되지 않는다. 결제 만료는 적체 알림으로 잡혔다(시나리오 5).
- **resolved 지연.** D4 수정으로 실제 resolved가 약 3분 늦게 온다(지표 갱신 1분 + 2분).

## 검증

- `infra/local-integration/observability/test-observability-config.sh`: 통과(Prometheus 15개 규칙 문법, promtool 단위 테스트, Alertmanager 설정·라우팅 3종, Loki 설정, Alloy 문법).
- promtool 테스트 추가: p95 저트래픽 미판정·실트래픽 firing, 재시작 중 firing 유지·실제 복구 후 해소, 회차 보정 정지 제외. 각 보정을 뺀 사본으로 돌리면 해당 테스트가 실패하는 것을 확인했다.
- `ObservabilityIntegrationTests`: 4개 통과(Java 25, 일회용 MySQL 8.4.10, test JVM UTC). 지표 이름은 바꾸지 않았다.
- INC-BASE-001 `verify-inc-base-001.ps1`: 수정 후 `FAILURE_EVIDENCE`, `RECOVERY_EVIDENCE`, 두 알림 firing/resolved, `GRAFANA_EVIDENCE`, `FIXTURE_DATA_UNCHANGED=PASS`, `DISPOSABLE_CLEANUP=PASS`. 출력 마지막 줄 이후 PowerShell 프로세스 종료 코드는 1이었는데, 스크립트의 마지막 native 명령 종료 코드가 남은 것으로 보인다(공유 Loki·Alloy 볼륨이 없어 `docker volume inspect`가 1을 반환하는 경로). 스크립트 자체의 실패 경로(throw)는 타지 않았다.
- Backend 코드는 바꾸지 않았다.

## 미실행 항목

- 실제 Discord 채널 수신(webhook 파일 없음). 가짜 수신기 전달까지만 확인했다.
- `PawCycleExternalCallInternalFailure`, `PawCycleApiServerErrorRate`, `PawCycleSubscriptionAutomationFailure`의 실행 리허설(규칙 테스트만).
- 결제사 실패로 끝난 고립 PROCESSING의 실패 확정·예약 해제(#417, 별도 승인 대상).
- 운영·OCI 환경.

## 복구·rollback

저장소 변경은 PR revert로 되돌린다. 되돌리면 로컬 회차 보정이 다시 꺼지고 INC-BASE-001 스크립트가 다시 멈춘다. 리허설 자원은 모두 지웠다.

## 남은 위험

- 가짜 결제사와 차단된 호스트로만 확인했다. 실제 Toss 응답(5xx, timeout) 분류는 Phase 1 테스트에 기대고 있다.
- 실제 Discord가 이 메시지 형식(제목·본문 길이)을 받아들이는지는 아직 모른다.
- `keep_firing_for: 2m`은 2분 안에 끝나는 backend 재시작만 덮는다. 2분 넘게 내려가 있으면 상태 알림의 `for` 시계가 처음부터 다시 돈다(예: 정기배송 지연은 복귀 후 다시 30분). 그 사이 상태 알림의 resolved가 scrape 중단 억제로 막히는지도 확인하지 않았다.
