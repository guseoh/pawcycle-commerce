# ARCH-016: 운영 Alertmanager와 critical 알림

## 상태

Accepted — 사용자 승인 2026-11-17 (운영에 Alertmanager만 추가, Loki 보류, critical 중심 규칙을 운영 전용 Discord 채널로 보냄). 저장소 준비 결정이며 실제 운영 적용은 #470 운영 트랙에서 별도 승인한다.

## 날짜

2026-11-17

## 작업 ID

`ARCH-016`

## 관계

[ARCH-012](ARCH-012-production-observability-boundary.md)(Production 관측 경계)를 **보완**한다. ARCH-012는 "Alertmanager, centralized logging/Loki"를 그 결정의 범위에서 제외했다. 이 ADR은 그중 Alertmanager만 운영에 추가하고 Loki는 계속 제외한다. ARCH-012의 나머지 결정(same-host Lean baseline, 별도 compose project, `app` network internal 유지, `observability` bridge, UI의 `127.0.0.1` publish, 30초 scrape, 7일·5GB retention, 자원 상한과 OFF/ON 재평가 원칙)은 그대로이며 ARCH-012 원문은 고치지 않는다.

## 맥락(Context)

운영 Prometheus·Grafana는 ARCH-012에 따라 `app01`에서 지표를 모으지만, 사람이 대시보드를 열지 않으면 장애를 알 수 없다. 로컬에서는 2단계(OBS-STACK-001~003, #543·#544·#546·#551)에서 Prometheus 규칙 16개, Alertmanager(critical·warning 두 Discord 경로, Watchdog blackhole, 억제 규칙), Loki를 구축하고 장애 리허설과 실제 Discord 전달까지 확인했다.

운영에 그대로 옮기기 어려운 조건이 있다.

- `app01`은 1 OCPU / 6 GB이고 Application(backend 640m, frontend 768m, proxy 128m)과 Observability(Prometheus 384m, Grafana 256m, metrics-proxy 32m)가 같은 host에 있다.
- 운영 scrape·평가 간격은 30초(로컬 15초)이고, job은 `pawcycle-production-backend`, target은 `metrics-proxy:9464`다.
- 운영 사용자는 한 명(PO 겸 Tech Lead)이다. 경고성 알림이 많으면 critical이 묻힌다.

## 결정(Decision)

1. **운영에 Alertmanager를 추가한다.** `infra/production-observability` compose project 안의 서비스로 둔다(새 compose project나 Compute를 만들지 않는다).
2. **Loki는 보류한다.** Loki와 수집기(Alloy)는 저장소·ingest·Docker socket 접근이 필요하다. Loki는 log index·chunk를 계속 쓰는 저장 서비스라 알림 몇 건을 다루는 Alertmanager보다 memory와 지속 disk 쓰기가 크고(운영 측정값은 없다), 수집기는 Docker socket(사실상 host root) 권한을 요구한다. 1 OCPU / 6 GB same-host에서 이 비용과 보안 경계를 지금 늘릴 근거가 없다. 운영 로그는 계속 container log(`json-file`, 10m×3)와 `docker logs`로 본다. 로그 기반 알림이 필요하다는 근거가 생기면 별도 결정으로 다시 연다(**Deferred — Evidence Triggered**).
3. **운영 알림은 critical 4개 + Watchdog으로 시작한다.** 모두 `scope=production`, 운영 job으로 고정한다. 로컬 규칙 파일을 복사하지 않고 `infra/production-observability/prometheus/rules/pawcycle-production-alerts.yml`로 분리했다. 각 규칙의 근거 출처는 파일 주석에 있다.

   | 알림 | 심각도 | 운영 조건(30초 scrape) | 로컬과 다른 점과 근거 |
   | --- | --- | --- | --- |
   | `PawCycleBackendScrapeUnavailable` | critical | `up{job="pawcycle-production-backend"} == 0`, `for: 1m` | 로컬 `for: 30s`는 15초 scrape 2회였다. 30초 scrape에서 같은 "연속 2회 실패"를 요구하려면 1분이 필요하다. metrics-proxy가 유일한 scrape 경로라서 backend 중단과 metrics-proxy 중단을 모두 잡는다 |
   | `PawCycleDbConnectionAcquireFailure` | critical | `sum(increase(hikaricp_connections_timeout_total{job=...}[5m])) >= 2`, `for: 1m` | 로컬은 `increase[2m] > 0 and max(pending) > 0`였다. 운영에서 트래픽이 없으면 DB를 쓰는 것은 스케줄 작업(지표 갱신: timeout 30초 + 다음 실행 60초)뿐이라 pending은 90초 중 30초만 0보다 크고, 30초 표본으로는 `for`가 계속 끊긴다. 그래서 pending 조건을 빼고 "5분 안 timeout 2건 이상"으로 바꿨다. 범위 선택자가 왼쪽 열린 구간이라 3분 창은 표본 150초만 덮어 90초 주기 timeout을 1건으로 볼 때가 있다(promtool 테스트로 확인). 5분 창(표본 270초)은 주기가 135초까지 늘어도 2건을 본다. timeout 1건은 외삽해도 약 1.1이라 울리지 않는다 |
   | `PawCycleStuckProcessingPayment` | critical | `pawcycle_payment_stuck{kind="normal_processing"} > 0`, `for: 1m`, `keep_firing_for: 3m` | 지표 자체에 #417의 10분 유예가 들어 있다. `keep_firing_for`를 로컬 2분에서 3분으로 늘렸다. `app01` backend 재시작(healthcheck start_period 40초) + scrape 30초 + 첫 갱신 동안 series가 비어 거짓 RESOLVED가 가는 것을 막는다 |
   | `PawCycleUnknownPaymentAging` | critical | `pawcycle_payment_stuck_oldest_age_seconds{kind="unknown"} > 1800`, `for: 5m`, `keep_firing_for: 3m` | 나이는 60초 갱신 시점에 계산되므로 30초 scrape가 의미를 바꾸지 않는다. `for`는 로컬과 같다 |
   | `Watchdog` | none | `vector(1)` | Discord로 보내지 않는다(blackhole). 운영자가 `amtool`로 경로를 확인할 때 쓴다 |

   로컬의 warning 규칙(구독 보정·자동화 실패, 스케줄 작업 정지·실패, 결제 만료 적체, 정기배송 지연, 외부 호출 실패, API p95, DB 대기)과 critical 중 API 5xx·외부 호출 내부 결함은 운영에 넣지 않았다. 앞의 것들은 warning이라 범위 밖이고, 5xx 규칙은 초당 0.2건 바닥값 아래의 운영 트래픽에서는 판정되지 않으며, 외부 호출 내부 결함은 운영 결제 경로의 기준 데이터가 아직 없다. 운영 측정 뒤 별도로 추가를 판단한다. 임계값은 모두 운영 calibration 전까지 잠정값이다.

4. **심각도와 라우팅.** 운영 전용 Discord 채널 하나로 보낸다(로컬 채널과 분리). `group_by: alertname`, `group_wait 10s`, `group_interval 1m`, `repeat_interval 30m`. root receiver도 운영 채널이라, 라우트에 맞지 않는 알림은 버려지지 않고 채널로 간다. `PawCycleBackendScrapeUnavailable`이 firing이면 나머지 운영 알림을 억제한다(지표가 없어 거짓 RESOLVED가 가기 때문, 로컬 OBS-STACK-002와 같은 이유).
5. **Discord webhook은 runtime secret file로만 주입한다.** `webhook_url_file: /run/pawcycle-secrets/discord-webhook`만 쓴다. host 경로 `/opt/pawcycle/runtime/observability/discord-webhook`(regular file, `65534:65534`, `0400`)를 compose가 read-only로 mount하고, 경로는 `PAWCYCLE_ALERTMANAGER_DISCORD_WEBHOOK_FILE`로 받는다(없으면 compose가 실패). URL은 저장소·환경 변수·명령 인자·로그에 넣지 않는다. 채널 생성과 file 작성은 사용자가 한다.
6. **Watchdog 처리.** 항상 firing인 Watchdog을 blackhole receiver로 보낸다. 운영자는 적용 후와 점검 때 `amtool alert query alertname=Watchdog`으로 "규칙 평가 → Alertmanager" 경로를 확인한다. 외부 dead man's switch(Watchdog이 끊기면 외부 서비스가 알리는 방식)는 새 외부 서비스와 비용·계정 결정이 필요해 이번에 하지 않는다.
7. **network와 publish.** Alertmanager는 `observability` bridge에만 붙고 Application `app` network에는 붙지 않는다. host port를 publish하지 않는다. 운영자는 `docker exec`로 container 안의 `amtool`을 쓴다. 단일 instance라 cluster gossip listener를 끈다(`--cluster.listen-address=`). ARCH-012의 network·publish 불변식은 그대로다.
8. **저장과 상태.** silence와 notification log는 named volume `pawcycle-production-observability-alertmanager-data`(`--data.retention=120h`)에 둔다. 재시작해도 silence가 남고 이미 보낸 알림을 다시 보내지 않는다. healthcheck는 `/-/ready`다. root filesystem은 read-only다.
9. **이미지.** `prom/alertmanager:v0.28.1`을 digest(`sha256:27c475db…`)로 고정한다. 로컬 리허설·실제 Discord 전달 확인(#551)과 같은 버전이다.

### 자원 상한

| 서비스 | memory | CPU | 출처 |
| --- | --- | --- | --- |
| backend / frontend / proxy | 640m / 768m / 128m | 0.75 / 0.35 / 0.20 | `infra/production/compose.yaml` |
| Prometheus / Grafana / metrics-proxy | 384m / 256m / 32m | 0.25 / 0.15 / 0.05 | ARCH-012 |
| **Alertmanager (신규)** | **64m** | **0.05** | 이 ADR |
| 합계 | 2272m (기존 2208m + 64m) | 1.80 | |

Alertmanager는 알림 몇 건과 receiver 하나를 다루는 상시 대기 서비스다. 상한은 일회용 재현(검증 절, 2026-11-18 로컬 WSL Docker)에서 critical firing·resolved 전달 직후 잰 `docker stats` 표본 19.3 MiB의 3배 이상 여유를 두고 64m로, CPU는 같은 성격의 metrics-proxy와 같은 0.05로 정했다. 합계 2272m은 6 GB(6144 MiB)의 약 37%이고 나머지가 OS·Docker·page cache와 병렬로 준비 중인 운영 세션 Redis(AUTH-005 트랙, 별도 결정)의 몫이다. CPU 상한은 예약이 아니라 상한이며 합계 1.80은 1 OCPU(x86에서 vCPU 2개, 실제 값은 calibration의 `nproc`로 확인) 안이다. 이 값은 성능 tuning 결과가 아니다. ARCH-012의 원칙대로 **실제 적용 후 Observability 전체 OFF와 Alertmanager 포함 ON을 같은 조건에서 비교**해 재평가한다. Alertmanager 없는 기존 상태를 OFF 기준으로 섞지 않는다.

## 실패 모드

| 실패 | 결과 | 대응 |
| --- | --- | --- |
| Alertmanager 중단·OOM | 운영 알림이 **유실**된다. Prometheus는 평가를 계속하지만 보낼 곳이 없다 | `restart: unless-stopped`. 외부 dead man's switch가 없으므로 사람이 모른 채 지나갈 수 있다. 점검 때 Watchdog 확인 |
| Discord 장애·rate limit·webhook 삭제 | Alertmanager가 재시도하다 실패하고 그 알림은 **유실**된다(`alertmanager_notifications_failed_total` 증가) | Runbook 전달 확인 절차의 지표와 로그(URL 가림)로 확인 |
| Prometheus 중단 | 규칙 평가가 멈춰 알림이 생기지 않는다 | ARCH-012의 Observability 복구 범위 |
| `app01` 자체 중단 | 관측과 알림이 Application과 함께 멈춘다(same-host failure domain) | ARCH-012의 Deferred — Evidence Triggered(별도 Compute) 범위. 이번에 해결하지 않는다 |
| webhook file 권한·경로 오류 | compose 적용 실패(경로 없음) 또는 전송 시 실패(읽기 불가) | 적용 전 조건에서 `65534:65534 400` 확인 |

알림이 오지 않는다는 사실만으로 "정상"이라고 판단할 수 없다는 점이 이 결정의 남은 위험이다.

## 검토한 대안(Alternatives Considered)

### 대안 A: Alertmanager + Loki 함께 추가

- 장점: 로컬과 같은 로그 기반 알림·검색.
- 단점: memory·disk·Docker socket 권한 증가. same-host 1 OCPU에서 근거 없이 늘리는 비용. 사용자 결정으로 보류.

### 대안 B: Grafana alerting으로 Discord 전송

- 장점: 새 컨테이너가 없다.
- 단점: 로컬에서 리허설한 Prometheus 규칙·promtool 단위 테스트·Alertmanager 라우팅·억제를 다시 만들어야 하고, Grafana가 알림 경로까지 맡아 장애 범위가 커진다.

### 대안 C: 로컬 규칙 16개를 그대로 운영에 복사

- 장점: 리허설한 규칙을 그대로 쓴다.
- 단점: 15초 scrape 기준의 `for`·창이 30초 scrape에서 의미가 달라진다(DB 규칙이 대표적). warning이 운영 채널을 덮는다.

## 결과와 영향(Consequences)

- 운영 critical 장애(backend 수집 중단, DB 연결 실패, 결제 고립·장기 UNKNOWN)를 운영 Discord 채널로 받는다.
- 계획된 배포·재시작이 1분을 넘으면 `PawCycleBackendScrapeUnavailable`이 온다. Runbook의 silence 절차로 막는다.
- Observability compose 적용에 webhook file이 필수가 된다. Alertmanager 없이 Prometheus·Grafana만 다시 적용하려면 Alertmanager가 없던 SHA로 되돌린다(Runbook rollback).
- 운영 규칙 지표 이름은 backend 통합 테스트(`ObservabilityIntegrationTests`)가 `/actuator/prometheus` 출력과 대조한다.

## 검증(Validation)

- `infra/production-observability/test-alerting-config.sh`: promtool 규칙 문법·단위 테스트(30초 간격, 운영 job), 규칙 집합 제약(critical 4 + Watchdog, 모두 `scope=production`·운영 job), amtool 설정 검사·라우팅 6건.
- `infra/production-observability/validate-observability.sh`: compose model 계약, internal app network를 흉내 낸 일회용 stack에서 Prometheus → Alertmanager 발견, Watchdog 도착, 가짜 Discord 수신기로 critical firing과 resolved 전달, Watchdog 미전송, `failed_total` 0, 자원 사용 표본 출력. 실제 Discord는 쓰지 않는다.
- 운영 실제 Discord 전달은 [OPS-OBS-001](../runbook/OPS-OBS-001-production-observability.md#운영-discord-전달-확인)의 `amtool` 시험 알림으로 실제 적용 때 확인한다. 그 전까지 Production Verified가 아니다.

## 사용자 승인(User Approval)

2026-11-17 사용자(PO 겸 Tech Lead)가 운영에 Alertmanager만 추가, Loki 보류, critical 중심 규칙을 운영 전용 Discord 채널로 보내기, ARCH-012를 보완하는 새 ADR 작성을 결정했다. 실제 운영 적용, 운영 Discord 채널 생성과 webhook file 작성은 사용자의 별도 작업·승인이다.
