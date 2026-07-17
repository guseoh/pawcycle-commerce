# PERF-006 Tech Lead → Platform/SRE 인수인계

## 전달 목적

PERF-005 선택지 B의 사용자 승인을 전달하고, Platform/SRE가 PERF-007에서 QA reset부터 cold와 warm 전체를 승인 순서로 한 번 다시 측정하도록 실행 경계와 중단 조건을 고정한다.

## 대상 역할과 다음 작업

- 대상 역할: Platform/SRE
- 다음 작업 ID: `PERF-007`
- 실행 게이트: `Open for PERF-007 Full Local Baseline Rerun`

## 입력 문서

- `AGENTS.md`
- `infra/AGENTS.md`
- `docs/roles/platform-sre.md`
- `.agents/skills/platform-sre/SKILL.md`
- `docs/performance/PERF-006-local-baseline-rerun-approval.md`
- `docs/performance/PERF-005-local-baseline-rerun-decision-request.md`
- `docs/performance/PERF-004-local-baseline-results.md`
- `docs/reports/PERF-004/sre-report.md`
- `docs/performance/PERF-003-local-baseline-detail-approval.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

## 확정된 결정

- PERF-005 선택지 `B. Cold와 warm 전체 재실행`은 `Approved by PERF-006`다.
- PERF-007은 cold와 warm 전체를 하나의 새 run으로 수행한다.
- PERF-004 cold 3회는 순서 이탈이 있는 부분 관측으로만 보존하고 PERF-007 결과와 합산하지 않는다.
- PERF-004 warm HTTP·container 결과는 `미완료·사용 불가`로 유지한다.
- PERF-002의 reset·seed·cardinality와 PERF-003의 warm-up·container sampling 조건은 변경하지 않는다.

## 사용 가능한 결과

- PERF-004 기준 commit과 환경 fingerprint
- PERF-004의 순서 이탈 cold 부분 관측과 warm 사용 불가 경계
- PERF-005의 래퍼 결함 유형·수정 방향 순수 객체 검증 결과
- PERF-006 선택지 B 승인 원본과 단일 전체 재실행 순서
- PERF-007의 사전검증·중단·증거 보존 경계

## 측정 시작 전 필수 게이트

1. 최신 `origin/main`을 fetch하고 실제 SHA를 PERF-007 기준 commit으로 고정한다.
2. `git status --short --branch`로 작업 트리가 깨끗하고 `HEAD`가 고정한 `origin/main` SHA와 일치하는지 확인한다.
3. OS·runtime, CPU·memory, Docker·Compose·PowerShell 버전, Docker Desktop 자원, container image ID, 전원 모드와 background workload를 기록한다.
4. PERF-004 기준 `a7ea1ec3447bc0ca34b20f5a7827a7882eec2f0d` 이후 제품 코드 또는 실행 설정 변경 여부를 확인한다.
5. 제품 코드 또는 실행 설정 변경이 있으면 측정을 시작하지 않고 사용자 결정을 요청한다.
6. 실제 재실행용 수정 래퍼 아티팩트의 로컬 경로 또는 이름, 적용 가능한 version·commit과 SHA-256을 기록한다.
7. 같은 식별자의 아티팩트로 request parameter 구성과 container stats parsing을 상태 변경 없는 로컬 입력으로 검증하고 검증 명령·비민감 입력 설명과 결과를 기록한다.
8. 실제 측정 직전에 아티팩트 SHA-256이 검증한 값과 같은지 다시 확인한다.
9. 식별자 불일치, 아티팩트 재현 실패 또는 검증 실패 시 endpoint 호출, QA reset·seed와 실제 측정을 시작하지 않는다.

## 승인된 실행 순서

필수 게이트 통과 뒤 다음 순서를 한 번만 실행한다.

1. QA 회원 구독 reset 1회
2. Reset 설정 즉시 `false` 복원
3. 측정 제외 seed 구독 1건 생성·목록·상세 확인
4. Volume을 삭제하지 않는 cold start 3회
5. 네 service `healthy` 확인
6. 측정 제외 30초 안정화
7. 다섯 읽기 route별 warm-up 5회와 해당 route 측정
8. 별도 warm-up 없는 인증 lifecycle 측정
9. 별도 warm-up 없는 구독 상태 변경 측정

Cold start는 volume을 삭제하지 않는 `down`과 `up --detach --wait --wait-timeout 180`을 사용한다. PERF-002의 반복 횟수, concurrency 1, 통계와 실패 처리, PERF-003의 event-based sampling·nullable 규칙을 그대로 적용한다.

## 인수 조건과 추적성

| 승인 결정 | PERF-007 적용 |
| --- | --- |
| 선택지 B | QA reset부터 cold·warm 전체를 하나의 새 run으로 실행 |
| 기준 commit | PERF-007 시작 시 최신 `origin/main` SHA 기록 |
| Git 상태 | 상태 변경·래퍼 검증 전에 clean worktree와 `HEAD == 고정한 origin/main SHA` 확인 |
| 환경 비교 | 환경 fingerprint 기록과 PERF-004 이후 제품·실행 설정 변경 검사 |
| 래퍼 게이트 | 실제 수정 아티팩트의 경로·이름, version·commit, SHA-256과 검증 명령·비민감 입력 설명 기록, 동일 SHA의 상태 변경 없는 request parameter·stats parsing 검증 |
| Cold 보존 | PERF-004 값을 부분 관측으로만 보존하고 새 결과와 합산 금지 |
| 실패 경계 | 상태 변경 이후 임의 reset·재재실행 없이 중단 |
| 승인 조건 | PERF-002·003 조건을 변경하지 않음 |

## 다음 역할의 검증 포인트

- 래퍼 사전검증이 QA 상태 변경과 endpoint 측정 전에 완료됐는가
- 기록한 아티팩트 SHA-256이 사전검증과 실제 측정 직전에 동일한가
- 최신 기준 commit과 환경 fingerprint가 결과에 기록됐는가
- PERF-004 이후 제품 코드·실행 설정 변경 부재가 확인됐는가
- QA reset → reset `false` → seed → cold 3회 순서가 지켜졌는가
- 세 번째 cold 뒤 네 service healthy와 측정 제외 30초 안정화가 적용됐는가
- 다섯 route의 warm-up 5회와 측정, 인증 lifecycle, 구독 상태 변경이 승인 순서로 수행됐는가
- 각 warm 단위의 `before`·`mid`·`after` sampling과 nullable 규칙이 지켜졌는가
- PERF-004 부분 관측을 새 결과에 합산하지 않았는가
- 실패·timeout·unexpected status를 삭제하거나 대체하지 않았는가
- Raw record·log와 민감정보가 저장소에 남지 않는가

## QA 필요 여부

PERF-007에서 기존 QA fixture와 smoke를 측정 전 환경 검증으로 사용한다. 제품 동작 변경이 없으므로 PERF-006 승인 문서 자체의 별도 QA 산출물은 필요하지 않다.

## AI 리뷰에서 남은 확인 항목

PERF-006 PR의 CI와 CodeRabbit/Codex Review가 완료되고 유효한 미해결 지적이 없는 문서를 PERF-007 입력으로 사용한다. 유효한 미해결 리뷰가 있으면 측정을 시작하지 않는다.

## 추가 승인 필요 여부

PERF-007이 승인된 조건을 그대로 적용하는 데 추가 제품·기술 결정은 필요하지 않다. 제품 코드·실행 설정 변경, 새 도구, 고부하, SLO·threshold, 병목·최적화가 필요하면 별도 사용자 승인을 요청한다.

## 중단 조건

- 최신 `origin/main` 또는 역할 브랜치의 원격 관계가 불명확함
- PERF-004 이후 제품 코드 또는 실행 설정 변경이 확인됨
- 실제 수정 래퍼 아티팩트 재현 또는 상태 변경 없는 검증에 실패함
- 래퍼 식별자를 기록할 수 없거나 측정 직전 SHA-256이 사전검증 값과 다름
- PERF-002·003 승인 조건 변경이 필요함
- QA 회원 외 데이터 삭제, volume 삭제 또는 iteration별 reset이 필요함
- 상태 변경 이후 실패해 임의 reset 또는 재재실행이 필요함
- 제품 코드, 실행 설정, CI, dependency 또는 repository script 변경이 필요함
- Raw record·log나 민감정보 보존이 필요함
- SLO·threshold·고부하·신규 도구·병목·최적화를 함께 결정해야 함

## 남은 위험과 주의 사항

- 실제 수정 래퍼 아티팩트 검증은 PERF-007 시작 전까지 미완료다.
- 로컬 결과는 운영 capacity나 SLO가 아니다.
- Event-based 표본은 짧은 peak를 놓칠 수 있다.
- 상태 변경 이후 실패하면 이번 승인으로 임의 재재실행할 수 없다.

## 완료 조건

- 최신 기준 commit·환경 fingerprint와 제품·실행 설정 변경 부재를 기록한다.
- 래퍼 사전검증을 통과한 뒤 승인 순서의 단일 전체 재실행을 수행한다.
- PERF-004 부분 관측과 새 결과를 분리한다.
- 집계 Markdown과 재현 근거만 저장소에 남긴다.
- SLO·threshold·병목·최적화와 capacity를 확정하지 않는다.
