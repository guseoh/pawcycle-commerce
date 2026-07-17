# PERF-005 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: `PERF-005`
- 역할: Platform/SRE
- 기준 브랜치: `main`
- 작업 브랜치: `ops/sre`
- 기준 commit: `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d`
- 작업 상태: `Decision Request Prepared`

## 작업 목적

PERF-004 계측 래퍼 오류를 실제 로컬 증거로 진단하고 cold 부분 결과와 사용할 수 없는 warm 상태를 보존하며, QA 초기 상태와 재실행 경계에 관한 사용자/Tech Lead 결정 요청을 작성한다.

## 입력 문서

- `AGENTS.md`
- `infra/AGENTS.md`
- `docs/roles/platform-sre.md`
- `.agents/skills/platform-sre/SKILL.md`
- `docs/performance/PERF-004-local-baseline-results.md`
- `docs/reports/PERF-004/sre-report.md`
- `docs/handoffs/PERF-004/sre-to-tl.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`

## 승인 입력

- PERF-004 기준 commit과 seed-before-cold 순서 이탈이 있는 cold start 3회 부분 관측
- Warm 측정 미완료, seed와 집계 제외 warm-up 실행 사실
- PERF-004 세 untracked 문서를 삭제·초기화하지 않고 보존
- 실제 성능 측정, reset, seed, warm-up과 endpoint 호출 금지
- PERF-002 reset·seed·cardinality와 PERF-003 sampling·warm-up 조건 유지

## 변경 범위

- PERF-004 세 문서의 해시·내용 보존 확인
- 실제 예외 메시지, 실패 함수·표현식과 상태 경계 정리
- 현재 session과 PSReadLine history의 관련 정의 존재 여부 확인
- 외부 요청 없는 순수 PowerShell 객체 최소 재현
- PERF-004 부분 결과 문서 사후 진단 보완
- PERF-005 A·B·C 재실행 결정 요청, 보고서와 Tech Lead 인수인계 작성
- PR #52에서 확인된 review thread 6개의 승인 문서·실행 증거 기반 반영

## 변경하지 않은 범위

- Cold start와 warm 성능 측정
- Endpoint 호출, QA reset·seed·warm-up
- 제품 코드와 테스트
- Compose, Nginx, Dockerfile, CI와 dependency
- Repository benchmark script와 신규 도구
- SLO, threshold, 병목, 최적화와 capacity
- Raw record·log와 민감정보

## 증거와 원인 분류

| 대상 | 증거 | 분류 |
| --- | --- | --- |
| Warm wrapper 1 | `System.Net.ProtocolViolationException: Cannot send a content-body with this verb-type.` 및 `[string]$Body = $null`의 빈 문자열 바인딩 재현 | PowerShell 함수·매개변수·HTTP parameter 구성 결함 |
| Warm wrapper 2 | `Convert-SizeToBytes`, `$number * $factor`, `[System.Object[]]` `op_Multiply` 오류 및 배열 곱셈 재현 | Container stats parsing 결함 |
| 제품 endpoint | 측정 request 전에 client harness가 실패 | 제품 결함 근거 없음 |
| Docker environment | 중단 당시 네 service healthy, restart count 0 | 환경·도구 실패 근거 없음 |

원본 래퍼는 process memory 실행 뒤 보존되지 않았고 current session과 지속 history에도 관련 정의가 없었다. 원본 파일·행 번호와 factor 배열을 만든 정확한 switch 분기 조합은 증거 부족으로 미확정이다.

## 래퍼 검증 상태

- 완료 — 순수 객체 최소 재현: `[string]$Body = $null`은 `System.String`, `IsNull=False`, `Length=0`이며 null 비교 사용 시 body 첨부 경로
- 완료 — 순수 객체 최소 재현: 두 factor 값은 `System.Object[]`, count 2이며 `$number * $factor`에서 PERF-004와 같은 `op_Multiply` RuntimeException
- 완료 — 수정 방향 객체 검증: null 또는 empty body 미첨부, non-empty JSON body만 첨부
- 완료 — 수정 방향 객체 검증: exact unit lookup에서 scalar factor count 1과 byte 계산
- 미완료 — 실제 재실행용 수정 래퍼 아티팩트의 request parameter 구성과 container stats parsing 검증

완료된 검증은 PowerShell object만 사용했으며 HTTP, Docker, DB와 실제 ID를 사용하지 않았다. 실제 수정 래퍼 아티팩트 검증은 별도 재실행 전 게이트다. 상태 변경 없는 로컬 입력 검증에 실패하거나 아티팩트를 재현할 수 없으면 측정을 시작하지 않는다. 래퍼 원본을 repository script로 추가하지 않았다.

## 주요 결과

- PERF-004 cold start 3회와 환경 fingerprint를 순서 이탈이 있는 사실 기반 부분 관측으로 유지했다.
- Warm HTTP·container 결과는 `미완료·사용 불가`로 유지했다.
- QA seed와 첫 route warm-up으로 최초 state가 소비된 경계를 명시했다.
- A를 별도 순서 이탈 수용이 필요한 부분 결과 연속안으로, B를 승인 순서의 완전 재실행 권고안으로, C를 측정 종료안으로 정리했다.
- A·B에 volume 보존 기동, 네 service healthy 확인과 warm 직전 측정 제외 30초 안정화를 명시했다.
- PERF-005 실행 게이트를 `Blocked Pending Rerun Decision`으로 설정했다.

