# PERF-008 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: `PERF-008`
- 역할: Platform/SRE
- 최종 작업 상태: `Stopped`
- 기준 branch: `ops/sre`
- 기준 commit: `306d35cd5dd7818e662fa773ff7968c6c3fabc84`
- 로컬 기준선 사용 가능 여부: 사용 불가
- 기능군 상태: 로컬 기준선 측정 최종 종료

## 작업 목적

PERF-007의 Windows PowerShell 5.1 native stderr 처리 결함을 일회성 래퍼에서 수정하고 PERF-002·003·006 조건으로 cold와 warm 전체 기준선을 최종 1회 측정하는 작업이다. 상태 변경 없는 SelfTest에서 별도 PowerShell parameter binding 결함이 발생해 승인 조건에 따라 Docker와 QA 상태 변경 전에 중단했다.

## 입력 문서

- `docs/performance/PERF-007-local-baseline-results.md`
- `docs/reports/PERF-007/sre-report.md`
- `docs/handoffs/PERF-007/sre-to-tl.md`
- `docs/performance/PERF-006-local-baseline-rerun-approval.md`
- `docs/handoffs/PERF-006/tl-to-sre.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

## 변경 범위

- PR #54 병합과 최신 `origin/main` 반영 확인
- 병합된 이전 역할 브랜치의 tree 동일성 확인과 새 `ops/sre` 준비
- 기준 commit·최초 Git 게이트와 환경 fingerprint 기록
- PERF-007 이후 제품·실행 설정 변경 부재 확인
- Repository 밖 PERF-008 일회성 래퍼 생성
- Native stdout·stderr·exit code 분리와 무상태 SelfTest 실행
- 중단 결과, 안전 상태와 Tech Lead 인수인계 문서화

## 제외 범위

- SelfTest 실패 뒤 래퍼 수정·재검증
- Docker 준비 기동, smoke와 endpoint 호출
- QA reset·seed·warm-up
- Cold·warm 성능 측정
- 제품·테스트 코드, Compose, Nginx, Dockerfile, CI와 dependency 변경
- Repository benchmark script와 신규 도구 추가
- SLO, threshold, 병목, 최적화와 운영 capacity 결정
- Raw record·raw stdout·stderr와 민감정보 보존
- README 완료 상태 변경
- PERF-009 추가 측정 요청과 자동 병합

## 기준 commit과 환경

최초 검사에서 작업 트리가 clean이고 `HEAD == origin/main == 306d35cd5dd7818e662fa773ff7968c6c3fabc84`임을 확인했다. PERF-007 기준 뒤 변경은 PERF-007 결과·보고서·인수인계와 PR 학습 기록에만 한정됐다. OS, CPU·memory, PowerShell, Docker Engine·Desktop·Compose, Docker 자원, 네 image ID, 전원 모드와 background workload를 결과 문서에 기록했다.

## 래퍼 변경 방향과 SelfTest

- 래퍼: `%LOCALAPPDATA%\Temp\pawcycle-perf008-wrapper.ps1`
- SHA-256: `ed67151588754935d7c8f421d44cf8a74a41b7664ec9600d507a02da226811d1`
- Native 구현: `System.Diagnostics.Process`
- Stream 소비: stdout·stderr `ReadToEndAsync()` 동시 시작
- 성공 판정: timeout과 process exit code
- `exit 0 + stderr`: 성공으로 검증
- Non-zero exit: 실패로 검증
- Native stderr의 PowerShell terminating error 변환: 발생하지 않음

Native 6경로와 GET/body parameter 검증은 통과했다. HTTP canonical record 4종을 배열에 만드는 SelfTest에서 쉼표가 다음 함수 호출을 같은 명령 인자로 결합해 `Cohort` 중복 parameter binding 오류가 발생했다. 전체 SelfTest가 실패했으므로 래퍼 게이트는 통과하지 않았다.

## 실제 실행 명령

실행한 명령은 상태 변경 없는 다음 SelfTest뿐이다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File <temp-wrapper> -Mode SelfTest
```

실제 `Run` mode, Docker Compose 기동과 product endpoint 명령은 실행하지 않았다.

## 중단 결과

- 중단 확인 UTC: `2026-07-17T13:31:49.5626288Z`
- 중단 단계: 래퍼 `SelfTest`
- 직접 근거: `Cannot bind parameter because parameter 'Cohort' is specified more than once.`
- 원인 분류: PowerShell 함수·매개변수 구성 결함
- 상태 변경 전후: Docker·QA 상태 변경 전
- 완료 측정 표본: 0개
- 사용할 수 있는 성능 결과: 없음

