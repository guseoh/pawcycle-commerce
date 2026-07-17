# PERF-007 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: `PERF-007`
- 역할: Platform/SRE
- 작업 상태: `Stopped`
- 기준 branch: `ops/sre`
- 기준 commit: `9f8e2e661dc353cfc0a2c9925164bf373ccfd7a9`
- 기준선 사용 가능 여부: 사용 불가

## 작업 목적

PERF-002·003·006에서 승인된 조건으로 cold와 warm 전체 로컬 성능 기준선을 하나의 run에서 측정하고 집계 근거를 남기는 작업이다. Reset 준비 기동 중 승인된 중단 조건이 발생해 재실행하지 않고 사실 기반 중단 결과와 안전 정리 상태를 문서화했다.

## 입력 문서

- `docs/performance/PERF-006-local-baseline-rerun-approval.md`
- `docs/handoffs/PERF-006/tl-to-sre.md`
- `docs/performance/PERF-005-local-baseline-rerun-decision-request.md`
- `docs/performance/PERF-004-local-baseline-results.md`
- `docs/reports/PERF-004/sre-report.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

## 변경 범위

- 최신 `origin/main` 기준 commit과 새 `ops/sre` 준비
- PERF-004 이후 제품·실행 설정 변경 부재 확인
- 환경 fingerprint 수집
- Repository 밖 일회성 PowerShell 계측 래퍼 생성과 무상태 사전검증
- 래퍼 SHA와 Git 정합성 재확인
- 승인된 전체 run 1회 시작과 중단 결과 보존
- PERF-007 결과, 보고서와 Tech Lead 인수인계 작성

## 제외 범위

- 제품·테스트 코드, Compose, Nginx, Dockerfile, CI와 dependency 변경
- Repository benchmark script와 신규 도구 추가
- 실패 뒤 래퍼 수정 실행, reset·seed·cold·warm 재시도
- SLO, threshold, 병목, 최적화와 운영 capacity 결정
- Raw record·log와 민감정보 보존
- README 완료 상태 변경
- 자동 병합

## 래퍼 사전검증

- 아티팩트: `%LOCALAPPDATA%\Temp\pawcycle-perf007-wrapper.ps1`
- SHA-256: `486e9828a3afdd41feb14314fb684ac002cc9c2f0755d7a7815db2a9d7762a85`
- PowerShell 5.1 parser: 통과
- `SelfTest`: 통과
- 외부 호출: network 0회, Docker 0회
- 검증 입력: 비민감 순수 PowerShell request, canonical record, stats와 percentile 객체

GET body 미첨부, body method 분기, HTTP outcome 변환, container stats parsing, scalar factor, nullable 값, percentile과 unexpected status 집계를 확인했다. 다만 native command가 exit code 0에서도 stderr에 진행 메시지를 쓰는 경우는 검증 입력에 포함되지 않았다.

## 실제 실행 명령

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File <temp-wrapper> -Mode Run -RepoRoot <repository> -ResultPath <os-temp-aggregate> -BaselineCommit 9f8e2e661dc353cfc0a2c9925164bf373ccfd7a9
```

Run은 한 번만 시작했고 다시 실행하지 않았다.

## 측정 중단 결과

- 실행 구간: `2026-07-17T12:46:50.4934243Z` ~ `2026-07-17T12:47:18.1779597Z`
- 완료 단계: PERF-004 volume 보존 `down` 확인
- 중단 단계: `reset_preparation_start`
- 직접 관측: `docker compose up`의 network 생성 진행 메시지가 PowerShell terminating error로 포착됨
- 원인 분류: 계측 래퍼의 PowerShell native stderr·반환 처리 결함
- 준비 기동 healthy 확인: 미완료
- QA reset·seed: 미시작
- Cold·warm·container 표본: 0개
- Product endpoint 호출: 0회

제품 또는 Docker 환경 결함으로 확정할 근거는 없다. 준비 기동 실패 중단 조건에 따라 측정을 시작하지 않았다.

## 실패 후 수행한 안전 조치

- Child process의 reset 환경을 `false`로 설정
- `.env.local` reset 값이 `false`임을 별도 확인
- Volume 삭제 없는 Compose 종료 시도
- 사후 `docker compose ps -a` 빈 출력으로 stack down 재확인
- Named MySQL volume 존재 확인
- Raw request record가 파일에 기록되지 않았음을 확인
- 임의 reset, seed, endpoint 호출과 run 재실행 미수행

래퍼의 cleanup success 플래그는 같은 stderr 처리 결함으로 `false`였으나 실제 Compose 상태는 별도 조회 결과를 근거로 판단했다.

