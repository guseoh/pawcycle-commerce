# PERF-004 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: `PERF-004`
- 역할: Platform/SRE
- 기준 브랜치: `main`
- 작업 브랜치: `ops/sre`
- 기준 commit: `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d`
- 작업 상태: `Partial / Stopped`

## 작업 목적

PERF-002와 PERF-003에서 승인한 조건으로 일회성 로컬 성능 기준선을 측정하고 집계 결과, 재현 조건과 제한을 문서화한다. 성능 목표, 병목과 최적화는 결정하지 않는다.

## 입력 문서

- `AGENTS.md`
- `infra/AGENTS.md`
- `docs/roles/platform-sre.md`
- `.agents/skills/platform-sre/SKILL.md`
- `docs/handoffs/PERF-003/tl-to-sre.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/performance/PERF-001-local-baseline-decision-request.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

## 승인 입력

- PR #51 병합 뒤 실행 게이트 `Open for One-time Local Baseline Measurement`
- PowerShell 5.1, 기존 Docker·Compose와 local-integration만 사용
- concurrency 1, think time 없음
- volume을 삭제하지 않는 cold start 3회와 service별 health convergence 분리
- 읽기 route별 warm-up 5회 뒤 30회, 인증 lifecycle 30회, 구독 상태 변경 10회
- warm 단위별 `before`·`mid`·`after` container sampling
- raw record·log와 민감정보 비보존

## 변경 범위

- 최신 `origin/main` 기준의 깨끗한 `ops/sre` 준비
- 환경 fingerprint와 container image fingerprint 수집
- Compose config, image build, QA fixture·smoke와 reset 조건 검증
- Cold start 3회 및 service health convergence 측정
- Warm cohort 계측 시도와 중단 근거 기록
- 부분 결과, 보고서와 Tech Lead 인수인계 작성

## 변경하지 않은 범위

- 제품 코드와 테스트 코드
- Compose, Nginx, Dockerfile, CI와 dependency
- repository benchmark script와 신규 도구
- QA 회원 외 데이터, Docker volume
- SLO, latency 목표, 오류 예산, regression threshold
- 병목 확정, 최적화와 capacity 판단
- 자동 병합

## 주요 결과

- 기준 commit은 `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d`로 고정했다.
- Compose·image build·초기 Full smoke와 승인된 reset·Empty smoke를 통과했다.
- Cold start 3회 전체 경과는 25,917 ms, 25,155 ms, 25,293 ms였다.
- 세 회차 모두 service가 healthy가 됐고 최종 안정화 30초를 적용했다.
- Warm 계측은 첫 유효 표본 전에 래퍼 오류가 두 차례 발생했다. 두 번째 실패 전에 seed와 집계 제외 warm-up이 수행돼 이후 실행의 비교 가능성을 보장할 수 없으므로 추가 시도를 중단했다.
- 중단 뒤 올바른 env loader로 `Preserved` smoke를 통과했고, 네 container restart count는 모두 0이었다. 이후 volume을 삭제하지 않고 stack을 종료했다.

## 차단 사유

첫 유효 warm 표본 전에 계측 래퍼의 GET body 처리와 Docker size 변환이 차례로 실패했다. 두 번째 실패 시점에는 seed와 `GET /products`의 집계 제외 warm-up 5회가 이미 수행됐다. 이 상태에서 다시 시작하면 실패 실행을 성공 결과로 대체하거나 서로 다른 준비 상태를 합칠 수 있어 사용자 중단 조건에 해당한다.

## PERF-005 사후 원인 분류

- Warm wrapper 1: `[string]$Body = $null`이 빈 문자열로 바인딩되고 null 비교만으로 GET body를 첨부한 PowerShell 함수·매개변수·HTTP parameter 구성 결함
- Warm wrapper 2: `Convert-SizeToBytes`의 `$factor`가 scalar 대신 `System.Object[]`이 되어 `$number * $factor`에서 실패한 container stats parsing 결함
- 제품 결함 또는 환경·Docker engine 실패: 이를 뒷받침하는 증거 없음
- 미확정 범위: process-memory 원본 래퍼와 history가 남지 않아 원본 파일·행 번호 및 배열을 만든 정확한 switch 분기 조합은 확인 불가

PERF-005의 순수 PowerShell 객체 최소 재현에서 두 type·연산 특성을 확인했으며 endpoint, Docker, DB와 QA state는 사용하지 않았다.

## 실행한 검증

- `git status --short --branch`: 작업 시작 시 clean 확인
- `git fetch origin --prune`: 통과
- `git pull --ff-only origin main`: 통과
- `docker compose --env-file .env.local config --quiet`: 통과
- `docker compose --env-file .env.local build backend frontend`: 통과
- `docker compose --env-file .env.local up --detach --wait --wait-timeout 180`: 통과
- `./smoke.ps1 -Scenario Full`: 통과
- 승인된 reset 뒤 `./smoke.ps1 -Scenario Empty`: 통과
- reset flag `false` 복원 뒤 `./smoke.ps1 -Scenario Empty`: 통과
- 중단 뒤 `./smoke.ps1 -Scenario Preserved`: 통과
- Cold start 3회: 통과
- 최종 volume-preserving `docker compose --env-file .env.local down`: 통과
- `py -3 scripts/validate-task-artifacts.py --task-id PERF-004`: 통과
- 세 신규 문서의 `git diff --check --no-index`: 통과
- 세 신규 문서의 credential·token·session·cookie 등 민감정보 패턴 검사: 통과

## 실행하지 못한 검증과 이유

- 다섯 읽기 route 30회와 percentile: 첫 measured iteration 전 warm 계측 중단
- 인증 lifecycle 30회: 앞선 warm 단위 중단
- 구독 상태 변경 10회와 cardinality: 앞선 warm 단위 중단
- warm container 3점 sampling과 지표 집계: 첫 `before` sample 변환 실패
- 완료된 전체 기준선의 재현성 확인: 실패 대체나 추가 reset 없이 수행할 수 없음

## API 영향

API 계약, route, request·response와 expected status를 변경하지 않았다. 기존 API를 smoke와 측정 대상으로만 호출했다.

## DB 영향

DB schema와 migration 변경은 없다. 승인된 QA subscription reset 뒤 seed 하나를 준비했으며, warm 측정 중단 후 추가 reset이나 상태 변경을 수행하지 않았다. QA 회원 외 데이터와 volume은 삭제하지 않았다.

## 보안 영향

`.env.local`의 실제 값은 process와 Docker Compose에만 전달했다. credential, 실제 ID, cookie, session ID, CSRF token, header, body와 raw log를 출력·문서화·커밋하지 않았다.

## 운영 영향

로컬 Docker Desktop stack만 기동·종료했다. 배포, CI, 외부 service, dashboard, alert와 운영 설정에는 영향이 없다.

## 성능 영향

제품이나 실행 설정을 변경하지 않았으므로 성능 변경은 없다. Cold start 3회의 부분 기준값만 얻었고 warm HTTP·container 기준선은 미완료다.

## 적용 방법

완료된 결과가 아니므로 regression 비교나 최적화 근거로 적용하지 않는다. Tech Lead는 `docs/performance/PERF-004-local-baseline-results.md`의 cold 결과와 실패 경계를 검토하고, 초기 상태 재구성과 새 실행을 승인할지 결정한다.

## 남은 위험

- HTTP, 인증 lifecycle, 상태 변경과 warm container 기준선이 없다.
- 현재 QA subscription state는 reset 직후 상태가 아니므로 승인 없이 재실행할 수 없다.
- 임시 process-memory 계측 래퍼는 저장소 산출물이 아니며 이번 실패 뒤 신뢰 가능한 실행 도구로 검증되지 않았다.
- Cold start 3회는 운영 capacity, SLO, threshold 또는 병목을 설명하지 않는다.

## 다음 작업

Tech Lead가 `docs/performance/PERF-005-local-baseline-rerun-decision-request.md`의 A·B·C 중 하나를 승인한다. 재실행하려면 승인된 초기 QA 상태, 실패 기록 보존 방식과 계측 래퍼 사전검증 경계를 명시해야 한다.

## Git 결과

- 기준 `main`: `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d`
- 작업 브랜치: `ops/sre`
- PERF-004 수행 당시에는 필수 측정 미완료로 commit과 push를 수행하지 않음
- 세 부분 결과 문서는 PERF-005 결정 요청과 함께 사실 기반 증거로 보존
- reset, rebase와 force push 없음

## PR 결과

- PERF-004 수행 당시에는 신규 PR을 생성하지 않음
- PERF-005에서 부분 결과 보존과 재실행 결정 요청을 main 대상 PR로 전달
- 자동 병합 없음
