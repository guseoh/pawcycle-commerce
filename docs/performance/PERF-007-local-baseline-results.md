# PERF-007 로컬 성능 기준선 실행 결과

## 결과 상태

- 상태: `Stopped`
- 기준선 사용 가능 여부: 사용 불가
- 기준 commit: `9f8e2e661dc353cfc0a2c9925164bf373ccfd7a9`
- 실행 시작: `2026-07-17T12:46:50.4934243Z`
- 실행 종료: `2026-07-17T12:47:18.1779597Z`
- 중단 단계: `reset_preparation_start`
- 중단 시점: QA reset·seed·cold·warm 시작 전
- 중단 조건: reset 준비 기동 실패

PERF-007은 승인된 중단 규칙에 따라 재실행하지 않았다. 완전한 cold·warm 기준선이나 성능 비교 자료로 사용할 수 없다.

## 승인 입력

- `docs/performance/PERF-006-local-baseline-rerun-approval.md`
- `docs/handoffs/PERF-006/tl-to-sre.md`
- `docs/performance/PERF-005-local-baseline-rerun-decision-request.md`
- `docs/performance/PERF-004-local-baseline-results.md`
- `docs/reports/PERF-004/sre-report.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

PERF-004의 부분 관측은 이 결과와 합산하지 않았다. PERF-002·003·006의 reset, seed, cold, warm-up, sampling과 집계 조건도 변경하지 않았다.

## 기준 commit과 Git 게이트

작업 시작 시 `git fetch origin --prune` 뒤 최신 `main`에서 병합된 이전 `ops/sre` 이력을 확인하고 역할 브랜치를 새로 준비했다.

| 확인 시점 | `git status --porcelain` | `HEAD` | `origin/main` | 결과 |
| --- | --- | --- | --- | --- |
| 최초 검사 | 빈 출력 | `9f8e2e661dc353cfc0a2c9925164bf373ccfd7a9` | `9f8e2e661dc353cfc0a2c9925164bf373ccfd7a9` | 통과 |
| 래퍼 사전검증 직후·준비 기동 직전 | 빈 출력 | `9f8e2e661dc353cfc0a2c9925164bf373ccfd7a9` | `9f8e2e661dc353cfc0a2c9925164bf373ccfd7a9` | 통과 |

고정 기준 commit은 두 검사 모두의 `HEAD`·`origin/main`과 일치했다. `git diff --name-only a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d..origin/main` 결과는 PERF-004·005·006의 performance, report, handoff, learning 문서로 제한됐으며 제품 코드, Compose, Dockerfile, 실행 script, dependency와 환경 계약 변경은 없었다.

## 환경 fingerprint

| 항목 | 실행 환경 |
| --- | --- |
| OS | Windows 11 Education `10.0.26200` build `26200` |
| CPU | Intel Core i5-10400F, 6 physical cores / 12 logical processors |
| Host memory | `17,087,311,872` bytes |
| PowerShell | Windows PowerShell `5.1.26100.8875` |
| Docker Engine | client/server `28.5.1` |
| Docker Desktop | `4.48.0.207573` |
| Docker Compose | `2.40.0-desktop.1` |
| Docker runtime | Linux kernel `5.15.153.1-microsoft-standard-WSL2`, `overlayfs` |
| Docker Desktop allocation | 12 CPUs, `8,282,898,432` bytes memory |
| 전원 모드 | Balanced |
| 의미 있는 background workload | Codex, Docker Desktop, `com.docker.backend` |
| 환경 확인 시작 UTC | `2026-07-17T12:37:15.2764364Z` |
| PERF-007 실행 구간 UTC | `2026-07-17T12:46:50.4934243Z` ~ `2026-07-17T12:47:18.1779597Z` |

### Container image fingerprint

| 대상 | Image ID |
| --- | --- |
| MySQL `8.4.10` | `c831a0f11348` |
| Backend | `5d3863272bd1` |
| Frontend | `080a938dfd26` |
| Proxy | `0d3b80406a13` |

## 계측 래퍼 식별과 사전검증

- 일회성 아티팩트: `%LOCALAPPDATA%\Temp\pawcycle-perf007-wrapper.ps1`
- 생성 기준: PERF-002·003·006 승인 조건을 구현한 PERF-007 일회성 PowerShell 5.1 래퍼
- SHA-256: `486e9828a3afdd41feb14314fb684ac002cc9c2f0755d7a7815db2a9d7762a85`
- 구문 검사: PowerShell parser, 통과
- 상태 변경 없는 검증 명령: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <temp-wrapper> -Mode SelfTest`
- 비민감 입력: 순수 PowerShell request parameter, canonical record, container stats와 집계 객체
- 외부 호출: network 0회, Docker 0회

사전검증은 GET body 미첨부, body method 분기, HTTP success·HTTP error·transport error·timeout record, CPU·memory·network·block I/O·PIDs parsing, scalar size factor, nullable PIDs·response bytes, p50·p95·max와 expected status 불일치 집계를 통과했다. 검증 직후 동일 SHA-256을 재확인했다.

## 실제 실행 명령과 승인 순서

