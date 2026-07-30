# OPS-021 Platform/SRE 작업 보고서

> 이 문서는 OPS-021 저장소 준비 당시의 역사적 보고서다. 이후 사용자가 직접 수행한 Production Control 채택, 실제 Application rollback과 재배포 결과는 `docs/reports/OPS-021/production-execution-report.md`를 따른다.

## 작업 정보

- 작업 ID: OPS-021
- 작업 등급: 고위험
- 역할: Platform/SRE
- 작업 브랜치: `ops/sre`
- 대상 브랜치: `main`

## 작업 목적

실행 중인 Backend·Frontend Application Release 상태와 Production Control 계약 상태를 분리하고, 보조 운영 파일 변경 때문에 정상 Application 배포가 과도하게 차단되는 문제를 해결한다.

동시에 저장된 계약 상태와 실제 Control checkout이 달라지거나 Control worktree가 직접 수정된 경우에는 Container 변경 전에 실패하도록 한다.

## 입력 문서

- `docs/runbook/OPS-010-production-single-release.md`
- `infra/production/release-common.sh`
- `infra/production/deploy.sh`
- `infra/production/rollback.sh`
- `infra/production/test-production-scripts.sh`
- `infra/production/validate-production-contracts.py`
- PR #72의 CodeRabbit 리뷰와 ChatGPT 독립 검토 결과

## 승인 입력

- 사용자가 OPS-021 저장소 준비와 PR #72 수정을 요청했다.
- 실제 Production Control 전환, Application 배포, 운영 DB 연결, 회원 생성과 인증 Session Smoke는 이번 승인 범위에 포함하지 않았다.

## 명시적 승인 근거 (고위험 필수)

현재 사용자 지시인 “PR #72 수정”을 저장소 변경 승인으로 사용했다. 실제 Production 실행은 별도 고위험 승인 전까지 금지한다.

## 변경 범위

- `current-sha`, `previous-sha` Application Release 상태와 `contract-sha`, `previous-contract-sha` Control 상태 분리
- Release 호환성 비교 경로를 Compose와 HTTP·HTTPS Nginx 계약으로 한정
- 현재 Control HEAD와 지정된 Control 파일의 clean worktree 검증
- 최초 상태 도입 시 기존 승인 Control 기준과 현재 Control HEAD의 Release 계약 비교
- 저장된 Control SHA와 현재 HEAD가 다를 때 현재 HEAD의 명시적 승인 요구
- Control 계약 채택 전에 현재 실행 Release의 image identity·digest·health·HTTP·HTTPS smoke 검증
- Rollback 전에 현재 Control HEAD와 `contract-sha`의 정확한 일치 검증
- 계약 상태 누락, Control drift, 불결한 worktree와 비호환 전환 회귀 테스트 보강
- OPS-010 Runbook 정합화
- OPS-021 고위험 작업 보고서 추가

## 변경하지 않은 범위

- Production 배포 또는 Rollback 실행
- AWS 리소스 생성·수정·삭제
- 운영 DB·Schema·Flyway·데이터 변경
- Secret·Credential 입력 또는 변경
- Production `contract-sha` 기록
- OPS-020 인증 Smoke 회원 생성
- OPS-018 로그인·Session·Logout Smoke
- Blue/Green, 자동 배포 또는 완전한 Release bundle 재설계

## 인수 조건 매핑

- Application Release 상태와 Control 상태가 분리된다.
- Control worktree가 불결하면 Docker 호출 전에 실패한다.
- 최초 `contract-sha`가 없을 때는 사용자가 승인한 기존 운영 기준 SHA와 현재 clean Control HEAD의 Release 계약이 같아야 한다.
- 기존 `contract-sha`와 현재 Control HEAD가 다르면 현재 HEAD를 정확히 명시하고 기존 승인 Control과 Release 계약이 같아야 한다.
- 검증 성공 뒤 `contract-sha`에는 과거 기준이 아니라 현재 clean Control HEAD가 기록된다.
- 대상 Application Release의 Release 계약이 현재 승인 Control과 다르면 활성화를 거부한다.
- Rollback은 `previous-sha`·`previous-contract-sha` 빠른 경로 또는 명시적 Release 계약 비교를 통과해야 한다.
- MySQL volume·Schema·데이터는 변경하거나 삭제하지 않는다.

## 주요 결과

