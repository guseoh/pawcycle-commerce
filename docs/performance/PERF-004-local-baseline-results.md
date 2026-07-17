# PERF-004 로컬 성능 기준선 부분 측정 결과

## 결과 상태

- 상태: `Partial / Stopped`
- 유효한 결과: 환경 fingerprint, 사전 검증, cold start 3회, service별 health convergence
- 미완료 결과: 다섯 읽기 route, 인증 lifecycle, 구독 상태 변경, warm container sampling
- 중단 이유: warm 측정 계측 래퍼가 첫 유효 표본 전에 두 차례 실패했고, 두 번째 실패 전에 QA seed와 집계 제외 대상 warm-up이 수행됐다. 실패 실행을 성공 재실행으로 대체하거나 서로 다른 준비 상태를 합치지 않기 위해 추가 측정을 중단했다.

이 문서는 완료된 로컬 기준선이 아니다. 아래 cold start 결과만 PERF-004의 부분 관측값으로 사용할 수 있다.

## 질문

PERF-002와 PERF-003에서 승인한 동일 조건 아래에서 PawCycle Commerce local-integration의 cold start, HTTP cohort와 container 자원 기준선은 무엇인가?

이번 실행은 cold start에 대해서만 답을 제공한다. HTTP와 warm container 기준선은 제공하지 않는다.

## 승인 입력