## 실행한 검증

- 최초 `git status --porcelain` 빈 출력
- 최초 `git rev-parse HEAD`·`git rev-parse origin/main`·고정 기준 commit 일치
- PERF-004 이후 변경이 문서에만 제한됨을 확인
- Docker Compose config와 QA 환경 변수 존재·reset `false` 확인
- PowerShell 5.1 parser와 래퍼 `SelfTest` 통과
- 래퍼 SHA-256 재확인
- 사전검증 직후 `git status --porcelain` 빈 출력과 두 Git SHA 재확인
- PERF-007 Run 1회 및 aggregate 상태 `Stopped` 확인
- 사후 stack down, reset `false`, named volume 보존 확인
- `py -3 scripts/validate-task-artifacts.py --task-id PERF-007`: 통과
- 세 PERF-007 문서의 trailing whitespace·마지막 newline 검사: 통과
- `git diff --check`: 통과
- 실제 식별자·email과 credential, authorization, cookie, session 할당 패턴 검사: 통과
- `scripts/validate-commit-message.sh --message 'docs(sre): PERF-007 로컬 성능 기준선 측정'`: 통과

## 실행하지 못한 검증과 이유

- Reset 준비 기동의 네 service healthy 확인: 래퍼 native stderr 처리 결함으로 중단
- 기존 QA fixture와 인증 정보 smoke: 준비 기동 전 중단
- Seed 목록·상세 확인: QA reset 전 중단
- Cold start 3회와 service health convergence: seed 전 중단
- 다섯 읽기 route, 인증 lifecycle, 상태 변경과 container sampling: cold 전 중단
- HTTP·container aggregate 검증: 표본 0개
- 최신 PR CI와 AI 리뷰: PR 생성 뒤 확인 예정

## 충족한 승인 조건

- 최신 `origin/main` SHA 고정과 두 시점 Git 검사
- PERF-004 이후 제품 코드·실행 설정 변경 부재 확인
- Repository 밖 래퍼 생성, SHA-256 기록과 상태 변경 없는 검증
- Reset 준비 전 volume 보존 down 확인
- 준비 기동 실패 시 QA reset·seed·측정을 시작하지 않는 중단 경계 준수
- 실패 표본 대체, 임의 reset과 재재실행 금지 준수
- Raw record와 민감정보 미보존

## DB 영향

DB schema와 migration 변경은 없다. QA reset과 seed가 시작되지 않았고 product endpoint를 호출하지 않았다. Named MySQL volume은 삭제하지 않았다.

## 보안 영향

Credential, 실제 ID, cookie, session, CSRF token, authorization header와 request·response body를 문서나 저장소에 기록하지 않았다. 래퍼는 credential을 하드코딩하지 않고 실행 시 기존 로컬 환경에서만 읽도록 구성했다.

## 운영 영향

Reset 준비를 위한 Docker Compose 기동이 시작됐으나 healthy 확인 전에 래퍼가 중단됐다. 사후 stack은 volume 보존 down 상태다. 배포·운영 환경과 설정에는 영향이 없다.

## 위험과 제한

- PERF-007은 완전한 cold·warm 기준선을 제공하지 않는다.
- 사전검증이 native stderr 정상 출력 경로를 포함하지 않아 실행 래퍼 결함을 사전에 차단하지 못했다.
- PERF-004 부분 관측은 PERF-007과 합산할 수 없다.
- 다음 재실행은 새 사용자 승인과 새 작업 ID 없이는 시작할 수 없다.
- SLO, threshold, 병목, 최적화와 capacity는 계속 미확정이다.

## 다음 작업

Tech Lead는 PERF-007 중단 결과를 검토하고, native command stderr·exit code 분리와 해당 경로의 무상태 사전검증을 포함한 새 전체 run을 승인할지 결정한다. 승인 전 reset, seed와 실제 측정을 시작하지 않는다.

## Git 결과

- 작업 branch: `ops/sre`
- 변경 파일: PERF-007 결과·보고서·인수인계 3개
- Commit message: `docs(sre): PERF-007 로컬 성능 기준선 측정`
- Commit SHA: 같은 commit에 자기 SHA를 기록할 수 없어 원격 이력과 완료 보고에서 확인
- Push: commit 후 `origin/ops/sre`로 수행 예정
- Reset·rebase·force push: 미수행

## PR 결과

- 대상: `main`
- 제목: `docs(sre): PERF-007 로컬 성능 기준선 측정`
- 상태: commit·push 뒤 생성하고 원격 상태를 완료 보고에서 확인
- 자동 병합: 미수행