- `contract-sha`는 검증을 통과한 현재 clean Control HEAD를 기록한다.
- 최초 도입에서는 기존 승인 Control 기준 SHA를 입력으로 받아 현재 Control HEAD와 호환성을 검증한다.
- 저장된 `contract-sha`와 현재 Control HEAD가 다른 상태를 자동 승인하지 않는다.
- Control Script 변경은 Application Release SHA와 직접 비교하지 않고 정확한 HEAD 승인과 clean worktree로 통제한다.
- Application Release 호환성은 실제 활성화 입력인 Compose와 Nginx 계약으로 판정한다.

## 변경 파일

- `docs/runbook/OPS-010-production-single-release.md`
- `docs/reports/OPS-021/sre-report.md`
- `infra/production/deploy.sh`
- `infra/production/release-common.sh`
- `infra/production/rollback.sh`
- `infra/production/test-production-scripts.sh`
- `infra/production/validate-production-contracts.py`

## 결정 상태

- 저장소 설계와 테스트 보강: 구현 완료, CI 재검증 진행 중
- PR #72 병합: 사용자 최종 승인 대기
- 실제 Production 적용: 미승인·미실행

## API 영향

API 요청·응답, HTTP 상태 코드, 인증·인가 동작 변경 없음.

## DB 영향

DB Schema, Flyway history, 운영 데이터와 MySQL volume 변경 없음.

## 보안 영향

- Control checkout 직접 수정과 승인되지 않은 HEAD 실행을 차단한다.
- 최초 상태 기록 전에 기존 승인 Control 기준과 현재 Control의 Release 계약을 비교한다.
- 상태 SHA 파일은 regular non-symlink, mode `600` 계약을 유지한다.
- Secret 값은 조회·출력·기록하지 않았다.

## 운영 영향

PR 병합만으로 Production 상태는 바뀌지 않는다. 실제 Control SHA 채택과 Application 배포는 별도 고위험 실행에서 적용 전후 증거와 중단 조건을 확인한다.

## 성능 영향

배포·Rollback 시작 시 제한된 Git status와 commit 간 세 파일 diff가 추가된다. 서비스 요청 처리 성능에는 영향이 없다.

## 실행한 검증

첫 구현 Commit 기준으로 다음 검증을 통과했다.

```text
PASS: shell syntax
PASS: python syntax
OPS-011 production script tests passed
PASS: production script tests (Linux container)
OPS-013 production backup and restore contracts validated
PASS: production contracts
git diff --check: PASS
```

리뷰 후 수정 Commit은 GitHub Repository Validation과 CodeRabbit 재검토를 통해 다시 검증한다.

## 적용 전 검증 (고위험 필수)

저장소 준비 전 Production read-only 확인에서 다음을 확인했다.

- 실행 중 Application `current-sha`
- 기존 승인 Control 기준 SHA와 현재 Control checkout
- Control worktree clean 상태
- 네 Production Container health
- 잔여 OPS-020 one-shot Container 부재
- Runtime·state directory root-only 권한
- 대상 Backend·Frontend image 존재
- 기존 기준과 대상의 핵심 Compose·Nginx 계약 동일성

## 적용 후 검증 (고위험 필수)

저장소 변경에 대해서만 Shell·Python 문법, Fake Git·Docker lifecycle, 정적 Production 계약과 whitespace를 검증한다. 실제 Production 적용 후 검증은 수행하지 않았으며 별도 실행 승인 후 기록한다.

## 독립 검증 (고위험 필수)

- CodeRabbit이 Runbook의 폐기된 설명과 누락 계약 테스트의 원인 검증을 지적했다.
- ChatGPT 독립 검토에서 Control HEAD drift, dirty worktree, 최초 기준선과 현재 Control 상태 의미 결함을 추가 식별했다.
- CodeRabbit의 기존 두 스레드는 수정 후 자동 해소됐다.
- Codex Review는 사용량 제한으로 실행되지 않았다.
- 수정 후 GitHub Actions와 CodeRabbit을 병합 Gate로 다시 사용한다.

## 실행하지 못한 검증과 이유

- 실제 Production Control 계약 채택: 별도 사용자 실행 승인 없음
- 대상 Application Release 배포·Rollback: 별도 사용자 실행 승인 없음
- OPS-020·OPS-018 운영 인증 Smoke: 선행 Production 배포 미완료
- Codex Review: 사용량 제한

## QA 필요 여부