- `docs/handoffs/PERF-003/tl-to-sre.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/performance/PERF-001-local-baseline-decision-request.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

실행 게이트는 `Open for One-time Local Baseline Measurement`였으며 concurrency 1, think time 없음, PowerShell 5.1과 기존 Docker·Compose만 사용했다.

## 기준 commit과 환경 fingerprint

| 항목 | 실제 값 |
| --- | --- |
| 측정일 | 2026-07-17 (Asia/Seoul) |
| 기준 브랜치 | `main` |
| 기준 commit | `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d` |
| 작업 브랜치 | `ops/sre` |
| OS | Microsoft Windows 11 Education, version 10.0.26200, build 26200 |
| CPU | Intel Core i5-10400F @ 2.90 GHz, 6 physical cores / 12 logical processors |
| 호스트 memory | 17,087,311,872 bytes |
| PowerShell | 5.1.26100.8875 |
| Docker Engine | client/server 28.5.1 |
| Docker Desktop | 4.48.0 (207573), WSL2 |
| Docker Compose | 2.40.0-desktop.1 |
| Docker runtime | Linux kernel 5.15.153.1-microsoft-standard-WSL2, overlayfs |
| Docker 할당 CPU | 12 |
| Docker 할당 memory | 8,282,898,432 bytes |
| 전원 모드 | Windows `균형 조정` |
| background workload | 일반 대화형 데스크톱(Codex, IDE, Docker Desktop). 사전 image build 뒤 별도 build, benchmark 또는 download를 의도적으로 실행하지 않음 |

### Container image fingerprint

| Service | Repository:tag | Image ID |
| --- | --- | --- |
| backend | `pawcycle-local-integration-backend:latest` | `5d3863272bd1` |
| frontend | `pawcycle-local-integration-frontend:latest` | `080a938dfd26` |
| mysql | `mysql:8.4.10` | `c831a0f11348` |
| proxy | `nginx:1.30.3-alpine3.23` | `0d3b80406a13` |

## 실제 실행 명령과 순서

명령은 `infra/local-integration`에서 실행했다. `.env.local` 값은 Docker Compose 또는 현재 PowerShell process에만 전달했고 출력하거나 문서화하지 않았다.

```powershell
docker compose --env-file .env.local config --quiet
docker compose --env-file .env.local build backend frontend
docker compose --env-file .env.local up --detach --wait --wait-timeout 180
./smoke.ps1 -Scenario Full
```

승인된 QA subscription reset은 process 환경에서 reset flag를 일시적으로 `true`로 override하여 backend와 proxy를 재생성한 뒤 `Empty` smoke로 확인했다. 이후 `.env.local`의 `false` 상태로 다시 재생성하고 `Empty` smoke를 재확인했다. 파일은 수정하지 않았고 volume은 삭제하지 않았다.

Cold start 각 회차는 다음 순서로 실행했다.

```powershell
docker compose --env-file .env.local down
docker compose --env-file .env.local up --detach --wait --wait-timeout 180
```

별도 500 ms polling으로 각 service가 처음 `healthy`가 된 경과를 기록했다. 세 번째 회차의 전체 healthy 뒤 30초 안정화를 적용했다. 이후 process-memory 계측 래퍼에서 QA seed를 생성하고 warm cohort를 시작했으나 첫 유효 warm 표본 전에 중단 조건에 도달했다. 마지막에는 `Preserved` smoke를 통과한 뒤 volume을 삭제하지 않는 `down`으로 정리했다.

## 사전 환경 검증

| 검증 | 결과 |
| --- | --- |
| Compose config | 통과 |
| backend·frontend image build | 통과 |
| 네 service `healthy` | 통과 |
| FOUNDATION-004 `Full` smoke | 통과 |
| 승인된 QA subscription reset 뒤 `Empty` smoke | 통과 |
| reset flag `false` 복원 뒤 `Empty` smoke | 통과 |
| 중단 뒤 QA seed 보존 `Preserved` smoke | 통과 |
| 최종 volume-preserving `down` | 통과 |

사후 smoke 첫 호출은 점검용 환경 변수 로더가 따옴표를 값에 포함해 로그인 401을 반환했다. 비밀값을 출력하지 않고 로더의 따옴표 처리만 바로잡아 같은 `Preserved` smoke를 통과시켰다. 이 호출은 성능 표본에 포함하지 않았다.

## Cold start 결과

단위는 millisecond다. 전체 경과는 `up --detach --wait` 명령 시작부터 종료까지이며, service convergence는 같은 시작점부터 첫 `healthy` 관측까지다. 500 ms polling 간격 때문에 service별 값에는 그만큼의 관측 오차가 있을 수 있다.

| Run | 전체 경과 | mysql healthy | backend healthy | frontend healthy | proxy healthy | 결과 |
| ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 25,917 | 9,396 | 19,652 | 10,048 | 25,228 | 성공 |
| 2 | 25,155 | 8,736 | 19,703 | 9,464 | 24,642 | 성공 |
| 3 | 25,293 | 9,146 | 19,327 | 9,715 | 24,778 | 성공 |

| 항목 | 최소 | 중앙값 | 최대 |
| --- | ---: | ---: | ---: |
| 전체 경과 | 25,155 | 25,293 | 25,917 |
| mysql healthy | 8,736 | 9,146 | 9,396 |
| backend healthy | 19,327 | 19,652 | 19,703 |
| frontend healthy | 9,464 | 9,715 | 10,048 |
| proxy healthy | 24,642 | 24,778 | 25,228 |

Cold start와 service health convergence는 HTTP elapsed 및 warm container sampling과 합산하지 않았다.

## HTTP route·cohort 집계

승인된 계산법은 p50을 정렬된 짝수 표본의 가운데 두 값 산술평균으로, p95를 nearest-rank `ceil(0.95 × n)`으로 계산하는 것이다. 실패, timeout, transport error와 expected status 불일치도 삭제하거나 성공 결과로 대체하지 않아야 한다.

첫 유효 measured iteration 전에 중단했으므로 아래 통계를 계산하지 않았다.

| Cohort | 승인 표본 수 | 실제 유효 표본 수 | p50 | p95 | max | 상태 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `GET /products` | 30 | 0 | 미계산 | 미계산 | 미계산 | 중단 |
| `GET /api/products` | 30 | 0 | 미계산 | 미계산 | 미계산 | 미실행 |
| `GET /api/products/{productId}` | 30 | 0 | 미계산 | 미계산 | 미계산 | 미실행 |
| `GET /api/subscriptions` | 30 | 0 | 미계산 | 미계산 | 미계산 | 미실행 |
| `GET /api/subscriptions/{subscriptionId}` | 30 | 0 | 미계산 | 미계산 | 미계산 | 미실행 |
| 인증 lifecycle | 30 | 0 | 미계산 | 미계산 | 미계산 | 미실행 |

`GET /products`의 집계 제외 warm-up 5회가 두 번째 실패 실행에서 수행됐지만, 바로 뒤 `before` container sample 생성 중 중단됐다. 이를 측정 표본이나 완료된 승인 warm-up으로 간주하지 않았다.

## 구독 상태 변경 집계와 cardinality

| 항목 | 승인값 | 실제 결과 |
| --- | ---: | --- |
| 상태 변경 iteration | 10 | 0회 측정 |
| p50 | 계산 대상 | 미계산 |
| max | 계산 대상 | 미계산 |
| `subscription_count_before` | iteration별 기록 | 미기록 |

계측 준비 과정의 seed 생성은 성공했고 중단 뒤 `Preserved` smoke로 보존 여부를 확인했다. 그러나 이는 10회 상태 변경 cohort의 표본이 아니며 cardinality 기준선으로 사용하지 않는다. 추가 reset이나 상태 변경은 수행하지 않았다.

## Expected status 불일치와 오류 결과

측정 HTTP record가 생성되지 않아 cohort별 expected status 불일치, timeout, transport error와 오류율 집계는 없다. 계측·검증 과정에서 발생한 실패는 삭제하지 않고 다음과 같이 남긴다.

| 단계 | 실패 | 표본 처리 |
| --- | --- | --- |
| Cold wrapper 준비 | Docker의 정상 stderr 진행 출력을 PowerShell `Stop` 오류로 취급 | stopwatch와 표본 생성 전 실패. Cold run 1~3에 포함하지 않음 |
| Warm wrapper 1 | 초기 fixture GET에서 `System.Net.ProtocolViolationException: Cannot send a content-body with this verb-type.` 발생 | seed, warm-up, HTTP·container 표본 생성 전 중단 |
| Warm wrapper 2 | `Convert-SizeToBytes`의 `$number * $factor`에서 `Method invocation failed because [System.Object[]] does not contain a method named 'op_Multiply'.` 발생 | seed와 집계 제외 warm-up 5회 뒤, 첫 measured iteration과 `before` sample 완료 전에 중단 |
| 사후 smoke 점검 1 | 점검용 env loader가 따옴표를 포함해 로그인 401 발생 | 성능 표본 아님. 원인 수정 후 `Preserved` smoke 통과 사실과 함께 보존 |

## PERF-005 계측 래퍼 원인 진단

PERF-004의 계측 래퍼는 repository 파일이 아니라 한 PowerShell process에서만 실행됐다. PERF-005 확인 시 현재 session과 PSReadLine 지속 history에 관련 함수 정의가 남아 있지 않아 원본 파일·행 번호와 당시 전체 함수 본문은 복구할 수 없었다. 다음 분류는 PERF-004 실행에서 보존된 실제 예외, 실패 표현식과 상태 경계를 근거로 한다.

| 오류 | 직접 원인 | 분류 | 확정 범위 |
| --- | --- | --- | --- |
| Warm wrapper 1 | `[string]$Body = $null` 기본값이 PowerShell 5.1에서 길이 0의 `System.String`으로 바인딩됐고, null 비교만 사용한 request parameter 구성에서 GET에도 빈 body가 첨부됨 | PowerShell 함수·매개변수·HTTP 예외 처리 결함 | 예외와 상태 변경 없는 최소 재현으로 확정 |
| Warm wrapper 2 | `Convert-SizeToBytes`의 곱셈 시 scalar여야 할 `$factor`가 `System.Object[]`이어서 `op_Multiply` 호출 실패 | container stats parsing 또는 집계 결함 | 실패 함수·표현식·반환 type은 확정. 원본 switch의 어떤 분기 조합이 배열을 만들었는지는 원본 정의 부재로 미확정 |

상태 변경 없는 순수 PowerShell 최소 재현에서 `[string]$Body = $null`은 `IsNull=False`, `Length=0`이고 null 비교만 사용하면 body를 첨부하는 경로가 됐다. 또한 두 값을 가진 `$factor`는 `System.Object[]`이며 `$number * $factor`에서 PERF-004와 같은 `op_Multiply` 오류를 냈다. 빈 body를 첨부하지 않는 조건과 단일 값 hashtable lookup을 사용한 대체 로직은 순수 객체 입력에서 각각 GET body 미첨부와 scalar factor 1개를 확인했다.

두 오류 모두 제품 endpoint의 응답 성능이나 Docker engine 실패를 나타내는 증거가 아니다. 첫 오류는 request를 보내기 전 client parameter 구성, 두 번째 오류는 container stats 값을 집계 객체로 변환하는 client-side harness에서 발생했다.

## Container sampling과 restart count

Warm 측정 단위의 `before` sample 변환 도중 중단되어 승인된 `before`·`mid`·`after` 3점 세트를 하나도 만들지 못했다. 따라서 CPU, memory, network RX/TX, block read/write와 PIDs 집계는 모두 `미측정`이며 `0`으로 대체하지 않는다. 누적 counter 차이나 처리량도 보고하지 않는다.

중단 직후 진단 시 네 container는 모두 `healthy`였고 restart count는 각각 0이었다. 이는 완성된 cohort의 전후 restart 집계가 아니라 사후 상태 진단이다.

| Service | 사후 restart count | 승인된 cohort 전후 차이 |
| --- | ---: | --- |
| backend | 0 | 미측정 |
| frontend | 0 | 미측정 |
| mysql | 0 | 미측정 |
| proxy | 0 | 미측정 |

## 원시 record와 민감정보 처리

- PERF-002 canonical allowlist를 벗어난 HTTP record를 생성하거나 직렬화하지 않았다.
- 생성된 원시 HTTP·container record와 raw log는 저장소에 기록하거나 장기 보존하지 않았다.
- 실제 ID, credential, cookie, session ID, CSRF token, header와 body를 결과에 남기지 않았다.
- 중단 시 process-memory 임시 상태를 폐기했다.

## 미실행 또는 부분 실행 항목과 이유

- 다섯 읽기 route 30회: 첫 route의 첫 measured iteration 전 계측 래퍼 실패로 미실행
- 인증 lifecycle 30회: 앞선 warm 단위 중단으로 미실행
- 구독 상태 변경 10회: 앞선 warm 단위 중단으로 미실행
- 모든 warm container sampling과 지표별 집계: 첫 `before` sample 변환 실패로 미실행
- HTTP percentile, status·오류율 집계: 유효 measured record가 없어 미계산
- `py -3 scripts/validate-task-artifacts.py --task-id PERF-004`: 통과
- 세 신규 문서의 `git diff --check --no-index`: 통과
- 세 신규 문서의 credential·token·session·cookie 등 민감정보 패턴 검사: 통과

추가 실행에는 실패 전 seed와 warm-up이 포함된 현재 상태를 승인된 초기 조건으로 다시 맞추거나, 새 PERF 작업에서 재시도 정책을 승인해야 한다. 이번 실행에서는 volume 삭제, 추가 reset, 실패 대체 또는 iteration 재실행을 하지 않았다.

## 결과 해석의 제한

- Cold start 3회만으로 운영 capacity, SLO, regression threshold 또는 병목을 판단할 수 없다.
- service convergence는 500 ms polling으로 관측한 client-side 값이며 내부 startup 구간을 설명하지 않는다.
- 일반 대화형 desktop background workload와 WSL2/Docker Desktop 조건의 일회성 로컬 관측이다.
- HTTP 및 container warm 결과가 없으므로 route, 인증, 상태 변경 또는 자원 사용량을 비교할 수 없다.
- 실패 원인은 제품 결함이나 성능 병목으로 확정할 수 없고, 저장소에 추가하지 않은 임시 계측 래퍼의 결함이다.

## 결론

기준 commit과 환경 fingerprint, cold start 3회는 재현 근거가 있는 부분 결과다. 승인된 warm cohort 전체를 완료하지 못했으므로 PERF-004 로컬 성능 기준선은 미완료이며 Tech Lead의 재실행 결정 전까지 성능 목표·병목·최적화 입력으로 사용하지 않는다.

PERF-005에서는 endpoint 호출, QA reset·seed·warm-up과 성능 측정을 실행하지 않고 위 래퍼 원인만 정적·순수 객체 방식으로 진단했다. Cold 부분 결과는 유지하고 warm 결과는 계속 `미완료·사용 불가`다.
