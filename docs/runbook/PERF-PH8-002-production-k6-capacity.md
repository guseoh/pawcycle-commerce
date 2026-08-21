# PERF-PH8-002 deployed Production k6 capacity

이 Runbook은 외부 desktop load generator에서 Production public HTTPS의 `GET /api/products`만 단계적으로 측정하기 위한 저장소 준비 절차다. 실제 Production load 실행은 포함하지 않으며, 별도의 고위험 사용자 승인이 있어야만 실행한다. 기존 loopback-only local harness와 `run-capacity.sh`는 이 절차의 대상이 아니다.

## 실행 전 승인과 READY/NORMAL 확인

실행 직전에 사용자/Tech Lead가 다음 범위를 명시적으로 승인한다: 외부 desktop에서 public HTTPS endpoint에 read-only load를 발생시키며 250 RPS를 넘지 않고, Scheduler·Production runtime·RDS·Secret·DNS·TLS 설정을 변경하지 않는다. 승인이 없거나 target HTTPS origin, 정확히 일치하는 target host 확인, `YES` acknowledgement 중 하나라도 없으면 runner와 k6 scenario 모두 시작 전에 실패한다.

Production 상태 진단은 기존 `docs/runbook/OPS-OBS-001-production-observability.md`의 승인된 두 단계 흐름을 그대로 따른다. Production EC2의 `diagnose-backend-state.sh --scope production` snapshot은 exit code 0과 `production_assessment=READY`여야 하며, 그 snapshot을 Observability EC2의 `--scope observability --production-result ...`로 결합한 최종 진단은 exit code 0과 `status=NORMAL`이어야 한다. Production snapshot이 `READY`가 아니거나 최종 상태가 `NORMAL`이 아니거나 release가 진행 중이거나 상태가 불확실하면 실행하지 않는다. 이 확인에는 credential, cookie, session, response body, product ID, raw DB data를 local artifact나 repository에 기록하지 않는다.

## 실행과 중단

승인 뒤 다음처럼 host를 눈으로 확인해 실행한다. runner는 `25 → 50 → 100 → 150 → 200 → 250` RPS를 각각 독립 k6 process로 실행한다.

```bash
infra/performance/k6/run-production-capacity.sh \
  --target-url https://<approved-production-host> \
  --confirm-target-host <approved-production-host> \
  --acknowledge-production-load YES
```

각 단계의 warm-up은 해당 단계와 같은 target RPS의 `constant-arrival-rate`로 30초 동안 실행하고, 이어서 같은 target RPS로 2분 measurement를 수행한다. 따라서 warm-up도 승인된 현재 단계 RPS를 넘기지 않는다. warm-up에서 HTTP 200 이외 응답이 한 건이라도 발생하면 현재 k6 test를 즉시 abort하여 measurement로 진행하지 않는다. measurement의 HTTP 상태 오류와 warm-up/measurement의 dropped iteration은 fail-close threshold로 처리되며, k6가 non-zero로 종료하면 shell의 `set -e`가 다음 RPS 단계를 막는다. scenario는 redirect를 따르지 않는다. health 이상, EC2/RDS 지표 악화 또는 load generator 이상도 즉시 k6 process를 종료하고 다음 단계를 실행하지 않는다. 입력 URL에는 path, query, fragment, credential 또는 HTTP scheme을 넣을 수 없다.

각 성공 단계의 stdout aggregate는 `targetRps`, `actualRps`, `droppedIterations`, `p50Ms`, `p95Ms`, `p99Ms`, `maxMs`, `expectedStatusErrorRate`, `allocatedVUs`, `activeVUs`만 포함한다. aggregate의 처리량·latency·expected-status error는 2분 measurement window 기준이며 warm-up 상태 오류는 즉시 중단용 별도 metric으로만 사용한다. response body·product ID·cookie·session·credential·raw DB data는 출력하거나 저장하지 않는다. 결과는 외부 desktop/network/load-generator 한계를 포함한 단일 배포 경로의 관측값이며 Application 또는 RDS의 독립 최대 capacity로 해석하지 않는다.

## 관측과 실행 후 확인

실행 중 다음 항목을 함께 관측한다.

- Production host/Application: EC2 CPU와 status check, backend container health/restart, proxy 5xx와 upstream connect error, 가능하면 host socket/TIME_WAIT 상태.
- RDS: CPUUtilization, DatabaseConnections, FreeableMemory, 필요 시 ReadLatency와 ReadIOPS.
- k6: aggregate의 target/actual RPS, dropped iteration, expected-status error, p50/p95/p99/max, allocated/active VUs.

마지막 실행 뒤에는 실행 전과 같은 두 단계 진단을 다시 수행한다. Production snapshot은 exit code 0과 `production_assessment=READY`, Observability 최종 진단은 exit code 0과 `status=NORMAL`이어야 한다. 둘 중 하나라도 만족하지 않으면 후속 단계나 재실행을 하지 않고 상태 안정화와 사용자 판단을 기다린다. 이 harness는 GET만 사용하고 Production nginx/Compose/Application/RDS 설정을 변경하지 않는다. 저장소 준비 변경의 복구는 이 PR을 revert하는 방식으로 한다.
