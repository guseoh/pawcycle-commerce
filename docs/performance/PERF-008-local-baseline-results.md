# PERF-008 최종 로컬 성능 기준선 실행 결과

## 결과 상태

- 상태: `Stopped`
- 로컬 기준선 사용 가능 여부: 사용 불가
- 기준 commit: `306d35cd5dd7818e662fa773ff7968c6c3fabc84`
- 최초 Git·환경 게이트 UTC: `2026-07-17T13:21:52.0892216Z`
- 중단 확인 UTC: `2026-07-17T13:31:49.5626288Z`
- 중단 단계: 상태 변경 없는 래퍼 `SelfTest`
- 중단 조건: 수정 래퍼 `SelfTest` 실패
- 상태 변경 시점: Docker 준비 기동·QA reset·seed·endpoint 호출 전

PERF-008은 승인된 중단 조건에 따라 래퍼 수정, SelfTest 재실행과 실제 측정을 수행하지 않았다. 이 결과는 완전한 cold·warm 기준선이 아니며, PERF-008로 로컬 기준선 측정 기능군을 최종 종료한다. 추가 PERF 재실행은 계획하지 않는다.

## 승인 입력

- `docs/performance/PERF-007-local-baseline-results.md`
- `docs/reports/PERF-007/sre-report.md`
- `docs/handoffs/PERF-007/sre-to-tl.md`
- `docs/performance/PERF-006-local-baseline-rerun-approval.md`
- `docs/handoffs/PERF-006/tl-to-sre.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

PERF-004와 PERF-007은 완전한 기준선이 아니며 PERF-008과 합산하지 않았다. PERF-002·003·006의 reset, seed, cold, warm-up, container sampling과 집계 조건은 변경하지 않았다.

## PR #54와 역할 브랜치 준비

- PR #54: `MERGED`
- 병합 UTC: `2026-07-17T13:18:39Z`
- 병합 commit: `fab38e495a7c2e099d19218d69e5053c464476e2`
- PR head: `4f38bf1375d21b65c4bfe7715ce7fa70f0f7d209`
- PR head와 병합 commit의 tree diff: 없음
- 병합 뒤 추가 main 변경: `docs/learning/pull-requests/2026/PR-54-docs-sre-perf-007.md` 1개

기존 `ops/sre`는 PR #54 head와 일치하고 병합 commit과 tree가 동일해 보존할 변경이 없음을 확인했다. 최신 `main`을 fast-forward한 뒤 기존 로컬·원격 역할 브랜치를 삭제하고 같은 이름을 최신 `main`에서 새로 만들었다. Reset, rebase, force push와 history rewrite는 수행하지 않았다.

## 기준 commit과 Git 게이트

| 확인 시점 | `git status --porcelain` | `HEAD` | `origin/main` | 결과 |
| --- | --- | --- | --- | --- |
| 최초 검사 | 빈 출력 | `306d35cd5dd7818e662fa773ff7968c6c3fabc84` | `306d35cd5dd7818e662fa773ff7968c6c3fabc84` | 통과 |
| SelfTest 이후·준비 기동 직전 | 미실행 | 미실행 | 미실행 | SelfTest 실패로 게이트 미도달 |

최초 검사에서 clean working tree와 `HEAD == origin/main == 고정 기준 commit`을 확인했다. SelfTest가 완료되지 않았으므로 래퍼 SHA와 Git을 두 번째 시점의 통과 결과로 표현하지 않으며 Docker 준비 기동을 시작하지 않았다.

## PERF-007 이후 변경 검사

`git diff --name-only 9f8e2e661dc353cfc0a2c9925164bf373ccfd7a9..origin/main` 결과는 다음 문서로 제한됐다.

- `docs/handoffs/PERF-007/sre-to-tl.md`
- `docs/learning/pull-requests/2026/PR-54-docs-sre-perf-007.md`
- `docs/performance/PERF-007-local-baseline-results.md`
- `docs/reports/PERF-007/sre-report.md`

Backend, Frontend, Compose, Dockerfile, Nginx, dependency, 환경 계약과 실행 script 변경은 없었다.

## 환경 fingerprint

| 항목 | 실행 환경 |
| --- | --- |
| OS | Windows 11 Education `10.0.26200` build `26200` |
| CPU | Intel Core i5-10400F, 6 physical cores / 12 logical processors |
| Host memory | `17,087,311,872` bytes |
| PowerShell | Windows PowerShell `5.1.26100.8875` |
| Docker Engine | client/server `28.5.1`, Linux Engine |
| Docker Desktop | `4.48.0.207573` |
| Docker Compose | `2.40.0-desktop.1` |
| Docker runtime | Linux kernel `5.15.153.1-microsoft-standard-WSL2`, `overlayfs` |
| Docker Desktop allocation | 12 CPUs, `8,282,898,432` bytes memory |
| 전원 모드 | Balanced |
| 의미 있는 background workload | Codex, Docker Desktop, `com.docker.backend` |

### Container image fingerprint

| 대상 | Image ID |
| --- | --- |
| MySQL `8.4.10` | `c831a0f11348d402b43d77453e17d770be2eef356615a2823fe0f5a0d6c8b9af` |
| Backend | `5d3863272bd11e56a1831a7d3f0b2cfd5e3b36e9e6fd9089411f723867195bca` |
| Frontend | `080a938dfd265669c4a6dba6ca9646871480d59d9e7d522e2e587b9ff073d616` |
| Proxy `nginx:1.30.3-alpine3.23` | `0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1` |

Compose config는 통과했고 stack down, `.env.local` reset `false`, QA·MySQL 필수 환경 변수의 비어 있지 않은 존재를 값 노출 없이 확인했다.

## 계측 래퍼 식별과 변경 방향

- 일회성 아티팩트: `%LOCALAPPDATA%\Temp\pawcycle-perf008-wrapper.ps1`
- 생성 기준: PERF-002·003·006과 PERF-008 native process 계약
- SHA-256: `ed67151588754935d7c8f421d44cf8a74a41b7664ec9600d507a02da226811d1`
- 저장소 benchmark script 추가: 없음

래퍼는 Windows PowerShell 5.1 호환 `System.Diagnostics.Process`를 사용해 stdout과 stderr의 `ReadToEndAsync()`를 동시에 시작하고 process exit code와 timeout을 별도로 판정하도록 작성했다. Native stderr를 `2>&1`로 합치거나 stderr 존재만으로 실패 처리하지 않았다. Raw stdout·stderr는 process memory에만 유지하고 상위 실패에는 비민감 단계·exit code 요약만 전달하도록 구성했다.

## 상태 변경 없는 SelfTest 결과

실행 명령은 다음 형태였다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File <temp-wrapper> -Mode SelfTest
```