PERF-007 native stderr 결함은 핵심 child process 경로에서 재현되지 않았지만, 전체 래퍼 SelfTest 실패 조건 때문에 실제 측정 승인을 충족하지 못했다.

## 안전 조치

- 래퍼 수정과 SelfTest 재실행 미수행
- 두 번째 Git·SHA 게이트로 진행하지 않음
- Docker 준비 기동, smoke, endpoint 호출 미수행
- QA reset·seed·warm-up 미수행
- `.env.local` reset `false` 확인
- Docker stack volume 보존 down 확인
- Named MySQL volume 존재 확인
- Temp aggregate 미생성 확인
- Temp 래퍼는 증거 추출 뒤 삭제

## 실행한 검증

- PR #54 `MERGED`, 병합 commit·head tree 동일성 확인
- 최신 `main` fast-forward와 clean `ops/sre` 재생성
- 최초 `git status --porcelain` 빈 출력
- 최초 `git rev-parse HEAD`·`git rev-parse origin/main`·고정 기준 commit 일치
- PERF-007 이후 변경 경로가 문서에만 제한됨을 확인
- Docker Compose config 통과
- Stack down, reset `false`, 필수 환경 변수 존재 확인
- 환경·container image fingerprint 수집
- PowerShell parser 통과
- Native stdout·stderr·exit code SelfTest 6경로 통과
- GET body 미첨부와 body method 분기 통과
- `py -3 scripts/validate-task-artifacts.py --task-id PERF-008`: 통과
- 세 PERF-008 문서의 trailing whitespace·마지막 newline 검사: 통과
- 실제 식별자·email과 credential, authorization, cookie, session 할당 패턴 검사: 통과
- 변경 파일 범위 검사: 통과
- `git diff --check`: 통과
- `scripts/validate-commit-message.sh --message 'docs(sre): PERF-008 최종 로컬 성능 기준선 측정'`: 통과

## 실행하지 못한 검증과 이유

- HTTP canonical record 4종 이후 SelfTest: parameter binding 오류로 중단
- Container stats, scalar factor, nullable 값, percentile과 status mismatch SelfTest: 앞선 SelfTest 중단
- SelfTest 이후 래퍼 SHA·Git 재검사: 전체 SelfTest 실패로 게이트 미도달
- Reset 준비 기동 exit code·stderr와 네 service healthy: SelfTest 실패
- QA fixture, reset, seed: 준비 기동 전 중단
- Cold start 3회, warm HTTP·container 전체: 상태 변경 전 중단
- 최신 PR CI와 AI 리뷰: PR 생성 뒤 확인 예정

## DB 영향

DB schema와 migration 변경은 없다. QA reset·seed·상태 변경 endpoint를 실행하지 않았고 named volume을 삭제하지 않았다.

## 보안 영향

실제 credential, ID, cookie, session, CSRF token, authorization header, query string과 body를 문서나 저장소에 기록하지 않았다. Native SelfTest는 비민감 child process만 사용했으며 raw stdout·stderr를 파일로 보존하지 않았다.

## 운영 영향

Docker stack을 기동하지 않았다. 최종 상태는 volume 보존 down이며 배포·운영 환경과 설정 변경은 없다. PERF-008 종료 뒤 로컬 기준선 측정 기능군에서 배포·운영 준비 기능군으로 전환한다.

## 위험과 제한

- 완전한 일회성 로컬 성능 기준선이 없다.
- PERF-004·007·008 결과를 합산할 수 없다.
- Native process 핵심 경로 통과는 전체 계측 래퍼의 실행 가능성을 의미하지 않는다.
- SLO, threshold, 병목, 최적화, capacity와 운영 성능은 미확정이다.
- 다음 배포·운영 준비는 로컬 기준선 부재를 명시적 위험으로 다뤄야 한다.

## 다음 작업

추가 PERF 재실행을 만들지 않는다. Tech Lead는 `Stopped` 결과와 로컬 기준선 부재를 인수하고 별도 승인 작업에서 실제 배포·운영 준비 범위를 결정한다.

## Git 결과

- 작업 branch: `ops/sre`
- 변경 파일: PERF-008 결과·보고서·인수인계 3개
- Commit message: `docs(sre): PERF-008 최종 로컬 성능 기준선 측정`
- Commit SHA: 같은 commit에 자기 SHA를 기록할 수 없어 원격 이력과 완료 보고에서 확인
- Push: commit 뒤 새 `origin/ops/sre`로 수행
- Reset·rebase·force push: 미수행

## PR 결과

- 대상: `main`
- 제목: `docs(sre): PERF-008 최종 로컬 성능 기준선 측정`
- 상태: commit·push 뒤 생성하고 원격 상태를 완료 보고에서 확인
- 자동 병합: 미수행