## 실행한 검증

- `git status --short --branch`: PERF-004 세 문서만 untracked인 상태 확인
- `git fetch origin --prune`: 통과
- `HEAD == origin/main == a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d`: 확인
- 작업 시작 당시 로컬 remote-tracking ref `origin/ops/sre`: 없음 확인
- PERF-004 세 문서 SHA-256 보존 확인
- current PowerShell session·PSReadLine history 관련 정의 검색: 관련 정의 없음 확인
- 외부 요청 없는 PowerShell body binding·array multiplication 최소 재현: 통과
- 외부 요청 없는 body 미첨부 조건·scalar factor 수정 방향 객체 검증: 통과
- `py -3 scripts/validate-task-artifacts.py --task-id PERF-004`: 통과
- `py -3 scripts/validate-task-artifacts.py --task-id PERF-005`: 통과
- 여섯 문서의 `git diff --check --no-index`: 통과
- 여섯 문서의 실제 local credential 값·일반 secret 패턴 검사: 통과
- `scripts/validate-commit-message.sh --message 'docs(sre): PERF-005 기준선 재실행 결정 요청'`: Git Bash에서 통과

## 실행하지 못한 검증과 이유

- 원본 래퍼 파일·행 번호 확인: process-memory 실행 뒤 함수 본문과 history가 보존되지 않음
- factor 배열을 만든 정확한 switch 분기 확인: 원본 함수 정의가 없어 증거 부족
- 실제 재실행용 수정 래퍼 아티팩트의 request parameter 구성·container stats parsing 검증: 이번 작업의 네트워크·Docker 실행 제외 범위이며 재실행 전 선행 게이트
- Cold·warm 재측정: 사용자/Tech Lead 결정 전 실행 금지

## API 영향

Endpoint를 호출하거나 API 계약을 변경하지 않았다.

## DB 영향

QA subscription을 조회·reset·seed하거나 다른 DB 상태를 변경하지 않았다. PERF-004 volume 보존 종료 상태를 그대로 유지했다.

## 보안 영향

Raw record, log, 실제 ID, credential, cookie, session ID, CSRF token, header와 body를 읽거나 저장하지 않았다. 전체 PowerShell history 대신 알려진 함수명·예외 문자열만 필터링했다.

## 운영 영향

Docker stack을 기동하지 않았고 실행 설정, 배포와 CI를 변경하지 않았다.

## 성능 영향

성능 측정을 실행하거나 제품·실행 설정을 변경하지 않았다. Cold는 seed-before-cold 순서 이탈이 있는 부분 관측으로, warm은 미완료·사용 불가로 보존했다.

## 적용 방법

사용자/Tech Lead가 `docs/performance/PERF-005-local-baseline-rerun-decision-request.md`의 A·B·C 중 하나를 승인한다. Platform/SRE 권고안은 승인 순서를 지키는 B다. 승인과 실제 수정 래퍼 아티팩트의 상태 변경 없는 검증 전에는 어떤 재실행도 시작하지 않는다.

## 남은 위험

- 원본 임시 래퍼 전체가 없어 수정본과 원본의 완전한 line-by-line 비교는 불가능하다.
- A는 환경 동일성과 별개로 seed-before-cold 순서 이탈 때문에 승인된 하나의 완전한 기준선을 제공하지 못한다.
- 재실행 중 상태 변경 이후 실패하면 다시 중단해야 한다.
- C 선택 시 warm 기준선은 계속 없다.

## 다음 작업

Tech Lead가 A·B·C 중 하나와 추가 조건을 결정한다. 권고안 B 또는 별도 순서 이탈 수용이 필요한 A를 승인하면 별도 작업 ID에서 실제 수정 래퍼 아티팩트를 상태 변경 없는 로컬 입력으로 먼저 검증한다. 실패하거나 아티팩트를 재현할 수 없으면 측정을 시작하지 않는다.

## Git 결과

- 작업 브랜치: `ops/sre`
- 작업 브랜치 생성·push 뒤 PERF-004 부분 결과, PERF-005 결정 요청, PR 결과 기록과 이전 리뷰 반영 commit은 PR #52 원격 이력에서 확인
- 이 정합화 수정 직전 확인한 PR #52 head: `64b044140b7a64a347ae7f50e7bd4f88c5174b70`
- 현재 리뷰 반영 commit은 자기 SHA를 같은 commit 안에 기록할 수 없으므로 PR #52의 최신 head와 원격 이력에서 확인
- reset, rebase와 force push 없음

## PR 결과

- PR: `#52`
- 상태: Open, Ready for review
- 제목: `docs(sre): PERF-005 기준선 재실행 결정 요청`
- base/head: `main` ← `ops/sre`
- 생성 직후 Repository Validation의 `Commit and PR conventions`와 Collaboration Notification 성공 확인
- 후속 commit 뒤 최신 CI와 AI review 상태는 PR #52 원격 상태와 완료 보고에서 확인
- 자동 병합하지 않음