실행 명령은 다음 형태로 한 번만 수행했다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File <temp-wrapper> -Mode Run -RepoRoot <repository> -ResultPath <os-temp-aggregate> -BaselineCommit 9f8e2e661dc353cfc0a2c9925164bf373ccfd7a9
```

승인 순서는 down 상태 확인 → reset 준비 기동·healthy 확인 → QA fixture 확인 → reset 1회·`false` 복원 → seed 생성·확인 → cold 3회 → healthy·30초 안정화 → 다섯 읽기 route → 인증 lifecycle → 구독 상태 변경이었다. 실제 실행은 첫 down 상태 확인까지만 완료했고, reset 준비 기동 중 중단됐다.

## 중단 근거와 원인 분류

래퍼의 `Invoke-NativeCommand`는 전역 `$ErrorActionPreference = "Stop"` 상태에서 native command의 stderr를 `2>&1`로 합쳤다. Windows PowerShell 5.1은 `docker compose up`이 정상 진행 상황으로 stderr에 기록한 `Network pawcycle-local-integration_default Creating`을 terminating error로 취급했고, 래퍼는 exit code 판정과 네 service health 확인에 도달하지 못했다.

- 관찰된 결함 유형: PowerShell 함수·native command 반환 처리 결함
- 관찰된 메시지: Docker Compose network 생성 진행 메시지
- 제품 endpoint 또는 제품 결함 근거: 없음
- Docker Engine 실패 근거: 없음
- 원본 위치: 일회성 래퍼의 native command stderr 병합 경로

사전 순수 객체 검증은 native command의 정상 stderr 진행 메시지를 포함하지 않아 이 결함을 검출하지 못했다. 준비 기동 실패 중단 조건을 적용했으며 래퍼를 수정해 재실행하지 않았다.

## 완료된 표본과 미완료 표본

| 측정 단위 | 승인 표본 수 | 완료 표본 수 | 상태 |
| --- | ---: | ---: | --- |
| Cold start | 3 | 0 | 미실행 |
| `GET /products` | 30 | 0 | 미실행 |
| `GET /api/products` | 30 | 0 | 미실행 |
| `GET /api/products/{productId}` | 30 | 0 | 미실행 |
| `GET /api/subscriptions` | 30 | 0 | 미실행 |
| `GET /api/subscriptions/{subscriptionId}` | 30 | 0 | 미실행 |
| 인증 lifecycle | 30 | 0 | 미실행 |
| 구독 상태 변경 | 10 | 0 | 미실행 |
| Warm container sampling | cohort별 3 | 0 | 미실행 |

HTTP aggregate, container aggregate, service health convergence, restart count와 상태 변경 cardinality는 모두 산출되지 않았다. p50·p95·max, status별 요청 수, 오류 비율, response bytes와 container 지표는 계산할 표본이 없다.

## 상태 변경과 최종 상태

- 준비 전 volume 보존 `down`: 확인 완료
- Reset 준비 기동: 시작했으나 네 service healthy 확인 전에 래퍼 중단
- QA fixture·credential 확인: 미실행
- QA reset: 미시작
- Reset 설정 복원: child process 환경은 `false`, `.env.local`도 `false`임을 별도 확인
- Seed 생성: 미시작
- Endpoint 호출·warm-up·성능 측정: 0회
- 사후 `docker compose ps -a`: 빈 출력으로 stack down 확인
- Named volume: `pawcycle-local-integration-mysql-data` 존재 확인
- Volume 삭제: 수행하지 않음
- Raw record 파일 기록: 없음

래퍼 내부 cleanup 플래그는 동일한 native stderr 처리 결함 때문에 `stack_volume_preserving_down = false`를 남겼다. 이는 실제 stack 상태 판정으로 사용하지 않았으며, 별도 Compose 조회로 down을 확인했다.

## 사용할 수 있는 결과와 제한

사용 가능한 결과는 기준 commit, 환경 fingerprint, 두 시점의 Git 게이트, 래퍼 식별·사전검증 결과, 중단 단계와 안전 정리 상태뿐이다. Latency, 오류율, cold convergence와 container 자원 기준선은 없다.

- PERF-004 부분 관측과 합산하지 않는다.
- 완전한 일회성 로컬 기준선으로 표현하지 않는다.
- SLO, latency 목표, 오류 예산, regression threshold, 병목, 최적화와 운영 capacity를 판단하지 않는다.
- 로컬 환경 결과를 배포 또는 운영 환경에 적용할 수 없다.
- README의 “로컬 성능 기준선 미측정” 상태는 유지한다.

## 다음 사용자 결정

새 작업 ID에서 수정된 래퍼로 전체 run을 다시 승인할지 사용자/Tech Lead 결정이 필요하다. 다시 승인한다면 native command의 stderr와 exit code를 분리하고, 정상 stderr 진행 메시지까지 포함하는 상태 변경 없는 검증을 통과한 새 아티팩트여야 한다. 이번 PERF-007에서는 reset, seed 또는 측정 재실행을 승인하지 않는다.

## 결론

PERF-007은 QA 상태 변경 전 reset 준비 기동 단계에서 안전 중단됐다. 성능 기준선은 확보되지 않았고, 부분 표본도 없다.