별도 제품 QA 문서는 필요하지 않다. 운영 Script의 계약 변경이므로 Linux lifecycle 회귀 테스트, 정적 Validator, CI, AI 리뷰와 사용자·Tech Lead 검토를 사용한다.

## QA 문서 경로 또는 생략 사유

제품 화면·API·도메인 동작 변경이 없고 실제 Production 적용도 이번 PR에 포함하지 않는다. 따라서 별도 QA 문서를 만들지 않는다.

## AI 리뷰 반영 여부

CodeRabbit 지적과 ChatGPT 독립 검토 결과를 반영했다. 최신 Commit 기준 재검토 결과는 병합 전에 다시 확인한다.

## AI 리뷰 미반영 항목과 이유

- Codex Review: 사용량 제한으로 결과 없음
- 완전한 Application·Control Release bundle 재설계: 현재 OPS-018 진행에 필요한 최소 범위를 초과하므로 후속 결정으로 남김

## 적용 방법

PR 병합 후 최신 승인 Control SHA를 `/opt/pawcycle/control`에 clean detached checkout한다.

- `contract-sha`가 없는 최초 전환: `--adopt-contract-sha`에 이미 승인된 기존 운영 Control 기준 SHA를 전달한다. Script가 현재 Control HEAD와의 Release 계약 호환성을 확인한 뒤 현재 HEAD를 기록한다.
- `contract-sha`가 존재하지만 현재 Control HEAD와 다른 후속 전환: `--adopt-contract-sha`에 현재 Control HEAD를 정확히 전달한다. Script가 저장된 승인 Control과의 Release 계약 호환성을 확인한 뒤 상태를 갱신한다.

두 경우 모두 실제 Production 실행은 별도 고위험 승인과 적용 전후 검증을 요구한다.

## 복구·롤백 증거 (고위험 필수)

Fake lifecycle에서 다음 경계를 검증한다.

- 계약 상태 누락과 잘못된 실패 원인 탐지
- 최초 기준선과 현재 Control Release 계약 불일치 차단
- 명시적 승인 없는 Control HEAD drift 차단
- 비호환 Control 계약 전환 차단
- dirty Control worktree 차단
- Rollback 시 Control HEAD drift 차단
- 대상 Release 계약 불일치 차단
- image digest drift 차단
- 대상 활성화 실패 후 이전 Release 복구
- MySQL volume 삭제 금지

실제 Production Rollback 증거는 별도 실행 승인 후 남긴다.

## 위험과 제한

- Release 계약 allowlist에 실제 활성화 입력 파일이 추가되면 Validator와 Runbook을 함께 갱신해야 한다.
- Control HEAD 변경은 Script-only 변경이어도 명시적 채택이 필요하다.
- Compose·Nginx Release 계약이 실제로 변경되는 배포는 이 절차로 자동 승인하지 않으며 별도 Release bundle 또는 전환 작업이 필요하다.
- Application Rollback은 DB Schema·데이터 복구를 수행하지 않는다.

## 남은 위험

- 리뷰 후 최신 수정 Commit의 CI와 CodeRabbit 결과가 아직 확정되지 않았다.
- 실제 Production에서 최초 `contract-sha` 채택과 대상 Release 배포를 검증하지 않았다.
- 대상 Release와 운영 DB Schema 호환성은 실제 실행 직전에 다시 확인해야 한다.

## 다음 작업

1. PR #72 Repository Validation 전체 성공 확인
2. 최신 CodeRabbit 재검토 결과 확인
3. 사용자·Tech Lead 병합 판단
4. 병합 후 별도 고위험 승인으로 Production Control 전환과 Application 배포
5. OPS-020 회원 생성과 OPS-018 인증 Session Smoke

## 인수인계 생략

이 PR의 저장소 결과를 즉시 입력으로 받아 구현할 별도 역할이 없다. 다음 행동자는 PR #72를 검토·병합할 사용자이며, 실제 Production 운영자는 같은 사용자다. 필요한 실행 절차와 중단 조건은 갱신된 Runbook과 PR 본문에 있으므로 별도 역할 인수인계 문서를 만들지 않는다.

## Git 결과

- 작업 브랜치: `ops/sre`
- PR #72 Head에 리뷰 수정 Commit을 순차 반영
- Force push, Rebase, Amend와 History rewrite 미사용

## PR 결과

- PR: #72
- 현재 상태: Open, 수정 및 재검증 진행 중
- 자동 병합: 사용하지 않음
- 최종 병합: 사용자 직접 판단