SelfTest는 Docker와 network를 호출하지 않는 비민감 PowerShell child process와 순수 객체만 사용했다.

| 검증 경로 | 결과 |
| --- | --- |
| stdout만 출력, exit 0 | 통과 |
| stderr 정상 진행 메시지, exit 0 | 통과 |
| stdout·stderr 동시 출력, exit 0 | 통과 |
| stderr 오류 메시지, non-zero exit | 통과 |
| 출력 없는 non-zero exit | 통과 |
| timeout | 통과 |
| GET body 미첨부 | 통과 |
| Body method에만 body 구성 | 통과 |
| HTTP canonical record 4종 | 실패 |
| Container stats parsing | 미실행 |
| Scalar size factor | 미실행 |
| Nullable PIDs·response bytes | 미실행 |
| p50·p95·max | 미실행 |
| Expected status 불일치 집계 | 미실행 |

Native 여섯 경로와 request parameter 검증은 실패 지점보다 앞에서 모든 assertion을 통과했다. 따라서 `exit 0 + stderr`는 성공, non-zero exit는 실패, timeout은 별도 상태로 처리됐고 native stderr 자체는 PowerShell terminating error로 변환되지 않았다.

## 중단 직접 근거와 원인 분류

Canonical record 4개를 배열에 구성하는 PowerShell 표현에서 각 함수 호출 끝의 쉼표가 다음 호출을 같은 명령의 인자로 이어 붙였다. 두 번째 record를 구성할 때 첫 호출에 `-Cohort`가 다시 전달된 것으로 해석돼 다음 parameter binding 오류가 발생했다.

```text
Cannot bind parameter because parameter 'Cohort' is specified more than once.
```

- 결함 유형: PowerShell 함수·매개변수 구성 결함
- 위치: 일회성 래퍼 SelfTest의 canonical record 배열 구성
- 제품 또는 Docker Engine 결함 근거: 없음
- Native stderr·exit code 분리 결함 재발 근거: 없음
- Raw SelfTest stdout·stderr 보존: 없음

수정 래퍼 SelfTest 실패 중단 조건을 적용했다. 배열 표현을 수정하거나 SelfTest를 재실행하지 않았다.

## 완료된 표본과 미완료 표본

| 측정 단위 | 승인 표본 수 | 완료 표본 수 | 상태 |
| --- | ---: | ---: | --- |
| Reset 준비 기동 | 측정 제외 1회 | 0 | 미실행 |
| Cold start | 3 | 0 | 미실행 |
| 다섯 읽기 route | route별 30 | 0 | 미실행 |
| 인증 lifecycle | 30 | 0 | 미실행 |
| 구독 상태 변경 | 10 | 0 | 미실행 |
| Warm container sampling | cohort별 3 | 0 | 미실행 |

HTTP·container raw record는 생성되지 않았다. Cold convergence, latency, status, 오류율, response bytes, container 지표, restart count와 cardinality 집계는 모두 사용할 수 없다.

## Docker·QA·volume 최종 상태

- Docker 준비 기동: 미실행
- Product endpoint·smoke: 0회
- QA reset·seed·warm-up: 0회
- QA 데이터 상태 변경: 없음
- Reset 설정: `.env.local` `false`
- Docker stack: volume 보존 `down`
- Named volume: `pawcycle-local-integration-mysql-data` 존재
- Raw record·raw native output 파일: 없음
- OS temp aggregate: 생성되지 않음
- OS temp 래퍼: 증거 추출 뒤 삭제

## 사용할 수 있는 결과와 제한

사용 가능한 결과는 PR #54 병합·Git 기준 상태, 환경 fingerprint, 래퍼 SHA-256, native process 핵심 6경로와 request parameter SelfTest의 순차 통과 근거, 실패 위치와 안전 최종 상태다. 성능 기준선으로 사용할 수 있는 결과는 없다.

- PERF-004·007과 합산하지 않는다.
- README의 “로컬 성능 기준선 미측정” 상태를 유지한다.
- SLO, latency 목표, 오류 예산, regression threshold, 병목, 최적화, capacity와 운영 성능을 판단하지 않는다.
- 로컬 값을 배포·운영 환경에 적용하지 않는다.
- PERF-009 또는 추가 로컬 기준선 재실행을 요청하지 않는다.

## 다음 작업

로컬 기준선 측정 기능군은 `Stopped`로 종료한다. 다음 기능군은 별도 사용자 승인 아래 실제 배포·운영 준비 범위로 전환하며, 로컬 기준선 부재를 명시적 제한으로 유지한다.

## 결론

PERF-008은 상태 변경 없는 SelfTest 단계에서 안전 중단됐다. 실제 측정과 QA 상태 변경은 없었으며 일회성 로컬 기준선은 확보되지 않았다.
